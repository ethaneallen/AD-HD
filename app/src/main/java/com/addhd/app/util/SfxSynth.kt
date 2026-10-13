package com.addhd.app.util

import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Procedural sound design for every [Sfx] event.
 *
 * Why this exists: no `sfx_*` clips were ever bundled in `res/raw`, so EVERY effect in the app fell
 * through to [android.media.ToneGenerator] — the system's DTMF/call-progress beeps. That is the
 * "1970s handheld game" sound: raw square-ish tones, no envelope, no decay, no space.
 *
 * Rather than ship (and license) a pile of audio files, each effect is synthesized here into a real
 * 16-bit/44.1 kHz PCM sample, cached on disk once, and played back through [SoundPool] like any
 * other asset. The ingredients are what actually makes a sound feel modern and expensive:
 *
 *  - **Inharmonic bell/metal partials** instead of a single tone — bronze, glass and gong timbres.
 *  - **Per-partial exponential decay** (highs die first, like real struck metal).
 *  - **Musical intervals** — everything sits in A minor pentatonic, so two effects landing together
 *    are consonant rather than a clash.
 *  - **A short room reverb tail**, so sounds exist in the tavern rather than right against the ear.
 *  - **Click-free edges + soft clipping**, which is most of the difference between "cheap" and "warm".
 *
 * Bump [VERSION] whenever a recipe changes — the cache directory is version-stamped, so old
 * renders are abandoned and regenerated on next launch.
 */
internal object SfxSynth {
    const val SAMPLE_RATE = 44_100
    const val VERSION = 1

    // A minor pentatonic — the scale every effect is tuned to.
    private const val A3 = 220.0f
    private const val C4 = 261.63f
    private const val D4 = 293.66f
    private const val E4 = 329.63f
    private const val A4 = 440.0f
    private const val C5 = 523.25f
    private const val D5 = 587.33f
    private const val E5 = 659.25f
    private const val A5 = 880.0f
    private const val E6 = 1318.5f

    /** Render [sfx] as mono 16-bit PCM wrapped in a .wav container. */
    fun renderWav(sfx: Sfx): ByteArray = wav(render(sfx))

