package com.example.asmrgame

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.asmrgame.ui.theme.ASMRGameTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        SpriteManager.load(this)

        setContent {
            ASMRGameTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MakeoverGameScreen(
                        onBackPressed = { finish() }
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun MakeoverGameScreenPreview() {
    ASMRGameTheme {
        MakeoverGameScreen(onBackPressed = {})
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun GameplayAnchorsPhonePreview() {
    ASMRGameTheme {
        MakeoverGameScreen(
            onBackPressed = {},
            initialScreen = GameScreen.GAMEPLAY,
            debugShowAnchors = true
        )
    }
}

@Preview(showBackground = true, widthDp = 768, heightDp = 1024)
@Composable
fun GameplayAnchorsTabletPreview() {
    ASMRGameTheme {
        MakeoverGameScreen(
            onBackPressed = {},
            initialScreen = GameScreen.GAMEPLAY,
            debugShowAnchors = true
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun GameplayPeelFrame1Preview() {
    ASMRGameTheme {
        MakeoverGameScreen(
            onBackPressed = {},
            initialScreen = GameScreen.GAMEPLAY,
            debugShowAnchors = true,
            previewPeelStage = 1
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun GameplayPeelFrame2Preview() {
    ASMRGameTheme {
        MakeoverGameScreen(
            onBackPressed = {},
            initialScreen = GameScreen.GAMEPLAY,
            debugShowAnchors = true,
            previewPeelStage = 2
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun GameplayPeelFrame3Preview() {
    ASMRGameTheme {
        MakeoverGameScreen(
            onBackPressed = {},
            initialScreen = GameScreen.GAMEPLAY,
            debugShowAnchors = true,
            previewPeelStage = 3
        )
    }
}
