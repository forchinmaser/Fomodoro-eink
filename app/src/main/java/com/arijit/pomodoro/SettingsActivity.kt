package com.arijit.pomodoro

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.arijit.pomodoro.widgets.EinkSwitch
import com.google.android.material.slider.Slider
import android.widget.EditText
import androidx.core.widget.addTextChangedListener
import android.content.res.Configuration
import androidx.activity.result.contract.ActivityResultContracts
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Environment
import androidx.cardview.widget.CardView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import android.content.pm.ActivityInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.widget.Toast
import com.arijit.pomodoro.utils.UltraFocusManager

class SettingsActivity : AppCompatActivity() {
    private lateinit var focusedTimeTxt: EditText
    private lateinit var focusedTimeSlider: Slider
    private lateinit var shortBreakTxt: EditText
    private lateinit var shortBreakSlider: Slider
    private lateinit var longBreakTxt: EditText
    private lateinit var longBreakSlider: Slider
    private lateinit var sessionsTxt: EditText
    private lateinit var sessionsSlider: Slider
    private lateinit var alarmTxt: EditText
    private lateinit var alarmSlider: Slider
    private lateinit var autoStartSessions: EinkSwitch
    private lateinit var darkModeToggle: EinkSwitch
    private lateinit var clockSoundToggle: EinkSwitch
    private lateinit var amoledToggle: EinkSwitch
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var githubCard: androidx.cardview.widget.CardView
    private lateinit var supportCard: androidx.cardview.widget.CardView
    private lateinit var settingsTxt: TextView
    private lateinit var uiSettingsTxt: TextView
    private lateinit var aboutTheAppTxt: TextView
    private lateinit var runningTimerTxt: TextView
    private lateinit var madeWithLoveTxt: TextView
    private lateinit var uiSettingsComponents: LinearLayout
    private lateinit var timerSettingsComponents: LinearLayout
    private lateinit var brownNoiseToggle: EinkSwitch
    private lateinit var whiteNoiseToggle: EinkSwitch
    private lateinit var rainfallToggle: EinkSwitch
    private lateinit var lightJazzToggle: EinkSwitch
    private lateinit var keepScreenAwakeToggle: EinkSwitch
    private lateinit var hapticFeedbackToggle: EinkSwitch
    private lateinit var statsCard: CardView
    private val CHANNEL_ID = "download_channel"
    private val NOTIFICATION_ID = 1
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private lateinit var ultraFocusModeToggle: EinkSwitch
    private var originalOrientation: Int = 0
    private var originalDndMode: Int = 0
    private lateinit var notificationManager: NotificationManager

