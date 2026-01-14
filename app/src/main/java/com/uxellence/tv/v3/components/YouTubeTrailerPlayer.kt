package com.uxellence.tv.v3.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.uxellence.tv.v3.utils.YouTubeStreamExtractor

private const val TAG = "YouTubeTrailerPlayer"

/**
 * YouTube Trailer Player używający NewPipe Extractor + ExoPlayer.
 * 
 * Ekstrakcja stream URL odbywa się asynchronicznie przy pierwszym renderze.
 * Odtwarzanie jest wyciszone i zapętlone (idealne dla trailerów w tle).
 * 
 * @param youtubeUrl URL YouTube (watch?v=..., youtu.be/..., embed/...)
 * @param modifier Modifier dla kontenera
 */
@Composable
fun YouTubeTrailerPlayer(
    youtubeUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Check if URL is a direct playable URL (no extraction needed)
    val isDirectUrl = remember(youtubeUrl) {
        youtubeUrl.endsWith(".mp4") ||
        youtubeUrl.endsWith(".m3u8") ||
        youtubeUrl.contains("supabase.co/storage")
    }

    // Stream URL state - initialized immediately for direct URLs
    var streamUrl by remember(youtubeUrl) {
        mutableStateOf(if (isDirectUrl) youtubeUrl else null)
    }
    var isLoading by remember(youtubeUrl) { mutableStateOf(!isDirectUrl) }
    var extractionFailed by remember(youtubeUrl) { mutableStateOf(false) }

    // Log direct URL detection
    LaunchedEffect(youtubeUrl) {
        Log.d(TAG, "YouTubeTrailerPlayer: url=$youtubeUrl, isDirectUrl=$isDirectUrl")
    }

    // Extract stream URL asynchronously ONLY for YouTube URLs (not direct)
    LaunchedEffect(youtubeUrl) {
        if (isDirectUrl) {
            Log.d(TAG, "Direct URL detected, skipping extraction: $youtubeUrl")
            return@LaunchedEffect
        }

        if (youtubeUrl.isBlank()) {
            Log.w(TAG, "Empty YouTube URL provided")
            isLoading = false
            extractionFailed = true
            return@LaunchedEffect
        }

        Log.d(TAG, "Starting extraction for YouTube URL: $youtubeUrl")
        isLoading = true
        extractionFailed = false

        val extracted = YouTubeStreamExtractor.extractStreamUrl(youtubeUrl)

        if (extracted != null) {
            Log.d(TAG, "Stream URL extracted successfully: ${extracted.take(60)}...")
            streamUrl = extracted
        } else {
            Log.e(TAG, "Failed to extract stream URL")
            extractionFailed = true
        }

        isLoading = false
    }

    // Show nothing while loading or if extraction failed
    // (transparent background, no visual feedback - just use backdrop as fallback)
    if (isLoading || extractionFailed || streamUrl == null) {
        Log.d(TAG, "YouTubeTrailerPlayer: waiting... isLoading=$isLoading, failed=$extractionFailed, url=${streamUrl?.take(30)}")
        // Return empty box - AnimatedVisibility in parent will handle fallback
        Box(
            modifier = modifier.background(Color.Transparent)
        )
        return
    }

    Log.d(TAG, "YouTubeTrailerPlayer: Ready to play! url=${streamUrl?.take(60)}...")

    // ExoPlayer instance
    val exoPlayer = remember(streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            // DIAGNOSTIC: Enable sound to verify playback is working
            // Change back to 0f after debugging
            volume = 1f

            // Loop forever
            repeatMode = Player.REPEAT_MODE_ALL

            // Set media source
            val mediaItem = MediaItem.fromUri(streamUrl!!)
            setMediaItem(mediaItem)

            // Prepare and auto-play
            prepare()
            playWhenReady = true

            Log.d(TAG, "ExoPlayer created with SOUND ENABLED for: ${streamUrl?.take(80)}...")
        }
    }

    // Cleanup on dispose
    DisposableEffect(streamUrl) {
        Log.d(TAG, "DisposableEffect started for stream")
        onDispose {
            Log.d(TAG, "Releasing ExoPlayer")
            try {
                exoPlayer.stop()
                exoPlayer.release()
                Log.d(TAG, "ExoPlayer released successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing ExoPlayer: ${e.message}")
            }
        }
    }

    // Use StyledPlayerView with RESIZE_MODE_ZOOM for fullscreen without letterboxing
    // Key forces recreation when URL changes (fixes state issues on trailer switch)
    key(streamUrl) {
        AndroidView(
            factory = { ctx ->
                StyledPlayerView(ctx).apply {
                    // RESIZE_MODE_ZOOM = CENTER_CROP - fills screen, crops excess
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM

                    // Hide all controls (trailer runs silently in background)
                    useController = false

                    // Attach player
                    player = exoPlayer

                    Log.d(TAG, "StyledPlayerView created for: ${streamUrl?.take(50)}")
                }
            },
            modifier = modifier.background(Color.Black),
            update = { playerView ->
                // Re-attach player if changed
                playerView.player = exoPlayer
            }
        )
    }
}

/**
 * Preview/Debug version that shows loading state
 */
@Composable
fun YouTubeTrailerPlayerWithDebug(
    youtubeUrl: String,
    modifier: Modifier = Modifier,
    showDebug: Boolean = false
) {
    val context = LocalContext.current
    
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(youtubeUrl) {
        if (youtubeUrl.isBlank()) {
            errorMessage = "Empty URL"
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        errorMessage = null
        
        try {
            val extracted = YouTubeStreamExtractor.extractStreamUrl(youtubeUrl)
            if (extracted != null) {
                streamUrl = extracted
            } else {
                errorMessage = "Extraction failed"
            }
        } catch (e: Exception) {
            errorMessage = e.message
        }
        
        isLoading = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (streamUrl != null) {
            YouTubeTrailerPlayer(
                youtubeUrl = youtubeUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Debug overlay (only if showDebug = true)
        if (showDebug) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.7f))
                    .fillMaxSize()
            ) {
                androidx.compose.material3.Text(
                    text = when {
                        isLoading -> "Loading..."
                        errorMessage != null -> "Error: $errorMessage"
                        streamUrl != null -> "Playing: ${streamUrl?.take(50)}..."
                        else -> "Unknown state"
                    },
                    color = Color.White
                )
            }
        }
    }
}
