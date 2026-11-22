/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.playback.exoplayer.impl.notification

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import org.json.JSONObject
import paige.navic.playback.exoplayer.impl.cache.NavicDataSource

/**
 * Adds the HyperOS media share payload to the notification produced by media3.
 *
 * HyperOS reads [FOCUS_MEDIA_EXTRA] from the notification extras rather than from app metadata.
 * Failing to build the payload must never prevent the media notification itself from being shown.
 */
@OptIn(UnstableApi::class)
class HyperOsMediaNotificationProvider(
	private val delegate: MediaNotification.Provider
) : MediaNotification.Provider {

	override fun createNotification(
		mediaSession: MediaSession,
		mediaButtonPreferences: ImmutableList<CommandButton>,
		actionFactory: MediaNotification.ActionFactory,
		onNotificationChangedCallback: MediaNotification.Provider.Callback
	): MediaNotification {
		val notification = delegate.createNotification(
			mediaSession,
			mediaButtonPreferences,
			actionFactory,
			onNotificationChangedCallback
		)

		runCatching {
			val mediaItem = mediaSession.player.currentMediaItem ?: return@runCatching
			val songId = mediaItem.mediaId.takeIf {
				it.isNotBlank() && !it.startsWith("radio_") && !it.startsWith("search:")
			} ?: return@runCatching
			val metadata = mediaItem.mediaMetadata
			val title = metadata.title?.toString()?.trim().orEmpty()
				.ifEmpty { return@runCatching }
			val content = listOfNotNull(
				metadata.artist?.toString()?.trim()?.takeIf(String::isNotEmpty),
				metadata.albumTitle?.toString()?.trim()?.takeIf(String::isNotEmpty)
			).joinToString(" · ")

			val shareData = JSONObject()
				.put("title", title)
				.put("content", content)
				.put("shareContent", songId.buildDeepLink())

			val params = JSONObject()
				.put("param_v2", JSONObject()
					.put("param_island", JSONObject()
						.put("shareData", shareData)
					)
				)

			notification.notification.extras.putString(FOCUS_MEDIA_EXTRA, params.toString())
		}

		return notification
	}

	override fun handleCustomCommand(
		session: MediaSession,
		action: String,
		extras: Bundle
	): Boolean = delegate.handleCustomCommand(session, action, extras)

	override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
		delegate.notificationChannelInfo

	private fun String.buildDeepLink() = android.net.Uri.Builder()
		.scheme(NavicDataSource.NAVIC_SCHEME)
		.authority(NavicDataSource.NAVIC_SONG_HOST)
		.appendPath(this)
		.build()
		.toString()

	private companion object {
		const val FOCUS_MEDIA_EXTRA = "miui.focus.param.media"
	}
}
