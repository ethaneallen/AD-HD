package com.addhd.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Log
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Ambient Focus Companions — long, loopable low-fi soundscapes for the Focus screen.
 *
 * Two-tier playback, resolved per companion at [play] time:
 *  1. If a bundled loop `res/raw/amb_<key>` exists (e.g. `amb_rain`), it plays on a looping
 *     [MediaPlayer] — the high-quality path.
 *  2. Otherwise the ambience is SYNTHESIZED from filtered noise on a streaming [AudioTrack] so the
 *     feature is never silent even before assets land (mirrors how [Sound] falls back to a tone).
 *
 * Threading: the synth worker streams PCM while it owns the current [generation]; [play]/[stop]
 * bump the generation to retire it (no join, never blocks the caller). The [MediaPlayer] is
 * released synchronously in [stop] (cheap).
 */
object Ambience {
    private const val SAMPLE_RATE = 22_050

    // How long the two asset players overlap at the loop seam, and the fade's update interval.
    private const val CROSSFADE_MS = 1500
    private const val FADE_STEP_MS = 40
    /** Safety margin so a fade always finishes before the outgoing clip reaches its end. */
    private const val END_GUARD_MS = 250

    // Monotonic token: the active synth/crossfade worker runs only while `generation` still equals
    // the value it captured at start. Incrementing it (play/stop) cleanly retires the worker.
    @Volatile private var generation = 0
    // The asset player currently audible, plus the idle instance waiting to take over at the seam.
    @Volatile private var player: MediaPlayer? = null
    @Volatile private var partner: MediaPlayer? = null

    /** The currently-playing companion key, or null when silent. */
    @Volatile var current: String? = null
        private set

    /** Start (or switch to) the ambience for [key]; a no-op re-selection of the same key is ignored. */
    fun play(context: Context, key: String) {
        if (current == key) return
        stop()
        if (!Sound.enabled) return
        current = key

        val appCtx = context.applicationContext
        val resId = appCtx.resources.getIdentifier("amb_$key", "raw", appCtx.packageName)
        if (resId != 0 && startAsset(appCtx, resId, key)) return   // real loop took over

        startSynth(key)                                            // graceful synthesized fallback
    }

    /**
     * Loop a bundled asset SEAMLESSLY. Returns true if it started; false to let the synth take over.
     *
     * `isLooping = true` is not usable here: an MP3 frame is 1152 samples, so the encoder pads both
     * ends of the file to a frame boundary and writes a chunk of silence there. On every wrap you
     * hear that padding as a tick, plus the hard discontinuity between the file's last sample and
     * its first — which on a continuous bed like rain or fire reads as the loop "starting over".
     *
     * Instead TWO players share the job: as one approaches the end, the other is started from the
     * top and the pair are crossfaded. The seam is then a gradual blend of two different moments of
     * the recording rather than a cut, so there is nothing to hear.
     *
     * The fade uses a CONSTANT-POWER (sqrt) curve, not a linear one. These beds are noise-like and
     * uncorrelated, so their powers add while their amplitudes don't — a linear fade would dip
     * audibly in loudness right at the seam, which is exactly the artefact we're removing.
     */
    private fun startAsset(appCtx: Context, resId: Int, key: String): Boolean {
        try {
            // Create with EXPLICIT modern audio attributes (USAGE_MEDIA/CONTENT_TYPE_MUSIC). The
            // plain MediaPlayer.create(ctx, resId) overload routes through a legacy stream that some
            // OEM ROMs handle inconsistently — the classic "plays on the emulator, silent on the
            // phone" trap.
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            fun open() = MediaPlayer.create(appCtx, resId, attrs, AudioManager.AUDIO_SESSION_ID_GENERATE)
                ?.apply {
                    isLooping = false
                    setOnErrorListener { _, what, extra ->
                        Log.w("Ambience", "MediaPlayer error for $key (what=$what extra=$extra)")
                        false
                    }
                }

            val a = open() ?: run {
                Log.w("Ambience", "MediaPlayer.create returned null for $key — using synth fallback")
                return false
            }
            val v = assetVolumeFor(key)

            // The crossfade needs a duration to schedule against, and a second instance to fade to.
            // If either is unavailable, degrade to plain looping — an audible seam still beats
            // dropping a real recording for the synthesized bed.
            val duration = a.duration
            val b = if (duration > 0) open() else null
            if (b == null) {
                a.isLooping = true
                a.setVolume(v, v)
                a.start()
                player = a
                return true
            }

            a.setVolume(v, v)
            a.start()
            player = a
            partner = b
            startCrossfadeLoop(key, v, duration)
            return true
        } catch (t: Throwable) {
            Log.w("Ambience", "asset play failed for $key — using synth fallback", t)
            return false
        }
    }

