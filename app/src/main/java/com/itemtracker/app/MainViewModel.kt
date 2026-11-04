package com.itemtracker.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Hold the app state and apply every change, saving locally and syncing with the shared file
 *
 * @param application the [Application] to use
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val TAG = "MainViewModel"
        const val SYNC_DELAY_MILLIS = 1500L
        val SYNC_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    }

    private val syncMutex = Mutex()
    private var syncDelayJob: Job? = null

    private val mutableData = MutableStateFlow(Store.loadLocal(application))
    private val mutableSettings = MutableStateFlow(SettingsStore.load(application))
    private val mutableSyncStatus = MutableStateFlow<String?>(null)
    private val mutableMessage = MutableStateFlow<String?>(null)
    private val mutablePendingShare = MutableStateFlow<Amazon.Shared?>(null)
    private val mutableUpdateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    private val mutableLaunchUpdate = MutableStateFlow<Updater.Release?>(null)

    /** All items, including deleted tombstones */
    val data: StateFlow<AppData> = mutableData.asStateFlow()

    val settings: StateFlow<Settings> = mutableSettings.asStateFlow()

    /** Last sync result, such as "Synced 9:41 PM" or an error */
    val syncStatus: StateFlow<String?> = mutableSyncStatus.asStateFlow()

    /** One-off message for a snackbar */
    val message: StateFlow<String?> = mutableMessage.asStateFlow()

    /** Product shared into the app, waiting to pre-fill the add dialog */
    val pendingShare: StateFlow<Amazon.Shared?> = mutablePendingShare.asStateFlow()

    /** Progress of the in-app updater */
    val updateState: StateFlow<UpdateState> = mutableUpdateState.asStateFlow()

    /** Newer release found when the app was opened, waiting for the update pop-up */
    val launchUpdate: StateFlow<Updater.Release?> = mutableLaunchUpdate.asStateFlow()

    private val context: Context get() = getApplication()

    /** Steps of checking for, downloading and installing an update */
    sealed interface UpdateState {
        data object Idle : UpdateState
        data object Checking : UpdateState
        data object UpToDate : UpdateState
        data class Available(val release: Updater.Release) : UpdateState
        data object Downloading : UpdateState
        data object NeedsPermission : UpdateState
        data class Failed(val reason: String) : UpdateState
    }

    /** Mark the snackbar message as shown */
    fun consumeMessage() {
        mutableMessage.value = null
    }

    /** Mark the shared product as handled */
    fun consumeShare() {
        mutablePendingShare.value = null
    }

    /**
     * Save new settings
     *
     * @param newSettings the [Settings] to save
     */
    fun updateSettings(newSettings: Settings) {
        mutableSettings.value = newSettings
        SettingsStore.save(context, newSettings)
    }

    /**
     * Pick up a product shared from another app
     *
     * @param intent the [Intent] that opened the app
     */
    fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        if (text.isNullOrBlank() && subject.isNullOrBlank()) return
        mutablePendingShare.value = Amazon.parseShare(text, subject)
        // Clear the action so the share is not handled again after rotation
        intent.action = null
    }

    /**
     * Add an item
     *
     * @param name the short name
     * @param amazonUrl the product link, or null
     * @param quantity units per use
     * @param imageBase64 the picture, or null
     */
    fun addItem(name: String, amazonUrl: String?, quantity: Int, imageBase64: String?) = mutate { current ->
        val item = Item(
            name = name.trim(),
            amazonUrl = amazonUrl?.trim()?.ifBlank { null },
            quantity = quantity.coerceAtLeast(1),
            imageBase64 = imageBase64,
        )
        current.copy(items = current.items + item)
    }

    /**
     * Change the details of an item; a blank name keeps the old name
     *
     * @param id the item id
     * @param details the new [ItemDetails]
     */
    fun updateItem(id: String, details: ItemDetails) = mutateItem(id) { item ->
        item.copy(
            name = details.name.trim().ifBlank { item.name },
            amazonUrl = details.amazonUrl?.trim()?.ifBlank { null },
            quantity = details.quantity.coerceAtLeast(1),
            subscription = details.subscription,
            lastDelivery = details.lastDelivery,
            updatedAt = System.currentTimeMillis(),
        )
    }

    /** Editable details of an item */
    data class ItemDetails(
        val name: String,
        val amazonUrl: String?,
        val quantity: Int,
        val subscription: String?,
        val lastDelivery: LocalDate?,
    )

    /**
     * Set or remove the picture of an item
     *
     * @param id the item id
     * @param imageBase64 the picture, or null to remove it
     */
    fun setImage(id: String, imageBase64: String?) = mutateItem(id) { item ->
        item.copy(imageBase64 = imageBase64, updatedAt = System.currentTimeMillis())
    }

    /**
     * Delete an item, keeping a tombstone for the sync file
     *
     * @param id the item id
     */
    fun deleteItem(id: String) = mutateItem(id) { item ->
        item.copy(deleted = true, updatedAt = System.currentTimeMillis())
    }

    /**
     * Start using an item, or change the start date of the current use
     *
     * @param id the item id
     * @param date the start date
     */
    fun start(id: String, date: LocalDate = LocalDate.now()) = mutateItem(id) { item ->
        item.copy(activeStart = date, updatedAt = System.currentTimeMillis())
    }

    /**
     * Forget the current use without recording it, for accidental starts
     *
     * @param id the item id
     */
    fun cancelCurrent(id: String) = mutateItem(id) { item ->
        item.copy(activeStart = null, updatedAt = System.currentTimeMillis())
    }

    /**
     * Stop the current use and add it to the history
     *
     * @param id the item id
     * @param endDate the day the item ran out; dates before the start count as the start date
     */
    fun stop(id: String, endDate: LocalDate = LocalDate.now()) = mutateItem(id) { item ->
        val start = item.activeStart ?: return@mutateItem item
        val end = if (endDate.isBefore(start)) start else endDate
        item.copy(
            activeStart = null,
            history = item.history + UsageEntry(start = start, end = end, quantity = item.quantity),
            updatedAt = System.currentTimeMillis(),
        )
    }

    /**
     * Add a past use
     *
     * @param id the item id
     * @param start the start date
     * @param end the run-out date
     * @param quantity units the use covered
     */
    fun addEntry(id: String, start: LocalDate, end: LocalDate, quantity: Int) = mutateItem(id) { item ->
        item.copy(history = item.history + UsageEntry(start = start, end = end, quantity = quantity.coerceAtLeast(1)))
    }

    /**
     * Replace a history entry
     *
     * @param id the item id
     * @param entry the changed [UsageEntry]
     */
    fun updateEntry(id: String, entry: UsageEntry) = mutateItem(id) { item ->
        val updated = entry.copy(updatedAt = System.currentTimeMillis())
        item.copy(history = item.history.map { existing -> if (existing.id == entry.id) updated else existing })
    }

    /**
     * Delete a history entry, keeping a tombstone for the sync file
     *
     * @param id the item id
     * @param entryId the entry id
     */
    fun deleteEntry(id: String, entryId: String) = mutateItem(id) { item ->
        val now = System.currentTimeMillis()
        item.copy(history = item.history.map { entry ->
            if (entry.id == entryId) entry.copy(deleted = true, updatedAt = now) else entry
        })
    }

    /** Name, picture and final link read from an Amazon product page */
    data class FetchResult(val title: String?, val imageBase64: String?, val url: String?)

    /**
     * Read the name and picture of an Amazon product
     *
     * @param url the product link
     * @return the details, or null when nothing could be read
     */
    suspend fun fetchAmazon(url: String): FetchResult? = withContext(Dispatchers.IO) {
        val product = Amazon.fetchProduct(url) ?: return@withContext null
        val image = product.imageUrl?.let(Amazon::downloadBytes)?.let(ImageUtil::fromBytes)
        if (product.title == null && image == null) null else FetchResult(product.title, image, product.finalUrl)
    }

    /**
     * Read, shrink and encode a picture the user picked
     *
     * @param uri the picture URI
     * @return the base64 JPEG, or null when the picture cannot be read
     */
    suspend fun imageFromUri(uri: Uri): String? = withContext(Dispatchers.IO) { ImageUtil.fromUri(context, uri) }

    /**
     * Export all items to a CSV document
     *
     * @param uri the document URI
     */
    fun exportCsv(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            Store.writeUri(context, uri, Csv.export(mutableData.value))
            mutableMessage.value = "Exported ${mutableData.value.liveItems.size} items"
        }
        catch (e: IOException) {
            Log.e(TAG, "Failed to export CSV to $uri", e)
            mutableMessage.value = "Export failed: ${e.message}"
        }
        catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to export CSV to $uri", e)
            mutableMessage.value = "Export failed: ${e.message}"
        }
    }

    /**
     * Replace all items with the contents of a CSV document, backing up the current data first
     *
     * @param uri the document URI
     */
    fun importCsv(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val current = mutableData.value
            val imported = Csv.importItems(Store.readUri(context, uri), current.items)
            Store.backup(context, current)
            mutate { data -> AppData(tombstoneAll(data.items) + imported) }
            mutableMessage.value = "Imported ${imported.size} items (previous data backed up)"
        }
        catch (e: IllegalArgumentException) {
            Log.w(TAG, "Rejected CSV import from $uri", e)
            mutableMessage.value = "Import failed: ${e.message}"
        }
        catch (e: IOException) {
            Log.e(TAG, "Failed to read CSV from $uri", e)
            mutableMessage.value = "Import failed: could not read that file"
        }
    }

    /**
     * Check whether an import backup exists
     *
     * @return true when there is a backup to restore
     */
    fun hasBackup(): Boolean = Store.latestBackup(context) != null

    /** Replace all items with the newest import backup */
    fun restoreLatestBackup() = viewModelScope.launch(Dispatchers.IO) {
        val file = Store.latestBackup(context)
        if (file == null) {
            mutableMessage.value = "No backup found"
            return@launch
        }
        try {
            val backup = Store.fromJson(file.readText())
            val backupIds = backup.items.map(Item::id).toSet()
            val now = System.currentTimeMillis()
            // New timestamps make the restored copy win when merged with the sync file
            val restored = backup.items.map { item ->
                item.copy(updatedAt = now, history = item.history.map { entry -> entry.copy(updatedAt = now) })
            }
            mutate { data -> AppData(restored + tombstoneAll(data.items.filter { item -> item.id !in backupIds })) }
            mutableMessage.value = "Restored backup"
        }
        catch (e: IllegalArgumentException) {
            Log.e(TAG, "Failed to restore backup ${file.name}", e)
            mutableMessage.value = "Could not restore backup"
        }
        catch (e: IOException) {
            Log.e(TAG, "Failed to read backup ${file.name}", e)
            mutableMessage.value = "Could not restore backup"
        }
    }

    /**
     * Start syncing with a new, empty file the user created
     *
     * @param uri the document URI
     */
    fun createSyncFile(uri: Uri) = linkSync(uri, "Sync file created")

    /**
     * Start syncing with an existing file, merging its data into this phone
     *
     * @param uri the document URI
     */
    fun linkSyncFile(uri: Uri) = linkSync(uri, "Linked to sync file")

    /** Stop syncing; the data stays on this phone and in the file */
    fun stopSync() {
        updateSettings(mutableSettings.value.copy(syncUri = null))
        mutableSyncStatus.value = null
    }

    /** Read the shared file, merge it with the data on this phone, then save both */
    suspend fun syncNow() {
        val uri = mutableSettings.value.syncUri?.let(Uri::parse) ?: return
        syncMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val remote = Store.fromJson(Store.readUri(context, uri))
                    mutableData.update { current -> Store.merge(current, remote) }
                    val merged = mutableData.value
                    Store.saveLocal(context, merged)
                    Store.writeUri(context, uri, Store.toJson(merged))
                    mutableSyncStatus.value = "Synced " + LocalTime.now().format(SYNC_TIME_FORMAT)
                }
                catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Sync file $uri is not Subscription Tracker data", e)
                    mutableSyncStatus.value =
                        "The sync file is not Subscription Tracker data, so it was not overwritten"
                }
                catch (e: SecurityException) {
                    Log.w(TAG, "Lost access to sync file $uri", e)
                    mutableSyncStatus.value = "Lost access to the sync file; choose it again in Settings"
                }
                catch (e: IOException) {
                    Log.w(TAG, "Failed to sync with $uri", e)
                    mutableSyncStatus.value = "Sync failed: ${e.message ?: "file unavailable"}"
                }
                catch (e: IllegalStateException) {
                    Log.w(TAG, "Failed to sync with $uri", e)
                    mutableSyncStatus.value = "Sync failed: ${e.message ?: "file unavailable"}"
                }
            }
        }
    }

    /** Ask GitHub whether a newer release exists */
    fun checkForUpdate() = viewModelScope.launch(Dispatchers.IO) {
        mutableUpdateState.value = UpdateState.Checking
        try {
            val release = Updater.latestRelease()
            mutableUpdateState.value = if (Updater.isNewer(release.version, BuildConfig.VERSION_NAME)) {
                UpdateState.Available(release)
            }
            else {
                UpdateState.UpToDate
            }
        }
        catch (e: IOException) {
            Log.w(TAG, "Failed to check for updates", e)
            mutableUpdateState.value = UpdateState.Failed("Could not reach GitHub")
        }
        catch (e: IllegalStateException) {
            Log.w(TAG, "Failed to read the latest release", e)
            mutableUpdateState.value = UpdateState.Failed(e.message ?: "Could not read the latest release")
        }
    }

    /** Quietly check for a newer release when the app is freshly opened; failures are ignored */
    fun checkForUpdateOnLaunch() = viewModelScope.launch(Dispatchers.IO) {
        try {
            val release = Updater.latestRelease()
            if (Updater.isNewer(release.version, BuildConfig.VERSION_NAME)) mutableLaunchUpdate.value = release
        }
        catch (e: IOException) {
            Log.i(TAG, "Skipped the launch update check", e)
        }
        catch (e: IllegalStateException) {
            Log.i(TAG, "Skipped the launch update check", e)
        }
    }

    /** Close the launch update pop-up */
    fun dismissLaunchUpdate() {
        mutableLaunchUpdate.value = null
    }

    /**
     * Download a release and open the Android installer, or the install permission setting when it is missing
     *
     * @param release the [Updater.Release] to install
     */
    fun installUpdate(release: Updater.Release) = viewModelScope.launch(Dispatchers.IO) {
        if (!Updater.canInstall(context)) {
            mutableUpdateState.value = UpdateState.NeedsPermission
            mutableMessage.value = "Allow installing apps for Subscription Tracker, then try the update again"
            Updater.openInstallPermissionSettings(context)
            return@launch
        }
        mutableUpdateState.value = UpdateState.Downloading
        mutableMessage.value = "Downloading ${release.version}…"
        try {
            val apk = Updater.download(context, release)
            mutableUpdateState.value = UpdateState.Available(release)
            Updater.install(context, apk)
        }
        catch (e: IOException) {
            Log.w(TAG, "Failed to download ${release.apkUrl}", e)
            mutableUpdateState.value = UpdateState.Failed("Download failed")
            mutableMessage.value = "Update download failed"
        }
    }

    private fun linkSync(uri: Uri, status: String) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        catch (e: SecurityException) {
            // Some providers do not offer lasting access; sync still works until the app restarts
            Log.w(TAG, "No persistable access to $uri", e)
        }
        updateSettings(mutableSettings.value.copy(syncUri = uri.toString()))
        mutableSyncStatus.value = status
        viewModelScope.launch { syncNow() }
    }

    /** Mark every live item deleted so a replacement also reaches the sync file */
    private fun tombstoneAll(items: List<Item>): List<Item> {
        val now = System.currentTimeMillis()
        return items.map { item -> if (item.deleted) item else item.copy(deleted = true, updatedAt = now) }
    }

    private fun mutate(change: (AppData) -> AppData) {
        mutableData.update(change)
        val snapshot = mutableData.value
        viewModelScope.launch(Dispatchers.IO) { Store.saveLocal(context, snapshot) }
        scheduleSync()
    }

    private fun mutateItem(id: String, change: (Item) -> Item) = mutate { current ->
        current.copy(items = current.items.map { item -> if (item.id == id) change(item) else item })
    }

    private fun scheduleSync() {
        if (mutableSettings.value.syncUri == null) return
        syncDelayJob?.cancel()
        syncDelayJob = viewModelScope.launch {
            delay(SYNC_DELAY_MILLIS)
            withContext(NonCancellable) { syncNow() }
        }
    }
}
