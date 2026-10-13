package com.addhd.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Low-latency RPG sound-effects manager.
 *
 * Design goals (audio-engine refactor):
 *  1. [SoundPool] architecture — every UI/combat SFX is decoded ONCE at startup and kept
 *     resident, so triggering one is a cheap `play()` on an already-loaded sample id (no
 *     per-shot object churn, no decode latency, overlapping shots mix instead of cutting off).
 *  2. Micro-variations — frequent effects ([SfxCategory.FREQUENT], e.g. menu taps / task
 *     toggles) get a small random playback rate (0.95f..1.05f) so rapid repeats never sound
 *     like the same robotic click twice.
 *  3. Clean event mapping — call sites speak in [Sfx] event enums; the mapping to a concrete
 *     16-bit asset lives here and nowhere else.
 *
 * Where each sample comes from, in priority order:
 *  1. A bundled clip in `res/raw/` named after the enum's [resName] (e.g. `sfx_boss_hit.ogg`) —
 *     drop one in and it takes over automatically; no code or call-site change.
 *  2. Otherwise [SfxSynth] renders the effect to a real 16-bit PCM .wav, cached in the app's files
 *     dir, and that is loaded into the pool. This is the normal path today — no clips are bundled.
 *  3. A [ToneGenerator] blip, but ONLY during the brief first-launch window while (2) renders.
 *
 * Step 2 exists because step 3 used to be the only path: every sound in the app was a system
 * DTMF/call-progress beep, which is exactly what made it sound like a cheap 1970s handheld.
 *
 * Muting: [enabled] gates all playback. AppViewModel flips it from the persisted Settings toggle.
 */
enum class SfxCategory {
    /** Fires often in tight succession — gets pitch randomization to avoid repetition fatigue. */
    FREQUENT,

    /** Discrete moment (win, boss defeat, level up) — plays at a stable pitch for consistency. */
    EVENT,
}

enum class Sfx(
    /** Expected `res/raw` file name (without extension). */
    val resName: String,
    val category: SfxCategory,
    /** Per-effect mix level 0f..1f (kept below 1f for chatty UI sounds). */
    val baseVolume: Float = 1f,
    // --- Fallback tone used ONLY until the real clip is bundled in res/raw ---
    val fallbackTone: Int = ToneGenerator.TONE_PROP_BEEP,
    val fallbackMs: Int = 60,
) {
    // ── Frequent UI (pitch-varied) ─────────────────────────────────────────
    // Levels pulled down from the old near-unity mix: chatty taps sit low so they never dominate,
    // and the mid/high-heavy fallback tones (BEEP/BEEP2) are the harshest, so they're quietest.
    SFX_MENU_TAP("sfx_menu_tap", SfxCategory.FREQUENT, 0.40f, ToneGenerator.TONE_PROP_ACK, 30),
    SFX_TASK_TOGGLE("sfx_task_toggle", SfxCategory.FREQUENT, 0.65f, ToneGenerator.TONE_PROP_ACK, 80),
    SFX_RUNE_POP("sfx_rune_pop", SfxCategory.FREQUENT, 0.55f, ToneGenerator.TONE_PROP_ACK, 45),
    SFX_DICE_ROLL("sfx_dice_roll", SfxCategory.FREQUENT, 0.75f, ToneGenerator.TONE_PROP_ACK, 110),

    // ── Discrete events (stable pitch) ─────────────────────────────────────
    // Events keep more headroom than chatty UI (they don't repeat), but still trimmed off unity so
    // a win landing on top of a tap can't sum into clipping.
    SFX_QUICK_WIN("sfx_quick_win", SfxCategory.EVENT, 0.80f, ToneGenerator.TONE_PROP_ACK, 70),
    SFX_POTION_HARVEST("sfx_potion_harvest", SfxCategory.EVENT, 0.85f, ToneGenerator.TONE_PROP_ACK, 130),
    SFX_STREAK_GUARD("sfx_streak_guard", SfxCategory.EVENT, 0.85f, ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 180),
    SFX_LOCK_POP("sfx_lock_pop", SfxCategory.EVENT, 0.90f, ToneGenerator.TONE_PROP_ACK, 150),
    SFX_DICE_CRIT("sfx_dice_crit", SfxCategory.EVENT, 0.90f, ToneGenerator.TONE_PROP_ACK, 160),
    SFX_BOSS_HIT("sfx_boss_hit", SfxCategory.EVENT, 0.90f, ToneGenerator.TONE_CDMA_LOW_L, 130),
    SFX_BOSS_DEFEAT("sfx_boss_defeat", SfxCategory.EVENT, 0.95f, ToneGenerator.TONE_CDMA_LOW_L, 220),
    SFX_LEVEL_UP("sfx_level_up", SfxCategory.EVENT, 0.95f, ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 240),
}

