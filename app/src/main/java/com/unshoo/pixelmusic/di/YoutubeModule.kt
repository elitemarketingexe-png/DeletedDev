package com.unshoo.pixelmusic.di

import android.content.Context
import com.unshoo.pixelmusic.data.remote.youtube.DatastoreRepository
import com.unshoo.pixelmusic.data.remote.youtube.DownloadRepository
import com.unshoo.pixelmusic.data.remote.youtube.SongRepository
import com.unshoo.pixelmusic.data.remote.youtube.YoutubePlaylistDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object YoutubeModule {

    @Provides
    @Singleton
    fun provideDatastoreRepository(@ApplicationContext context: Context): DatastoreRepository {
        return DatastoreRepository(context)
    }

    @Provides
    @Singleton
    fun provideDownloadRepository(@ApplicationContext context: Context): DownloadRepository {
        return DownloadRepository(context)
    }

    @Provides
    @Singleton
    fun provideSongRepository(): SongRepository {
        return SongRepository()
    }

    @Provides
    @Singleton
    fun provideYoutubePlaylistDataSource(): YoutubePlaylistDataSource {
        return YoutubePlaylistDataSource()
    }

    // Note: AutoQueueManager and QueuePreloadManager are Kotlin `object` singletons.
    // They do not require Hilt providers — call .attach() / .detach() directly
    // from your playback service's onCreate() / onDestroy().
}