    /**
     * Drives the seamless wrap: wait until the playing instance is [fadeMs] from the end, bring the
     * idle one in from the top, and crossfade. Retires itself when [generation] moves on.
     */
    private fun startCrossfadeLoop(key: String, volume: Float, durationMs: Int) {
        val myGen = ++generation
        // Long enough to hide the seam, but never more than a quarter of the clip.
        val fadeMs = CROSSFADE_MS.coerceAtMost(durationMs / 4 - END_GUARD_MS).coerceAtLeast(120)
        Thread {
            try {
                while (generation == myGen) {
                    val playing = player ?: return@Thread
                    val idle = partner ?: return@Thread

                    // Poll until the fade window opens (cheap: a check every 100 ms). END_GUARD_MS
                    // keeps the whole fade clear of the clip's end: if the outgoing instance were
                    // allowed to reach PlaybackCompleted mid-fade, the pause() below would throw
                    // and the loop would die after a single wrap.
                    val fadeAt = durationMs - fadeMs - END_GUARD_MS
                    while (generation == myGen && playing.currentPosition < fadeAt) {
                        Thread.sleep(100)
                    }
                    if (generation != myGen) return@Thread

                    idle.seekTo(0)
                    idle.setVolume(0f, 0f)
                    idle.start()

                    val steps = (fadeMs / FADE_STEP_MS).coerceAtLeast(2)
                    for (i in 1..steps) {
                        if (generation != myGen) return@Thread
                        val p = i.toFloat() / steps
                        // Constant power: out² + in² == 1 at every point of the blend.
                        playing.setVolume(volume * sqrt(1f - p), volume * sqrt(1f - p))
                        idle.setVolume(volume * sqrt(p), volume * sqrt(p))
                        Thread.sleep(FADE_STEP_MS.toLong())
                    }
                    if (generation != myGen) return@Thread

                    // Park the outgoing instance back at the top, ready for its next turn. Each step
                    // is guarded individually: if one fails (e.g. it slipped into PlaybackCompleted)
                    // the swap must still happen, or playback would stall here forever.
                    runCatching { if (playing.isPlaying) playing.pause() }
                    runCatching { playing.seekTo(0) }
                    runCatching { playing.setVolume(volume, volume) }
                    runCatching { idle.setVolume(volume, volume) }
                    player = idle
                    partner = playing
                }
            } catch (_: Throwable) {
                // stop() released a player mid-fade, or the device hiccuped. Nothing to recover:
                // stop() owns teardown, and a dead ambience must never take the session down.
            }
        }.apply { isDaemon = true; name = "ambience-loop-$key" }.start()
    }

    /** Synthesize the ambience from filtered noise on a streaming AudioTrack. */
    private fun startSynth(key: String) {
        val myGen = ++generation
        val (alpha, volume, flicker) = synthParamsFor(key)
        Thread {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(2048)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            val rnd = Random(System.nanoTime())
            val buf = ShortArray(minBuf)
            var lp = 0f                 // low-pass state
            var lfo = 0f                // slow amplitude flicker phase
            track.play()
            try {
                while (generation == myGen) {
                    for (i in buf.indices) {
                        val white = rnd.nextFloat() * 2f - 1f
                        lp += (white - lp) * alpha
                        lfo += 0.00018f
                        val flick = 1f - flicker * (0.5f + 0.5f * sin(lfo.toDouble()).toFloat())
                        val s = (lp * volume * flick).coerceIn(-1f, 1f)
                        buf[i] = (s * Short.MAX_VALUE).toInt().toShort()
                    }
                    track.write(buf, 0, buf.size)
                }
            } catch (_: Throwable) {
                // Device audio hiccup — fail silent rather than crash the focus session.
            } finally {
                try { track.stop() } catch (_: Throwable) {}
                track.release()
            }
        }.apply { isDaemon = true; name = "ambience-$key" }.start()
    }

    /** Silence any active ambience. Safe to call from the UI thread (never blocks). */
    fun stop() {
        generation++        // retires the synth worker and/or the crossfade worker
        current = null
        listOf(player, partner).forEach { p ->
            p ?: return@forEach
            try { if (p.isPlaying) p.stop() } catch (_: Throwable) {}
            try { p.release() } catch (_: Throwable) {}
        }
        player = null
        partner = null
    }

    // Per-companion playback level for the bundled recordings (they're normalized differently).
    private fun assetVolumeFor(key: String): Float = when (key) {
        "nature" -> 0.85f
        "hearth" -> 0.7f
        "rain" -> 0.7f
        else -> 0.8f
    }

    // (lowPassAlpha, masterVolume, flickerDepth) for the SYNTHESIZED fallback. Tuned low-fi/calm.
    private fun synthParamsFor(key: String): Triple<Float, Float, Float> = when (key) {
        // Low, dark rumble that slowly "breathes" like a fire flickering in the hearth.
        "hearth" -> Triple(0.020f, 0.55f, 0.45f)
        // Airy, open filtered noise — a calm outdoors bed under birdsong.
        "nature" -> Triple(0.150f, 0.22f, 0.0f)
        // Brighter, steady static — rain drumming on the tent canvas.
        "rain" -> Triple(0.380f, 0.38f, 0.0f)
        else -> Triple(0.1f, 0.25f, 0.0f)
    }
}
