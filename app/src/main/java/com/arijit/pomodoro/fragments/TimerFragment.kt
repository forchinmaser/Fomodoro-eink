package com.arijit.pomodoro.fragments

import android.Manifest
import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.RequiresPermission
import androidx.cardview.widget.CardView
import com.arijit.pomodoro.R
import android.widget.RelativeLayout
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import android.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arijit.pomodoro.adapters.TimerPresetAdapter
import com.arijit.pomodoro.models.TimerPreset
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.widget.EditText
import com.arijit.pomodoro.services.TimerService
import android.content.SharedPreferences
import androidx.annotation.RequiresApi
import com.arijit.pomodoro.utils.StatsManager

class TimerFragment : Fragment() {
    private lateinit var focusCard: CardView
    private lateinit var timerTxt: TextView
    private lateinit var playBtn: View
    private lateinit var pauseBtn: View
    private lateinit var resetBtn: ImageView
    private lateinit var skipBtn: ImageView
    private lateinit var brain: ImageView
    private lateinit var focusCardBg: LinearLayout
    private lateinit var focusTxt: TextView
    private lateinit var sessionsTxt: TextView
    private var mediaPlayer: MediaPlayer? = null
    private var clockSoundPlayer: MediaPlayer? = null
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var statsManager: StatsManager
    
    private var countDownTimer: CountDownTimer? = null
    private var timeLeftInMillis: Long = 0
    private var timerRunning = false
    private var currentSession = 1
    private var totalSessions = 4
    private var autoStart = false
    private var isFromShortBreak = false
    private var elapsedSeconds = 0 // Add this variable to track seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = requireContext().getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
        statsManager = StatsManager(requireContext())

        // Initialize clock sound player
        clockSoundPlayer = MediaPlayer.create(requireContext(), R.raw.clock_sound)

