package com.roonshield

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.roonshield.ui.RoonApp
import com.roonshield.ui.RoonViewModel
import com.roonshield.ui.theme.RoonShieldTheme

class MainActivity : ComponentActivity() {

    private val viewModel: RoonViewModel by viewModels()
    private var consumeUpKeyCode: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A remote should stay awake while it's on screen (no screensaver interrupting playback control).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        intent?.getStringExtra("start_tab")?.let { viewModel.initialTab = it }
        setContent {
            RoonShieldTheme {
                RoonApp(viewModel)
            }
        }
    }

    // singleTask: a launch while we're already running lands here instead of onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("start_tab")?.let { viewModel.requestTab(it) }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppOpened() // opening the app while music plays lands on Now Playing
    }

    override fun onResume() {
        super.onResume()
        viewModel.onUserInteraction() // start the idle countdown while foreground
    }

    override fun onPause() {
        super.onPause()
        viewModel.cancelIdle()
    }

    /** Any input resets the idle timer. While the screensaver is up, the first key wakes it and is
     *  consumed (so it doesn't also navigate), matching standard screensaver behaviour. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (viewModel.screensaver.value) {
            if (event.action == KeyEvent.ACTION_DOWN) consumeUpKeyCode = event.keyCode
            viewModel.onUserInteraction()
            return true
        }
        if (event.action == KeyEvent.ACTION_UP && event.keyCode == consumeUpKeyCode) {
            consumeUpKeyCode = null
            viewModel.onUserInteraction()
            return true
        }
        viewModel.onUserInteraction()
        return super.dispatchKeyEvent(event)
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