    private fun render(sfx: Sfx): FloatArray = when (sfx) {
        // ── Frequent UI ────────────────────────────────────────────────────────
        // A soft wooden tap: a short mid thunk with the tiniest room on it. Deliberately plain —
        // it fires constantly, so it has to disappear into the background.
        Sfx.SFX_MENU_TAP -> canvas(170).apply {
            wood(freq = D5, amp = 0.5f, atMs = 0, durMs = 60)
            bell(freq = D5 * 2, amp = 0.10f, atMs = 0, durMs = 90, decay = 20f)
            room(mix = 0.12f, decay = 0.26f)
        }

        // Checking a quest off: a two-note harp flick up a fifth. Light, affirming, not a "ding".
        Sfx.SFX_TASK_TOGGLE -> canvas(520).apply {
            pluck(freq = A4, amp = 0.55f, atMs = 0, durMs = 220)
            pluck(freq = E5, amp = 0.45f, atMs = 55, durMs = 260)
            room(mix = 0.22f, decay = 0.34f)
        }

        // A rune clearing: glassy ping that bends up as it dies. Short and bright.
        Sfx.SFX_RUNE_POP -> canvas(360).apply {
            bellGlide(fromHz = C5, toHz = D5 * 1.5f, amp = 0.5f, atMs = 0, durMs = 200, decay = 9f)
            noise(amp = 0.06f, atMs = 0, durMs = 40, alpha = 0.55f, decay = 40f)
            room(mix = 0.2f, decay = 0.3f)
        }

        // Dice tumbling in a leather cup: a scatter of woody clatter, pitch-varied, then settling.
        Sfx.SFX_DICE_ROLL -> canvas(520).apply {
            val rnd = Random(0xD1CE)
            var t = 0
            repeat(7) { i ->
                val f = 300f + rnd.nextFloat() * 520f
                wood(freq = f, amp = 0.30f - i * 0.02f, atMs = t, durMs = 42)
                noise(amp = 0.05f, atMs = t, durMs = 30, alpha = 0.7f, decay = 60f)
                t += 22 + (rnd.nextFloat() * 34f).toInt()
            }
            room(mix = 0.2f, decay = 0.32f)
        }

        // ── Discrete events ───────────────────────────────────────────────────
        // A small win: a three-note pentatonic arpeggio on a warm bell. Rewarding, over quickly.
        Sfx.SFX_QUICK_WIN -> canvas(900).apply {
            bell(freq = A4, amp = 0.42f, atMs = 0, durMs = 420, decay = 5f)
            bell(freq = C5, amp = 0.40f, atMs = 70, durMs = 420, decay = 5f)
            bell(freq = E5, amp = 0.44f, atMs = 140, durMs = 520, decay = 4.5f)
            shimmer(freq = A5, amp = 0.10f, atMs = 150, durMs = 420)
            room(mix = 0.3f, decay = 0.42f)
        }

        // Harvesting the garden: liquid blips gliding upward, with a soft pour of hiss underneath.
        Sfx.SFX_POTION_HARVEST -> canvas(900).apply {
            val rnd = Random(0xB0771E)
            repeat(5) { i ->
                val base = 420f + i * 90f
                bellGlide(
                    fromHz = base, toHz = base * 1.6f,
                    amp = 0.26f, atMs = 40 * i, durMs = 150, decay = 16f,
                )
                wood(freq = base * 0.5f, amp = 0.08f, atMs = 40 * i, durMs = 50)
                if (rnd.nextBoolean()) noise(amp = 0.05f, atMs = 40 * i, durMs = 70, alpha = 0.3f, decay = 26f)
            }
            noise(amp = 0.08f, atMs = 0, durMs = 420, alpha = 0.22f, decay = 7f)
            room(mix = 0.26f, decay = 0.38f)
        }

        // The streak holding: a struck shield — metal ring with a swell of air around it.
        Sfx.SFX_STREAK_GUARD -> canvas(1200).apply {
            metal(freq = D4, amp = 0.46f, atMs = 0, durMs = 800)
            bell(freq = D5, amp = 0.18f, atMs = 0, durMs = 500, decay = 6f)
            noiseSwell(amp = 0.10f, atMs = 0, durMs = 420, alpha = 0.3f)
            room(mix = 0.34f, decay = 0.5f)
        }

        // A lock giving way: mechanical clunk, then the bright release ping above it.
        Sfx.SFX_LOCK_POP -> canvas(800).apply {
            wood(freq = 180f, amp = 0.5f, atMs = 0, durMs = 90)
            noise(amp = 0.14f, atMs = 0, durMs = 60, alpha = 0.65f, decay = 48f)
            bell(freq = E5, amp = 0.34f, atMs = 70, durMs = 420, decay = 6f)
            bell(freq = A5, amp = 0.18f, atMs = 85, durMs = 380, decay = 8f)
            room(mix = 0.3f, decay = 0.44f)
        }

        // A critical roll: a stacked fifth that blooms, with sparkle riding on top.
        Sfx.SFX_DICE_CRIT -> canvas(1300).apply {
            bell(freq = A4, amp = 0.40f, atMs = 0, durMs = 700, decay = 3.4f)
            bell(freq = E5, amp = 0.34f, atMs = 20, durMs = 700, decay = 3.4f)
            bell(freq = A5, amp = 0.26f, atMs = 40, durMs = 760, decay = 3.2f)
            sparkle(amp = 0.12f, atMs = 90, durMs = 620, seed = 0xC217)
            shimmer(freq = E6, amp = 0.08f, atMs = 120, durMs = 560)
            room(mix = 0.36f, decay = 0.54f)
        }

        // Landing a hit on a boss: a weighted low impact with a crunch of debris. No tone at all.
        Sfx.SFX_BOSS_HIT -> canvas(700).apply {
            thud(fromHz = 150f, toHz = 48f, amp = 0.62f, atMs = 0, durMs = 260)
            noise(amp = 0.22f, atMs = 0, durMs = 150, alpha = 0.5f, decay = 20f)
            metal(freq = A3 * 0.75f, amp = 0.12f, atMs = 10, durMs = 300)
            room(mix = 0.22f, decay = 0.4f)
        }

        // The boss going down: a gong struck once, left to ring out over a low rumble.
        Sfx.SFX_BOSS_DEFEAT -> canvas(2000).apply {
            thud(fromHz = 120f, toHz = 40f, amp = 0.5f, atMs = 0, durMs = 420)
            metal(freq = A3, amp = 0.5f, atMs = 0, durMs = 1500)
            metal(freq = E4, amp = 0.26f, atMs = 40, durMs = 1300)
            noise(amp = 0.18f, atMs = 0, durMs = 260, alpha = 0.42f, decay = 14f)
            room(mix = 0.42f, decay = 0.62f)
        }

        // Levelling up: a four-note pentatonic climb resolving an octave higher, with a long tail.
        Sfx.SFX_LEVEL_UP -> canvas(2000).apply {
            bell(freq = A4, amp = 0.38f, atMs = 0, durMs = 500, decay = 5f)
            bell(freq = C5, amp = 0.38f, atMs = 95, durMs = 500, decay = 5f)
            bell(freq = E5, amp = 0.38f, atMs = 190, durMs = 560, decay = 4.5f)
            bell(freq = A5, amp = 0.46f, atMs = 285, durMs = 1000, decay = 2.6f)
            bell(freq = E5 * 2, amp = 0.14f, atMs = 300, durMs = 900, decay = 3f)
            sparkle(amp = 0.10f, atMs = 300, durMs = 900, seed = 0x1E7E1)
            shimmer(freq = E6, amp = 0.07f, atMs = 340, durMs = 800)
            room(mix = 0.4f, decay = 0.6f)
        }
    }.finish()

