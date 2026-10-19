package com.quantumvikings.weightloss

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/** Opened by Health Connect to explain what step data is used for: shows the privacy policy. */
class PermissionsRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://quantumvikings.com/weightloss/privacy.html")))
        }
        finish()
    }
}
