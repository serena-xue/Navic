/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.lyrics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.time.Duration
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.di.LocalNavStack
import paige.navic.di.LocalSheetState
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.model.DomainSong
import paige.navic.domain.model.settings.NowPlayingBackgroundStyle
import paige.navic.domain.model.settings.ToolbarPosition
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.component.common.BlendBackground
import paige.navic.ui.component.common.ErrorBox
import paige.navic.ui.component.layout.SheetScaffold
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.PersistentViewModelStoreOwner
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screen.lyrics.component.DEFAULT_BLUR_DELTA
import paige.navic.ui.screen.lyrics.component.LyricsScreenContent
import paige.navic.ui.screen.lyrics.component.LyricsScreenLoadingView
import paige.navic.ui.screen.lyrics.component.LyricsScreenPlaceholder
import paige.navic.ui.screen.lyrics.component.LyricsScreenToolbar
import paige.navic.ui.screen.lyrics.component.rememberPlaybackPosition
import paige.navic.ui.screen.lyrics.dialog.LyricsShareSheet
import paige.navic.ui.screen.lyrics.viewmodel.LyricsScreenViewModel
import paige.navic.ui.util.KeepScreenOn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(song: DomainSong?) {
	val backStack = LocalNavStack.current

	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsStateWithLifecycle()
	val preferenceManager = koinInject<PreferenceManager>()

	val viewModel = koinViewModel<LyricsScreenViewModel>(
		key = song?.id,
		parameters = { parametersOf(song) },
		viewModelStoreOwner = koinInject<PersistentViewModelStoreOwner>()
	)
	val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()

	var isSelecting by rememberSaveable { mutableStateOf(false) }
	val selectedIndices = rememberSaveable { mutableStateListOf<Int>() }
	var wasPlayingBeforeSelection by rememberSaveable { mutableStateOf(false) }
	var shareSheetOpen by rememberSaveable { mutableStateOf(false) }

	val currentSong = song ?: return LyricsScreenPlaceholder(
		onRefresh = { viewModel.refreshResults() }
	)
	val duration = currentSong.duration
	val positionProvider = rememberPlaybackPosition(
		progress = playerState.progress,
		duration = duration,
		isPaused = playerState.isPaused
	)

	val showArtworkBackground =
		preferenceManager.nowPlayingBackgroundStyle == NowPlayingBackgroundStyle.Dynamic
	val textColor = if (showArtworkBackground) {
		Color.White
	} else {
		MaterialTheme.colorScheme.onBackground
	}

	val spatialSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
	val effectSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()

	val beginSelection: () -> Unit = {
		if (!isSelecting) {
			wasPlayingBeforeSelection = !playerState.isPaused
			player.pause()
			isSelecting = true
		}
	}

	val toggleIsSelecting = {
		if (isSelecting) {
			isSelecting = false
			selectedIndices.clear()
			if (wasPlayingBeforeSelection) {
				player.resume()
			}
		} else {
			beginSelection()
		}
	}

	val startSelectionAt: (Int) -> Unit = { index ->
		beginSelection()
		selectedIndices.clear()
		selectedIndices.add(index)
	}

	val seekToLine: (Duration) -> Unit = { time ->
		if (duration > Duration.ZERO) {
			player.seek((time / duration).toFloat())
			if (playerState.isPaused) {
				player.resume()
			}
		}
	}

	val sheetState = LocalSheetState.current
	val closeScope = rememberCoroutineScope()
	val animateToDismiss = {
		closeScope.launch {
			sheetState.hide()
		}.invokeOnCompletion {
			if (!sheetState.isVisible) {
				backStack.remove(Screen.Lyrics)
			}
		}
	}

	if (preferenceManager.lyricsKeepAlive) {
		KeepScreenOn()
	}

	SheetScaffold(
		toolbar = { windowInsets ->
			LyricsScreenToolbar(
				onDismissRequest = { animateToDismiss() },
				onShare = { shareSheetOpen = true },
				isSelecting = isSelecting,
				toggleIsSelecting = toggleIsSelecting,
				windowInsets = windowInsets,
				selectedIndices = selectedIndices.toImmutableList()
			)
		},
		toolbarPosition = ToolbarPosition.Top
	) { contentPadding ->
		Box(Modifier.fillMaxSize()) {
			if (showArtworkBackground) {
				BlendBackground(
					coverArtId = currentSong.coverArtId,
					isPaused = playerState.isPaused
				)
			}
			AnimatedContent(
				targetState = lyricsState,
				modifier = Modifier.fillMaxSize(),
				transitionSpec = {
					ContentTransform(
						targetContentEnter = fadeIn(effectSpec) + scaleIn(spatialSpec, 0.8f),
						initialContentExit = fadeOut(effectSpec) + scaleOut(spatialSpec)
					)
				}
			) { lyricsState ->
				when (lyricsState) {
					is UiState.Error -> ErrorBox(
						error = lyricsState,
						modifier = Modifier.wrapContentSize(),
						onRetry = { viewModel.refreshResults() }
					)

					is UiState.Loading -> LyricsScreenLoadingView()

					is UiState.Success -> LyricsScreenContent(
						data = lyricsState.data,
						onRefresh = { viewModel.refreshResults() },
						isSelecting = isSelecting,
						selectedIndices = selectedIndices.toImmutableList(),
						onAddSelectedIndex = { index -> selectedIndices.add(index) },
						onRemoveSelectedIndex = { index -> selectedIndices.remove(index) },
						onRestartAtIndex = { index ->
							selectedIndices.clear()
							selectedIndices.add(index)
						},
						onStartSelectionAt = startSelectionAt,
						onSeekToLine = seekToLine,
						positionProvider = positionProvider,
						duration = duration,
						autoScroll = preferenceManager.lyricsAutoscroll,
						useBlur = preferenceManager.lyricsBlur,
						blurDelta = if (preferenceManager.lyricsBrightInactive) {
							1f
						} else {
							DEFAULT_BLUR_DELTA
						},
						textColor = textColor,
						contentPadding = contentPadding
					)
				}
			}

			if (shareSheetOpen) {
				val lyricsList = lyricsState.data?.lines?.map { line ->
					(line.time?.inWholeMilliseconds ?: 0L) to line.text
				}

				if (lyricsList != null) {
					val sortedIndices = selectedIndices.sorted()
					val stringsToShare = sortedIndices.mapNotNull { index ->
						lyricsList.getOrNull(index)?.second
					}.toImmutableList()

					LyricsShareSheet(
						song = currentSong,
						selectedLyrics = stringsToShare,
						onDismiss = { shareSheetOpen = false },
						onShare = {
							shareSheetOpen = false
							isSelecting = false
							selectedIndices.clear()
						}
					)
				}
			}
		}
	}
}
