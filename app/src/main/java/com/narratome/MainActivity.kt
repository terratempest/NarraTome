package com.narratome

import android.os.Bundle
import android.content.Intent
import android.app.SearchManager
import android.provider.MediaStore
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.narratome.player.PlaybackConnector
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.narratome.data.local.auth.TokenStore
import com.narratome.presentation.theme.AudiobookTheme
import com.narratome.presentation.root.AudiobookRoot
import com.narratome.sync.BackgroundWorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var playbackConnector: PlaybackConnector

    @Inject
    lateinit var workScheduler: BackgroundWorkScheduler

    @Inject
    lateinit var tokenStore: TokenStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!tokenStore.getToken().isNullOrBlank()) {
            workScheduler.schedulePeriodicMaintenance()
        }
        enableEdgeToEdge()
        setContent {
            AudiobookTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AudiobookRoot()
                }
            }
        }
        if (savedInstanceState == null) handleSearchIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSearchIntent(intent)
    }

    private fun handleSearchIntent(intent: Intent) {
        if (intent.action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return
        lifecycleScope.launch {
            try {
                playbackConnector.playFromSearch(intent.getStringExtra(SearchManager.QUERY).orEmpty())
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                val message = if (error is NoSuchElementException) "No matching audiobook available." else
                    "Unable to start voice playback. Check Server settings."
                android.widget.Toast.makeText(this@MainActivity, message,
                    android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}