object Sound {
    // Global mute. Defaults on; AppViewModel restores the persisted preference at startup.
    @Volatile
    var enabled: Boolean = true

    // Max simultaneous streams — enough for combat bursts (hit + level-up + rune) to overlap.
    private const val MAX_STREAMS = 8
    // Pitch jitter for FREQUENT effects: 0.95x..1.05x so rapid repeats never sound identical.
    private const val PITCH_MIN = 0.95f
    private const val PITCH_SPAN = 0.10f

    // ── Speaker-clipping protection ──────────────────────────────────────────
    // A phone speaker clips when several full-volume shots sum past unity gain. Two guards:
    //  1. THROTTLE — a FREQUENT effect can't retrigger faster than this; a spam of combo pops /
    //     rune clears collapses to at most one shot per window instead of a stacked wall of sound.
    //  2. DUCK — the more shots fired inside OVERLAP_WINDOW, the more each is attenuated, so N
    //     overlapping voices share the mixing headroom (≈1/√n) rather than summing into distortion.
    private const val FREQUENT_THROTTLE_MS = 45L
    private const val OVERLAP_WINDOW_MS = 180L
    // Placeholder ToneGenerator master volume. Kept low because raw system tones are harsh in the
    // mid/high band; only reachable in the first-launch window before the synth cache is warm.
    private const val TONE_VOLUME = 42
    // A RIFF/WAVE header alone is 44 bytes — anything that size or smaller is a truncated render.
    private const val WAV_HEADER_BYTES = 44L

    private var pool: SoundPool? = null

    /** Sfx -> loaded SoundPool sample id (present only once the raw asset exists). */
    private val sampleIds = ConcurrentHashMap<Sfx, Int>()

    /** Sample ids the pool has confirmed as fully decoded and ready to play. */
    private val ready = ConcurrentHashMap<Int, Boolean>()

    /** Last play time per effect (throttle) and a rolling window of recent shots (duck). */
    private val lastPlayedMs = ConcurrentHashMap<Sfx, Long>()
    private val recentShots = java.util.concurrent.ConcurrentLinkedDeque<Long>()

