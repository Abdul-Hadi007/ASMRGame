package com.example.asmrgame

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.Executors
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural ASMR sound and haptic generator.
 * Uses AudioTrack PCM synthesis so no external audio assets are needed.
 */
class SoundManager(context: Context) {

    private val appContext = context.applicationContext
    private val audioExecutor = Executors.newSingleThreadExecutor()

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    var isSoundEnabled: Boolean = true
    var isVibrationEnabled: Boolean = true

    private var lastVibeTime = 0L
    private var lastSoundTime = 0L

    // Pre-rendered sample buffers for high performance
    private val wipeBuffer: ShortArray by lazy { generateFilteredNoise(0.08f, 22050, 0.25f) }
    private val pluckBuffer: ShortArray by lazy { generatePluckTone(22050) }
    private val creamBuffer: ShortArray by lazy { generateCreamTone(22050) }
    private val peelBuffer: ShortArray by lazy { generatePeelTone(22050) }
    private val chimeBuffer: ShortArray by lazy { generateChimeArpeggio(22050) }
    private val victoryBuffer: ShortArray by lazy { generateVictoryFanfare(22050) }
    private val grindBuffer: ShortArray by lazy { generateGrinderTone(0.1f, 22050) }

    // --- Sound Synthesis ---

    fun playWipeSound() {
        val now = System.currentTimeMillis()
        if (now - lastSoundTime < 60) return
        lastSoundTime = now
        playBuffer(wipeBuffer, 22050)
    }

    fun playGrindSound() {
        val now = System.currentTimeMillis()
        if (now - lastSoundTime < 50) return
        lastSoundTime = now
        playBuffer(grindBuffer, 22050)
    }

    fun playPluckSound() {
        playBuffer(pluckBuffer, 22050)
    }

    fun playCreamSound() {
        val now = System.currentTimeMillis()
        if (now - lastSoundTime < 80) return
        lastSoundTime = now
        playBuffer(creamBuffer, 22050)
    }

    fun playPeelSound() {
        val now = System.currentTimeMillis()
        if (now - lastSoundTime < 50) return
        lastSoundTime = now
        playBuffer(peelBuffer, 22050)
    }

    fun playChime() {
        playBuffer(chimeBuffer, 22050)
    }

    fun playVictory() {
        playBuffer(victoryBuffer, 22050)
    }

