package com.itemtracker.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Relay a share into the app's own window, then close
 *
 * Android starts a share target inside the sharing app's task (Amazon, for example). This invisible activity runs
 * there only long enough to forward the shared text to [MainActivity] in the app's own task, bringing an open copy
 * forward instead of starting a second one.
 */
class ShareActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val forward = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, intent.getStringExtra(Intent.EXTRA_TEXT))
            putExtra(Intent.EXTRA_SUBJECT, intent.getStringExtra(Intent.EXTRA_SUBJECT))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(forward)
        finish()
    }
}
