package net.spross.app.audio

import android.annotation.SuppressLint
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * The noise gate on a player's own audio session: a `DynamicsProcessing` holding nothing but
 * one full-band downward expander, shaped by [gateBand]. Pre-EQ, post-EQ and the limiter are
 * off, the compressor runs at 1:1 and there is no input gain, so above the threshold the word
 * passes untouched.
 */
class NoiseGate private constructor(private val effect: DynamicsProcessing) {

    /** Moves the threshold to where [band] puts it — a replay under a moved fade. */
    @SuppressLint("NewApi") // only [attach] builds one, and only from API 28
    fun update(band: GateBand) = effect.setMbcBandAllChannelsTo(0, mbcBand(band))

    fun release() = effect.release()

    companion object {
        /**
         * The gate for [band] on [audioSession]; null below API 28, where there is no
         * `DynamicsProcessing`, and wherever the device will not build one.
         */
        fun attach(audioSession: Int, band: GateBand): NoiseGate? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
            return try {
                NoiseGate(DynamicsProcessing(0, audioSession, config(band)).apply { enabled = true })
            } catch (_: RuntimeException) {
                // why: like the enhancer, the effect is not on every device, and an ungated
                // word is a far better answer than a crash on the way to saying it.
                null
            }
        }

        @RequiresApi(Build.VERSION_CODES.P)
        private fun config(band: GateBand): DynamicsProcessing.Config {
            val mbc = DynamicsProcessing.Mbc(true, true, 1).apply { setBand(0, mbcBand(band)) }
            return DynamicsProcessing.Config.Builder(
                // why: AOSP's engine only builds the frequency variant; the time one leaves it null and the gate inert.
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                1, // the effect copies channel 0 onto however many channels the session mixes
                false, 0, // pre-EQ
                true, 1, // MBC: the expander
                false, 0, // post-EQ
                false, // limiter
            )
                .setMbcAllChannelsTo(mbc)
                .setInputGainAllChannelsTo(0f)
                .build()
        }

        @RequiresApi(Build.VERSION_CODES.P)
        private fun mbcBand(band: GateBand) = DynamicsProcessing.MbcBand(
            true,
            FULL_BAND_HZ,
            band.attackMs,
            band.releaseMs,
            1f, // compressor ratio: never compresses
            0f, // compressor threshold, dBFS
            0f, // knee width, dB
            band.thresholdDb,
            band.expanderRatio,
            0f, // pre-gain, dB
            0f, // post-gain, dB
        )

        /** The one band's upper edge: the top of the audible range. */
        private const val FULL_BAND_HZ = 20_000f
    }
}