    private var isUpdatingSlider = false

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // Permissions granted, proceed with download
            handleMusicToggle()
        }
    }

    private val notificationPolicyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Permission granted, enable ultra focus mode
            sharedPreferences.edit().putBoolean("ultraFocusMode", true).apply()
            UltraFocusManager.enableUltraFocusMode(this)
            UltraFocusManager.setOrientation(this, true)
        } else {
            // Permission denied, revert toggle
            ultraFocusModeToggle.isChecked = false
            sharedPreferences.edit().putBoolean("ultraFocusMode", false).apply()
            Toast.makeText(this, "Permission denied. Ultra focus mode requires notification policy access.", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val RESULT_TIMER_SETTINGS_CHANGED = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize sharedPreferences first
        sharedPreferences = getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
        
        // Lock to portrait unless ultra focus mode is enabled
        if (sharedPreferences.getBoolean("ultraFocusMode", false)) {
            UltraFocusManager.enableUltraFocusMode(this)
            UltraFocusManager.setOrientation(this, true)
        } else {
            UltraFocusManager.setOrientation(this, false)
        }
        
        // Initialize notification manager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Initialize theme before setting content view
        val darkMode = sharedPreferences.getBoolean("darkMode", false)
        val amoledMode = sharedPreferences.getBoolean("amoledMode", false)
        
        // Set theme mode only once
        when {
            amoledMode -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                sharedPreferences.edit().apply {
                    putBoolean("darkMode", false)
                    apply()
                }
            }
            darkMode -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                sharedPreferences.edit().apply {
                    putBoolean("amoledMode", false)
                    apply()
                }
            }
            else -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
        
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializeViews()
        loadSavedSettings()
        setupListeners()
        applyTheme()
        checkTimerState()
        createNotificationChannel()
        initializeMusicToggles()
        setupMusicToggleListeners()

        // Initialize ultra focus mode toggle
        ultraFocusModeToggle = findViewById(R.id.ultra_focus_mode_toggle)
        // Set initial state from SharedPreferences
        ultraFocusModeToggle.isChecked = sharedPreferences.getBoolean("ultraFocusMode", false)
        
        // Save original orientation
        originalOrientation = requestedOrientation
        
        setupUltraFocusModeToggle()

        // Apply ultra focus mode if it was enabled
        if (ultraFocusModeToggle.isChecked) {
            enableUltraFocusMode()
        }

        statsCard.setOnClickListener {
            vibrate()
            startActivity(Intent(this@SettingsActivity, StatsActivity::class.java))
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Ignore system theme changes
        val darkMode = sharedPreferences.getBoolean("darkMode", false)
        val amoledMode = sharedPreferences.getBoolean("amoledMode", false)
        
        when {
            amoledMode -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                sharedPreferences.edit().apply {
                    putBoolean("darkMode", false)
                    apply()
                }
            }
            darkMode -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                sharedPreferences.edit().apply {
                    putBoolean("amoledMode", false)
                    apply()
                }
            }
            else -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
        applyTheme()
    }

    private fun checkTimerState() {
        val isTimerRunning = sharedPreferences.getBoolean("timerRunning", false)
        val isBreakActive = sharedPreferences.getBoolean("isBreakActive", false)

        if (isTimerRunning || isBreakActive) {
            timerSettingsComponents.visibility = View.GONE
            uiSettingsTxt.visibility = View.GONE
            uiSettingsComponents.visibility = View.GONE
            runningTimerTxt.visibility = View.VISIBLE
        } else {
            timerSettingsComponents.visibility = View.VISIBLE
            uiSettingsTxt.visibility = View.VISIBLE
            uiSettingsComponents.visibility = View.VISIBLE
            runningTimerTxt.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        // Lock to portrait unless ultra focus mode is enabled
        val ultraFocusMode = sharedPreferences.getBoolean("ultraFocusMode", false)
        if (ultraFocusMode) {
            UltraFocusManager.enableUltraFocusMode(this)
            UltraFocusManager.setOrientation(this, true)
        } else {
            UltraFocusManager.disableUltraFocusMode(this)
            UltraFocusManager.setOrientation(this, false)
        }
        checkTimerState()
    }

    override fun onPause() {
        super.onPause()
        saveSettings()
    }

    private fun initializeViews() {
        focusedTimeTxt = findViewById(R.id.focused_time_txt)
        focusedTimeSlider = findViewById(R.id.slider_focused_time)
        shortBreakTxt = findViewById(R.id.short_break_txt)
        shortBreakSlider = findViewById(R.id.slider_short_break)
        longBreakTxt = findViewById(R.id.long_break_txt)
        longBreakSlider = findViewById(R.id.slider_long_break)
        sessionsTxt = findViewById(R.id.sessions_txt)
        sessionsSlider = findViewById(R.id.slider_sessions)
        autoStartSessions = findViewById(R.id.auto_start_toggle)
        darkModeToggle = findViewById(R.id.dark_mode_toggle)
        clockSoundToggle = findViewById(R.id.clock_sound_toggle)
        amoledToggle = findViewById(R.id.amoled_toggle)
        githubCard = findViewById(R.id.github_card)
        supportCard = findViewById(R.id.support_card)
        settingsTxt = findViewById(R.id.settings_txt)
        alarmTxt = findViewById(R.id.alarm_txt)
        alarmSlider = findViewById(R.id.slider_alarm)
        uiSettingsTxt = findViewById(R.id.ui_settings_txt)
        runningTimerTxt = findViewById(R.id.running_timer_txt)
        madeWithLoveTxt = findViewById(R.id.made_with_love_txt)
        aboutTheAppTxt = findViewById(R.id.about_the_app_txt)
        uiSettingsComponents = findViewById(R.id.ui_settings_components)
        timerSettingsComponents = findViewById(R.id.timer_settings_components)
        brownNoiseToggle = findViewById(R.id.brown_noise_toggle)
        whiteNoiseToggle = findViewById(R.id.white_noise_toggle)
        rainfallToggle = findViewById(R.id.rainfall_toggle)
        lightJazzToggle = findViewById(R.id.light_jazz_toggle)
        keepScreenAwakeToggle = findViewById(R.id.keep_screen_awake_toggle)
        hapticFeedbackToggle = findViewById(R.id.haptic_feedback_toggle)
        statsCard = findViewById(R.id.stats_card)
    }

    private fun loadSavedSettings() {
        focusedTimeSlider.value = sharedPreferences.getInt("focusedTime", 25).toFloat()
        shortBreakSlider.value = sharedPreferences.getInt("shortBreak", 5).toFloat()
        longBreakSlider.value = sharedPreferences.getInt("longBreak", 10).toFloat()
        sessionsSlider.value = sharedPreferences.getInt("sessions", 4).toFloat()
        alarmSlider.value = sharedPreferences.getInt("alarmDuration", 3).toFloat()
        autoStartSessions.isChecked = sharedPreferences.getBoolean("autoStart", false)
        val darkMode = sharedPreferences.getBoolean("darkMode", false)
        darkModeToggle.isChecked = darkMode
        val amoledMode = sharedPreferences.getBoolean("amoledMode", false)
        amoledToggle.isChecked = amoledMode
        val keepScreenAwake = sharedPreferences.getBoolean("keepScreenAwake", false)
        keepScreenAwakeToggle.isChecked = keepScreenAwake
        updateWakeLock(keepScreenAwake)
        hapticFeedbackToggle.isChecked = sharedPreferences.getBoolean("hapticFeedback", true)
        clockSoundToggle.isChecked = sharedPreferences.getBoolean("clockSound", false)
        updateTexts()
    }

    private fun applyTheme() {
        val mainLayout = findViewById<android.widget.ScrollView>(R.id.main)
        val darkMode = sharedPreferences.getBoolean("darkMode", false)
        val amoledMode = sharedPreferences.getBoolean("amoledMode", false)

        if (amoledMode) {
            mainLayout.setBackgroundColor(resources.getColor(android.R.color.black))
        } else if (darkMode) {
            mainLayout.setBackgroundColor(resources.getColor(R.color.dark_background))
        }
    }

    private fun setupListeners() {

        focusedTimeSlider.addOnChangeListener { _, value, _ ->
            if (!isUpdatingSlider) {
                isUpdatingSlider = true
                focusedTimeTxt.setText("${value.toInt()}")
                isUpdatingSlider = false
            }
            markTimerSettingsModified()
        }

        focusedTimeTxt.addTextChangedListener { text ->
            if (!isUpdatingSlider) {
                isUpdatingSlider = true
                val value = text.toString().toIntOrNull()
                if (value != null) {
                    val clampedValue = value.coerceIn(focusedTimeSlider.valueFrom.toInt(), focusedTimeSlider.valueTo.toInt())
                    focusedTimeSlider.value = clampedValue.toFloat()
                    if (value != clampedValue) {
                        focusedTimeTxt.setText(clampedValue.toString())
                        focusedTimeTxt.setSelection(focusedTimeTxt.text.length)
                    }
                    markTimerSettingsModified()
                }
                isUpdatingSlider = false
            }
        }

        shortBreakSlider.addOnChangeListener { _, value, _ ->
            if (!isUpdatingSlider) {
                isUpdatingSlider = true
                shortBreakTxt.setText("${value.toInt()}")
                isUpdatingSlider = false
            }
            markTimerSettingsModified()
        }

        shortBreakTxt.addTextChangedListener { text ->
            if (!isUpdatingSlider) {
                isUpdatingSlider = true
                val value = text.toString().toIntOrNull()
                if (value != null) {
                    val clampedValue = value.coerceIn(shortBreakSlider.valueFrom.toInt(), shortBreakSlider.valueTo.toInt())
                    shortBreakSlider.value = clampedValue.toFloat()
                    if (value != clampedValue) {
                        shortBreakTxt.setText(clampedValue.toString())
                        shortBreakTxt.setSelection(shortBreakTxt.text.length)
                    }
                    markTimerSettingsModified()
                }
                isUpdatingSlider = false
            }
        }

        longBreakSlider.addOnChangeListener { _, value, _ ->
            if (!isUpdatingSlider) {
                isUpdatingSlider = true
                longBreakTxt.setText("${value.toInt()}")
                isUpdatingSlider = false
            }
            markTimerSettingsModified()
        }

        longBreakTxt.addTextChangedListener { text ->
            if (!isUpdatingSlider) {
                isUpdatingSlider = true
                val value = text.toString().toIntOrNull()
                if (value != null) {
                    val clampedValue = value.coerceIn(longBreakSlider.valueFrom.toInt(), longBreakSlider.valueTo.toInt())
                    longBreakSlider.value = clampedValue.toFloat()
                    if (value != clampedValue) {
                        longBreakTxt.setText(clampedValue.toString())
                        longBreakTxt.setSelection(longBreakTxt.text.length)
                    }
                    markTimerSettingsModified()
                }
                isUpdatingSlider = false
            }
        }

        sessionsSlider.addOnChangeListener { _, value, _ ->
            if (!isUpdatingSlider) {
                isUpdatingSlider = true
                sessionsTxt.setText("${value.toInt()}")
                isUpdatingSlider = false
            }
            markTimerSettingsModified()
        }

        sessionsTxt.addTextChangedListener { text ->
            if (!isUpdatingSlider) {
                isUpdatingSlider = true
                val value = text.toString().toIntOrNull()
                if (value != null) {
                    val clampedValue = value.coerceIn(sessionsSlider.valueFrom.toInt(), sessionsSlider.valueTo.toInt())
                    sessionsSlider.value = clampedValue.toFloat()
                    if (value != clampedValue) {
                        sessionsTxt.setText(clampedValue.toString())
                        sessionsTxt.setSelection(sessionsTxt.text.length)
                    }
                    markTimerSettingsModified()
                }
                isUpdatingSlider = false
            }
        }

        alarmSlider.addOnChangeListener { _, value, _ ->
            if (!isUpdatingSlider) {
                isUpdatingSlider = true
                alarmTxt.setText("${value.toInt()}")
                isUpdatingSlider = false
            }
            markTimerSettingsModified()
        }

        alarmTxt.addTextChangedListener { text ->
            if (!isUpdatingSlider) {
                isUpdatingSlider = true
                val value = text.toString().toIntOrNull()
                if (value != null) {
                    val clampedValue = value.coerceIn(alarmSlider.valueFrom.toInt(), alarmSlider.valueTo.toInt())
                    alarmSlider.value = clampedValue.toFloat()
                    if (value != clampedValue) {
                        alarmTxt.setText(clampedValue.toString())
                        alarmTxt.setSelection(alarmTxt.text.length)
                    }
                    markTimerSettingsModified()
                }
                isUpdatingSlider = false
            }
        }

        autoStartSessions.setOnCheckedChangeListener { _, _ ->
            markTimerSettingsModified()
        }

        darkModeToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                amoledToggle.isChecked = false
                sharedPreferences.edit().apply {
                    putBoolean("darkMode", true)
                    putBoolean("amoledMode", false)
                    apply()
                }
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                sharedPreferences.edit().putBoolean("darkMode", false).apply()
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            applyTheme()
        }

        amoledToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                darkModeToggle.isChecked = false
                sharedPreferences.edit().apply {
                    putBoolean("amoledMode", true)
                    putBoolean("darkMode", false)
                    apply()
                }
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                sharedPreferences.edit().putBoolean("amoledMode", false).apply()
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            applyTheme()
        }

        githubCard.setOnClickListener {
            vibrate()
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            intent.data = android.net.Uri.parse("https://github.com/Arijit-05/Minimal-Pomodoro")
            startActivity(intent)
        }

        supportCard.setOnClickListener {
            vibrate()
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            intent.data = android.net.Uri.parse("https://arijit-05.github.io/website/")
            startActivity(intent)
        }

        keepScreenAwakeToggle.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("keepScreenAwake", isChecked).apply()
            updateWakeLock(isChecked)
        }

        hapticFeedbackToggle.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("hapticFeedback", isChecked).apply()
        }

        clockSoundToggle.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("clockSound", isChecked).apply()
        }
    }

    private fun markTimerSettingsModified() {
        sharedPreferences.edit().apply {
            putBoolean("wereTimerSettingsModified", true)
            putString("focusText", "Focus")  // Reset focus text when any setting is modified
            apply()
        }
    }

    private fun updateTexts() {
        focusedTimeTxt.setText("${focusedTimeSlider.value.toInt()}")
        shortBreakTxt.setText("${shortBreakSlider.value.toInt()}")
        longBreakTxt.setText("${longBreakSlider.value.toInt()}")
        val sessions = sessionsSlider.value.toInt()
        sessionsTxt.setText("$sessions")
        alarmTxt.setText("${alarmSlider.value.toInt()}")
    }

    private fun saveSettings() {
        sharedPreferences.edit().apply {
            putInt("focusedTime", focusedTimeSlider.value.toInt())
            putInt("shortBreak", shortBreakSlider.value.toInt())
            putInt("longBreak", longBreakSlider.value.toInt())
            putInt("sessions", sessionsSlider.value.toInt())
            putInt("alarmDuration", alarmSlider.value.toInt())
            putBoolean("autoStart", autoStartSessions.isChecked)
            putBoolean("hapticFeedback", hapticFeedbackToggle.isChecked)
            putString("focusText", "Focus")  // Reset focus text when settings are changed
            putBoolean("wereTimerSettingsModified", true)
            apply()
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrate() {
        if (!hapticFeedbackToggle.isChecked) return // Don't vibrate if haptic feedback is disabled
        
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(vibrationEffect)
            } else {
                vibrator.vibrate(50)
            }
        }
    }

    private fun checkStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            storagePermissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_AUDIO))
        } else {
            storagePermissionLauncher.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Download Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for audio downloads"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showDownloadNotification(musicName: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading Audio")
            .setContentText("Downloading $musicName audio")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun dismissDownloadNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun getMusicFile(musicName: String): File {
        val musicDir = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "pomodoro_music")
        if (!musicDir.exists()) {
            musicDir.mkdirs()
        }
        return File(musicDir, "${musicName}.mp3")
    }

    private suspend fun downloadMusic(url: String, musicName: String) {
        withContext(Dispatchers.IO) {
            try {
                val musicFile = getMusicFile(musicName)
                if (!musicFile.exists()) {
                    showDownloadNotification(musicName)
                    val connection = URL(url).openConnection()
                    connection.connect()
                    val inputStream = connection.getInputStream()
                    val outputStream = FileOutputStream(musicFile)
                    inputStream.copyTo(outputStream)
                    outputStream.close()
                    inputStream.close()
                }
                dismissDownloadNotification()
            } catch (e: Exception) {
                e.printStackTrace()
                dismissDownloadNotification()
            }
        }
    }

    private fun handleMusicToggle() {
        val musicName = when {
            brownNoiseToggle.isChecked -> "brown_noise"
            whiteNoiseToggle.isChecked -> "white_noise"
            rainfallToggle.isChecked -> "rainfall"
            lightJazzToggle.isChecked -> "light_jazz"
            else -> null
        }

        if (musicName != null) {
            val url = when (musicName) {
                "brown_noise" -> "https://github.com/Arijit-05/fomodoro_assets/releases/download/brown-noise/brown_noise.mp3"
                "white_noise" -> "https://github.com/Arijit-05/fomodoro_assets/releases/download/white-noise/white_noise.mp3"
                "rainfall" -> "https://github.com/Arijit-05/fomodoro_assets/releases/download/rainfall/rainfall.mp3"
                "light_jazz" -> "https://github.com/Arijit-05/fomodoro_assets/releases/download/light-jazz/light_jazz.mp3"
                else -> null
            }

            if (url != null) {
                CoroutineScope(Dispatchers.Main).launch {
                    downloadMusic(url, musicName)
                }
            }
        }
    }

    private fun initializeMusicToggles() {
        // Load saved toggle states
        val savedMusic = sharedPreferences.getString("selected_music", null)
        brownNoiseToggle.isChecked = savedMusic == "brown_noise"
        whiteNoiseToggle.isChecked = savedMusic == "white_noise"
        rainfallToggle.isChecked = savedMusic == "rainfall"
        lightJazzToggle.isChecked = savedMusic == "light_jazz"
    }

    private fun setupMusicToggleListeners() {
        val toggleListener = { toggle: EinkSwitch, musicName: String ->
            if (toggle.isChecked) {
                // Uncheck other toggles
                brownNoiseToggle.isChecked = toggle == brownNoiseToggle
                whiteNoiseToggle.isChecked = toggle == whiteNoiseToggle
                rainfallToggle.isChecked = toggle == rainfallToggle
                lightJazzToggle.isChecked = toggle == lightJazzToggle

                // Save selection
                sharedPreferences.edit().putString("selected_music", musicName).apply()

                // Check permissions and download if needed
                if (checkStoragePermissions()) {
                    handleMusicToggle()
                } else {
                    requestStoragePermissions()
                }
            } else {
                sharedPreferences.edit().remove("selected_music").apply()
            }
        }

        brownNoiseToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) toggleListener(brownNoiseToggle, "brown_noise")
        }

        whiteNoiseToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) toggleListener(whiteNoiseToggle, "white_noise")
        }

        rainfallToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) toggleListener(rainfallToggle, "rainfall")
        }

        lightJazzToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) toggleListener(lightJazzToggle, "light_jazz")
        }
    }

    private fun updateWakeLock(enable: Boolean) {
        if (enable) {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                wakeLock = powerManager.newWakeLock(
                    android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    android.os.PowerManager.ON_AFTER_RELEASE,
                    "MinimalPomodoro::ScreenWakeLock"
                )
                wakeLock?.acquire(10*60*1000L /*10 minutes*/)
            }
        } else {
            wakeLock?.release()
            wakeLock = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.release()
        wakeLock = null
        // Ensure we restore original settings when activity is destroyed
        if (ultraFocusModeToggle.isChecked) {
            disableUltraFocusMode()
        }
    }

    private fun setupUltraFocusModeToggle() {
        ultraFocusModeToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Check if we have notification policy access
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (notificationManager.isNotificationPolicyAccessGranted) {
                        sharedPreferences.edit().putBoolean("ultraFocusMode", true).apply()
                        UltraFocusManager.enableUltraFocusMode(this)
                        UltraFocusManager.setOrientation(this, true)
                    } else {
                        // Request notification policy access
                        val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        notificationPolicyPermissionLauncher.launch(intent)
                    }
                } else {
                    sharedPreferences.edit().putBoolean("ultraFocusMode", true).apply()
                    UltraFocusManager.enableUltraFocusMode(this)
                    UltraFocusManager.setOrientation(this, true)
                }
            } else {
                sharedPreferences.edit().putBoolean("ultraFocusMode", false).apply()
                UltraFocusManager.disableUltraFocusMode(this)
                UltraFocusManager.setOrientation(this, false)
            }
        }
    }

    private fun enableUltraFocusMode() {
        try {
            // Save current DND mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (notificationManager.isNotificationPolicyAccessGranted) {
                    originalDndMode = notificationManager.currentInterruptionFilter
                    // Set DND mode to priority only
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                } else {
                    throw SecurityException("Notification policy access not granted")
                }
            }

            // Force landscape orientation
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

            // Disable notifications
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = notificationManager.getNotificationChannel("timer_notifications")
                channel?.let {
                    it.enableLights(false)
                    it.enableVibration(false)
                    it.setSound(null, null)
                }
            }
        } catch (e: SecurityException) {
            // Handle permission error
            ultraFocusModeToggle.isChecked = false
            sharedPreferences.edit().putBoolean("ultraFocusMode", false).apply()
            Toast.makeText(this, "Permission denied. Ultra focus mode requires notification policy access.", Toast.LENGTH_LONG).show()
        }
    }

    private fun disableUltraFocusMode() {
        try {
            // Restore original DND mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (notificationManager.isNotificationPolicyAccessGranted) {
                    notificationManager.setInterruptionFilter(originalDndMode)
                }
            }

            // Restore original orientation
            requestedOrientation = originalOrientation

            // Re-enable notifications
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = notificationManager.getNotificationChannel("timer_notifications")
                channel?.let {
                    it.enableLights(true)
                    it.enableVibration(true)
                    it.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build())
                }
            }
        } catch (e: SecurityException) {
            // Handle permission error
            Toast.makeText(this, "Error restoring settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}