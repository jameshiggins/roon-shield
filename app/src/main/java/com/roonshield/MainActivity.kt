package com.roonshield

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.roonshield.ui.RoonApp
import com.roonshield.ui.RoonViewModel
import com.roonshield.ui.theme.RoonShieldTheme

class MainActivity : ComponentActivity() {

    private val viewModel: RoonViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RoonShieldTheme {
                RoonApp(viewModel)
            }
        }
    }

    /**
     * Global controller / media-key mapping. These fire regardless of which view has D-pad
     * focus, so hardware transport keys and gamepad shoulder/face buttons always work.
     * D-pad arrows and the center/select button are intentionally left to Compose's focus
     * system for on-screen navigation.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_BUTTON_Y,
            -> viewModel.playPause()

            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_BUTTON_R1,
            -> viewModel.next()

            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_BUTTON_L1,
            -> viewModel.previous()

            KeyEvent.KEYCODE_MEDIA_STOP -> viewModel.stop()
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> viewModel.seekBy(30)
            KeyEvent.KEYCODE_MEDIA_REWIND -> viewModel.seekBy(-30)

            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }
}
