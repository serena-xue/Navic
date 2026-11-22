/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.navigation

import android.net.Uri

sealed interface NavicDeepLink {
	data class Open(val screen: Screen) : NavicDeepLink
	data class Play(val query: String) : NavicDeepLink
}

fun parseNavicDeepLink(uri: Uri): NavicDeepLink? {
	if (!uri.scheme.equals("navic", ignoreCase = true)) return null

	when (uri.host?.lowercase()) {
		"play" -> return uri.getQueryParameter("query")
			?.trim()
			?.takeIf { it.isNotEmpty() }
			?.let(NavicDeepLink::Play)

		"song" -> return uri.firstPathId()
			?.let { NavicDeepLink.Open(Screen.SongDetailScreen(it)) }

		"album", "playlist" -> return uri.firstPathId()
			?.let { NavicDeepLink.Open(Screen.CollectionDetail(it)) }

		"artist" -> return uri.firstPathId()
			?.let { NavicDeepLink.Open(Screen.ArtistDetail(it)) }

		else -> return null
	}
}

private fun Uri.firstPathId(): String? =
	pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }
