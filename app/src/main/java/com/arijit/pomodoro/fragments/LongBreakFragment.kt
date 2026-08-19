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
import android.widget.RelativeLayout
import androidx.annotation.RequiresPermission
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.arijit.pomodoro.R
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import android.content.Intent

class LongBreakFragment : Fragment() {
    private lateinit var backToTimer: TextView
    private lateinit var timerTxt: TextView
    private lateinit var playBtn: CardView
    private lateinit var pauseBtn: CardView
    private lateinit var resetBtn: ImageView
    private lateinit var skipBtn: ImageView
    private lateinit var coffee: ImageView
    private lateinit var longBreakCardBg: LinearLayout
    private lateinit var longBreakTxt: TextView
    private lateinit var sessionsTxt: TextView
    private var mediaPlayer: MediaPlayer? = null
    private var clockSoundPlayer: MediaPlayer? = null
    
    private var countDownTimer: CountDownTimer? = null
    private var timeLeftInMillis: Long = 0
    private var timerRunning = false
    private var currentSession = 1
    private var totalSessions = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize clock sound player
        clockSoundPlayer = MediaPlayer.create(requireContext(), R.raw.clock_sound)
        
        if (savedInstanceState != null) {
            timeLeftInMillis = savedInstanceState.getLong("timeLeftInMillis")
            timerRunning = savedInstanceState.getBoolean("timerRunning")
            currentSession = savedInstanceState.getInt("currentSession")
            totalSessions = savedInstanceState.getInt("totalSessions")
        } else {
            loadSettings()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("timeLeftInMillis", timeLeftInMillis)
        outState.putBoolean("timerRunning", timerRunning)
        outState.putInt("currentSession", currentSession)
        outState.putInt("totalSessions", totalSessions)
    }

    private fun loadSettings() {
        val sharedPreferences = requireContext().getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
        timeLeftInMillis = sharedPreferences.getInt("longBreak", 10) * 60 * 1000L
    }
    
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_long_break, container, false)
        backToTimer = view.findViewById(R.id.back_to_timer_txt)
        timerTxt = view.findViewById(R.id.timer_txt)
        playBtn = view.findViewById(R.id.play_btn)
        pauseBtn = view.findViewById(R.id.pause_btn)
        resetBtn = view.findViewById(R.id.reset_btn)
        skipBtn = view.findViewById(R.id.skip_btn)
        coffee = view.findViewById(R.id.coffee)
        longBreakCardBg = view.findViewById(R.id.long_break_card_bg)
        longBreakTxt = view.findViewById(R.id.long_break_txt)
        sessionsTxt = requireActivity().findViewById(R.id.sessions_txt)
        
        val parentLayout = requireActivity().findViewById<RelativeLayout>(R.id.main)
        val sharedPreferences = requireContext().getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
        val amoledMode = sharedPreferences.getBoolean("amoledMode", false)

        if (amoledMode) {
            parentLayout.setBackgroundColor(resources.getColor(android.R.color.black))
        }

        updateCountdownText()
        updateSessionsText()
        
        backToTimer.setOnClickListener {
            vibrate()
            loadTimerFragment()
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
            resetTimer()
        }
        
        skipBtn.setOnClickListener {
            vibrate()
            loadTimerFragment()
        }

        // Restore timer state if it was running
        if (timerRunning) {
            playBtn.visibility = View.GONE
            pauseBtn.visibility = View.VISIBLE
            resetBtn.visibility = View.VISIBLE
            skipBtn.visibility = View.VISIBLE
            startTimer()
        }

        return view
    }
    
    private fun loadTimerFragment() {
        val parentLayout = requireActivity().findViewById<RelativeLayout>(R.id.main)
        val sharedPreferences = requireContext().getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
        val darkMode = sharedPreferences.getBoolean("darkMode", false)
        val amoledMode = sharedPreferences.getBoolean("amoledMode", false)
        
        if (amoledMode) {
            parentLayout.setBackgroundColor(resources.getColor(android.R.color.black))
        } else if (darkMode) {
            parentLayout.setBackgroundResource(R.color.deep_red)
        } else {
            parentLayout.setBackgroundResource(R.color.light_red)
        }
        
        val fragment = TimerFragment.newInstance(1, totalSessions, false, false)
        parentFragmentManager.beginTransaction()
            .replace(R.id.frame_layout, fragment)
            .commit()
    }
    
    private fun startTimer() {
        countDownTimer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateCountdownText()
                playClockSound()
            }

            override fun onFinish() {
                timerRunning = false
                updateBreakState(false)
                playAlarm()
                Toast.makeText(requireContext(), "Break completed", Toast.LENGTH_SHORT).show()
                loadTimerFragment()
            }
        }.start()
        
        timerRunning = true
        updateBreakState(true)
        playBtn.visibility = View.GONE
        pauseBtn.visibility = View.VISIBLE
        resetBtn.visibility = View.VISIBLE
        skipBtn.visibility = View.VISIBLE
    }
    
    private fun pauseTimer() {
        countDownTimer?.cancel()
        timerRunning = false
        updateBreakState(false)
        playBtn.visibility = View.VISIBLE
        pauseBtn.visibility = View.GONE
    }
    
    private fun resetTimer() {
        countDownTimer?.cancel()
        loadSettings()
        timerRunning = false
        updateBreakState(false)
        updateCountdownText()

        playBtn.visibility = View.VISIBLE
        pauseBtn.visibility = View.GONE
        resetBtn.visibility = View.GONE
        skipBtn.visibility = View.GONE
    }
    
    private fun updateCountdownText() {
        val minutes = (timeLeftInMillis / 1000).toInt() / 60
        val seconds = (timeLeftInMillis / 1000).toInt() % 60
        
        timerTxt.text = String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateSessionsText() {
        sessionsTxt.text = "$currentSession/$totalSessions"
    }

    private fun updateBreakState(isActive: Boolean) {
        val sharedPreferences = requireContext().getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
        sharedPreferences.edit().apply {
            putBoolean("isTimerRunning", false)
            putBoolean("isBreakActive", isActive)
            apply()
        }
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
    
    private fun playAlarm() {
        try {
            val sharedPreferences = requireContext().getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
            val alarmDuration = sharedPreferences.getInt("alarmDuration", 3) * 1000L // Convert to milliseconds

            // Show notification
            val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val builder = NotificationCompat.Builder(requireContext(), "timer_notifications")
                .setSmallIcon(R.drawable.coffee)
                .setContentTitle("Long Break Complete")
                .setContentText("Rest or reset?")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            notificationManager.notify(3, builder.build())

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

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        clockSoundPlayer?.release()
        clockSoundPlayer = null
    }

    private fun playClockSound() {
        val sharedPreferences = requireContext().getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)
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

    fun setSessionInfo(currentSession: Int, totalSessions: Int) {
        this.currentSession = currentSession
        this.totalSessions = totalSessions
    }
}