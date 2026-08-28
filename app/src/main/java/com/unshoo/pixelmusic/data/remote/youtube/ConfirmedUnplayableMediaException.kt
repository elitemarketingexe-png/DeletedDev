/*
 * PixelMusic (2026)
 * © Chartreux Westia — github.com/ianshulyadav
 * GPL-3.0 License
 */

package com.unshoo.pixelmusic.data.remote.youtube

import java.io.IOException

/**
 * A provider explicitly identified the media as unavailable — age gate,
 * geographic restriction, private video, paid content, removed — rather than
 * a request merely failing because the network or an extractor was slow.
 *
 * Throwing this instead of a generic exception lets the stream ladder stop
 * immediately (no point racing 14 more clients for a video YouTube says is
 * gone) and lets the player skip the track instead of burning recovery
 * attempts on it. Marker strings mirror LastWave's classification.
 */
class ConfirmedUnplayableMediaException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {

    companion object {
        /** Lowercased substrings that identify an authoritative "unavailable" verdict. */
        private val CONFIRMED_MARKERS = listOf(
            "agerestricted", "age restricted", "confirm your age",
            "geographicrestriction", "not available in your country",
            "contentnotavailable", "video is unavailable", "video unavailable",
            "privatecontent", "this video is private", "paidcontent",
            "members-only", "login required", "sign in to watch",
        )

        /**
         * Bot-wall phrasing must never be classified as permanent: the video is
         * fine, the request just looks automated (visitor rotation / a fresh
         * BotGuard session fixes it). Note "sign in to confirm you're not a bot"
         * deliberately does NOT match "sign in to watch".
         */
        private val BOT_WALL_MARKERS = listOf(
            "not a bot", "confirm you're not", "confirm you are not",
            "unusual traffic", "automated",
        )

        /** Playability states that will never turn into a playable stream. */
        val PERMANENT_PLAYABILITY_STATES = setOf(
            "UNPLAYABLE",
            "LOGIN_REQUIRED",
            "AGE_CHECK_REQUIRED",
            "CONTENT_CHECK_REQUIRED",
            "LIVE_STREAM_OFFLINE",
        )

        fun isPermanentPlayabilityState(status: String?): Boolean =
            status != null && status in PERMANENT_PLAYABILITY_STATES

        /**
         * Walks the throwable chain looking for marker strings; returns the
         * most useful human-readable reason, or null when the failure looks
         * transient (network, timeouts, parse errors...).
         */
        fun from(throwable: Throwable): ConfirmedUnplayableMediaException? {
            val causes = generateSequence(throwable) { it.cause }.take(10).toList()
            val diagnostic = causes.joinToString(" ") {
                "${it::class.java.simpleName} ${it.message.orEmpty()}"
            }.lowercase()
            if (CONFIRMED_MARKERS.none(diagnostic::contains)) {
                return causes.filterIsInstance<ConfirmedUnplayableMediaException>().firstOrNull()
            }
            val reason = causes.firstNotNullOfOrNull { it.message?.takeIf(String::isNotBlank) }
            return ConfirmedUnplayableMediaException(reason ?: "Media is unavailable", throwable)
        }

        /**
         * Classify an InnerTube playability (status, reason) pair. Returns a
         * [ConfirmedUnplayableMediaException] when the verdict is permanent.
         * Bot-wall variants of LOGIN_REQUIRED stay transient (returns null).
         */
        fun fromPlayability(status: String?, reason: String?): ConfirmedUnplayableMediaException? {
            if (status == null) return null
            val lowerReason = reason.orEmpty().lowercase()
            if (BOT_WALL_MARKERS.any(lowerReason::contains)) return null
            if (isPermanentPlayabilityState(status)) {
                return ConfirmedUnplayableMediaException(
                    reason?.ifBlank { status } ?: status,
                )
            }
            // Some transient-looking states still carry a marker in the reason.
            return from(IOException(reason?.ifBlank { status } ?: status))
        }
    }
}
