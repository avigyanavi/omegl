package com.am24.omeglecreations

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.am24.omeglecreations.ui.theme.Omeglecreationstheme

class FullscreenVideoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoUrl = intent.getStringExtra("videoUrl") ?: return
        setContent {
            Omeglecreationstheme{
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    FullscreenVideoPlayer(videoUrl = videoUrl)
                }
            }
        }
    }
}

@Composable
fun FullscreenVideoPlayer(videoUrl: String) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = ExoPlayer.Builder(ctx).build().apply {
                    setMediaItem(MediaItem.fromUri(videoUrl))
                    prepare()
                    playWhenReady = true
                }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize()
    )
}
