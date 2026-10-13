package com.addhd.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies every [SfxSynth] recipe renders a well-formed, audible 16-bit PCM wav.
 *
 * Audio is the one part of the app that can't be eyeballed in a screenshot, so this covers the
 * failure modes that would otherwise only show up as silence (or a crackle) on a real device:
 * a malformed RIFF header, NaN/Inf leaking out of the DSP, a sample that renders silent, or one
 * that clips flat against full scale.
 *
 * As a side effect it writes every effect to `app/build/sfx-preview/` so the sound design can
 * actually be LISTENED to on a desktop before building and installing the APK.
 */
class SfxSynthTest {

    private val previewDir = File("build/sfx-preview").apply { mkdirs() }

    @Test
    fun everyEffectRendersAWellFormedAudibleWav() {
        for (sfx in Sfx.entries) {
            val wav = SfxSynth.renderWav(sfx)
            val name = sfx.resName

            // --- container ---
            assertEquals("$name: RIFF magic", "RIFF", String(wav, 0, 4, Charsets.US_ASCII))
            assertEquals("$name: WAVE magic", "WAVE", String(wav, 8, 4, Charsets.US_ASCII))
            assertEquals("$name: channels", 1, le16(wav, 22))
            assertEquals("$name: sample rate", SfxSynth.SAMPLE_RATE, le32(wav, 24))
            assertEquals("$name: bit depth", 16, le16(wav, 34))
            val dataBytes = le32(wav, 40)
            assertEquals("$name: data chunk size matches file", wav.size - 44, dataBytes)
            assertEquals("$name: RIFF size field", wav.size - 8, le32(wav, 4))

            // --- audio content ---
            val samples = IntArray(dataBytes / 2) { i ->
                val v = le16(wav, 44 + i * 2)
                if (v >= 0x8000) v - 0x10000 else v          // to signed
            }
            val durationMs = samples.size * 1000 / SfxSynth.SAMPLE_RATE
            assertTrue("$name: implausibly short ($durationMs ms)", durationMs >= 100)
            assertTrue("$name: implausibly long ($durationMs ms)", durationMs <= 2500)

            val peak = samples.maxOf { kotlin.math.abs(it) }
            assertTrue("$name: silent (peak $peak)", peak > 8_000)
            assertTrue("$name: peaks at full scale — would clip (peak $peak)", peak < 32_767)

            // A NaN/Inf escaping the DSP converts to 0, so a dead run reads as near-silence; an
            // RMS floor catches the case where only one transient survived.
            val rms = kotlin.math.sqrt(samples.sumOf { it.toDouble() * it } / samples.size)
            assertTrue("$name: no sustained signal (rms %.0f)".format(rms), rms > 150)

            // Both edges must be at rest or the sample starts/ends with an audible click.
            assertTrue("$name: clicks in (${samples.first()})", kotlin.math.abs(samples.first()) < 100)
            assertTrue("$name: clicks out (${samples.last()})", kotlin.math.abs(samples.last()) < 100)

            File(previewDir, "$name.wav").writeBytes(wav)
        }
        println("SFX previews written to ${previewDir.absolutePath}")
    }

    /** Renders must be deterministic, or the on-disk cache would differ from run to run. */
    @Test
    fun rendersAreDeterministic() {
        for (sfx in Sfx.entries) {
            assertTrue(
                "${sfx.resName} is not reproducible",
                SfxSynth.renderWav(sfx).contentEquals(SfxSynth.renderWav(sfx)),
            )
        }
    }

    private fun le16(b: ByteArray, at: Int) = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)
    private fun le32(b: ByteArray, at: Int) = le16(b, at) or (le16(b, at + 2) shl 16)
}
