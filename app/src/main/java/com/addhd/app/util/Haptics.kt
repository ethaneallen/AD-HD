package com.addhd.app.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Breathing haptics for Dopamine SOS "Ride the Urge" — designed for eyes-closed use.
 * Inhale ramps up, exhale fades out, and the finish is a distinct heavy triple pulse.
 *
 * Uses waveform amplitude patterns on API 26+. On very old devices the amplitude arrays
 * are ignored and it falls back to a plain timed vibration, which still gives the cue.
 */
object Haptics {

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            mgr?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** ~4s slowly building rumble (amplitude ramps 0 -> full). */
    fun inhale(context: Context, durationMs: Long = 4000L) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        val steps = 20
        val timings = LongArray(steps) { durationMs / steps }
        val amplitudes = IntArray(steps) { i ->
            // 12 (barely-there) climbing to 255 (full)
            (12 + (243.0 * i / (steps - 1)).toInt()).coerceIn(1, 255)
        }
        v.cancel()
        v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    /** ~4s smooth fading rumble (amplitude fades full -> 0). */
    fun exhale(context: Context, durationMs: Long = 4000L) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        val steps = 20
        val timings = LongArray(steps) { durationMs / steps }
        val amplitudes = IntArray(steps) { i ->
            (255 - (243.0 * i / (steps - 1)).toInt()).coerceIn(1, 255)
        }
        v.cancel()
        v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    /** Distinct heavy triple pulse — "you rode it out". */
    fun finishTriple(context: Context) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        // wait, buzz, gap, buzz, gap, buzz — all at full strength.
        val timings = longArrayOf(0, 220, 130, 220, 130, 320)
        val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
        v.cancel()
        v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    fun stop(context: Context) {
        vibrator(context)?.cancel()
    }

    /** Tiny tactile click — used when a finger slides onto a new grid cell. */
    fun tick(context: Context) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(18L, 90))
    }

    /**
     * Dice tumble: a rapid, irregular clatter that ramps down as the die "settles" — meant to
     * feel like a d20 bouncing across a wooden tray. ~700ms.
     */
    fun diceRoll(context: Context) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        // Alternating buzz/gap pairs; amplitudes decay so it loses energy like a real roll.
        val timings = longArrayOf(0, 40, 55, 35, 70, 30, 90, 26, 120, 22, 150)
        val amplitudes = intArrayOf(0, 200, 0, 175, 0, 150, 0, 120, 0, 90, 0)
        v.cancel()
        v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    /** The die lands — a single firm thud. */
    fun diceLand(context: Context) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(45L, 255))
    }

    /** Critical hit celebration — two crisp escalating pops. */
    fun diceCrit(context: Context) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        val timings = longArrayOf(0, 60, 80, 140)
        val amplitudes = intArrayOf(0, 200, 0, 255)
        v.cancel()
        v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    /**
     * Lockpick tumbler click. [strength] 0..1 scales amplitude so clicks feel heavier as the
     * dial nears the sweet spot. A very short, sharp tick.
     */
    fun lockTick(context: Context, strength: Float) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        val amp = (40 + (strength.coerceIn(0f, 1f) * 215)).toInt().coerceIn(1, 255)
        v.vibrate(VibrationEffect.createOneShot(12L, amp))
    }

    /** The lock pops open — heavy double thunk. */
    fun lockPop(context: Context) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        val timings = longArrayOf(0, 90, 60, 180)
        val amplitudes = intArrayOf(0, 255, 0, 255)
        v.cancel()
        v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    /** Rune pop — a snappy single click. */
    fun runePop(context: Context) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(22L, 180))
    }

    /** Board cleared — celebratory triple. */
    fun boardClear(context: Context) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        val timings = longArrayOf(0, 70, 50, 70, 50, 200)
        val amplitudes = intArrayOf(0, 200, 0, 220, 0, 255)
        v.cancel()
        v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }
}