    /**
     * Build the pool and load every effect into it. Safe to call more than once (idempotent).
     * Call from Application/ViewModel startup with an application context.
     *
     * Bundled `res/raw` clips are loaded synchronously (just a decode). Everything else is handed
     * to a single background thread that renders [SfxSynth]'s recipe once, caches the .wav, and
     * loads it — so a first launch never blocks on DSP, and later launches only hit the disk cache.
     */
    fun init(context: Context) {
        if (pool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val sp = SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(attrs)
            .build()
        sp.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) ready[sampleId] = true
        }
        val appCtx = context.applicationContext
        val res = appCtx.resources
        val pkg = appCtx.packageName
        val needsSynth = mutableListOf<Sfx>()
        for (sfx in Sfx.entries) {
            // Resolve by name so a missing asset breaks neither compilation nor startup.
            val resId = res.getIdentifier(sfx.resName, "raw", pkg)
            if (resId != 0) sampleIds[sfx] = sp.load(appCtx, resId, 1) else needsSynth += sfx
        }
        pool = sp
        if (needsSynth.isNotEmpty()) loadSynthesized(appCtx, sp, needsSynth)
    }

    /** Render-and-cache (once per [SfxSynth.VERSION]) then load each effect, off the main thread. */
    private fun loadSynthesized(appCtx: Context, sp: SoundPool, effects: List<Sfx>) {
        Thread {
            val dir = java.io.File(appCtx.filesDir, "sfx/v${SfxSynth.VERSION}")
            dir.mkdirs()
            // A stale render from an earlier recipe is dead weight — drop sibling version dirs.
            dir.parentFile?.listFiles()?.forEach { if (it.isDirectory && it != dir) it.deleteRecursively() }
            for (sfx in effects) {
                try {
                    val file = java.io.File(dir, "${sfx.resName}.wav")
                    if (!file.exists() || file.length() <= WAV_HEADER_BYTES) {
                        // Write via a temp file + rename so a kill mid-write can't leave a
                        // truncated .wav that would then be cached and loaded forever.
                        val tmp = java.io.File(dir, "${sfx.resName}.tmp")
                        tmp.writeBytes(SfxSynth.renderWav(sfx))
                        if (!tmp.renameTo(file)) { tmp.delete(); continue }
                    }
                    sampleIds[sfx] = sp.load(file.absolutePath, 1)
                } catch (_: Throwable) {
                    // Out of space / IO error — this one effect keeps the tone fallback.
                }
            }
        }.apply { isDaemon = true; name = "sfx-synth"; priority = Thread.MIN_PRIORITY }.start()
    }

    /**
     * Fire a sound event. Uses the preloaded SoundPool sample when available (with pitch
     * randomization for [SfxCategory.FREQUENT]); otherwise falls back to a short synthesized
     * tone so feedback is never lost while assets are still being swapped in.
     */
    fun play(context: Context, sfx: Sfx) {
        if (!enabled) return
        val now = android.os.SystemClock.uptimeMillis()

        // Throttle rapid FREQUENT retriggers so combo pops / rune clears don't stack into a wall.
        if (sfx.category == SfxCategory.FREQUENT) {
            val last = lastPlayedMs[sfx]
            if (last != null && now - last < FREQUENT_THROTTLE_MS) return
        }
        lastPlayedMs[sfx] = now

        // Duck by how many shots fired inside the overlap window — N voices share headroom (~1/√n)
        // instead of summing past unity and clipping the speaker.
        recentShots.addLast(now)
        while (recentShots.peekFirst()?.let { now - it > OVERLAP_WINDOW_MS } == true) recentShots.pollFirst()
        val overlap = recentShots.size
        val duck = if (overlap <= 1) 1f else (1f / kotlin.math.sqrt(overlap.toFloat()))

        val jitter = sfx.category == SfxCategory.FREQUENT
        val rate = if (jitter) PITCH_MIN + Random.nextFloat() * PITCH_SPAN else 1f
        val vol = (sfx.baseVolume * duck).coerceIn(0f, 1f)

        val sp = pool
        val sampleId = sampleIds[sfx]
        if (sp != null && sampleId != null && ready[sampleId] == true) {
            sp.play(sampleId, vol, vol, /* priority = */ 1, /* loop = */ 0, rate)
        } else {
            playFallbackTone(context, sfx, vol)
        }
    }

    /** Release the pool (e.g. on process teardown). Playback silently no-ops until [init] again. */
    fun release() {
        pool?.release()
        pool = null
        sampleIds.clear()
        ready.clear()
        lastPlayedMs.clear()
        recentShots.clear()
    }

    // Transitional synthesized blip — retired for a given event the moment its clip is bundled.
    // Volume folds in the overlap duck AND the effect's own baseVolume so placeholder tones respect
    // the same mix/clipping budget as real samples, kept under a soft master ceiling.
    private fun playFallbackTone(context: Context, sfx: Sfx, vol: Float) {
        try {
            val master = (TONE_VOLUME * (vol / sfx.baseVolume.coerceAtLeast(0.01f))).toInt().coerceIn(1, 100)
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, master)
            tg.startTone(sfx.fallbackTone, sfx.fallbackMs)
            android.os.Handler(context.mainLooper).postDelayed(
                { tg.release() },
                (sfx.fallbackMs + 120).toLong(),
            )
        } catch (_: Exception) {
            // Audio unavailable (e.g. busy) — haptics still carry the feedback.
        }
    }
}