    // ── Voices ────────────────────────────────────────────────────────────────

    private fun canvas(ms: Int) = FloatArray(ms * SAMPLE_RATE / 1000)

    /** Struck-bell partial ratios (mildly inharmonic — bronze, not a pure sine). */
    private val BELL_PARTIALS = floatArrayOf(1f, 2.02f, 3.01f, 4.17f, 5.43f, 6.79f)

    /** Gong/shield metal: strongly inharmonic, so it reads as metal rather than as a note. */
    private val METAL_PARTIALS = floatArrayOf(1f, 1.52f, 2.13f, 2.97f, 3.71f, 4.63f, 5.89f, 7.31f)

    /**
     * A struck bell at [freq]. Higher partials are quieter AND decay faster, which is the whole
     * reason this sounds like a struck object instead of an organ chord.
     */
    private fun FloatArray.bell(freq: Float, amp: Float, atMs: Int, durMs: Int, decay: Float) =
        partials(BELL_PARTIALS, freq, amp, atMs, durMs, decay, partialDecayBias = 0.9f)

    /** Inharmonic metal ring — shields, gongs, armour. Rings far longer than a bell. */
    private fun FloatArray.metal(freq: Float, amp: Float, atMs: Int, durMs: Int) =
        partials(METAL_PARTIALS, freq, amp, atMs, durMs, decay = 2.2f, partialDecayBias = 0.55f)

    private fun FloatArray.partials(
        ratios: FloatArray,
        freq: Float,
        amp: Float,
        atMs: Int,
        durMs: Int,
        decay: Float,
        partialDecayBias: Float,
    ) {
        val start = msToSamples(atMs)
        val len = msToSamples(durMs)
        for ((i, ratio) in ratios.withIndex()) {
            val pAmp = amp / (1f + i * 1.6f)
            val pDecay = decay * (1f + i * partialDecayBias)
            val w = 2.0 * PI * freq * ratio / SAMPLE_RATE
            // A couple of ms of attack keeps the transient from reading as a click.
            val attack = msToSamples(3).coerceAtLeast(1)
            for (n in 0 until len) {
                val idx = start + n
                if (idx >= size) break
                val t = n.toFloat() / SAMPLE_RATE
                val env = exp(-pDecay * t) * (if (n < attack) n.toFloat() / attack else 1f)
                this[idx] += (pAmp * env * sin(w * n)).toFloat()
            }
        }
    }

    /** A bell whose pitch bends from [fromHz] to [toHz] over its decay — a magical "pop". */
    private fun FloatArray.bellGlide(fromHz: Float, toHz: Float, amp: Float, atMs: Int, durMs: Int, decay: Float) {
        val start = msToSamples(atMs)
        val len = msToSamples(durMs)
        var phase = 0.0
        val attack = msToSamples(2).coerceAtLeast(1)
        for (n in 0 until len) {
            val idx = start + n
            if (idx >= size) break
            val p = n.toFloat() / len
            val f = fromHz + (toHz - fromHz) * p * p          // eases into the bend
            phase += 2.0 * PI * f / SAMPLE_RATE
            val t = n.toFloat() / SAMPLE_RATE
            val env = exp(-decay * t) * (if (n < attack) n.toFloat() / attack else 1f)
            this[idx] += (amp * env * (sin(phase) + 0.3 * sin(phase * 2.02))).toFloat()
        }
    }

