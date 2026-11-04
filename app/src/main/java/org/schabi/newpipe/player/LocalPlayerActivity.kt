package org.schabi.newpipe.player

import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.util.MimeTypes
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.launch
import org.schabi.newpipe.R
import org.schabi.newpipe.cast.AirPlayReceiver
import org.schabi.newpipe.cast.AirPlaySender
import org.schabi.newpipe.cast.LocalFileHttpServer
import org.schabi.newpipe.util.BookmarkHelper
import org.schabi.newpipe.util.SubtitleHelper
import org.schabi.newpipe.util.WatchProgressHelper

class LocalPlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TITLE = "local_player_title"
        const val EXTRA_VIDEO_ID = "local_player_video_id"
        const val EXTRA_MIME_TYPE = "local_player_mime_type"
    }

    private var localPlayer: ExoPlayer? = null
    private lateinit var playerView: StyledPlayerView
    private lateinit var prefs: SharedPreferences
    private var fileUri: Uri? = null
    private var progressKey: String? = null
    private var bookmarkKey: String = ""
    private var videoTitle: String = ""
    private var mimeType: String = "video/mp4"
    private var fileSize: Long = -1L

    private var airPlaySender: AirPlaySender? = null
    private var fileServer: LocalFileHttpServer? = null
    private var currentAirPlayReceiver: AirPlayReceiver? = null
    private val discoveredReceivers = mutableListOf<AirPlayReceiver>()

    private lateinit var topBar: View
    private lateinit var titleView: TextView

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

        val resolvedKey = videoId ?: fileUri.toString().hashCode().toString()
        progressKey = WatchProgressHelper.PREFS_POSITION_PREFIX + resolvedKey
        bookmarkKey = resolvedKey

        topBar = findViewById(R.id.local_player_top_bar)
        playerView.setControllerVisibilityListener(
            StyledPlayerView.ControllerVisibilityListener { visibility ->
                topBar.visibility = visibility
            }
        )
        titleView = findViewById(R.id.local_player_title)
        if (videoTitle.isNotEmpty()) titleView.text = videoTitle

        findViewById<ImageButton>(R.id.local_player_back).setOnClickListener {
            @Suppress("DEPRECATION") onBackPressed()
        }
        findViewById<ImageButton>(R.id.local_player_airplay).setOnClickListener {
            showAirPlayPicker()
        }
        findViewById<ImageButton>(R.id.local_player_pip).setOnClickListener {
            enterPipIfSupported()
        }

        playerView.findViewById<ImageButton>(R.id.bookmark_add)?.apply {
            setOnClickListener { addBookmarkAtCurrentPosition() }
            setOnLongClickListener { showBookmarkDialog(); true }
        }
        playerView.findViewById<ImageButton>(R.id.bookmark_prev)?.setOnClickListener {
            val prev = BookmarkHelper.prevBookmark(
                this, bookmarkKey, localPlayer?.currentPosition ?: 0L)
            if (prev != null) localPlayer?.seekTo(prev)
            else Toast.makeText(this, "No earlier bookmarks", Toast.LENGTH_SHORT).show()
        }
        playerView.findViewById<ImageButton>(R.id.bookmark_next)?.setOnClickListener {
            val next = BookmarkHelper.nextBookmark(
                this, bookmarkKey, localPlayer?.currentPosition ?: 0L)
            if (next != null) localPlayer?.seekTo(next)
            else Toast.makeText(this, "No later bookmarks", Toast.LENGTH_SHORT).show()
        }

        fileUri?.let { uri ->
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                fileSize = pfd.statSize
            }
        }

        if (fileUri != null) {
            initLocalPlayer()
            startAirPlayDiscovery()
        } else {
            finish()
        }
    }

    private fun addBookmarkAtCurrentPosition() {
        val pos = localPlayer?.currentPosition ?: return
        BookmarkHelper.addBookmark(this, bookmarkKey, pos)
        Toast.makeText(this, "Bookmark added at ${formatDuration(pos)}", Toast.LENGTH_SHORT).show()
    }

    private fun showBookmarkDialog() {
        val bookmarks = BookmarkHelper.getBookmarks(this, bookmarkKey)
        if (bookmarks.isEmpty()) {
            Toast.makeText(this, "No bookmarks yet — tap the flag to add one", Toast.LENGTH_SHORT)
                .show()
            return
        }

        val dp8 = (8 * resources.displayMetrics.density).toInt()
        val dp48 = (48 * resources.displayMetrics.density).toInt()

        val scrollView = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp8, dp8, dp8, dp8)
        }
        scrollView.addView(container)

        var dialog: android.app.AlertDialog? = null

        fun rebuildRows() {
            container.removeAllViews()
            val currentBookmarks = BookmarkHelper.getBookmarks(this, bookmarkKey)
            for (posMs in currentBookmarks) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                }

                val timeBtn = TextView(this).apply {
                    text = formatDuration(posMs)
                    textSize = 16f
                    setPadding(dp8, dp8, dp8, dp8)
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    setOnClickListener {
                        localPlayer?.seekTo(posMs)
                        dialog?.dismiss()
                    }
                }

                val deleteBtn = ImageButton(this).apply {
                    setImageResource(R.drawable.ic_delete)
                    background = null
                    layoutParams = LinearLayout.LayoutParams(dp48, dp48)
                    contentDescription = "Delete bookmark"
                    setOnClickListener {
                        BookmarkHelper.removeBookmark(this@LocalPlayerActivity, bookmarkKey, posMs)
                        rebuildRows()
                        if (BookmarkHelper.getBookmarks(this@LocalPlayerActivity, bookmarkKey)
                                .isEmpty()) {
                            dialog?.dismiss()
                            Toast.makeText(
                                this@LocalPlayerActivity, "All bookmarks deleted",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                row.addView(timeBtn)
                row.addView(deleteBtn)
                container.addView(row)
            }
        }

        rebuildRows()

        dialog = AlertDialog.Builder(this)
            .setTitle("Bookmarks")
            .setView(scrollView)
            .setNegativeButton("Close", null)
            .create()
        dialog.show()
    }

    private fun formatDuration(ms: Long): String {
        val totalSecs = ms / 1000
        val hours = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    }

    private fun enterPipIfSupported() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && localPlayer != null) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (localPlayer?.isPlaying == true) {
            enterPipIfSupported()
        }
    }

    override fun onPictureInPictureModeChanged(isInPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPip, newConfig)
        if (isInPip) {
            topBar.visibility = View.GONE
            playerView.useController = false
        } else {
            topBar.visibility = View.VISIBLE
            playerView.useController = true
            hideSystemUi()
        }
    }

    private fun startAirPlayDiscovery() {
        airPlaySender = AirPlaySender(this).also { sender ->
            sender.startDiscovery { receivers ->
                runOnUiThread {
                    discoveredReceivers.clear()
                    discoveredReceivers.addAll(receivers)
                }
            }
        }
    }

    private fun showAirPlayPicker() {
        if (discoveredReceivers.isEmpty()) {
            Toast.makeText(this, "No AirPlay receivers found on this network", Toast.LENGTH_SHORT).show()
            return
        }
        val names = discoveredReceivers.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Play on…")
            .setItems(names) { _, which ->
                castToReceiver(discoveredReceivers[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun castToReceiver(receiver: AirPlayReceiver) {
        val uri = fileUri ?: return
        val ip = getLocalIpAddress() ?: run {
            Toast.makeText(this, "Could not determine local IP", Toast.LENGTH_SHORT).show()
            return
        }

        // Start or reuse file server
        val server = fileServer ?: LocalFileHttpServer(contentResolver).also { fileServer = it }
        server.setFile(uri, mimeType, fileSize)
        if (!server.isAlive) server.start()

        val videoUrl = server.localUrl(ip)
        val startSecs = (localPlayer?.currentPosition ?: 0L) / 1000.0

        saveProgress()
        currentAirPlayReceiver = receiver

        lifecycleScope.launch {
            val ok = airPlaySender?.play(receiver, videoUrl, startSecs) ?: false
            if (ok) {
                runOnUiThread {
                    localPlayer?.pause()
                    Toast.makeText(
                        this@LocalPlayerActivity,
                        "Playing on ${receiver.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                runOnUiThread {
                    Toast.makeText(
                        this@LocalPlayerActivity,
                        "Could not connect to ${receiver.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun initLocalPlayer() {
        val uri = fileUri ?: return
        localPlayer = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo

            val mediaItemBuilder = MediaItem.Builder().setUri(uri)
            val subtitleUri = SubtitleHelper.getSubtitleUri(this, bookmarkKey)
            if (subtitleUri != null) {
                try {
                    val subtitle = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                        .setMimeType(SubtitleHelper.mimeTypeFor(subtitleUri))
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                    mediaItemBuilder.setSubtitleConfigurations(listOf(subtitle))
                } catch (e: Exception) {
                    // proceed without subtitle if configuration fails
                }
            }

            exo.setMediaItem(mediaItemBuilder.build())
            exo.prepare()
            val saved = prefs.getLong(progressKey ?: return@also, 0L)
            if (saved > 0L) exo.seekTo(saved)
            exo.play()
        }
    }

    private fun saveProgress() {
        val key = progressKey ?: return
        val player = localPlayer ?: return
        val position = if (player.duration > 0 && player.currentPosition > 0) player.currentPosition else return
        val duration = player.duration
        prefs.edit()
            .putLong(key, position)
            .putLong(key.replace(WatchProgressHelper.PREFS_POSITION_PREFIX, WatchProgressHelper.PREFS_DURATION_PREFIX), duration)
            .apply()
    }

    override fun onPause() {
        super.onPause()
        // In PiP mode the player keeps running — don't pause or save mid-pip
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) return
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
        airPlaySender?.stopDiscovery()
        airPlaySender = null
        fileServer?.stop()
        fileServer = null
        super.onDestroy()
    }

    private fun hideSystemUi() {
        @Suppress("DEPRECATION")
        playerView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            )
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
