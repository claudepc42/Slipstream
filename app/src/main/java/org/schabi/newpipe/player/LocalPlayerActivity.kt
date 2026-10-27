package org.schabi.newpipe.player

import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.StyledPlayerView
import org.schabi.newpipe.R

/**
 * Plays local video/audio files using ExoPlayer2. Saves and restores play position
 * in SharedPreferences keyed by the file URI so progress persists across sessions.
 */
class LocalPlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TITLE = "local_player_title"
        private const val PREFS_PREFIX = "local_player_progress_"
    }

    private var player: ExoPlayer? = null
    private lateinit var playerView: StyledPlayerView
    private lateinit var prefs: SharedPreferences
    private var fileUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_local_player)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        playerView = findViewById(R.id.local_player_view)

        fileUri = intent.data
        val title = intent.getStringExtra(EXTRA_TITLE)
        if (!title.isNullOrEmpty()) {
            supportActionBar?.title = title
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (fileUri != null) {
            initPlayer()
        } else {
            finish()
        }
    }

    private fun initPlayer() {
        val uri = fileUri ?: return
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            exo.setMediaItem(MediaItem.fromUri(uri))
            exo.prepare()

            val saved = prefs.getLong(prefKey(uri), 0L)
            if (saved > 0L) {
                exo.seekTo(saved)
            }
            exo.play()
        }
    }

    private fun saveProgress() {
        val uri = fileUri ?: return
        val exo = player ?: return
        if (exo.duration > 0 && exo.currentPosition > 0) {
            prefs.edit().putLong(prefKey(uri), exo.currentPosition).apply()
        }
    }

    private fun prefKey(uri: Uri) = PREFS_PREFIX + uri.toString().hashCode()

    override fun onPause() {
        super.onPause()
        saveProgress()
        player?.pause()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
    }

    override fun onDestroy() {
        saveProgress()
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun hideSystemUi() {
        playerView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
    }
}
