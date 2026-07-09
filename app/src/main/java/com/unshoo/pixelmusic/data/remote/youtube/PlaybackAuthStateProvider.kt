package com.unshoo.pixelmusic.data.remote.youtube

import unshoo.ianshulyadav.pixelmusic.innertube.PlaybackAuthState

/**
 * Interface for providing playback authentication state.
 * Implementations should return the current YouTube Music authentication state.
 */
interface PlaybackAuthStateProvider {
    fun getPlaybackAuthState(): PlaybackAuthState
}
