/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.lyrics.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs
import paige.navic.R

/** Steps offered by the panel; 0.1s for fine tuning, 1s for a rough first correction. */
private val OffsetSteps = listOf(-1000L, -100L, 100L, 1000L)

/** Largest offset the panel allows in either direction. */
private const val MAX_OFFSET_MILLIS = 10_000L

/**
 * Adjusts the lyric time code offset of the current song.
 *
 * Positive values delay the lyrics, which is what a user needs when the lyrics run ahead of the
 * audio; the value is persisted per song by the caller.
 */
@Composable
internal fun LyricsOffsetPanel(
	offsetMillis: Long,
	onOffsetChange: (Long) -> Unit,
	onDismiss: () -> Unit,
	modifier: Modifier = Modifier
) {
	Surface(
		modifier = modifier.fillMaxWidth(),
		shape = MaterialTheme.shapes.large,
		color = MaterialTheme.colorScheme.surfaceContainerHigh,
		tonalElevation = 3.dp
	) {
		Column(
			modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = stringResource(R.string.title_lyrics_offset),
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.SemiBold
					)
					Text(
						text = stringResource(R.string.info_lyrics_offset_hint),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
				Text(
					text = formatOffset(offsetMillis),
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.Bold
				)
			}

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(4.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				OffsetSteps.forEach { step ->
					TextButton(
						onClick = {
							onOffsetChange(
								(offsetMillis + step).coerceIn(-MAX_OFFSET_MILLIS, MAX_OFFSET_MILLIS)
							)
						}
					) {
						Text(stepLabel(step))
					}
				}
				TextButton(onClick = { onOffsetChange(0L) }, enabled = offsetMillis != 0L) {
					Text(stringResource(R.string.action_reset))
				}
				TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
					Text(stringResource(R.string.action_done))
				}
			}
		}
	}
}

private fun stepLabel(millis: Long): String =
	if (millis < 0) "-${abs(millis) / 1000f}s" else "+${millis / 1000f}s"

private fun formatOffset(millis: Long): String {
	val seconds = millis / 1000f
	return String.format(Locale.US, "%+.1fs", seconds)
}
