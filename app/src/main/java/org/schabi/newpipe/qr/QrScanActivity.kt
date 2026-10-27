package org.schabi.newpipe.qr

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.schabi.newpipe.R
import org.schabi.newpipe.RouterActivity
import org.schabi.newpipe.util.ThemeHelper

class QrScanActivity : AppCompatActivity() {

    companion object {
        const val PREF_PRESET_1_NAME = "qr_preset_1_name"
        const val PREF_PRESET_2_NAME = "qr_preset_2_name"
        const val PREF_PRESET_3_NAME = "qr_preset_3_name"
        const val DEFAULT_PRESET_1 = "HD Video"
        const val DEFAULT_PRESET_2 = "Audio Only"
        const val DEFAULT_PRESET_3 = "SD Video"
        private val YOUTUBE_URL_REGEX = Regex(
            "(?:https?://)?(?:www\\.)?(?:youtube\\.com/watch\\?.*v=|youtu\\.be/)[A-Za-z0-9_-]{11}"
        )
    }

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val url = result.contents
        if (url == null) {
            finish()
            return@registerForActivityResult
        }
        if (!YOUTUBE_URL_REGEX.containsMatchIn(url)) {
            Toast.makeText(this, R.string.qr_not_youtube_url, Toast.LENGTH_LONG).show()
            finish()
            return@registerForActivityResult
        }
        showPresetChooser(url)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.setTheme(this)
        super.onCreate(savedInstanceState)
        val options = ScanOptions().apply {
            setPrompt(getString(R.string.qr_scan_prompt))
            setBeepEnabled(true)
            setOrientationLocked(false)
            setCaptureActivity(PortraitCaptureActivity::class.java)
        }
        scanLauncher.launch(options)
    }

    private fun showPresetChooser(url: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val presets = arrayOf(
            prefs.getString(PREF_PRESET_1_NAME, DEFAULT_PRESET_1) ?: DEFAULT_PRESET_1,
            prefs.getString(PREF_PRESET_2_NAME, DEFAULT_PRESET_2) ?: DEFAULT_PRESET_2,
            prefs.getString(PREF_PRESET_3_NAME, DEFAULT_PRESET_3) ?: DEFAULT_PRESET_3
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.qr_choose_preset)
            .setItems(presets) { _, which ->
                routeToDownload(url, presets[which])
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun routeToDownload(url: String, presetName: String) {
        val intent = Intent(this, RouterActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse(url)
            putExtra(RouterActivity.EXTRA_FORCE_DOWNLOAD, true)
        }
        startActivity(intent)
        finish()
    }
}
