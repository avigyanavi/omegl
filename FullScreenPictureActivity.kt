package com.am24.omeglecreations

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import com.am24.omeglecreations.ui.theme.Omeglecreationstheme

class FullscreenPictureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pictureUrl = intent.getStringExtra("pictureUrl") ?: return
        setContent {
            Omeglecreationstheme{
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    FullscreenPictureViewer(pictureUrl = pictureUrl)
                }
            }
        }
    }
}

@Composable
fun FullscreenPictureViewer(pictureUrl: String) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        ImageRequest.Builder(context)
            .data(pictureUrl)
            .size(Size.ORIGINAL)
            .build()
    )

    Image(
        painter = painter,
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize()
    )
}
