package org.schabi.newpipe.qr

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.qr.QrScanActivity.Companion.DEFAULT_PRESET_1
import org.schabi.newpipe.qr.QrScanActivity.Companion.DEFAULT_PRESET_2
import org.schabi.newpipe.qr.QrScanActivity.Companion.DEFAULT_PRESET_3
import org.schabi.newpipe.qr.QrScanActivity.Companion.PREF_PRESET_1_NAME
import org.schabi.newpipe.qr.QrScanActivity.Companion.PREF_PRESET_2_NAME
import org.schabi.newpipe.qr.QrScanActivity.Companion.PREF_PRESET_3_NAME
import org.schabi.newpipe.util.ThemeHelper

class QrPresetsActivity : AppCompatActivity() {

    private lateinit var editPreset1: EditText
    private lateinit var editPreset2: EditText
    private lateinit var editPreset3: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.setTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_presets)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.qr_presets_title)
        }

        editPreset1 = findViewById(R.id.edit_preset_1)
        editPreset2 = findViewById(R.id.edit_preset_2)
        editPreset3 = findViewById(R.id.edit_preset_3)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        editPreset1.setText(prefs.getString(PREF_PRESET_1_NAME, DEFAULT_PRESET_1))
        editPreset2.setText(prefs.getString(PREF_PRESET_2_NAME, DEFAULT_PRESET_2))
        editPreset3.setText(prefs.getString(PREF_PRESET_3_NAME, DEFAULT_PRESET_3))

        findViewById<Button>(R.id.btn_save_presets).setOnClickListener {
            prefs.edit()
                .putString(PREF_PRESET_1_NAME, editPreset1.text.toString().trim().ifEmpty { DEFAULT_PRESET_1 })
                .putString(PREF_PRESET_2_NAME, editPreset2.text.toString().trim().ifEmpty { DEFAULT_PRESET_2 })
                .putString(PREF_PRESET_3_NAME, editPreset3.text.toString().trim().ifEmpty { DEFAULT_PRESET_3 })
                .apply()
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
