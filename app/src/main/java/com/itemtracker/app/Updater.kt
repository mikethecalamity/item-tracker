package com.itemtracker.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.core.content.FileProvider
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Find, download and install new releases published on GitHub */
object Updater {
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/mikethecalamity/item-tracker/releases/latest"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val UPDATE_DIRECTORY = "updates"
    private const val UPDATE_FILE = "ItemTracker-update.apk"
    private const val TIMEOUT_MILLIS = 15_000

    /** A published release and its APK download link */
    data class Release(val version: String, val apkUrl: String)

    /**
     * Ask GitHub for the latest release; blocking, so call it on Dispatchers.IO
     *
     * @return the latest release
     * @throws IOException if GitHub cannot be reached
     * @throws IllegalStateException if the latest release has no APK
     */
    fun latestRelease(): Release {
        val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("GitHub answered ${connection.responseCode}")
            }
            return parseRelease(connection.inputStream.use { input -> input.readBytes().toString(Charsets.UTF_8) })
        }
        finally {
            connection.disconnect()
        }
    }

    /**
     * Read a release from the GitHub API response
     *
     * @param json the response body of the latest-release endpoint
     * @return the release
     * @throws IllegalStateException if the release has no APK or the response is not a release
     */
    fun parseRelease(json: String): Release = try {
        val release = JSONObject(json)
        val assets = release.getJSONArray("assets")
        val apkUrl = (0 until assets.length()).map(assets::getJSONObject)
            .firstOrNull { asset -> asset.getString("name").endsWith(".apk") }
            ?.getString("browser_download_url")
            ?: throw IllegalStateException("The latest release has no APK")
        Release(release.getString("tag_name").removePrefix("v"), apkUrl)
    }
    catch (e: JSONException) {
        throw IllegalStateException("Unexpected answer from GitHub", e)
    }

    /**
     * Compare two versions such as 0.1.10 and 0.1.9 part by part
     *
     * @param candidate the version that might be newer
     * @param current the installed version
     * @return true when the candidate is newer
     */
    fun isNewer(candidate: String, current: String): Boolean {
        val candidateParts = candidate.split(".").map { part -> part.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { part -> part.toIntOrNull() ?: 0 }
        val comparison = (0 until maxOf(candidateParts.size, currentParts.size))
            .map { index -> candidateParts.getOrElse(index) { 0 }.compareTo(currentParts.getOrElse(index) { 0 }) }
            .firstOrNull { result -> result != 0 }
        return comparison != null && comparison > 0
    }

    /**
     * Download an APK into the app cache; blocking, so call it on Dispatchers.IO
     *
     * @param context the [Context] to use
     * @param release the [Release] to download
     * @return the downloaded file
     * @throws IOException if the download fails
     */
    fun download(context: Context, release: Release): File {
        val directory = File(context.cacheDir, UPDATE_DIRECTORY).apply { mkdirs() }
        val file = File(directory, UPDATE_FILE)
        val connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Download answered ${connection.responseCode}")
            }
            connection.inputStream.use { input -> file.outputStream().use(input::copyTo) }
        }
        finally {
            connection.disconnect()
        }
        return file
    }

    /**
     * Check whether Android lets this app open the installer
     *
     * @param context the [Context] to use
     * @return true when installing is allowed
     */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /**
     * Open the Android setting that allows this app to install updates
     *
     * @param context the [Context] to use
     */
    fun openInstallPermissionSettings(context: Context) {
        val packageUri = Uri.parse("package:${context.packageName}")
        val intent = Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri)
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Open the Android installer for a downloaded APK
     *
     * @param context the [Context] to use
     * @param apk the downloaded APK
     */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