        if (savedInstanceState == null) {
            timerRunning = sharedPreferences.getBoolean("timerRunning", false)
            timeLeftInMillis = sharedPreferences.getLong("timeLeftInMillis", 0)
            // Only set currentSession, totalSessions, autoStart if not already set by newInstance
            if (this.currentSession == 1) {
                currentSession = sharedPreferences.getInt("currentSession", 1)
            }
            if (this.totalSessions == 4) {
                totalSessions = sharedPreferences.getInt("totalSessions", 4)
            }
            if (!this.autoStart) {
                autoStart = sharedPreferences.getBoolean("autoStart", false)
            }
            // If timer is not running or timeLeftInMillis is 0, always load from settings
            if (!timerRunning || timeLeftInMillis == 0L) {
                loadSettings()
            }
        }
        // else: state will be restored from the bundle, do not overwrite!
    }
    
    private fun loadSettings() {
        timeLeftInMillis = sharedPreferences.getInt("focusedTime", 25) * 60 * 1000L
        totalSessions = sharedPreferences.getInt("sessions", 4)
        autoStart = sharedPreferences.getBoolean("autoStart", false)
        
        // Only reset focus text if settings were modified
        if (sharedPreferences.getBoolean("wereTimerSettingsModified", false)) {
            sharedPreferences.edit().putString("focusText", "Focus").apply()
            sharedPreferences.edit().putBoolean("wereTimerSettingsModified", false).apply()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_timer, container, false)
        focusCard = view.findViewById(R.id.focus_card)
        timerTxt = view.findViewById(R.id.timer_txt)
        playBtn = view.findViewById(R.id.play_btn)
        pauseBtn = view.findViewById(R.id.pause_btn)
        resetBtn = view.findViewById(R.id.reset_btn)
        skipBtn = view.findViewById(R.id.skip_btn)
        focusCardBg = view.findViewById(R.id.focus_card_bg)
        focusTxt = view.findViewById(R.id.focus_txt)
        brain = view.findViewById(R.id.brain)
        sessionsTxt = requireActivity().findViewById(R.id.sessions_txt)

        val sharedPreferences = requireContext().getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
        
        // Load saved focus text after view initialization
        focusTxt.text = sharedPreferences.getString("focusText", "Focus")

        val parentLayout = requireActivity().findViewById<android.widget.RelativeLayout>(R.id.main)
        val amoledMode = sharedPreferences.getBoolean("amoledMode", false)

        if (amoledMode) {
            parentLayout.setBackgroundColor(resources.getColor(android.R.color.black))
        }

        updateCountdownText()
        updateSessionsText()

        focusCard.setOnClickListener {
            vibrate()
            Toast.makeText(requireContext(), "This feature is still in beta. Bugs may exist", Toast.LENGTH_SHORT).show()
            showPresetDialog()
        }
        
        playBtn.setOnClickListener {
            vibrate()
            startTimer()
        }
        
        pauseBtn.setOnClickListener {
            vibrate()
            pauseTimer()
        }
        
        resetBtn.setOnClickListener {
            vibrate()
            // Show confirmation dialog before resetting
            AlertDialog.Builder(requireContext())
                .setTitle("Reset Timer")
                .setMessage("Do you want to reset the timer?")
                .setPositiveButton("Yes") { _, _ ->
                    // Reset session count to 1
                    currentSession = 1
                    sharedPreferences.edit().putInt("currentSession", 1).apply()
                    resetTimer()
                    updateSessionsText()
                }
                .setNegativeButton("No", null)
                .show()
        }
        
        skipBtn.setOnClickListener {
            vibrate()
            loadShortBreakFragment()
        }

        return view
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Restore timer state if it was running
        if (timerRunning) {
            playBtn.visibility = View.GONE
            pauseBtn.visibility = View.VISIBLE
            resetBtn.visibility = View.VISIBLE
            skipBtn.visibility = View.VISIBLE
            startTimer()
        } else if (autoStart && isFromShortBreak) {
            startTimer()
        } else {
            // Ensure timer is stopped and UI is in initial state
            playBtn.visibility = View.VISIBLE
            pauseBtn.visibility = View.GONE
            resetBtn.visibility = View.GONE
            skipBtn.visibility = View.GONE
            updateCountdownText()
        }
    }

    override fun onResume() {
        super.onResume()
        TimerService.appOpened(requireContext())

        // Only update from SharedPreferences if not restoring from rotation
        if (!timerRunning && timeLeftInMillis == 0L) {
            timerRunning = sharedPreferences.getBoolean("timerRunning", false)
            timeLeftInMillis = sharedPreferences.getLong("timeLeftInMillis", 0)
        }

        if (timerRunning && timeLeftInMillis > 0) {
            // Resume timer
            playBtn.visibility = View.GONE
            pauseBtn.visibility = View.VISIBLE
            resetBtn.visibility = View.VISIBLE
            skipBtn.visibility = View.VISIBLE
            if (countDownTimer == null) {
                startTimer()
            }
            updateCountdownText()
        } else {
            // Not running, show timer with value from settings
            timerRunning = false
            loadSettings()
            playBtn.visibility = View.VISIBLE
            pauseBtn.visibility = View.GONE
            resetBtn.visibility = View.GONE
            skipBtn.visibility = View.GONE
            updateCountdownText()
        }
    }

    override fun onPause() {
        super.onPause()
        sharedPreferences.edit().putBoolean("isAppInForeground", false).apply()
    }

    private fun startTimer() {
        countDownTimer?.cancel() // Cancel any existing timer
        elapsedSeconds = 0 // Reset elapsed seconds at the start
        countDownTimer = object : CountDownTimer(timeLeftInMillis, 1000) {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateCountdownText()
                
                // Play clock sound if enabled
                playClockSound()
                
                // Update SharedPreferences with current time
                sharedPreferences.edit().apply {
                    putLong("timeLeftInMillis", timeLeftInMillis)
                    putBoolean("timerRunning", true)
                    // Save elapsedSeconds for background recovery
                    putInt("elapsedSeconds", ++elapsedSeconds)
                    apply()
                }
                // Update stats every minute
                if (elapsedSeconds % 60 == 0) {
                    statsManager.updateStats(1) // Add 1 minute to stats
                }
            }

            @RequiresApi(Build.VERSION_CODES.O)
            override fun onFinish() {
                timerRunning = false
                updateTimerState(false)
                playAlarm()
                // Only add any remaining seconds as minutes if not already counted
                val leftoverSeconds = elapsedSeconds % 60
                if (leftoverSeconds > 0) {
                    // Add partial minute if any
                    statsManager.updateStats(1) // Optionally, could use: Math.round(leftoverSeconds / 60.0)
                }
                // Remove double-counting: do NOT add full session again
                if (currentSession < totalSessions) {
                    loadShortBreakFragment()
                } else {
                    Toast.makeText(requireContext(), "All sessions completed", Toast.LENGTH_SHORT).show()
                    loadLongBreakFragment()
                }
            }
        }.start()
        
        timerRunning = true
        updateTimerState(true)
        playBtn.visibility = View.GONE
        pauseBtn.visibility = View.VISIBLE
        resetBtn.visibility = View.VISIBLE
        skipBtn.visibility = View.VISIBLE

        // Start the foreground service
        val sessionInfo = "$currentSession/$totalSessions"
        TimerService.startTimer(requireContext(), "focus", sessionInfo)
    }
    
    private fun pauseTimer() {
        countDownTimer?.cancel()
        timerRunning = false
        updateTimerState(false)
        playBtn.visibility = View.VISIBLE
        pauseBtn.visibility = View.GONE

        // Update SharedPreferences
        sharedPreferences.edit().apply {
            putLong("timeLeftInMillis", timeLeftInMillis)
            putBoolean("timerRunning", false)
            apply()
        }

        // Stop the foreground service
        TimerService.stopTimer(requireContext())
    }
    
    private fun resetTimer() {
        countDownTimer?.cancel()
        loadSettings()
        timerRunning = false
        updateTimerState(false)
        updateCountdownText()

        // Update SharedPreferences
        sharedPreferences.edit().apply {
            putLong("timeLeftInMillis", timeLeftInMillis)
            putBoolean("timerRunning", false)
            apply()
        }

        playBtn.visibility = View.VISIBLE
        pauseBtn.visibility = View.GONE
        resetBtn.visibility = View.GONE
        skipBtn.visibility = View.GONE

        // Stop the foreground service
        TimerService.stopTimer(requireContext())
    }
    
    private fun updateCountdownText() {
        val minutes = (timeLeftInMillis / 1000).toInt() / 60
        val seconds = (timeLeftInMillis / 1000).toInt() % 60
        
        timerTxt.text = String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateSessionsText() {
        sessionsTxt.text = "$currentSession/$totalSessions"
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrate() {
        val sharedPreferences = requireContext().getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
        if (!sharedPreferences.getBoolean("hapticFeedback", true)) return // Don't vibrate if haptic feedback is disabled
        
        val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(vibrationEffect)
            } else {
                vibrator.vibrate(50)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        clockSoundPlayer?.release()
        clockSoundPlayer = null
    }

    private fun playClockSound() {
        val clockSoundEnabled = sharedPreferences.getBoolean("clockSound", false)
        if (clockSoundEnabled && clockSoundPlayer != null) {
            try {
                clockSoundPlayer?.seekTo(0)
                clockSoundPlayer?.start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadShortBreakFragment() {
        val parentLayout = requireActivity().findViewById<RelativeLayout>(R.id.main)
        val sharedPreferences = requireContext().getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
        val darkMode = sharedPreferences.getBoolean("darkMode", false)
        val amoledMode = sharedPreferences.getBoolean("amoledMode", false)

        if (amoledMode) {
            parentLayout.setBackgroundColor(resources.getColor(android.R.color.black))
        } else if (darkMode) {
            parentLayout.setBackgroundResource(R.color.deep_green)
        } else {
            parentLayout.setBackgroundResource(R.color.light_green)
        }

        val fragment = ShortBreakFragment()
        fragment.setSessionInfo(currentSession, totalSessions, autoStart, true)
        parentFragmentManager.beginTransaction()
            .replace(R.id.frame_layout, fragment)
            .commit()
    }

    private fun loadLongBreakFragment() {
        val parentLayout = requireActivity().findViewById<RelativeLayout>(R.id.main)
        val sharedPreferences = requireContext().getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
        val darkMode = sharedPreferences.getBoolean("darkMode", false)
        val amoledMode = sharedPreferences.getBoolean("amoledMode", false)

        if (amoledMode) {
            parentLayout.setBackgroundColor(resources.getColor(android.R.color.black))
        } else if (darkMode) {
            parentLayout.setBackgroundResource(R.color.deep_blue)
        } else {
            parentLayout.setBackgroundResource(R.color.light_blue)
        }

        val fragment = LongBreakFragment()
        fragment.setSessionInfo(1, totalSessions) // Reset to first session
        parentFragmentManager.beginTransaction()
            .replace(R.id.frame_layout, fragment)
            .commit()
    }

    private fun updateTimerState(isRunning: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean("timerRunning", isRunning)
            putInt("currentSession", currentSession)
            putInt("totalSessions", totalSessions)
            apply()
        }
    }

    private fun playAlarm() {
        try {
            val sharedPreferences = requireContext().getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
            val alarmDuration = sharedPreferences.getInt("alarmDuration", 3) * 1000L // Convert to milliseconds

            // Show notification
            val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val builder = NotificationCompat.Builder(requireContext(), "timer_notifications")
                .setSmallIcon(R.drawable.brain)
                .setContentTitle("Timer Complete")
                .setContentText("Your timer is up. Good job!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            notificationManager.notify(1, builder.build())

            // Vibrate
            try {
                val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (vibrator.hasVibrator()) {
                    val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    val canVibrate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        notificationManager.isNotificationPolicyAccessGranted &&
                        notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_NONE
                    } else true

                    if (canVibrate) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val vibrationEffect = VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0)
                            vibrator.vibrate(vibrationEffect)
                        } else {
                            vibrator.vibrate(longArrayOf(0, 500, 500), 0)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // Stop after configured duration
            android.os.Handler().postDelayed({
                try {
                    val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    vibrator.cancel()
                } catch (_: Exception) {}
            }, alarmDuration)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("timeLeftInMillis", timeLeftInMillis)
        outState.putBoolean("timerRunning", timerRunning)
        outState.putInt("currentSession", currentSession)
        outState.putInt("totalSessions", totalSessions)
        outState.putBoolean("autoStart", autoStart)
        outState.putBoolean("isFromShortBreak", isFromShortBreak)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (savedInstanceState != null) {
            timeLeftInMillis = savedInstanceState.getLong("timeLeftInMillis")
            timerRunning = savedInstanceState.getBoolean("timerRunning")
            currentSession = savedInstanceState.getInt("currentSession")
            totalSessions = savedInstanceState.getInt("totalSessions")
            autoStart = savedInstanceState.getBoolean("autoStart")
            isFromShortBreak = savedInstanceState.getBoolean("isFromShortBreak")
            
            // Restore UI state
            if (timerRunning) {
                playBtn.visibility = View.GONE
                pauseBtn.visibility = View.VISIBLE
                resetBtn.visibility = View.VISIBLE
                skipBtn.visibility = View.VISIBLE
                startTimer()
            } else {
                playBtn.visibility = View.VISIBLE
                pauseBtn.visibility = View.GONE
                resetBtn.visibility = View.GONE
                skipBtn.visibility = View.GONE
            }
            updateCountdownText()
            updateSessionsText()
        }
    }

    private fun showPresetDialog() {
        val inflater = LayoutInflater.from(context)
        val presetView = inflater.inflate(R.layout.preset_layout, null)
        val presetRecyclerView = presetView.findViewById<RecyclerView>(R.id.preset_rv)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(presetView)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Set up RecyclerView
        presetRecyclerView.layoutManager = LinearLayoutManager(context)
        
        // Load presets from SharedPreferences
        val presets = loadPresets().toMutableList()
        
        val adapter = TimerPresetAdapter(presets) { preset ->
            vibrate()
            // Stop any running timer
            if (timerRunning) {
                pauseTimer()
            }
            
            // Update all timer durations in SharedPreferences
            val sharedPreferences = requireContext().getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
            sharedPreferences.edit().apply {
                putInt("focusedTime", preset.focusMinutes)
                putInt("shortBreak", preset.shortBreakMinutes)
                putInt("longBreak", preset.longBreakMinutes)
                putString("focusText", preset.name)
                apply()
            }
            
            // Update current timer
            timeLeftInMillis = preset.focusMinutes * 60 * 1000L
            updateCountdownText()
            focusTxt.text = preset.name
            
            // Reset timer state
            playBtn.visibility = View.VISIBLE
            pauseBtn.visibility = View.GONE
            resetBtn.visibility = View.GONE
            skipBtn.visibility = View.GONE
            
            dialog.dismiss()
        }

        // Add long press listener for deletion
        adapter.setOnLongClickListener { preset ->
            showDeleteConfirmationDialog(preset) {
                // Refresh the RecyclerView after deletion
                val updatedPresets = loadPresets()
                adapter.updatePresets(updatedPresets)
            }
            true
        }
        
        presetRecyclerView.adapter = adapter
        
        // Handle add new timer button click
        presetView.findViewById<CardView>(R.id.add_new_timer_btn).setOnClickListener {
            vibrate()
            dialog.dismiss()
            showAddTimerDialog {
                // Refresh the RecyclerView after adding new preset
                val updatedPresets = loadPresets()
                adapter.updatePresets(updatedPresets)
            }
        }
        
        dialog.show()
    }

    private fun showAddTimerDialog(onPresetAdded: () -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_timer, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.timer_name_input)
        val durationInput = dialogView.findViewById<EditText>(R.id.timer_duration_input)
        val shortBreakInput = dialogView.findViewById<EditText>(R.id.short_break_input)
        val longBreakInput = dialogView.findViewById<EditText>(R.id.long_break_input)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Add New Timer")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString()
                val duration = durationInput.text.toString().toIntOrNull()
                val shortBreak = shortBreakInput.text.toString().toIntOrNull()
                val longBreak = longBreakInput.text.toString().toIntOrNull()

                if (name.isNotEmpty() && duration != null && shortBreak != null && longBreak != null) {
                    val newPreset = TimerPreset(name, duration, shortBreak, longBreak)
                    savePreset(newPreset)
                    onPresetAdded()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun showDeleteConfirmationDialog(preset: TimerPreset, onPresetDeleted: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Timer")
            .setMessage("Are you sure you want to delete ${preset.name}?")
            .setPositiveButton("Delete") { _, _ ->
                deletePreset(preset)
                onPresetDeleted()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadPresets(): List<TimerPreset> {
        val sharedPreferences = requireContext().getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
        val presetsJson = sharedPreferences.getString("timer_presets", null)
        
        // If no presets are saved, initialize with default presets
        if (presetsJson == null) {
            val defaultPresets = listOf(
                TimerPreset("Pomodoro", 25, 5, 15),
                TimerPreset("Quick Focus", 15, 3, 10),
                TimerPreset("Deep Work", 45, 10, 20),
                TimerPreset("Study Session", 30, 5, 15)
            )
            sharedPreferences.edit().putString("timer_presets", Gson().toJson(defaultPresets)).apply()
            return defaultPresets
        }
        
        val type = object : TypeToken<List<TimerPreset>>() {}.type
        return Gson().fromJson(presetsJson, type) ?: emptyList()
    }

    private fun savePreset(preset: TimerPreset) {
        val sharedPreferences = requireContext().getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
        val presetsJson = sharedPreferences.getString("timer_presets", "[]")
        val type = object : TypeToken<List<TimerPreset>>() {}.type
        val presets = Gson().fromJson<List<TimerPreset>>(presetsJson, type).toMutableList()
        
        presets.add(preset)
        
        sharedPreferences.edit().putString("timer_presets", Gson().toJson(presets)).apply()
    }

    private fun deletePreset(preset: TimerPreset) {
        val sharedPreferences = requireContext().getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
        val presetsJson = sharedPreferences.getString("timer_presets", "[]")
        val type = object : TypeToken<List<TimerPreset>>() {}.type
        val presets = Gson().fromJson<List<TimerPreset>>(presetsJson, type).toMutableList()
        
        // Check if the preset being deleted is the currently selected one
        val currentFocusText = sharedPreferences.getString("focusText", "Focus")
        if (currentFocusText == preset.name) {
            // Reset to default values
            sharedPreferences.edit().apply {
                putInt("focusedTime", 25)
                putInt("shortBreak", 5)
                putInt("longBreak", 15)
                putString("focusText", "Focus")
                apply()
            }
            
            // Update current timer display
            timeLeftInMillis = 25 * 60 * 1000L
            updateCountdownText()
            focusTxt.text = "Focus"
        }
        
        presets.removeAll { it.name == preset.name }
        sharedPreferences.edit().putString("timer_presets", Gson().toJson(presets)).apply()
    }

    companion object {
        fun newInstance(currentSession: Int, totalSessions: Int, autoStart: Boolean, isFromShortBreak: Boolean = false): TimerFragment {
            return TimerFragment().apply {
                this.currentSession = currentSession
                this.totalSessions = totalSessions
                this.autoStart = autoStart
                this.isFromShortBreak = isFromShortBreak
            }
        }
    }
}