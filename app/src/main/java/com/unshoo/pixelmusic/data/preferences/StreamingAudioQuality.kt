package com.unshoo.pixelmusic.data.preferences

/**
 * Streaming audio quality levels for YouTube playback.
 * Controls the maximum bitrate ceiling when selecting stream formats.
 *
 * On WiFi: user's chosen quality is honored.
 * On metered/mobile data: defaults to LOW unless user overrides with
 * "Force high quality on mobile data" toggle.
 *
 * Playback starts at the quality the user selected:
 * - HIGH: highest available stream first (instant high-quality start)
 * - MEDIUM: best stream under the medium ceiling
 * - LOW: lowest available stream first (best for weak networks)
 *
 * @property maxBitrateKbps Maximum bitrate ceiling in kbps
 * @property label Human-readable label for Settings UI
 */
enum class StreamingAudioQuality(val maxBitrateKbps: Int, val label: String) {
    LOW(64, "Low (64 kbps) — Saves data"),
    MEDIUM(128, "Medium (128 kbps) — Balanced"),
    HIGH(256, "High (256 kbps) — Best quality");

    companion object {
        /**
         * Parse stored preference. Unset values default to [HIGH] for Wi‑Fi-oriented
         * callers; use [fromNameOrLow] for mobile/low-connectivity paths.
         */
        fun fromName(name: String?): StreamingAudioQuality {
            return entries.find { it.name == name } ?: HIGH
        }

        /** Safe default for weak/metered networks when preference is unset. */
        fun fromNameOrLow(name: String?): StreamingAudioQuality {
            return entries.find { it.name == name } ?: LOW
        }
    }
}
