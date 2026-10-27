package org.schabi.newpipe.player

import android.content.SharedPreferences
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.mediarouter.app.MediaRouteButton
import androidx.preference.PreferenceManager
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ext.cast.CastPlayer
import com.google.android.exoplayer2.ext.cast.SessionAvailabilityListener
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import org.schabi.newpipe.R
import org.schabi.newpipe.cast.LocalFileHttpServer
import java.net.Inet4Address
import java.net.NetworkInterface

class LocalPlayerActivity : AppCompatActivity(), SessionAvailabilityListener {

    companion object {
        const val EXTRA_TITLE = "local_player_title"
        const val EXTRA_VIDEO_ID = "local_player_video_id"
        const val EXTRA_MIME_TYPE = "local_player_mime_type"
        private const val PREFS_PREFIX = "local_player_progress_"
    }

    private var localPlayer: ExoPlayer? = null
    private var castPlayer: CastPlayer? = null
    private lateinit var playerView: StyledPlayerView
    private lateinit var prefs: SharedPreferences
    private var fileUri: Uri? = null
    private var progressKey: String? = null
    private var videoTitle: String = ""
    private var mimeType: String = "video/mp4"
    private var fileSize: Long = -1L

    private var castContext: CastContext? = null
    private var fileServer: LocalFileHttpServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_local_player)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        playerView = findViewById(R.id.local_player_view)

        fileUri = intent.data
        videoTitle = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)
        mimeType = intent.getStringExtra(EXTRA_MIME_TYPE) ?: "video/mp4"

        progressKey = if (videoId != null) PREFS_PREFIX + videoId
                      else PREFS_PREFIX + fileUri.toString().hashCode()

        if (videoTitle.isNotEmpty()) supportActionBar?.title = videoTitle
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Resolve file size for range-request support in the HTTP server
        fileUri?.let { uri ->
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                fileSize = pfd.statSize
            }
        }

        try {
            castContext = CastContext.getSharedInstance(this)
        } catch (e: Exception) {
            // Cast not available (e.g. no Play Services) — local only
        }

        if (fileUri != null) {
            initLocalPlayer()
        } else {
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.local_player_menu, menu)
        val routeButton = menu.findItem(R.id.media_route_menu_item)?.actionView as? MediaRouteButton
        if (routeButton != null && castContext != null) {
            CastButtonFactory.setUpMediaRouteButton(this, routeButton)
        }
        return true
    }

    private fun initLocalPlayer() {
        val uri = fileUri ?: return
        localPlayer = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            exo.setMediaItem(MediaItem.fromUri(uri))
            exo.prepare()

            val saved = prefs.getLong(progressKey ?: return@also, 0L)
            if (saved > 0L) exo.seekTo(saved)
            exo.play()
        }

        // Wire Cast session listener so we can hand off when Cast connects
        val ctx = castContext ?: return
        castPlayer = CastPlayer(ctx).also { cp ->
            cp.setSessionAvailabilityListener(this)
        }
    }

    // Called when a Cast session becomes available
    override fun onCastSessionAvailable() {
        val uri = fileUri ?: return
        val cp = castPlayer ?: return

        // Save local progress before handing off
        saveProgress()
        localPlayer?.pause()

        // Start local HTTP server to serve the file to Chromecast
        val ip = getLocalIpAddress() ?: run {
            // Can't determine IP — fall back to local player
            localPlayer?.play()
            return
        }

        val server = LocalFileHttpServer(contentResolver)
        server.setFile(uri, mimeType, fileSize)
        if (!server.isAlive) server.start()
        fileServer = server

        val streamUrl = server.localUrl(ip)
        cp.setMediaItem(com.google.android.exoplayer2.MediaItem.fromUri(streamUrl))
        cp.prepare()
        cp.play()
        playerView.player = cp
    }

    // Called when the Cast session ends — return to local player
    override fun onCastSessionUnavailable() {
        fileServer?.stop()
        fileServer = null
        playerView.player = localPlayer
        localPlayer?.play()
    }

    private fun saveProgress() {
        val key = progressKey ?: return
        val position = (playerView.player as? ExoPlayer)?.let {
            if (it.duration > 0 && it.currentPosition > 0) it.currentPosition else null
        } ?: localPlayer?.let {
            if (it.duration > 0 && it.currentPosition > 0) it.currentPosition else null
        } ?: return
        prefs.edit().putLong(key, position).apply()
    }

    override fun onPause() {
        super.onPause()
        saveProgress()
        localPlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
    }

    override fun onDestroy() {
        saveProgress()
        localPlayer?.release()
        localPlayer = null
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        castPlayer = null
        fileServer?.stop()
        fileServer = null
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        @Suppress("DEPRECATION")
        onBackPressed()
        return true
    }

    private fun hideSystemUi() {
        @Suppress("DEPRECATION")
        playerView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
    }

    private fun getLocalIpAddress(): String? {
        return try {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            if (ip != 0) {
                val bytes = byteArrayOf(
                    (ip and 0xff).toByte(),
                    (ip shr 8 and 0xff).toByte(),
                    (ip shr 16 and 0xff).toByte(),
                    (ip shr 24 and 0xff).toByte()
                )
                Inet4Address.getByAddress(bytes).hostAddress
            } else {
                // Fallback: iterate network interfaces
                NetworkInterface.getNetworkInterfaces()?.toList()
                    ?.flatMap { it.inetAddresses.toList() }
                    ?.filterIsInstance<Inet4Address>()
                    ?.firstOrNull { !it.isLoopbackAddress }
                    ?.hostAddress
            }
        } catch (e: Exception) {
            null
        }
    }
}