    /** Plucked string / harp: a full harmonic stack that decays fast. Warmer than a sine. */
    private fun FloatArray.pluck(freq: Float, amp: Float, atMs: Int, durMs: Int) {
        val start = msToSamples(atMs)
        val len = msToSamples(durMs)
        val attack = msToSamples(2).coerceAtLeast(1)
        for (h in 1..8) {
            val hAmp = amp / (h * 1.5f)
            val w = 2.0 * PI * freq * h / SAMPLE_RATE
            val decay = 7f + h * 2.2f
            for (n in 0 until len) {
                val idx = start + n
                if (idx >= size) break
                val t = n.toFloat() / SAMPLE_RATE
                val env = exp(-decay * t) * (if (n < attack) n.toFloat() / attack else 1f)
                this[idx] += (hAmp * env * sin(w * n)).toFloat()
            }
        }
    }

    /** Wooden tap/clack: a low sine with a very fast decay plus a hint of its second partial. */
    private fun FloatArray.wood(freq: Float, amp: Float, atMs: Int, durMs: Int) {
        val start = msToSamples(atMs)
        val len = msToSamples(durMs)
        val w = 2.0 * PI * freq / SAMPLE_RATE
        val attack = msToSamples(1).coerceAtLeast(1)
        for (n in 0 until len) {
            val idx = start + n
            if (idx >= size) break
            val t = n.toFloat() / SAMPLE_RATE
            val env = exp(-34f * t) * (if (n < attack) n.toFloat() / attack else 1f)
            this[idx] += (amp * env * (sin(w * n) + 0.35 * sin(w * n * 2.76))).toFloat()
        }
    }

    /** Weighted impact: a sine sliding down in pitch — the body of a hit, no tone implied. */
    private fun FloatArray.thud(fromHz: Float, toHz: Float, amp: Float, atMs: Int, durMs: Int) {
        val start = msToSamples(atMs)
        val len = msToSamples(durMs)
        var phase = 0.0
        for (n in 0 until len) {
            val idx = start + n
            if (idx >= size) break
            val p = n.toFloat() / len
            val f = fromHz + (toHz - fromHz) * p
            phase += 2.0 * PI * f / SAMPLE_RATE
            val env = exp(-9f * p)
            this[idx] += (amp * env * tanh(1.6 * sin(phase))).toFloat()
        }
    }

    /** Low-passed noise with an exponential decay — debris, hiss, clatter. */
    private fun FloatArray.noise(amp: Float, atMs: Int, durMs: Int, alpha: Float, decay: Float) {
        val start = msToSamples(atMs)
        val len = msToSamples(durMs)
        val rnd = Random(atMs * 7919 + durMs)
        var lp = 0f
        for (n in 0 until len) {
            val idx = start + n
            if (idx >= size) break
            lp += ((rnd.nextFloat() * 2f - 1f) - lp) * alpha
            val t = n.toFloat() / SAMPLE_RATE
            this[idx] += amp * lp * exp(-decay * t)
        }
    }

    /** Noise that fades IN then out — an airy swell behind a struck sound. */
    private fun FloatArray.noiseSwell(amp: Float, atMs: Int, durMs: Int, alpha: Float) {
        val start = msToSamples(atMs)
        val len = msToSamples(durMs)
        val rnd = Random(durMs * 104_729L + atMs)
        var lp = 0f
        for (n in 0 until len) {
            val idx = start + n
            if (idx >= size) break
            lp += ((rnd.nextFloat() * 2f - 1f) - lp) * alpha
            val p = n.toFloat() / len
            this[idx] += amp * lp * sin(PI * p).toFloat()   // single hump: in and back out
        }
    }