    private fun playBuffer(buffer: ShortArray, sampleRate: Int) {
        if (!isSoundEnabled) return
        audioExecutor.execute {
            try {
                val minSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(buffer.size * 2, minSize)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size)
                track.play()
                // Sleep and release
                val durationMs = (buffer.size * 1000L) / sampleRate
                Thread.sleep(durationMs + 30)
                track.stop()
                track.release()
            } catch (_: Exception) {
            }
        }
    }

    // --- Haptic Feedback ---

    fun vibrateTick(intensity: Int = 80) {
        val now = System.currentTimeMillis()
        if (now - lastVibeTime < 55) return
        lastVibeTime = now
        vibrate(20, intensity)
    }

    fun vibrateGrind() {
        val now = System.currentTimeMillis()
        if (now - lastVibeTime < 40) return
        lastVibeTime = now
        vibrate(30, 180)
    }

    fun vibratePop() {
        vibrate(45, 255)
    }

    fun vibrateSuccess() {
        vibrate(100, 200)
    }

    private fun vibrate(durationMs: Long, amplitude: Int) {
        if (!isVibrationEnabled) return
        try {
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val clampedAmp = amplitude.coerceIn(1, 255)
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(durationMs, clampedAmp)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {
        }
    }

    // --- Audio Generators ---

    private fun generateFilteredNoise(durationSec: Float, sampleRate: Int, volume: Float): ShortArray {
        val totalSamples = (durationSec * sampleRate).toInt()
        val buffer = ShortArray(totalSamples)
        var last = 0f
        for (i in 0 until totalSamples) {
            val progress = i.toFloat() / totalSamples
            // Smooth bell envelope
            val env = sin(progress * Math.PI).toFloat()
            val white = (Random.nextFloat() * 2f - 1f)
            // Low-pass filter for soft brush/wipe sound
            last += 0.18f * (white - last)
            val sample = (last * env * volume * 32767).toInt().coerceIn(-32768, 32767)
            buffer[i] = sample.toShort()
        }
        return buffer
    }

    private fun generatePluckTone(sampleRate: Int): ShortArray {
        val durationSec = 0.12f
        val totalSamples = (durationSec * sampleRate).toInt()
        val buffer = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val progress = i.toFloat() / totalSamples
            val env = (1f - progress) * (1f - progress)
            // Upward pop frequency from 300Hz to 850Hz
            val freq = 300.0 + 550.0 * (1.0 - progress)
            val angle = 2.0 * Math.PI * freq * (i.toDouble() / sampleRate)
            val sample = (sin(angle) * env * 0.7 * 32767).toInt().coerceIn(-32768, 32767)
            buffer[i] = sample.toShort()
        }
        return buffer
    }

    private fun generateGrinderTone(durationSec: Float, sampleRate: Int): ShortArray {
        val totalSamples = (durationSec * sampleRate).toInt()
        val buffer = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val progress = i.toFloat() / totalSamples
            val env = sin(progress * Math.PI).toFloat()
            // 240Hz buzzing hum with harmonic
            val base = sin(2.0 * Math.PI * 240.0 * t)
            val harm = 0.5 * sin(2.0 * Math.PI * 480.0 * t)
            val noise = 0.2 * (Random.nextFloat() * 2f - 1f)
            val sample = ((base + harm + noise) * env * 0.35 * 32767).toInt().coerceIn(-32768, 32767)
            buffer[i] = sample.toShort()
        }
        return buffer
    }

    private fun generateCreamTone(sampleRate: Int): ShortArray {
        val durationSec = 0.09f
        val totalSamples = (durationSec * sampleRate).toInt()
        val buffer = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val progress = i.toFloat() / totalSamples
            val env = sin(progress * Math.PI).toFloat()
            val freq = 520.0 + sin(t * 80.0) * 80.0
            val sample = (sin(2.0 * Math.PI * freq * t) * env * 0.25 * 32767).toInt().coerceIn(-32768, 32767)
            buffer[i] = sample.toShort()
        }
        return buffer
    }

    private fun generateChimeArpeggio(sampleRate: Int): ShortArray {
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.50) // C5, E5, G5, C6
        val noteDuration = 0.08
        val totalDuration = noteDuration * notes.size + 0.2
        val totalSamples = (totalDuration * sampleRate).toInt()
        val buffer = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            var sum = 0.0
            for ((idx, freq) in notes.withIndex()) {
                val noteStart = idx * noteDuration
                if (t >= noteStart) {
                    val noteT = t - noteStart
                    val env = kotlin.math.exp(-noteT * 7.0)
                    sum += sin(2.0 * Math.PI * freq * noteT) * env
                }
            }
            val sample = (sum * 0.3 * 32767).toInt().coerceIn(-32768, 32767)
            buffer[i] = sample.toShort()
        }
        return buffer
    }

    private fun generateVictoryFanfare(sampleRate: Int): ShortArray {
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.50, 1318.51)
        val noteDur = 0.11
        val totalDuration = noteDur * notes.size + 0.35
        val totalSamples = (totalDuration * sampleRate).toInt()
        val buffer = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            var sum = 0.0
            for ((idx, freq) in notes.withIndex()) {
                val start = idx * noteDur
                if (t >= start) {
                    val dt = t - start
                    val env = kotlin.math.exp(-dt * 5.0)
                    sum += (sin(2.0 * Math.PI * freq * dt) + 0.3 * sin(4.0 * Math.PI * freq * dt)) * env
                }
            }
            val sample = (sum * 0.28 * 32767).toInt().coerceIn(-32768, 32767)
            buffer[i] = sample.toShort()
        }
        return buffer
    }

    private fun generatePeelTone(sampleRate: Int): ShortArray {
        val durationSec = 0.08f
        val totalSamples = (durationSec * sampleRate).toInt()
        val buffer = ShortArray(totalSamples)
        var lp = 0f
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val progress = i.toFloat() / totalSamples
            val env = sin(progress * Math.PI).toFloat()
            val crackle = if (Random.nextFloat() < 0.28f) (Random.nextFloat() * 2f - 1f) * 0.85f else 0f
            val baseNoise = (Random.nextFloat() * 2f - 1f) * 0.25f
            lp += 0.35f * ((crackle + baseNoise) - lp)
            val freq = 420.0 + 380.0 * (1.0 - progress)
            val resonance = sin(2.0 * Math.PI * freq * t) * 0.2
            val sample = ((lp + resonance) * env * 0.45 * 32767).toInt().coerceIn(-32768, 32767)
            buffer[i] = sample.toShort()
        }
        return buffer
    }

    fun release() {
        audioExecutor.shutdown()
    }
}
