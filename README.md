# Subscription Tracker

(Formerly Item Tracker. The package name `com.itemtracker.app` is unchanged, so updates install over earlier versions.)

A personal Android app that tracks how long household items last, so you can set subscription and reorder frequencies.

## Build and install

### Option 0: from your phone (GitHub builds it)
Every push to `main` runs the **Build APK** workflow (`.github/workflows/build.yml`), which runs the tests and publishes
`SubscriptionTracker-<version>.apk` under **Releases**. On your phone, open the repository → Releases → tap the APK → Install.
The app version lives in `version.txt`. A push publishes a release (tagged `v<version>`) only when that version has not
been released yet, so bump it for every release: patch for fixes, minor for features. Android's internal versionCode is
derived from it (0.1.1 → 101). Builds are signed with `app/release.keystore`, so each new build installs over the previous one and keeps your data.

The app checks for a newer release each time it's freshly opened and offers to update. You can also update from: **Settings → Check for updates → Update to x.y.z**.
The first time, Android asks you to allow Subscription Tracker to install apps. This needs the repository to stay public, since
the app reads the latest release without signing in to GitHub.

### Option A: Android Studio (about 15 minutes, mostly downloads)
1. Install **Android Studio** (free): https://developer.android.com/studio
2. Unzip this folder, then **File → Open** and choose the `ItemTracker` folder. Let Gradle sync finish.
   The build uses Gradle 9.1 and Android Gradle Plugin 9.0 (built-in Kotlin), so use a current Android Studio.
3. On your phone, turn on **Developer options** (Settings → About phone → tap *Build number* 7 times), then turn on **USB debugging**.
4. Plug the phone in, pick it in the device dropdown, and press **Run ▶**. The app installs and opens.

To get an APK file instead: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. The file lands in `app/build/outputs/apk/debug/app-debug.apk`. Copy it to the phone and open it (allow "install unknown apps" when asked).

### Option B: Claude Code on your computer
With Android Studio installed (for the Android SDK), run `claude` in this folder and ask:
"build the debug APK and fix any build errors".

### Tests
Unit tests (JUnit 6 + AssertJ) cover the logic: averages, rounding, order dates, CSV, sync merging and share parsing.
Run them with `./gradlew test` or right-click the `test` folder in Android Studio → Run.

## How it works

- **Tap** an item that isn't in use to start it. You'll be asked to confirm; "Other day…" picks a past start date.
- **Tap** an item that's in use to stop it. You'll be asked to confirm. "Other day…" lets you choose when it ran out.
- **Long-press** an item to set a past start date, or to correct the current one.
- **Green border** = in use. **Dark border** = not in use.
- **☰ (bottom left)**: history, edit, picture, Reorder on Amazon, estimated order date.
  Tap an entry to edit it, swipe left to delete it, or use **+ Add past use** to backfill.
- **🗑 (bottom right)**: delete the item (asks first).
- **+ (bottom middle)**: add an item. From the Amazon app, **Share → Subscription Tracker** fills in the link, name and picture.
- **Order badge (top right)** appears when the order date is within your "due soon" window. Those items sort first, then everything is A→Z.

### Subscriptions
In **☰ → Edit**, set the item's subscription frequency and the date of a past delivery. The app rolls that date forward
by the frequency to find the next delivery.
- With no finished uses yet, the tile shows the subscription in *gray italics* as a rough estimate, and the order
  badge uses it. It never counts toward the average. Once there's a real average, it shows in bold.
- With history, a red **⚠** (top-left) and red border appear when the subscription disagrees with the average:
  the frequency doesn't match it, the next delivery comes after you run out (beyond the grace days), or it comes more
  than one week or month (the subscription's unit) before you need it. Tap the ⚠ for details and a suggested frequency.

### The numbers
- Each use's length is stored in exact days and divided by the quantity it covered (per-unit days).
  The average is the per-unit mean × the item's current quantity, so changing the quantity rescales it.
- Shown values round **up** to: 1, 2, 3 weeks, 1 month, 5, 6, 7 weeks, 2 months, 3 months, …
  (Months allow ~1.5 days of slack because calendar months vary in length.)
- **Run-out** = in-use start + exact average.
- **Order date** = the first order day (Settings) on or after run-out, if that's within "days you can go without" (default 7).
  Otherwise, the order day one month earlier.

### Google Drive sync
Settings → *Create a new sync file*, then choose **Drive** in the picker (the Google Drive app must be installed).
The app reads and merges the file on open and writes it shortly after each change.
For partner mode, both phones choose the same file ("Use an existing sync file"). The newest change to each item and each history entry wins, and deletes carry over.
If your partner shared the file with you, add it to your My Drive (Drive → ⋮ → Organize → Add shortcut) so it shows up in the picker.

### CSV
`name,amazon_url,quantity,in_use_since,subscription,last_delivery,use_start,use_end,use_quantity` with one row per past use and dates as `YYYY-MM-DD`.
Import replaces everything, but a backup is saved first (Settings → *Restore data from before the last import*).

### Amazon
Amazon retired its product API for apps like this in 2026. The replacement requires an affiliate account with sales.
The app reads the product page's title and picture the way a link preview does. If Amazon blocks that, add a picture yourself from the ☰ screen.
