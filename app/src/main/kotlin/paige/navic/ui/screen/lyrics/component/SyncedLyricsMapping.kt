/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.lyrics.component

import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import paige.navic.domain.model.lyrics.LyricsLine
import paige.navic.domain.model.lyrics.LyricsResult
import paige.navic.domain.model.lyrics.LyricsWord

/** Length assumed for the final line when neither its duration nor the song length is known. */
private val TRAILING_LINE_DURATION = 4_000.milliseconds

/**
 * Maps the app lyric model onto the render model of `com.mocharealm.accompanist:lyrics-ui`.
 *
 * Lines that carry word timings become [KaraokeLine.MainKaraokeLine] so the renderer highlights
 * word by word; every other line becomes [SyncedLine], which the renderer sweeps from left to
 * right across the line duration.
 */
internal fun LyricsResult.toSyncedLyrics(songDuration: Duration): SyncedLyrics =
	SyncedLyrics(lines = lines.toSyncedLines(songDuration), title = "")

private fun List<LyricsLine>.toSyncedLines(songDuration: Duration): List<ISyncedLine> {
	val starts = map { it.time }
	return mapIndexedNotNull { index, line ->
		val start = starts[index] ?: return@mapIndexedNotNull null
		val end = line.endFrom(start, starts.drop(index + 1), songDuration)
			.coerceAtLeast(start)
		line.toSyncedLine(start.inWholeMilliseconds, end.inWholeMilliseconds)
	}
}

private fun LyricsLine.endFrom(
	start: Duration,
	followingStarts: List<Duration?>,
	songDuration: Duration
): Duration {
	duration?.let { return start + it }
	// A line with word timings ends when its last word does; otherwise it would stay focused
	// until the song ends.
	val lastWordEnd = words?.maxOfOrNull { it.time + it.duration }
	if (lastWordEnd != null && lastWordEnd > start) return lastWordEnd
	val nextStart = followingStarts.firstOrNull { it != null }
	if (nextStart != null) return nextStart
	return songDuration.takeIf { it > start } ?: (start + TRAILING_LINE_DURATION)
}

private fun LyricsLine.toSyncedLine(startMillis: Long, endMillis: Long): ISyncedLine {
	val translation = translatedText?.takeIf { it.isNotBlank() }
	val syllables = words?.toSyllables(text)
	if (syllables == null) {
		return SyncedLine(
			content = text,
			translation = translation,
			start = startMillis.toInt(),
			end = endMillis.toInt()
		)
	}
	return KaraokeLine.MainKaraokeLine(
		syllables = syllables,
		translation = translation,
		alignment = KaraokeAlignment.Start,
		start = startMillis.toInt(),
		end = endMillis.toInt()
	)
}

/**
 * Splits [sourceText] into timed syllables for the karaoke renderer.
 *
 * The renderer lays syllables out back to back, so the returned contents must concatenate back to
 * [sourceText] exactly, whitespace included: any gap between two words is prefixed to the word that
 * follows it. Returns `null` when fewer than two words are timed or when a word cannot be located
 * in [sourceText], in which case the caller falls back to line level highlighting.
 */
private fun List<LyricsWord>.toSyllables(sourceText: String): List<KaraokeSyllable>? {
	if (size < 2) return null
	val syllables = ArrayList<KaraokeSyllable>(size)
	var cursor = 0
	for (word in this) {
		if (word.text.isEmpty()) continue
		val index = sourceText.indexOf(word.text, startIndex = cursor, ignoreCase = true)
		if (index < 0) return null
		syllables += word.toSyllable(separator = sourceText.substring(cursor, index))
		cursor = index + word.text.length
	}
	if (syllables.isEmpty()) return null
	val last = syllables.last()
	syllables[syllables.lastIndex] =
		last.copy(content = last.content + sourceText.substring(cursor))
	return syllables
}

private fun LyricsWord.toSyllable(separator: String) = KaraokeSyllable(
	content = separator + text,
	start = time.inWholeMilliseconds.toInt(),
	end = (time + duration).inWholeMilliseconds.toInt()
)
