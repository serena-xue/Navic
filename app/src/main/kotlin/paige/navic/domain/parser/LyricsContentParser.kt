/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.parser

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import paige.navic.domain.model.lyrics.LyricsLine
import paige.navic.domain.model.lyrics.LyricsWord
import paige.navic.util.Logger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Serializable
private data class YoulyResponse(
	val lyrics: List<YoulyLine> = emptyList()
)

@Serializable
private data class YoulyLine(
	val time: Long = 0L,
	val text: String = "",
	val syllabus: List<YoulySyllable>? = null
)

@Serializable
private data class YoulySyllable(
	val time: Long = 0L,
	val duration: Long = 0L,
	val text: String = ""
)

object LyricsContentParser {
	private val jsonParser = Json {
		isLenient = true
		explicitNulls = false
		ignoreUnknownKeys = true
	}

	fun parse(content: String): List<LyricsLine>? {
		val text = content.trim()
		if (text.isEmpty()) return null

		return try {
			if (text.startsWith("{")) {
				parseJson(text)
			} else {
				parseLrc(text)
			}
		} catch (e: Exception) {
			Logger.e("LyricRepository", "Lyrics parsing failed!", e)
			null
		}
	}

	private fun parseJson(jsonString: String): List<LyricsLine>? {
		val jsonObject = jsonParser.parseToJsonElement(jsonString).jsonObject

		val syncedStr = jsonObject["syncedLyrics"]?.jsonPrimitive?.contentOrNull
		if (!syncedStr.isNullOrEmpty()) {
			return parseLrc(syncedStr)
		}

		val plainStr = jsonObject["plainLyrics"]?.jsonPrimitive?.contentOrNull
		if (!plainStr.isNullOrEmpty()) {
			return normalize(plainStr.lineSequence()
				.mapNotNull { parseLyricsLine(it.trim()) }
				.toList())
		}

		if (jsonObject.containsKey("lyrics")) {
			val youlyResponse = jsonParser.decodeFromString<YoulyResponse>(jsonString)
			return parseYoulyResponse(youlyResponse)
		}

		return null
	}

	private fun parseYoulyResponse(response: YoulyResponse): List<LyricsLine>? {
		if (response.lyrics.isEmpty()) return null
		return normalize(response.lyrics.mapNotNull { line ->
			parseLyricsLine(line.text, line.time.milliseconds)?.copy(
				words = line.syllabus?.map { syl ->
					LyricsWord(syl.time.milliseconds, syl.duration.milliseconds, syl.text)
				}
			)
		})
	}

	private fun parseLrc(input: String): List<LyricsLine> {
		val lines = input.lineSequence().toList()

		if (!input.contains("[")) {
			return normalize(lines.mapNotNull { parseLyricsLine(text = it.trim()) })
		}

		return normalize(lines
			.filter { it.isNotBlank() }
			.flatMap { parseTimestampedLine(it) })
	}

	fun parseLine(text: String, time: kotlin.time.Duration? = null): LyricsLine? =
		parseLyricsLine(text, time)

	fun normalize(lines: List<LyricsLine>): List<LyricsLine> {
		if (lines.none { it.time != null }) return lines

		val sorted = lines.sortedBy { it.time }
		val merged = mutableListOf<LyricsLine>()
		for (line in sorted) {
			val previous = merged.lastOrNull()
			if (
				previous != null &&
				previous.time == line.time &&
				previous.translatedText.isNullOrBlank() &&
				line.translatedText.isNullOrBlank()
			) {
				merged[merged.lastIndex] = previous.copy(translatedText = line.text)
			} else {
				merged += line
			}
		}

		return merged.mapIndexed { index, line ->
			val start = line.time ?: return@mapIndexed line
			val nextStart = merged.getOrNull(index + 1)?.time
			val inferredDuration = when {
				nextStart != null -> (nextStart - start).coerceAtLeast(1.milliseconds)
				line.duration != null -> line.duration
				line.words?.isNotEmpty() == true -> {
					val end = line.words.maxOf { it.time + it.duration }
					(end - start).coerceAtLeast(1.milliseconds)
				}
				else -> null
			}
			line.copy(duration = inferredDuration)
		}
	}

	private fun parseTimestampedLine(line: String): List<LyricsLine> {
		val timestamps = mutableListOf<kotlin.time.Duration>()
		var remaining = line.trim()
		while (true) {
			val match = timestampPattern.find(remaining) ?: break
			timestamps += match.timestamp()
			remaining = remaining.removeRange(match.range).trimStart()
		}

		if (timestamps.isEmpty()) return listOfNotNull(parseLyricsLine(remaining))
		val lyric = parseLyricsLine(remaining) ?: return emptyList()
		return timestamps.map { lyric.copy(time = it) }
	}

	private fun MatchResult.timestamp(): kotlin.time.Duration {
		val minutes = groupValues[1].toLong()
		val seconds = groupValues[2].toLong()
		val fraction = groupValues[3]
		val fractionMillis = when (fraction.length) {
			0 -> 0L
			1 -> fraction.toLong() * 100
			2 -> fraction.toLong() * 10
			else -> fraction.take(3).toLong()
		}
		return minutes.minutes + seconds.seconds + fractionMillis.milliseconds
	}

	private fun parseLyricsLine(text: String, time: kotlin.time.Duration? = null): LyricsLine? {
		if (text.isBlank()) return null

		val separatorIndex = text.indexOf(" / ")
		if (separatorIndex < 0) {
			return LyricsLine(time = time, text = text.trim())
		}

		val original = text.substring(0, separatorIndex).trim()
		val translation = text.substring(separatorIndex + 3).trim()
		if (original.isEmpty()) return null

		return LyricsLine(
			time = time,
			text = original,
			translatedText = translation.takeIf { it.isNotEmpty() }
		)
	}

	private val timestampPattern =
		Regex("""^\[(\d+):(\d{1,2})(?:[.:](\d{1,3}))?\]""")
}
