/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.lyrics.component

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlin.time.Duration

/**
 * Returns a frame accurate playback clock for the karaoke renderer.
 *
 * The player only reports [progress] five times per second, which the renderer would turn into
 * visibly stepped word highlighting. The reported value is therefore used as an anchor that is
 * advanced every frame by the elapsed wall clock time; each new report re-anchors the clock, so
 * drift never exceeds one report interval.
 *
 * The returned lambda reads Compose state, so it must be sampled outside composition (the renderer
 * samples it while drawing).
 */
@Composable
internal fun rememberPlaybackPosition(
	progress: Float,
	duration: Duration,
	isPaused: Boolean
): () -> Int {
	val durationMillis = duration.inWholeMilliseconds.coerceAtLeast(0L)
	val position = remember { mutableIntStateOf(0) }
	val anchorPosition = remember { mutableLongStateOf(0L) }
	val anchorRealtime = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

	LaunchedEffect(progress, durationMillis) {
		anchorPosition.longValue = (progress * durationMillis).toLong()
		anchorRealtime.longValue = SystemClock.elapsedRealtime()
		position.intValue = anchorPosition.longValue.toInt()
	}

	LaunchedEffect(isPaused, durationMillis) {
		if (isPaused) return@LaunchedEffect
		while (true) {
			withFrameNanos { }
			val elapsed = SystemClock.elapsedRealtime() - anchorRealtime.longValue
			position.intValue =
				(anchorPosition.longValue + elapsed).coerceIn(0L, durationMillis).toInt()
		}
	}

	return remember { { position.intValue } }
}
