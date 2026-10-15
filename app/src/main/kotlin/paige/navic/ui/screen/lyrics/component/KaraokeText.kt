/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.lyrics.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import paige.navic.domain.manager.PreferenceManager

@Composable
fun LyricsScreenKaraokeText(
	text: String,
	translatedText: String?,
	progress: Float,
	isActive: Boolean,
	isSynced: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier
) {
	val preferenceManager = koinInject<PreferenceManager>()

	val smoothProgress by animateFloatAsState(
		targetValue = progress,
		animationSpec = spring(stiffness = Spring.StiffnessLow, visibilityThreshold = 0.001f)
	)

	val lyricsBrightInactive = preferenceManager.lyricsBrightInactive

	val inactiveAlpha = when {
		!isSynced -> 1f
		lyricsBrightInactive -> 0.9f
		else -> 0.35f
	}

	val alphaTransition by animateFloatAsState(
		targetValue = if (isActive) 1f else inactiveAlpha,
		animationSpec = spring(stiffness = Spring.StiffnessLow)
	)

	Box(modifier = modifier.clickable { onClick() }) {
		androidx.compose.foundation.layout.Column {
			KaraokeTextLine(
				text = text,
				progress = smoothProgress,
				isActive = isActive,
				alphaTransition = alphaTransition,
				lyricsBrightInactive = lyricsBrightInactive,
			)
			translatedText?.takeIf { it.isNotBlank() }?.let {
				Text(
					text = it,
					fontSize = 32.sp,
					fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
					style = MaterialTheme.typography.headlineLargeEmphasized,
					color = if (isActive) Color.White.copy(alpha = alphaTransition)
					else if (lyricsBrightInactive) Color.White.copy(alpha = 0.36f)
					else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
					modifier = Modifier.fillMaxWidth()
				)
			}
	}
	}
}

@Composable
private fun KaraokeTextLine(
	text: String,
	progress: Float,
	isActive: Boolean,
	alphaTransition: Float,
	lyricsBrightInactive: Boolean
) {
	val layoutResult = remember(text) { mutableStateOf<TextLayoutResult?>(null) }
	val isRtl = layoutResult.value?.let { layout ->
		(0 until layout.lineCount).any { index ->
			layout.getBidiRunDirection(layout.getLineStart(index)) == ResolvedTextDirection.Rtl
		}
	} ?: false

	Box(modifier = Modifier.fillMaxWidth()) {
		Text(
			text = text,
			fontSize = 32.sp,
			fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
			textAlign = if (isRtl) TextAlign.End else TextAlign.Start,
			style = MaterialTheme.typography.headlineLargeEmphasized,
			color = if (lyricsBrightInactive) Color.White.copy(alpha = alphaTransition * 0.4f)
			else MaterialTheme.colorScheme.onSurface.copy(alpha = alphaTransition * 0.4f),
			onTextLayout = {
				layoutResult.value = it
			},
			modifier = Modifier.fillMaxWidth()
		)

		if (isActive) {
			Text(
				text = text,
				fontSize = 32.sp,
				fontWeight = FontWeight.Bold,
				textAlign = if (isRtl) TextAlign.End else TextAlign.Start,
				style = MaterialTheme.typography.headlineLargeEmphasized,
				color = Color.White,
				modifier = Modifier
					.fillMaxWidth()
					.alpha(alphaTransition)
					.karaokeFill(layoutResult.value, progress)
			)
		}
	}
}

private fun Modifier.karaokeFill(
	textLayoutResult: TextLayoutResult?,
	progress: Float
) = graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
	.drawWithCache {
		onDrawWithContent {
			val layout = textLayoutResult ?: return@onDrawWithContent
			drawContent()

			val totalWidth = (0 until layout.lineCount).sumOf {
				(layout.getLineRight(it) - layout.getLineLeft(it)).toDouble()
			}.toFloat()
			val feather = 50f
			val currentPixelTarget = ((totalWidth + feather * 2) * progress) - feather
			var accumulatedWidth = 0f

			for (index in 0 until layout.lineCount) {
				val lineLeft = layout.getLineLeft(index)
				val lineRight = layout.getLineRight(index)
				val lineWidth = lineRight - lineLeft
				if (lineWidth <= 0f) continue

				val lineTop = layout.getLineTop(index)
				val lineBottom = layout.getLineBottom(index)
				val isRtl =
					layout.getBidiRunDirection(layout.getLineStart(index)) == ResolvedTextDirection.Rtl
				val startFade = currentPixelTarget - accumulatedWidth - feather
				val endFade = currentPixelTarget - accumulatedWidth + feather
				val startX = if (isRtl) lineRight else lineLeft
				val endX = if (isRtl) lineLeft else lineRight

				drawRect(
					brush = Brush.linearGradient(
						0f to Color.White,
						(startFade / lineWidth).coerceIn(0f, 1f) to Color.White,
						(endFade / lineWidth).coerceIn(0f, 1f) to Color.Transparent,
						1f to Color.Transparent,
						start = Offset(startX, 0f),
						end = Offset(endX, 0f)
					),
					topLeft = Offset(lineLeft, lineTop),
					size = Size(lineWidth, lineBottom - lineTop),
					blendMode = BlendMode.SrcIn
				)
				accumulatedWidth += lineWidth
			}
		}
	}
