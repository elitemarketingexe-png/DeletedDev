package com.unshoo.pixelmusic

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.repository.ArtistImageRepository
import com.unshoo.pixelmusic.data.telegram.TelegramRepository
import com.unshoo.pixelmusic.presentation.viewmodel.LibraryStateHolder
import com.unshoo.pixelmusic.presentation.viewmodel.ThemeStateHolder
import com.unshoo.pixelmusic.utils.AlbumArtCacheManager
import com.unshoo.pixelmusic.utils.AlbumArtUtils
import com.unshoo.pixelmusic.utils.CrashHandler
import com.unshoo.pixelmusic.utils.AppLocaleManager
import com.unshoo.pixelmusic.utils.MediaItemBuilder
import com.unshoo.pixelmusic.utils.MediaMetadataRetrieverPool
import com.unshoo.pixelmusic.utils.potoken.BotGuardTokenGenerator
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class PixelMusicApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    @Inject
    lateinit var telegramCoilFetcherFactory: dagger.Lazy<com.unshoo.pixelmusic.data.image.TelegramCoilFetcher.Factory>



    @Inject
    lateinit var localArtworkCoilFetcherFactory: dagger.Lazy<com.unshoo.pixelmusic.data.image.LocalArtworkCoilFetcher.Factory>

    @Inject
    lateinit var themeStateHolder: dagger.Lazy<ThemeStateHolder>

    @Inject
    lateinit var artistImageRepository: dagger.Lazy<ArtistImageRepository>

    @Inject
    lateinit var telegramRepository: dagger.Lazy<TelegramRepository>

    @Inject
    lateinit var libraryStateHolder: dagger.Lazy<LibraryStateHolder>

    @Inject
    lateinit var userPreferencesRepository: dagger.Lazy<UserPreferencesRepository>

    // BUGFIX (slow first playback): ExoCache.cache is a `by lazy` SimpleCache. SimpleCache's
    // constructor synchronously scans/reconciles its on-disk index - cheap when the cache is
    // small, but it grows slower as more audio gets cached over time. Because ExoCache is a
    // Hilt @Singleton, whichever thread touches `.cache` FIRST pays that cost. Previously nothing
    // touched it until MusicService.onCreate() -> DualPlayerEngine.initialize() -> buildPlayer()
    // did, on the MAIN THREAD, which is exactly what MediaController connection (and therefore
    // the very first "tap a song, wait for it to start" moment) is blocked behind. Warming it
    // here, off the main thread, as early as possible means that by the time MusicService needs
    // it, the lazy value is already resolved (or nearly done), instead of a synchronous main-
    // thread disk scan sitting directly in the cold-start-to-first-playback path.
    @Inject
    lateinit var exoCache: dagger.Lazy<com.unshoo.pixelmusic.data.remote.youtube.ExoCache>

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // BUGFIX (startup strategy): warm-up work (ExoCache, BotGuard) previously ran on the shared
    // Dispatchers.IO pool, which runs at normal thread priority and can compete with other IO
    // work (including things the UI is actively waiting on) right when the app is trying to
    // render its first frame and become interactive. This dedicated single-thread dispatcher is
    // created at THREAD_PRIORITY_BACKGROUND once, up front - not toggled per-task on a shared
    // pooled thread, which would risk leaking a lowered priority onto unrelated later work on
    // that same reused thread. Android's scheduler is then free to prioritize the UI thread
    // while this still makes forward progress as early as possible in the background.
    private val warmUpDispatcher = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PixelMusic-WarmUp").apply {
            priority = Thread.MIN_PRIORITY
        }
    }.asCoroutineDispatcher()
    private val warmUpScope = CoroutineScope(SupervisorJob() + warmUpDispatcher)
    private var exoCacheWarmUpJob: kotlinx.coroutines.Job? = null
    private var botGuardWarmUpJob: kotlinx.coroutines.Job? = null

    // AÑADE EL COMPANION OBJECT
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "pixelmusic_music_channel"
    }

    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            libraryStateHolder.get().restoreAfterTrimIfNeeded()
        }

        // BUGFIX (startup strategy - don't waste work the user won't benefit from): if the user
        // opens the app and leaves again before warm-up finished, keep spending CPU/battery on
        // it in the background for no benefit. Cancelling here is safe for actual playback: real
        // stream-resolution/BotGuard-minting triggered by an actual tap or the foreground service
        // runs on its own scopes (MusicService's serviceScope, YouTubeTelemetryManager's own
        // scope, DualPlayerEngine's resolve jobs) - never on warmUpScope - so this can never
        // cancel or interrupt playback that's actually in progress.
        override fun onStop(owner: LifecycleOwner) {
            exoCacheWarmUpJob?.takeIf { it.isActive }?.cancel()
            botGuardWarmUpJob?.takeIf { it.isActive }?.cancel()
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(base))
    }

    override fun onCreate() {
        super.onCreate()

        MediaItemBuilder.initialize(this)
        BotGuardTokenGenerator.initialize(this)

        // BUGFIX (slow first playback): resolve ExoCache's lazy SimpleCache off the main thread
        // now, instead of letting MusicService.onCreate() do it synchronously on the main thread
        // later. See the field comment on `exoCache` above for the full explanation.
        exoCacheWarmUpJob = warmUpScope.launch {
            try {
                exoCache.get().cache
            } catch (e: Exception) {
                Timber.w(e, "ExoCache pre-warm failed (non-fatal, will retry lazily)")
            }
        }

        // Initialize AdMob and increment app open count
        try {
            com.unshoo.pixelmusic.data.ads.AdManager.initialize(this)
            com.unshoo.pixelmusic.data.ads.AdManager.incrementAppOpenCount(this)
        } catch (e: Throwable) {
            Timber.e(e, "AdMob initialization failed")
        }

        // Initialize Last.fm client
        com.unshoo.pixelmusic.data.lastfm.LastFM.initialize(
            apiKey = BuildConfig.LASTFM_API_KEY,
            secret = BuildConfig.LASTFM_SECRET
        )
        startupScope.launch {
            val prefs = userPreferencesRepository.get()
            val savedApiKey = prefs.lastfmApiKeyFlow.first()
            val savedSecret = prefs.lastfmApiSecretFlow.first()
            if (savedApiKey.isNotEmpty() && savedSecret.isNotEmpty()) {
                com.unshoo.pixelmusic.data.lastfm.LastFM.initialize(
                    apiKey = savedApiKey,
                    secret = savedSecret
                )
            }
            val sessionKey = prefs.lastfmSessionFlow.first()
            if (sessionKey.isNotEmpty()) {
                com.unshoo.pixelmusic.data.lastfm.LastFM.sessionKey = sessionKey
            }
        }

        // Initialize NewPipe YouTube Extractor
        org.schabi.newpipe.extractor.NewPipe.init(
            com.unshoo.pixelmusic.data.remote.youtube.YoutubeExtractor(
                com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.client
            )
        )

        // Bind Content Language and Country to YouTube.locale
        startupScope.launch {
            kotlinx.coroutines.flow.combine(
                userPreferencesRepository.get().contentLanguageFlow,
                userPreferencesRepository.get().contentCountryFlow
            ) { language, country ->
                unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeLocale(
                    gl = country,
                    hl = language
                )
            }.collect { locale ->
                unshoo.ianshulyadav.pixelmusic.innertube.YouTube.locale = locale
            }
        }

        // Benchmark variant intentionally restarts/kills app process during tests.
        // Avoid persisting those events as user-facing crash reports.
        if (BuildConfig.BUILD_TYPE != "benchmark") {
            CrashHandler.install(this)
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // Release tree: only WARN/ERROR/WTF - no DEBUG/VERBOSE/INFO
            Timber.plant(ReleaseTree())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "PixelMusic Music Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)

        // DNS pre-warming
        startupScope.launch {
            try {
                java.net.InetAddress.getAllByName("music.youtube.com")
                java.net.InetAddress.getAllByName("googlevideo.com")
            } catch (e: Exception) {
                Timber.w(e, "DNS pre-warming failed")
            }
        }

        // BUGFIX (slow first playback, part 2 - the bigger one): YouTube.player() - the actual
        // stream-resolution call used to fetch playable audio URLs, not just telemetry - mints a
        // BotGuard PoToken SYNCHRONOUSLY as the first thing it does whenever PoToken playback is
        // enabled (see YouTube.kt's `player()`: `BotGuardTokenGenerator.mintToken(...)` is
        // awaited before anything else). On a cold engine that means bootstrapping a hidden
        // WebView and running BotGuard's JS challenge, bounded by a 15s timeout - so the very
        // first song after a fresh launch can genuinely stall for several seconds to that full
        // 15s on stream resolution alone, before audio starts. This was previously deliberately
        // avoided here (see prior comment) to protect memory/CPU on low-end devices; per request,
        // prewarming is now enabled, but still skipped on ActivityManager.isLowRamDevice() so
        // that original intent isn't silently dropped for the devices it mattered most for.
        botGuardWarmUpJob = warmUpScope.launch {
            try {
                val isLowRam = (getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)
                    ?.isLowRamDevice == true
                if (!isLowRam) {
                    // Prefer the real session id (visitorData/dataSyncId) so the warmed session
                    // token is actually reusable by the first real mintToken() call, but don't
                    // block prewarming indefinitely on auth/network being slow to load - the
                    // WebView bootstrap itself (the expensive part) is still worth doing early
                    // even with a throwaway id.
                    // BUGFIX (never duplicate initialization work): BotGuardTokenGenerator keys
                    // its cached engine by sessionId (see mintTokenInternal: `engineSessionId !=
                    // sessionId` forces a full close()+recreate(), not a cheap re-mint). A
                    // throwaway random UUID here would prewarm an engine that the first REAL
                    // mintToken() call then immediately discards and rebuilds from scratch - pure
                    // wasted work, worse than not prewarming at all. So: wait longer for the real
                    // visitorData/dataSyncId, and if it genuinely never arrives this launch, skip
                    // prewarming rather than warm the wrong session - mintToken() still works
                    // fine cold on first real use, exactly as before this change.
                    val sessionId = kotlinx.coroutines.withTimeoutOrNull(5_000L) {
                        unshoo.ianshulyadav.pixelmusic.innertube.YouTube.authStateFlow
                            .first { !it.sessionId.isNullOrBlank() }
                            .sessionId
                    }
                    if (sessionId != null) {
                        BotGuardTokenGenerator.preWarm(sessionId)
                    } else {
                        Timber.d("BotGuard pre-warm skipped: no session id available yet this launch")
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "BotGuard pre-warm failed (non-fatal, will fall back to lazy cold-start)")
            }
        }

        startupScope.launch {
            AlbumArtUtils.migrateLegacyCacheLocation(this@PixelMusicApplication)
            val savedLimit = runCatching {
                userPreferencesRepository.get().albumArtCacheLimitMbFlow.first()
            }.getOrNull()
            if (savedLimit != null) {
                AlbumArtCacheManager.configuredCacheLimitMb = savedLimit.toLong()
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return imageLoader.get().newBuilder()
            .components {
                add(localArtworkCoilFetcherFactory.get())
                add(telegramCoilFetcherFactory.get())
            }
            .build()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        imageLoader.get().memoryCache?.trimMemory(level)

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            themeStateHolder.get().trimMemory(level)
        }

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            artistImageRepository.get().clearCache()
            telegramRepository.get().clearMemoryCache()
            MediaMetadataRetrieverPool.clear()
            startupScope.launch { BotGuardTokenGenerator.onAppBackgrounded() }
        }

        libraryStateHolder.get().trimMemory(level)

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        ) {
            imageLoader.get().memoryCache?.clear()
        }
    }

    // 3. Sobrescribe el método para proveer la configuración de WorkManager
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

}
