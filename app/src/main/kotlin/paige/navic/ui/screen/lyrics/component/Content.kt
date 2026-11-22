/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.lyrics.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.collections.immutable.ImmutableList
import paige.navic.R
import paige.navic.domain.model.lyrics.LyricsLine
import paige.navic.domain.model.lyrics.LyricsResult
import paige.navic.domain.model.lyrics.displayText

/** Longest selection, in characters, that can be shared in one sheet. */
private const val MAX_SELECTION_CHARS = 150

private val LineHorizontalPadding = 32.dp

/**
 * Body of the lyrics sheet.
 *
 * Synced lyrics are rendered by [AppleMusicLyrics]; picking lines to share, unsynced lyrics and
 * playback without auto scroll all fall back to plain lists, since the karaoke renderer always
 * follows playback.
 */
@Composable
fun LyricsScreenContent(
	data: LyricsResult?,
	onRefresh: () -> Unit,
	isSelecting: Boolean,
	selectedIndices: ImmutableList<Int>,
	onAddSelectedIndex: (Int) -> Unit,
	onRemoveSelectedIndex: (Int) -> Unit,
	onRestartAtIndex: (Int) -> Unit,
	onStartSelectionAt: (Int) -> Unit,
	onSeekToLine: (Duration) -> Unit,
	positionProvider: () -> Int,
	duration: Duration,
	autoScroll: Boolean,
	useBlur: Boolean,
	blurDelta: Float,
	textColor: Color,
	contentPadding: PaddingValues
) {
	val lyrics = data?.lines.orEmpty()
	if (data == null || lyrics.isEmpty()) {
		LyricsScreenPlaceholder(onRefresh = onRefresh)
		return
	}

	val providerName = data.providerName
	val selectedCharCount = selectedIndices.sumOf { index ->
		lyrics.getOrNull(index)?.displayText()?.length ?: 0
	}
	val toggleSelection: (Int) -> Unit = { index ->
		lyrics.getOrNull(index)?.let { line ->
			toggleLineSelection(
				index = index,
				lineLength = line.text.length,
				selectedIndices = selectedIndices,
				selectedCharCount = selectedCharCount,
				onAdd = onAddSelectedIndex,
				onRemove = onRemoveSelectedIndex,
				onRestart = onRestartAtIndex
			)
		}
	}

	when {
		isSelecting -> SelectableLyricsList(
			lines = lyrics,
			selectedIndices = selectedIndices,
			onToggleIndex = toggleSelection,
			providerName = providerName,
			contentPadding = contentPadding
		)

		data.isSynced && autoScroll -> Box(
			modifier = Modifier
				.fillMaxSize()
				.padding(contentPadding)
		) {
			val syncedLyrics = remember(data, duration) { data.toSyncedLyrics(duration) }
			AppleMusicLyrics(
				lyrics = syncedLyrics,
				positionProvider = positionProvider,
				onLineClick = { startMillis -> onSeekToLine(startMillis.milliseconds) },
				onLineLongClick = { startMillis ->
					val index = lyrics.indexOfFirst {
						it.time?.inWholeMilliseconds == startMillis.toLong()
					}
					if (index >= 0) onStartSelectionAt(index)
				},
				modifier = Modifier
					.fillMaxSize()
					.padding(horizontal = 16.dp),
				textColor = textColor,
				useBlur = useBlur,
				blurDelta = blurDelta
			)
		}

		else -> PlainLyricsList(
			lines = lyrics,
			providerName = providerName,
			textColor = textColor,
			contentPadding = contentPadding
		)
	}
}

/**
 * Applies one tap inside the selection: extends the range when the tapped line touches it,
 * shrinks it when an end of the range is tapped and restarts at the tapped line otherwise.
 */
private fun toggleLineSelection(
	index: Int,
	lineLength: Int,
	selectedIndices: List<Int>,
	selectedCharCount: Int,
	onAdd: (Int) -> Unit,
	onRemove: (Int) -> Unit,
	onRestart: (Int) -> Unit
) {
	if (index in selectedIndices) {
		val isRangeEnd = index == selectedIndices.first() || index == selectedIndices.last()
		if (isRangeEnd) onRemove(index) else onRestart(index)
		return
	}
	if (selectedIndices.isEmpty()) {
		if (lineLength <= MAX_SELECTION_CHARS) onAdd(index)
		return
	}
	if (selectedCharCount + lineLength > MAX_SELECTION_CHARS) return

	val isAdjacent = index == (selectedIndices.minOrNull() ?: index) - 1 ||
		index == (selectedIndices.maxOrNull() ?: index) + 1
	if (isAdjacent) onAdd(index) else onRestart(index)
}

@Composable
private fun SelectableLyricsList(
	lines: List<LyricsLine>,
	selectedIndices: ImmutableList<Int>,
	onToggleIndex: (Int) -> Unit,
	providerName: String?,
	contentPadding: PaddingValues
) {
	LazyColumn(
		modifier = Modifier.fillMaxSize(),
		contentPadding = contentPadding + PaddingValues(vertical = 16.dp)
	) {
		itemsIndexed(lines) { index, line ->
			val isSelected = index in selectedIndices
			LyricsLineRow(
				line = line,
				color = if (isSelected) {
					MaterialTheme.colorScheme.onPrimaryContainer
				} else {
					MaterialTheme.colorScheme.onSurface
				},
				modifier = Modifier
					.padding(horizontal = 16.dp, vertical = 2.dp)
					.clip(MaterialTheme.shapes.medium)
					.background(
						if (isSelected) {
							MaterialTheme.colorScheme.primaryContainer
						} else {
							Color.Transparent
						}
					)
					.clickable { onToggleIndex(index) }
			)
		}
		providerName?.let { name ->
			item { LyricsProviderFooter(providerName = name) }
		}
	}
}

@Composable
private fun PlainLyricsList(
	lines: List<LyricsLine>,
	providerName: String?,
	textColor: Color,
	contentPadding: PaddingValues
) {
	LazyColumn(
		modifier = Modifier.fillMaxSize(),
		contentPadding = contentPadding + PaddingValues(vertical = 16.dp)
	) {
		itemsIndexed(lines) { _, line ->
			LyricsLineRow(
				line = line,
				color = textColor,
				modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
			)
		}
		providerName?.let { name ->
			item { LyricsProviderFooter(providerName = name) }
		}
	}
}

@Composable
private fun LyricsLineRow(line: LyricsLine, color: Color, modifier: Modifier = Modifier) {
	Column(
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = LineHorizontalPadding, vertical = 12.dp)
	) {
		Text(
			text = line.text,
			color = color,
			fontSize = 22.sp,
			fontWeight = FontWeight.SemiBold
		)
		line.translatedText?.takeIf { it.isNotBlank() }?.let { translation ->
			Text(
				text = translation,
				color = color.copy(alpha = 0.7f),
				fontSize = 16.sp
			)
		}
	}
}

@Composable
private fun LyricsProviderFooter(providerName: String) {
	Text(
		text = stringResource(R.string.info_lyrics_provider, providerName),
		textAlign = TextAlign.Center,
		modifier = Modifier
			.fillMaxWidth()
			.padding(vertical = 16.dp)
	)
}
