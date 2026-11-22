/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.model.lyrics

import androidx.compose.runtime.Immutable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Immutable
data class LyricsLine(
	val time: Duration? = null,
	val duration: Duration? = null,
	val text: String,
	val translatedText: String? = null,
	val words: List<LyricsWord>? = null
)

fun LyricsLine.displayText(): String =
	translatedText
		?.takeIf { it.isNotBlank() }
		?.let { "$text\n$it" }
		?: text

fun LyricsLine.isStartedAt(currentDuration: Duration): Boolean =
	time != null && currentDuration >= time

fun LyricsLine.progressAt(
	currentDuration: Duration,
	fallbackEnd: Duration,
	preEmphasis: Duration = 0.milliseconds
): Float {
	val start = time ?: return 1f
	val lineDuration = (duration ?: (fallbackEnd - start))
		.coerceAtLeast(1.milliseconds)
	val effectiveStart = start - preEmphasis
	val effectiveDuration = lineDuration + preEmphasis

	return when {
		currentDuration < effectiveStart -> 0f
		currentDuration >= effectiveStart + effectiveDuration -> 1f
		else -> ((currentDuration - effectiveStart) / effectiveDuration)
			.toFloat()
			.coerceIn(0f..1f)
	}
}