    /** A slow, vibrato-ed high sine — the "magic" air over a reward sound. */
    private fun FloatArray.shimmer(freq: Float, amp: Float, atMs: Int, durMs: Int) {
        val start = msToSamples(atMs)
        val len = msToSamples(durMs)
        var phase = 0.0
        for (n in 0 until len) {
            val idx = start + n
            if (idx >= size) break
            val t = n.toFloat() / SAMPLE_RATE
            val vib = 1f + 0.012f * sin(2.0 * PI * 5.5 * t).toFloat()
            phase += 2.0 * PI * freq * vib / SAMPLE_RATE
            val p = n.toFloat() / len
            val env = sin(PI * p).toFloat() * exp(-1.2f * p)
            this[idx] += (amp * env * sin(phase)).toFloat()
        }
    }

    /** Scattered high bell grains — the little "sparks" on a crit or level-up. */
    private fun FloatArray.sparkle(amp: Float, atMs: Int, durMs: Int, seed: Int) {
        val rnd = Random(seed)
        val scale = floatArrayOf(A5, C5 * 2, D5 * 2, E5 * 2, A5 * 2)
        repeat(9) {
            val at = atMs + (rnd.nextFloat() * durMs * 0.8f).toInt()
            val f = scale[rnd.nextInt(scale.size)]
            bell(freq = f, amp = amp * (0.5f + rnd.nextFloat() * 0.5f), atMs = at, durMs = 240, decay = 13f)
        }
    }

    // ── Space & polish ────────────────────────────────────────────────────────

    /**
     * A small tavern room, built from four feedback comb delays at mutually prime lengths (so their
     * repeats never line up into a flutter) plus a single all-pass to smear the early reflections.
     * This is the single biggest reason the result reads as "a sound in a place" rather than a beep.
     */
    private fun FloatArray.room(mix: Float, decay: Float) {
        if (mix <= 0f) return
        val combs = intArrayOf(1021, 1217, 1453, 1709)   // ≈23–39 ms at 44.1 kHz
        val wet = FloatArray(size)
        for (len in combs) {
            val buf = FloatArray(len)
            var i = 0
            for (n in 0 until size) {
                val delayed = buf[i]
                val v = this[n] + delayed * decay
                buf[i] = v
                wet[n] += delayed * 0.25f
                i = (i + 1) % len
            }
        }
        // All-pass smear (≈5 ms) — breaks up the metallic ring the combs leave behind.
        val apLen = 221
        val apBuf = FloatArray(apLen)
        var j = 0
        val g = 0.6f
        for (n in 0 until size) {
            val delayed = apBuf[j]
            val v = wet[n] + delayed * g
            apBuf[j] = v
            wet[n] = delayed - v * g
            j = (j + 1) % apLen
        }
        for (n in 0 until size) this[n] += wet[n] * mix
    }

    /** Normalize to a consistent peak, soft-clip for warmth, and fade the edges so nothing clicks. */
    private fun FloatArray.finish(): FloatArray {
        var peak = 0f
        for (v in this) peak = max(peak, abs(v))
        if (peak > 0f) {
            val gain = 0.92f / peak
            for (n in indices) this[n] = tanh(this[n] * gain * 1.1f).toFloat() * 0.9f
        }
        val fadeIn = msToSamples(2).coerceAtLeast(1)
        val fadeOut = msToSamples(12).coerceAtLeast(1)
        for (n in 0 until minOf(fadeIn, size)) this[n] *= n.toFloat() / fadeIn
        for (n in 0 until minOf(fadeOut, size)) {
            this[size - 1 - n] *= n.toFloat() / fadeOut
        }
        return this
    }

    private fun msToSamples(ms: Int) = ms * SAMPLE_RATE / 1000

    // ── WAV container ─────────────────────────────────────────────────────────

    /** Wrap mono float samples as a 16-bit PCM RIFF/WAVE file (what SoundPool wants on disk). */
    private fun wav(samples: FloatArray): ByteArray {
        val dataBytes = samples.size * 2
        val out = ByteArrayOutputStream(44 + dataBytes)
        fun ascii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun le32(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
        fun le16(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))

        ascii("RIFF"); le32(36 + dataBytes); ascii("WAVE")
        ascii("fmt "); le32(16); le16(1); le16(1)          // PCM, mono
        le32(SAMPLE_RATE); le32(SAMPLE_RATE * 2)           // byte rate (mono, 2 bytes/sample)
        le16(2); le16(16)                                  // block align, bits per sample
        ascii("data"); le32(dataBytes)
        for (s in samples) {
            val v = (s.coerceIn(-1f, 1f) * 32_767f).toInt()
            le16(v)
        }
        return out.toByteArray()
    }
}
