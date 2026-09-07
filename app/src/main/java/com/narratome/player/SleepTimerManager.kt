package com.narratome.player

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.narratome.data.local.preferences.AppPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

@Singleton
class SleepTimerManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val playbackConnector: PlaybackConnector,
    private val preferences: AppPreferencesRepository,
) : SensorEventListener {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null

    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private val sensorManager by lazy { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private val accelerometer by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    private var lastOption: SleepTimerOption? = null
    private var lastShakeTime: Long = 0
    private var fadeDurationSeconds: Int = 60
    private var shakeToExtend: Boolean = true

    init {
        scope.launch {
            playbackConnector.playerState.collect { playerState ->
                if (_state.value.isActive && playerState.libraryItemId == null) {
                    cancel()
                }
            }
        }
        scope.launch {
            preferences.sleepTimerFadeSeconds.collect { fadeDurationSeconds = it }
        }
        scope.launch {
            preferences.sleepTimerShakeToExtend.collect { shakeToExtend = it }
        }
    }

    fun start(option: SleepTimerOption) {
        cancel()
        
        if (option is SleepTimerOption.Off) {
            return
        }

        lastOption = option

        val durationMs = if (option is SleepTimerOption.ThirtySeconds) 30 * 1000L else option.durationMinutes * 60 * 1000L
        if (durationMs <= 0) {
            return
        }

        _state.update { it.copy(isActive = true, timeLeftMs = durationMs) }
        if (shakeToExtend) {
            registerShakeListener()
        }

        timerJob = scope.launch {
            var remaining = durationMs
            val fadeDurationMs = fadeDurationSeconds * 1000L
            while (remaining > 0L) {
                delay(1000)
                val playerState = playbackConnector.playerState.value
                if (playerState.libraryItemId == null) {
                    cancel()
                    return@launch
                }
                if (!playerState.isPlaying) {
                    continue
                }

                remaining = (remaining - 1000L).coerceAtLeast(0L)
                _state.update { it.copy(timeLeftMs = remaining.coerceAtLeast(0)) }

                if (fadeDurationMs > 0L && remaining <= fadeDurationMs) {
                    val volume = (remaining.toFloat() / fadeDurationMs).coerceIn(0f, 1f)
                    playbackConnector.setVolume(volume)
                }
            }
            playbackConnector.pauseSilent()
            playbackConnector.setVolume(1.0f)
            cancel()
        }
    }

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        _state.update { SleepTimerState() }
        unregisterShakeListener()
        playbackConnector.setVolume(1.0f)
    }

    private fun registerShakeListener() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun unregisterShakeListener() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val accelerationMagnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val delta = abs(accelerationMagnitude - SensorManager.GRAVITY_EARTH)

        if (delta > 10.0f) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastShakeTime > 2000L) {
                lastShakeTime = currentTime
                resetTimer()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun resetTimer() {
        lastOption?.let {
            if (it !is SleepTimerOption.Off) {
                start(it)
                triggerVibration()
            }
        }
    }

    private fun triggerVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return

        val pattern = longArrayOf(0, 150, 100, 150)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
}

data class SleepTimerState(
    val isActive: Boolean = false,
    val timeLeftMs: Long = 0L
)

sealed class SleepTimerOption(val durationMinutes: Int) {
    object Off : SleepTimerOption(0)
    object ThirtySeconds : SleepTimerOption(0)
    object FiveMinutes : SleepTimerOption(5)
    object FifteenMinutes : SleepTimerOption(15)
    object TenMinutes : SleepTimerOption(10)
    object ThirtyMinutes : SleepTimerOption(30)
    object FortyFiveMinutes : SleepTimerOption(45)
    object OneHour : SleepTimerOption(60)
}
