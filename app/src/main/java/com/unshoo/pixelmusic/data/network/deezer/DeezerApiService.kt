package com.unshoo.pixelmusic.data.network.deezer

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for Deezer API.
 * Used primarily for fetching artist artwork.
 */
interface DeezerApiService {

    /**
     * Search for an artist by name.
     * @param query Artist name to search for
     * @param limit Maximum number of results to return
     * @return Search response containing list of matching artists
     */
    @GET("search/artist")
    suspend fun searchArtist(
        @Query("q") query: String,
        @Query("limit") limit: Int = 1
    ): DeezerSearchResponse

    /**
     * Search for a track/song by name and/or artist.
     * @param query Search query string
     * @param limit Maximum number of results to return
     * @return Search response containing list of matching tracks
     */
    @GET("search")
    suspend fun searchTrack(
        @Query("q") query: String,
        @Query("limit") limit: Int = 1
    ): DeezerTrackSearchResponse
}
