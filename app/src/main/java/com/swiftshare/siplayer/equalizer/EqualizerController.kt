package com.swiftshare.siplayer.equalizer

import android.media.audiofx.Equalizer
import com.swiftshare.common.AppLogger

data class EqualizerBand(
    val index: Int,
    val centerFreqHz: Int,
    val minLevelMb: Short,
    val maxLevelMb: Short,
    var currentLevelMb: Short
)

data class EqualizerPreset(
    val index: Int,
    val name: String
)

class EqualizerController {

    private var equalizer: Equalizer? = null
    private var currentSessionId: Int = 0

    var isEnabled: Boolean = false
        private set

    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == currentSessionId) return
        release()
        currentSessionId = audioSessionId

        try {
            val eq = Equalizer(0, audioSessionId)
            eq.enabled = isEnabled
            equalizer = eq
        } catch (e: Exception) {
            AppLogger.w(message = "EqualizerController: failed to attach to session $audioSessionId: ${e.message}")
            equalizer = null
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        try {
            equalizer?.enabled = enabled
        } catch (e: Exception) {
            AppLogger.w(message = "EqualizerController: setEnabled failed: ${e.message}")
        }
    }

    fun getBands(): List<EqualizerBand> {
        val eq = equalizer ?: return getDefaultFallbackBands()
        return try {
            val numBands = eq.numberOfBands.toInt()
            val minLevel = eq.bandLevelRange.firstOrNull() ?: -1500
            val maxLevel = eq.bandLevelRange.getOrNull(1) ?: 1500

            (0 until numBands).map { bandIdx ->
                val centerFreqHz = eq.getCenterFreq(bandIdx.toShort()) / 1000
                val currentLvl = eq.getBandLevel(bandIdx.toShort())
                EqualizerBand(
                    index = bandIdx,
                    centerFreqHz = centerFreqHz,
                    minLevelMb = minLevel,
                    maxLevelMb = maxLevel,
                    currentLevelMb = currentLvl
                )
            }
        } catch (e: Exception) {
            AppLogger.w(message = "EqualizerController: getBands failed: ${e.message}")
            getDefaultFallbackBands()
        }
    }

    fun setBandLevel(bandIndex: Int, levelMb: Short) {
        try {
            equalizer?.setBandLevel(bandIndex.toShort(), levelMb)
        } catch (e: Exception) {
            AppLogger.w(message = "EqualizerController: setBandLevel failed: ${e.message}")
        }
    }

    fun getPresets(): List<EqualizerPreset> {
        val eq = equalizer ?: return getDefaultFallbackPresets()
        return try {
            val count = eq.numberOfPresets.toInt()
            (0 until count).map { idx ->
                EqualizerPreset(idx, eq.getPresetName(idx.toShort()))
            }
        } catch (e: Exception) {
            AppLogger.w(message = "EqualizerController: getPresets failed: ${e.message}")
            getDefaultFallbackPresets()
        }
    }

    fun usePreset(presetIndex: Int) {
        try {
            equalizer?.usePreset(presetIndex.toShort())
        } catch (e: Exception) {
            AppLogger.w(message = "EqualizerController: usePreset failed: ${e.message}")
        }
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (_: Exception) {}
        equalizer = null
        currentSessionId = 0
    }

    companion object {
        fun getDefaultFallbackBands(): List<EqualizerBand> {
            val freqs = listOf(60, 230, 910, 3600, 14000)
            return freqs.mapIndexed { idx, freq ->
                EqualizerBand(idx, freq, -1500, 1500, 0)
            }
        }

        fun getDefaultFallbackPresets(): List<EqualizerPreset> {
            return listOf(
                EqualizerPreset(0, "Normal"),
                EqualizerPreset(1, "Classical"),
                EqualizerPreset(2, "Dance"),
                EqualizerPreset(3, "Flat"),
                EqualizerPreset(4, "Folk"),
                EqualizerPreset(5, "Heavy Metal"),
                EqualizerPreset(6, "Hip Hop"),
                EqualizerPreset(7, "Jazz"),
                EqualizerPreset(8, "Pop"),
                EqualizerPreset(9, "Rock")
            )
        }
    }
}
