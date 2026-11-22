/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.lyrics.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.ui.composable.list.rememberLyricsLazyListState
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.LyricsAnchor
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.LyricsFade

private val LyricTextStyle = TextStyle(
	fontSize = 30.sp,
	fontWeight = FontWeight.Bold,
	letterSpacing = (-0.4).sp,
	lineHeight = TextUnit.Unspecified,
	textMotion = TextMotion.Animated
)

private val TranslationTextStyle = TextStyle(
	fontSize = 18.sp,
	fontWeight = FontWeight.SemiBold,
	lineHeight = TextUnit.Unspecified,
	textMotion = TextMotion.Static
)

/** The focused line sits a third of the way down, as in Apple Music, instead of being centred. */
private val ActiveLineAnchor = LyricsAnchor.Fraction(0.38f)

/** Spacing between lines while the karaoke renderer animates their height. */
private val LineSpacing = 20.dp

/** Blur radius added per line away from the focused one; Apple Music steps 2-6pt. */
internal const val DEFAULT_BLUR_DELTA = 3f

/**
 * Apple Music style lyrics surface for a single song.
 *
 * Delegates all playback-synced rendering to `KaraokeLyricsView`: word level fill driven by
 * syllable timings, per-word scale and glow for Latin text, group glow for CJK, distance based
 * blur on the surrounding lines and a spring follow that keeps the focused line on [ActiveLineAnchor].
 *
 * @param positionProvider playback position in milliseconds, sampled outside composition.
 * @param onLineClick invoked with the start of the tapped line, in milliseconds.
 * @param onLineLongClick invoked when a line is long-pressed, used to start a selection.
 */
@Composable
internal fun AppleMusicLyrics(
	lyrics: SyncedLyrics,
	positionProvider: () -> Int,
	onLineClick: (startMillis: Int) -> Unit,
	onLineLongClick: (startMillis: Int) -> Unit,
	modifier: Modifier = Modifier,
	textColor: Color = Color.White,
	useBlur: Boolean = true,
	blurDelta: Float = DEFAULT_BLUR_DELTA
) {
	val listState = rememberLyricsLazyListState()
	KaraokeLyricsView(
		listState = listState,
		lyrics = lyrics,
		currentPosition = positionProvider,
		onLineClicked = { line -> onLineClick(line.start) },
		onLinePressed = { line -> onLineLongClick(line.start) },
		modifier = modifier,
		normalLineTextStyle = LyricTextStyle,
		translationTextStyle = TranslationTextStyle,
		textColor = textColor,
		useBlurEffect = useBlur,
		blurDelta = blurDelta,
		showPhonetic = false,
		anchor = ActiveLineAnchor,
		topFade = LyricsFade.ToAnchor(16.dp),
		bottomFade = LyricsFade.Fraction(0.35f),
		itemSpacing = LineSpacing,
		// The default additive blend washes the text out over light themes.
		blendMode = BlendMode.SrcOver
	)
}
