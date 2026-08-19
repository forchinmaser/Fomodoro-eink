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
import com.arijit.pomodoro.widgets.EinkToggle
import android.content.res.Configuration
import androidx.activity.result.contract.ActivityResultContracts
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Environment
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
    private lateinit var focusedTimeValueTxt: TextView
    private lateinit var focusedTimeMinusBtn: View
    private lateinit var focusedTimePlusBtn: View
    private lateinit var shortBreakValueTxt: TextView
    private lateinit var shortBreakMinusBtn: View
    private lateinit var shortBreakPlusBtn: View
    private lateinit var longBreakValueTxt: TextView
    private lateinit var longBreakMinusBtn: View
    private lateinit var longBreakPlusBtn: View
    private lateinit var sessionsValueTxt: TextView
    private lateinit var sessionsMinusBtn: View
    private lateinit var sessionsPlusBtn: View
    private lateinit var alarmValueTxt: TextView
    private lateinit var alarmMinusBtn: View
    private lateinit var alarmPlusBtn: View
    private lateinit var autoStartSessions: EinkToggle
    private lateinit var darkModeToggle: EinkToggle
    private lateinit var clockSoundToggle: EinkToggle
    private lateinit var amoledToggle: EinkToggle
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var githubCard: View
    private lateinit var supportCard: View
    private lateinit var settingsTxt: TextView
    private lateinit var backBtn: ImageView
    private lateinit var uiSettingsTxt: TextView
    private lateinit var aboutTheAppTxt: TextView
    private lateinit var runningTimerTxt: TextView
    private lateinit var madeWithLoveTxt: TextView
    private lateinit var uiSettingsComponents: LinearLayout
    private lateinit var timerSettingsComponents: LinearLayout
    private lateinit var musicRow: View
    private lateinit var musicValueTxt: TextView
    private lateinit var keepScreenAwakeToggle: EinkToggle
    private lateinit var hapticFeedbackToggle: EinkToggle
    private lateinit var statsCard: View
    private val CHANNEL_ID = "download_channel"
    private val NOTIFICATION_ID = 1
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private lateinit var ultraFocusModeToggle: EinkToggle
    private var originalOrientation: Int = 0
    private var originalDndMode: Int = 0
    private lateinit var notificationManager: NotificationManager

    private var focusedTimeValue = 25
    private var shortBreakValue = 5
    private var longBreakValue = 10
    private var sessionsValue = 4
    private var alarmValue = 3

    private val musicOptions: List<Pair<String?, String>> = listOf(
        null to "Off",
        "brown_noise" to "Brown Noise",
        "white_noise" to "White Noise",
        "rainfall" to "Rainfall",
        "light_jazz" to "Light Jazz",
    )
    private var selectedMusic: String? = null

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

        backBtn.setOnClickListener {
            vibrate()
            finish()
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
        focusedTimeValueTxt = findViewById(R.id.focused_time_value_txt)
        focusedTimeMinusBtn = findViewById(R.id.focused_time_minus_btn)
        focusedTimePlusBtn = findViewById(R.id.focused_time_plus_btn)
        shortBreakValueTxt = findViewById(R.id.short_break_value_txt)
        shortBreakMinusBtn = findViewById(R.id.short_break_minus_btn)
        shortBreakPlusBtn = findViewById(R.id.short_break_plus_btn)
        longBreakValueTxt = findViewById(R.id.long_break_value_txt)
        longBreakMinusBtn = findViewById(R.id.long_break_minus_btn)
        longBreakPlusBtn = findViewById(R.id.long_break_plus_btn)
        sessionsValueTxt = findViewById(R.id.sessions_value_txt)
        sessionsMinusBtn = findViewById(R.id.sessions_minus_btn)
        sessionsPlusBtn = findViewById(R.id.sessions_plus_btn)
        alarmValueTxt = findViewById(R.id.alarm_value_txt)
        alarmMinusBtn = findViewById(R.id.alarm_minus_btn)
        alarmPlusBtn = findViewById(R.id.alarm_plus_btn)
        autoStartSessions = findViewById(R.id.auto_start_toggle)
        darkModeToggle = findViewById(R.id.dark_mode_toggle)
        clockSoundToggle = findViewById(R.id.clock_sound_toggle)
        amoledToggle = findViewById(R.id.amoled_toggle)
        githubCard = findViewById(R.id.github_card)
        supportCard = findViewById(R.id.support_card)
        settingsTxt = findViewById(R.id.settings_txt)
        backBtn = findViewById(R.id.back_btn)
        uiSettingsTxt = findViewById(R.id.ui_settings_txt)
        runningTimerTxt = findViewById(R.id.running_timer_txt)
        madeWithLoveTxt = findViewById(R.id.made_with_love_txt)
        aboutTheAppTxt = findViewById(R.id.about_the_app_txt)
        uiSettingsComponents = findViewById(R.id.ui_settings_components)
        timerSettingsComponents = findViewById(R.id.timer_settings_components)
        musicRow = findViewById(R.id.music_row)
        musicValueTxt = findViewById(R.id.music_value_txt)
        keepScreenAwakeToggle = findViewById(R.id.keep_screen_awake_toggle)
        hapticFeedbackToggle = findViewById(R.id.haptic_feedback_toggle)
        statsCard = findViewById(R.id.stats_card)
    }

    private fun loadSavedSettings() {
        focusedTimeValue = sharedPreferences.getInt("focusedTime", 25)
        shortBreakValue = sharedPreferences.getInt("shortBreak", 5)
        longBreakValue = sharedPreferences.getInt("longBreak", 10)
        sessionsValue = sharedPreferences.getInt("sessions", 4)
        alarmValue = sharedPreferences.getInt("alarmDuration", 3)
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
        renderStepperValues()
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

    private fun renderFocusedTime() { focusedTimeValueTxt.text = "$focusedTimeValue mins" }
    private fun renderShortBreak() { shortBreakValueTxt.text = "$shortBreakValue mins" }
    private fun renderLongBreak() { longBreakValueTxt.text = "$longBreakValue mins" }
    private fun renderSessions() { sessionsValueTxt.text = "$sessionsValue sessions" }
    private fun renderAlarm() { alarmValueTxt.text = "$alarmValue times" }

    private fun renderStepperValues() {
        renderFocusedTime()
        renderShortBreak()
        renderLongBreak()
        renderSessions()
        renderAlarm()
    }

    private fun setupStepper(
        minusBtn: View,
        plusBtn: View,
        min: Int,
        max: Int,
        step: Int,
        getValue: () -> Int,
        setValue: (Int) -> Unit,
        render: () -> Unit,
    ) {
        minusBtn.setOnClickListener {
            vibrate()
            setValue((getValue() - step).coerceAtLeast(min))
            render()
            markTimerSettingsModified()
        }
        plusBtn.setOnClickListener {
            vibrate()
            setValue((getValue() + step).coerceAtMost(max))
            render()
            markTimerSettingsModified()
        }
    }

    private fun setupListeners() {

        setupStepper(
            focusedTimeMinusBtn, focusedTimePlusBtn,
            min = 1, max = 120, step = 5,
            getValue = { focusedTimeValue }, setValue = { focusedTimeValue = it },
            render = ::renderFocusedTime,
        )

        setupStepper(
            shortBreakMinusBtn, shortBreakPlusBtn,
            min = 1, max = 10, step = 1,
            getValue = { shortBreakValue }, setValue = { shortBreakValue = it },
            render = ::renderShortBreak,
        )

        setupStepper(
            longBreakMinusBtn, longBreakPlusBtn,
            min = 1, max = 30, step = 5,
            getValue = { longBreakValue }, setValue = { longBreakValue = it },
            render = ::renderLongBreak,
        )

        setupStepper(
            sessionsMinusBtn, sessionsPlusBtn,
            min = 1, max = 5, step = 1,
            getValue = { sessionsValue }, setValue = { sessionsValue = it },
            render = ::renderSessions,
        )

        setupStepper(
            alarmMinusBtn, alarmPlusBtn,
            min = 1, max = 5, step = 1,
            getValue = { alarmValue }, setValue = { alarmValue = it },
            render = ::renderAlarm,
        )

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

    private fun saveSettings() {
        sharedPreferences.edit().apply {
            putInt("focusedTime", focusedTimeValue)
            putInt("shortBreak", shortBreakValue)
            putInt("longBreak", longBreakValue)
            putInt("sessions", sessionsValue)
            putInt("alarmDuration", alarmValue)
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
        val musicName = selectedMusic ?: return

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

    private fun renderMusic() {
        musicValueTxt.text = musicOptions.first { it.first == selectedMusic }.second
    }

    private fun initializeMusicToggles() {
        // Load saved selection
        selectedMusic = sharedPreferences.getString("selected_music", null)
        renderMusic()
    }

    private fun setupMusicToggleListeners() {
        musicRow.setOnClickListener {
            vibrate()

            val currentIndex = musicOptions.indexOfFirst { it.first == selectedMusic }
            selectedMusic = musicOptions[(currentIndex + 1) % musicOptions.size].first
            renderMusic()

            if (selectedMusic != null) {
                sharedPreferences.edit().putString("selected_music", selectedMusic).apply()

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
