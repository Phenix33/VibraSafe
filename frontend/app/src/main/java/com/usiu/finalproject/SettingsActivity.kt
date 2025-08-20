package com.usiu.finalproject

import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
        }
    }
}

// Utility class for managing app preferences
class AppPreferences(private val sharedPreferences: SharedPreferences) {

    companion object {
        const val KEY_SERVER_URL = "server_url"
        const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        const val KEY_VISUAL_FEEDBACK_ENABLED = "visual_feedback_enabled"
        const val KEY_SENSITIVITY = "sensitivity"
        const val KEY_AUTO_RECORD = "auto_record"
        const val KEY_RECORD_DURATION = "record_duration"

        private const val DEFAULT_SERVER_URL = "http://10.0.2.2:5000/"
        private const val DEFAULT_RECORD_DURATION = 4000L
    }

    var serverUrl: String
        get() = sharedPreferences.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(value) = sharedPreferences.edit().putString(KEY_SERVER_URL, value).apply()

    var vibrationEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_VIBRATION_ENABLED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_VIBRATION_ENABLED, value).apply()

    var visualFeedbackEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_VISUAL_FEEDBACK_ENABLED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_VISUAL_FEEDBACK_ENABLED, value).apply()

    var sensitivity: Float
        get() = sharedPreferences.getFloat(KEY_SENSITIVITY, 0.7f)
        set(value) = sharedPreferences.edit().putFloat(KEY_SENSITIVITY, value).apply()

    var autoRecord: Boolean
        get() = sharedPreferences.getBoolean(KEY_AUTO_RECORD, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_AUTO_RECORD, value).apply()

    var recordDuration: Long
        get() = sharedPreferences.getLong(KEY_RECORD_DURATION, DEFAULT_RECORD_DURATION)
        set(value) = sharedPreferences.edit().putLong(KEY_RECORD_DURATION, value).apply()
}
