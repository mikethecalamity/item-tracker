package com.itemtracker.app

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

private const val APK_URL = "https://github.com/mikethecalamity/item-tracker/releases/download/v0.2.0/app.apk"

/** Tests for [Updater] */
internal class UpdaterTest {

    @Test
    fun parseReleaseTest() {
        val json = """
            {"tag_name": "v0.2.0", "assets": [
                {"name": "notes.txt", "browser_download_url": "https://example.com/notes.txt"},
                {"name": "app.apk", "browser_download_url": "$APK_URL"}
            ]}
        """

        assertThat(Updater.parseRelease(json)).isEqualTo(Updater.Release("0.2.0", APK_URL))
    }

    @Test
    fun parseReleaseTestNoApk() {
        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy { Updater.parseRelease("""{"tag_name": "v0.2.0", "assets": []}""") }
            .withMessage("The latest release has no APK")
    }

    @Test
    fun parseReleaseTestNotARelease() {
        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy { Updater.parseRelease("""{"message": "Not Found"}""") }
            .withMessage("Unexpected answer from GitHub")
    }

    @ParameterizedTest(name = "{0} newer than {1}: {2}")
    @CsvSource(
        "0.1.4, 0.1.3, true",
        "0.1.10, 0.1.9, true",
        "0.2.0, 0.1.9, true",
        "1.0, 0.9.9, true",
        "0.1.3, 0.1.3, false",
        "0.1.2, 0.1.3, false",
        "0.1.3, 0.1.3.0, false",
    )
    fun isNewerTest(candidate: String, current: String, expected: Boolean) {
        assertThat(Updater.isNewer(candidate, current)).isEqualTo(expected)
    }
}
