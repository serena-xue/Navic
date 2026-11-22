/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : AppCompatActivity() {
	private var deepLinkUri by mutableStateOf<Uri?>(null)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		if (savedInstanceState == null) {
			deepLinkUri = intent?.data
		}
		setContent {
			App(
				deepLink = deepLinkUri,
				onDeepLinkHandled = { deepLinkUri = null }
			)
		}
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		deepLinkUri = intent.data
	}
}
