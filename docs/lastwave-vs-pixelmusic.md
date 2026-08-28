# ThePixelMusic vs LastWave-native — Streaming Architecture Deep-Dive

**Scope:** InnerTube layer, YouTube backend, PO tokens / BotGuard, YouTube fallback chains,
fast-playback engineering, Qobuz (app + Cloudflare Worker), warm-up ("token warm") strategy,
and the four referenced LastWave commits.

| | ThePixelMusic (this repo) | LastWave-native |
|---|---|---|
| Package | `com.unshoo.pixelmusic` | `com.lastwave.app` |
| InnerTube core | `unshoo/.../innertube/InnerTube.kt` (874 L) + `YouTube.kt` (1759 L) — InnerTune/OuterTune-lineage | `data/music/InnerTubeMusicApi.kt` (1859 L) — hand-rolled kotlinx.serialization client |
| Client matrix | 16+ clients (`YouTubeClient.kt`), versions current to 2026-01 | 6 hardcoded `PLAYER_CLIENTS` (Aug-2024 era versions) |
| Playback host | `MusicService : MediaLibraryService` + `DualPlayerEngine` (4312 + 1640 L) | `MusicPlayer` singleton + thin `MusicPlaybackService` (822 L) |
| Resolution layers | 3 stages: umihi ANDROID_VR → ArchiveTune multi-client → NewPipe | 2 racers in parallel: NewPipe ↔ InnerTube client, then NewPipe last resort |
| PO token | Real BotGuard (WebView) **only for web clients** + a **fake XOR token generator** fallback | Real BotGuard (WebView) always attempted, 50 ms non-blocking budget |
| Qobuz | ❌ none | ✅ app client + Cloudflare Worker backend (Hi-Res FLAC primary, YouTube fallback) |
| Warm-up | Staggered thermal-aware pipeline (T+0.5 s → T+5 s) | Immediate NewPipe preWarm, BotGuard deliberately deferred to first mint |
| Quality strategy | Quality-ceiling ladder (LOW/MED/HIGH/AUTO, low-first on weak links, bg upgrade) | Best-Opus-first on YouTube, Qobuz Hi-Res primary when matched |

---

## 1. End-to-end architecture flows

### 1.1 ThePixelMusic — click-to-sound path

```mermaid
flowchart TD
    UI["UI / PlayerViewModel"] --> MS["MusicService<br/>(MediaLibraryService)"]
    MS --> ENG["DualPlayerEngine<br/>playerA master / playerB crossfade"]
    ENG -->|"youtube://videoId"| RUC["resolvedUriCache<br/>LruCache(100)"]
    RUC -->|"miss"| RESOLVE["YoutubeHelper.getSongPlayerUrl()"]

    subgraph RESOLVE["resolve + cache ladder"]
        L1{"song.audioFilePath<br/>exists?"}
        L1 -->|yes| RET["return local file"]
        L1 -->|no| L2{"localFilePathCache<br/>LruCache(200)"}
        L2 -->|hit| RET
        L2 -->|miss| L3{"Room DB<br/>downloaded song?"}
        L3 -->|yes| RET
        L3 -->|no| QP["resolveStreamQualityPlan()<br/>AUTO/LOW/MED/HIGH + weak-link detect"]
        QP --> L4{"streamUrlLruCache<br/>videoId_low / _qNNN / _high"}
        L4 -->|"hit + expire=&gt;now+90s"| RET
        L4 -->|miss| ST1

        subgraph STAGE["3-stage fallback ladder"]
            ST1["STAGE 1 — umihi fast path<br/>single ANDROID_VR_1_61_48 player call<br/>BotGuard DISABLED (webClientPoTokenEnabled=false)"]
            ST1 -->|"OK + direct url"| WIN["winner"]
            ST1 -->|"bot detect"| ROT["rotate visitorData<br/>retry once"]
            ST1 -->|fail/empty| ST2["STAGE 2 — ArchiveTune ladder<br/>14 clients: last-success → preferred →<br/>login-first → VR family → TV/WEB → IOS/ANDROID"]
            ST2 -->|"ciphered format"| DEOB["NewPipeUtils: deobfuscate sig<br/>+ n-param, patch cver"]
            ST2 -->|"bot detect"| REPAIR["repairPlaybackAuth:<br/>invalidate BotGuard + rotate visitorData<br/>retry same client 1×"]
            ST2 -->|fail| ST3["STAGE 3 — NewPipe extractor<br/>RETRY_COUNT × backoff"]
            ST3 -->|fail| THROW["throw → upper<br/>quality-fallback retry<br/>(HIGH fail→LOW, LOW fail→unrestricted)"]
        end

        WIN --> PUT["cache streamUrlLruCache<br/>+ mimeType + bitrate (200 entries)"]
        PUT --> WARM["if started LOW on AUTO:<br/>warmHigherQualityInBackground()<br/>delay 8s → resolve target quality"]
    end

    RESOLVE --> ENGINE2["engine.preCacheFirstChunk(url)<br/>+ resolvedUriCache.put"]
    QP2["QueuePreloadManager<br/>(on transition / playing)"] -->|"next N tracks"| RESOLVE
    QP2 --> ART["prefetch artwork →<br/>thumbnails dir"]
```

### 1.2 LastWave — click-to-sound path

```mermaid
flowchart TD
    UI["UI"] --> MP["MusicPlayer (singleton)<br/>ExoPlayer + NativeProcessingAudioSink"]
    MP -->|"play(track)"| PRE["innerTube.prefetchStream(videoId)<br/>fire-and-forget cache seed"]
    MP -->|"play()"| FS["ensureForegroundService()<br/>MusicPlaybackService"]
    MP -->|"setMediaItem(lastwave://youtube/id?title&artist)"| EXO["player.prepare() / play()"]

    subgraph LOADER["ExoPlayer loader thread"]
        EXO --> RES["ResolvingDataSource<br/>scheme == lastwave ?"]
        RES -->|"35s bound"| RTS["resolveTrackAudioStream()"]
        RES -->|"http(s) url"| CDS
    end

    RTS --> QZ{"allowQobuz?"}
    QZ -->|yes| QOB["QobuzMusicApi.resolveStream()<br/>withTimeoutOrNull(4s)"]
    QOB -->|"verified match"| QWIN["HI-RES / LOSSLESS / MP3 320<br/>ResolvedStream(isQobuz=true)"]
    QOB -->|"null / timeout"| YTFB
    QZ -->|no| YTFB

    subgraph YT["resolveAudioStream — dual racer"]
        YTFB["cache 4h TTL / 64 entries<br/>+ in-flight dedup (Deferred)"] --> RACE
        subgraph RACE["coroutineScope + Channel(2)"]
            R1["Racer 1: NewPipe<br/>YouTubeStreamExtractor<br/>(Opus-preferred)"]
            R2["Racer 2: InnerTube player<br/>first non-cooled-down client of<br/>ANDROID_VR → TVHTML5 → IOS_MUSIC<br/>→ IOS → ANDROID → WEB_REMIX<br/>+ circuit breaker 60s"]
            POT["poToken: 50ms lookup budget<br/>else background mint, continue without"]
            POT --> R1
            POT --> R2
        end
        RACE -->|"first stream wins"| CACHED["cache + return"]
        RACE -->|"15s STREAM_RACE_TIMEOUT"| LAST["last resort: NewPipe<br/>bounded 12s"]
    end

    YTFB --> YWIN["OPUS/AAC ResolvedStream"]
    CDS["CacheDataSource<br/>SimpleCache 256MB LRU"] --> HTTP["googlevideo / streaming CDN"]
```

### 1.3 Side-by-side of the resolve pipeline

| Phase | ThePixelMusic | LastWave |
|---|---|---|
| 0th hit | LRU stream-URL cache (3 quality keys per video) + `expire=` param check (O(1), no network) | In-memory `streamCache` (4 h TTL, 64 entries) |
| Concurrency guard | none on resolve itself (callers serialize per UI action); QueuePreloadManager serializes with delays | `activeStreamRequests: Deferred` dedup — identical concurrent requests share one resolve |
| Fast path | 1 × `ANDROID_VR_1_61_48` player call, **no PO token**, direct URLs only | Parallel race: NewPipe **vs** InnerTube client — first byte of truth wins |
| Middle path | ArchiveTune ladder over 14 clients w/ dynamic `signatureTimestamp`, cipher deobfuscation, cver patch | Second client only after 60 s circuit-breaker cooldown; hardcoded `signatureTimestamp=19940` |
| Last resort | NewPipe × RETRY_COUNT with backoff | NewPipe bounded 12 s |
| Failure semantics | exception → upper layer retries with different quality | `ConfirmedUnplayableMediaException` (marker strings) → skip track permanently |
| Tracking | `registerPlayback` (cpn + videostats watchtime) so YTM counts plays | none |

---

## 2. InnerTube layer comparison

### 2.1 Request plumbing

| Aspect | ThePixelMusic | LastWave |
|---|---|---|
| JSON stack | Retrofit/Kotlin-serialization model tree (`models/response/*`) — typed | Hand-built `buildJsonObject` bodies — flexible, untyped |
| Search/browse client | `WEB_REMIX 1.20260114.01.00` (constant) | `WEB_REMIX`, but **API key + clientVersion + visitorData bootstrapped at runtime** from `music.youtube.com` HTML (`INNERTUBE_API_KEY`, `INNERTUBE_CLIENT_VERSION`, `VISITOR_DATA` regex), fallback to `AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30` / `1.20240715.00.00` |
| Config invalidation | n/a (constants) | `webConfig = null` on HTTP 400/403/429; 1 retry with 200 ms sleep; non-JSON body retried as transient |
| Auth | `PlaybackAuthState` (cookies, SAPISIDHASH, visitorData, dataSyncId, per-client poTokens) via `YoutubeAuthHelper` | `YtMusicAuthManager` cookie + SAPISIDHASH opt-in per call (`authenticated = true`) |
| Playlist engine | full CRUD + move + setVideoId paging via typed endpoints | full CRUD + setVideoId + **continuation loop to 600 pages** (universal import), auth-first-then-anonymous |
| Playback tracking | ✅ `registerPlayback` with random cpn, `s.youtube.com` → `music.youtube.com` rewrite, GVS poToken | ❌ none |

**Take:** ThePixelMusic has the deeper, typed, more current InnerTube surface (16+ clients, 2026 versions). LastWave's runtime config bootstrap is the standout idea — its search calls never die from a stale baked-in version/key, and it self-heals on 400/403/429.

### 2.2 Player client strategies

**ThePixelMusic `STREAM_FALLBACK_CLIENTS`** (ArchiveTune order — plain-URL clients first so zero decipher work):

```
ANDROID_VR_NO_AUTH(1.37) → ANDROID_VR_1_61_48 → ANDROID_VR_1_43_32 → TVHTML5 →
WEB_CREATOR → WEB_REMIX → WEB → IOS → MOBILE(ANDROID 21.10.38) → ANDROID_MUSIC →
ANDROID_CREATOR → IPADOS → VISIONOS → TVHTML5_SIMPLY_EMBEDDED_PLAYER
```

Dynamic re-ordering: last-successful client first → user-preferred → login-capable clients first when logged in → always append `WEB_REMIX` if missing. Bot-detection triggers a one-time auth repair (BotGuard session invalidation + visitorData rotation) and a single same-client retry.

**LastWave `PLAYER_CLIENTS`** (only one is tried per resolve, others via 60 s circuit breaker):

```
ANDROID_VR 1.65.10 (Quest UA) → TVHTML5 7.20240715 (Cobalt) → IOS_MUSIC 6.42.1 →
IOS 19.29.1 → ANDROID 19.13.36 → WEB_REMIX 1.20240715
```

Only the first non-cooled-down client races; if it fails to deliver it is benched for 60 s so the next resolve advances the ladder. Per-client API keys + UAs + `osVersion` are hardcoded; `signatureTimestamp` is pinned at `19940`.

**Take:** PixelMusic's ladder is far more exhaustive and self-healing per track; LastWave's circuit breaker is a cheaper memory of "this client is dead right now" that avoids burning 1–2 s per resolution on a known-bad client. The best design is **both**: ladder + circuit breaker.

---

## 3. PO tokens / BotGuard

### 3.1 Shared lineage

Both apps run the same engine concept (PixelMusic's is the origin; LastWave's is a hardened derivative — identical `TAG`, jnn URLs, `REQUEST_KEY = O43z0dpjhgX20SCx4KAo`, LRU size 200, 50-min expiry):

1. Load `assets/po_token.html` (BotGuard JS: `loadBotGuard` / `runBotGuard` / `createPoTokenMinter` / `obtainPoToken`) into a headless WebView with base URL `https://www.youtube.com`.
2. `POST https://www.youtube.com/api/jnn/v1/Create` with the request key → challenge JSON (descramble if scrambled) → run BotGuard in JS.
3. `POST .../GenerateIT` with the BotGuard response → integrity token + lifetime.
4. Create a PoToken minter once; mint per-video player tokens (and a session/GVS token).

### 3.2 Differences

| Aspect | ThePixelMusic `utils/potoken/BotGuardTokenGenerator` | LastWave `data/music/potoken/BotGuardTokenGenerator` |
|---|---|---|
| Timeouts | cold 15 s / warm 5 s | cold 10 s / warm 3 s |
| Pre-warm guard | `AtomicBoolean preWarmStarted`, resets on failure | `hasUsableWebView()` probe before any WebView creation |
| Engine creation | `BotGuardEngine.create` via `suspendCancellableCoroutine` | `CompletableDeferred` + guaranteed `engine.close()` on timeout (fixes WebView leak) |
| Concurrent mints | `Collections.synchronizedMap(ArrayMap<Continuation>)` | `computeIfAbsent` on `CompletableDeferred` (identical dedup, cleaner) |
| Mint failure retry | retry with fresh engine | retry with fresh engine |
| Background handling | `onAppBackgrounded()` destroys WebView (~50 MB reclaimed); recreated on demand | no equivalent (keeps engine alive while valid) |
| Fatal WebView state | `BrokenWebViewException → permanentlyBroken` | `permanentlyBroken` + WebView-provider probe |
| Session | session id = visitorData/dataSyncId (per account state) | fixed `"lastwave_session"` + cached session token |

### 3.3 How each app uses the tokens — and the fake-token problem

**ThePixelMusic:**
- Only when `webClientPoTokenEnabled` and only for WEB-family clients (`needsServiceIntegrity`): `YouTube.player()` mints via BotGuard and puts `playerToken` in `serviceIntegrityDimensions` + `poTokenGvs` for playback/GVS requests.
- **Stage-1 fast path (ANDROID_VR) deliberately skips BotGuard** (`copy(webClientPoTokenEnabled = false)`) — the comment is explicit: synchronous minting "was adding seconds per request". This is the single biggest latency decision in the app.
- ⚠️ **`PlaybackAuthState.resolvePlayerPoToken/resolveGvsPoToken` fall back to `PoTokenGenerator.generateSessionToken(id)` — a local XOR + protobuf-looking blob.** This is not a BotGuard output; YouTube's attestation will not validate it. On WEB-family clients with `webClientPoTokenEnabled=true` and no working WebView this token is *worse than none*: it marks requests as bot-suspect instead of simply untokened. Recommendation: return `null` instead.
- The legitimate `innertube/utils/PoTokenGenerator.kt` (XOR builder) is likewise only used by `PlaybackAuthState` — i.e., only in that same invalid fallback path.

**LastWave:**
- `resolveAudioStreamInternal` gives BotGuard a **50 ms budget** (`withTimeoutOrNull(50ms)`): cache hit = instant; miss = kick off a background mint and resolve **without** the token this time. Token arrives in cache (LRU 200) for the next track. Token goes both into the player body (`serviceIntegrityDimensions.poToken`) **and** is appended as `&pot=` onto NewPipe-resolved URLs.
- Because NewPipe URLs are gvs (`googlevideo`) URLs, the `pot=` parameter meaningfully reduces 403/bot-wall risk there.
- `Application.onCreate` only *initializes* (stores context) and deliberately does **not** build the WebView at launch (OEM/Android-11 WebView crash protection); playback mints on demand.

**Take:** LastWave's "never let the token block audio" budget + pot-on-NewPipe-URLs is the more production-safe pattern. PixelMusic's skip-on-VR approach achieves similar latency but then silently degrades to fake tokens on the web path — that fallback should be removed.

---

## 4. Qobuz implementation (LastWave only)

### 4.1 App side — `QobuzMusicApi.kt`

- Talks to **its own Cloudflare Worker**: `https://qobuz-backend.clashgram.workers.dev`, `X-API-Key: BuildConfig.QOBUZ_API_KEY`.
- `resolveStream(title, artist, expectedDuration, expectedAlbum, preferredQuality)`:
  1. progressive queries (`"title artist"`, `"artist title"`, title, raw title; limit 15),
  2. **strict anti-mismatch verification** — normalized title equality, identity-variant sets must match (`live/acoustic/karaoke/instrumental/tribute/cover/remix/mashup/demo/slowed/reverb/sped-up/nightcore/radio edit/extended`), artist verified against performer/album-artist/performing-role credits only (songwriter credits can't fake a match), duration within ±8 s, then scoring (base 1000, exact artist +300, album +120, duration proximity +10/s),
  3. `/api/track/{id}/url?quality=27|7|6|5&fallback=true` → direct CDN URL + format metadata.
- No high-confidence match ⇒ `null` ⇒ caller stays on YouTube. False-positive covers/tributes are effectively impossible.

### 4.2 Worker side — `qobuz-worker-backend/` (Cloudflare Worker, zero-dependency JS)

```mermaid
flowchart LR
    APP["LastWave app<br/>QobuzMusicApi"] -->|"X-API-Key"| W["CF Worker<br/>src/index.js (562 L)"]
    W --> AUTH{"have app_id +<br/>working secret?"}
    AUTH -->|no| BS["BundleScraper (bundle.js)<br/>GET play.qobuz.com/login<br/>→ bundle.js regex<br/>initialSeed × timezones → secrets<br/>+ hardcoded fallback IDs/secrets"]
    BS --> SIGN["signature.js<br/>pure-JS MD5:<br/>trackId+quality+ts+secret"]
    AUTH -->|yes| SIGN
    SIGN --> API["Qobuz public API<br/>www.qobuz.com/api.json/0.2<br/>search / track/get<br/>file_url (signed)"]
    API --> CDN["Streaming CDN<br/>FLAC 16/44.1 → 24/192"]
    W -.->|"optional user/login<br/>email+password"| AUTH
```

- Routes: `/api/search`, `/api/track/:id`, `/api/track/:id/url` (meta+URL), `/api/stream/:id`, `/api/download/*` (track/album/playlist/discography manifests, M3U, cover), `/api/auth/login`, `/api/tokens`, user favorites/playlists; API-key auth (`X-API-Key` or `?key=`) + CORS control.
- Quality ladder: `5` MP3 320 → `6` CD FLAC → `7` 24/96 → `27` 24/192; app sends `fallback=true` so the worker steps down if the account/tier can't serve the requested format.

### 4.3 Playback wiring

`MusicPlayer.resolveTrackAudioStream` tries Qobuz **first** with a **4 s ceiling** (`QOBUZ_RESOLVE_TIMEOUT_MS`); on miss/timeout it falls through to YouTube (`findBestMatch` bounded 3.5 s → `resolveAudioStream`), and any Qobuz CDN failure mid-play gets **exactly one** immediate YouTube retry before permanent-skip logic applies. UI badges: `HI-RES FLAC` / `LOSSLESS` / `MP3 320k`.

**Porting note for ThePixelMusic:** the Qobuz worker is self-contained (pure JS, no secrets baked into the app beyond the API key), so the entire subsystem is portable: copy `qobuz-worker-backend/`, deploy, add `QobuzMusicApi.kt` + `BuildConfig.QOBUZ_API_KEY`, and insert a Qobuz-first branch at the top of `YoutubeHelper.getSongPlayerUrl()` behind the same 4 s budget.

---

## 5. "Token warm" — warm-up strategy comparison

### ThePixelMusic (`PixelMusicApplication`)

Staggered, thermal-conscious pipeline on `warmUpScope` (MIN_PRIORITY) — comments explicitly note that 7+ parallel startups force CPU governor max-frequency:

| T+ | What warms |
|---|---|
| 0 | `MediaItemBuilder.initialize`, `BotGuardTokenGenerator.initialize` (main, trivial) |
| 500 ms | ExoCache `SimpleCache` index (lazy init off main) |
| 1500 ms | `NewPipe.init(YoutubeExtractor)` + CardColorExtractor |
| 3000 ms | AdMob + LastFM init |
| 5000 ms | DNS pre-resolution `music.youtube.com` + `googlevideo.com` → `awaitMainThreadIdle()` → **`BotGuardTokenGenerator.preWarm("warmup_session")`** (full WebView boot + Create + GenerateIT so first mint is warm) |
| background | `onAppBackgrounded` → destroys WebView (~50 MB) |

Plus runtime warm paths: `QueuePreloadManager` (next N queue items: resolve URL → `preCacheFirstChunk` → first-chunk byte prefetch → artwork), `warmHigherQualityInBackground` (waits 8 s after LOW start to avoid competing with the active stream), `resolvedUriCache` in DualPlayerEngine.

**Design intent:** never block main thread, never thermally spike; accept a few seconds of delay before the BotGuard engine is hot.

### LastWave (`LastWaveApplication`)

| When | What warms |
|---|---|
| onCreate (sync) | `CrashGuard.install`, `PlaybackDiagnostics.install`, `BotGuardTokenGenerator.initialize` (context only) |
| onCreate (IO scope) | orphan `dl_raw_` temp cleanup; **`YouTubeStreamExtractor.preWarm()` = `NewPipe.init` only** |
| +1.5 s | YtMusicSync heartbeat |
| explicit no-warm | **BotGuard WebView is intentionally NOT pre-warmed** — comment cites OEM Android-11 WebView-provider crashes killing the process at launch; engine builds on first mint (cold budget 10 s) |
| per play() | `innerTube.prefetchStream(videoId)` of the track being played (racer + NewPipe cache seeded before ExoPlayer even opens the DataSource) |
| on transition | `preloadNextTrack` = resolve next track + `CacheWriter` first **4 MB** into SimpleCache |

**Design intent:** stability first (no WebView at launch), speed via cache-seeding rather than engine pre-warming; the 50 ms PO-token budget means a cold BotGuard engine never delays audio — the first track just goes out tokenless, subsequent ones are warm.

### Verdict

- **PixelMusic warms more, earlier, but pays thermal + memory risk** and its BotGuard warm result is only usable on the web-client path (which the fast path doesn't even use!).
- **LastWave warms less but with better cost/benefit**: NewPipe init + prefetch-seeding cover ~all of the click→sound latency; BotGuard rides along non-blocking.
- **Ideal hybrid for PixelMusic:** keep the DNS + NewPipe + ExoCache stages; keep BotGuard preWarm but gate it behind `hasUsableWebView()` like LastWave, add the `AtomicBoolean` + leak-safe `engine.close()` on timeout, and only warm it if the user's preferred client actually needs a PO token.

---

## 6. Speed engineering ("0 ms / instant") claims

| Technique | PixelMusic | LastWave |
|---|---|---|
| URL cache at target quality | ✅ 3-key LRU(200) + `expire=`-based O(1) validity (network probes removed as stall source) | ✅ single-key 4 h TTL cache(64) |
| In-flight dedup | ❌ | ✅ `activeStreamRequests` Deferred map |
| Parallel racing | ❌ sequential stages | ✅ NewPipe ∥ InnerTube via Channel(2), 15 s cap |
| Skip-token fast path | ✅ ANDROID_VR with no PO token | ✅ 50 ms token budget, race continues without |
| Lazy resolution in player | URL resolved before `setMediaItem` (eager, cached) | ✅ `lastwave://` scheme resolved inside `ResolvingDataSource` (35 s bound) |
| Next-track warm | ✅ QueuePreloadManager: N items, URL + first chunk + artwork | ✅ next track only, 4 MB into disk cache |
| Background quality upgrade | ✅ 8 s after LOW start | n/a (always top quality) |
| Weak-link strategy | ✅ AUTO quality plan, low-first, then bg upgrade | ❌ |
| Buffer tuning | ExoCache + engine-level (per-download cache) | `DefaultLoadControl` 45 s/120 s, playback-at 4 s, rebuffer 8 s, back buffer 15 s |
| First-chunk pre-cache | ✅ `preCacheFirstChunk` in engine | via CacheWriter prefetch |
| Gapless/crossfade | ✅ DualPlayerEngine (playerA/B crossfade) | ❌ (single player; crossfade = volume ramp hack) |

**Net:** PixelMusic optimizes cold-start-per-track through aggressive multi-key caching and staged fallbacks; LastWave optimizes by racing two resolution sources and by resolving lazily inside the player so a cache hit is truly 0 ms and a miss doesn't block UI. LastWave's in-flight dedup is a cheap, high-value add for PixelMusic (e.g., double-tap play + QueuePreloadManager racing each other).

---

## 7. The four referenced LastWave commits

| Commit | Date | What it did (verified via `git show --stat`) |
|---|---|---|
| `ac71330` — "Add Qobuz Hi-Res lossless direct CDN streaming, quality control, strict anti-mismatch verification…" | 2026-08-23 | Introduced the whole Qobuz subsystem: `QobuzMusicApi.kt` (290 L), the **entire `qobuz-worker-backend/`** (worker, bundle scraper, signature, downloader, m3u, docs, server.js, tests), `qobuzQuality` setting, MusicPlayer Qobuz-first wiring |
| `92e1408` — "feat(streaming): bulletproof 0ms streaming with BotGuard PO token, Qobuz Hi-Res fallback, instant native playback, universal playlist import" | 2026-08-23 | Added `potoken/` package + `po_token.html`, +177 L in `InnerTubeMusicApi` (the dual-racer + pot + circuit breaker), rewired `MusicPlayer` (+314 L: `lastwave://` lazy resolve, 256 MB cache, prefetch), universal playlist import (+461 L import screen, 600-page continuations) |
| `629b46e` — "Optimize stream & artwork resolution, fix session persistence and discovery fallbacks" | 2026-08-23 | Stream-cache pruning/limits in `InnerTubeMusicApi` (−90 L churn), artwork pipeline overhaul, session persistence (`SessionPreferences`), discovery/generate fallbacks; added the Application-side NewPipe preWarm |
| `2480e7f` — "fix(core): enhance Qobuz resolution, lyrics sync, community search, player UI" | 2026-08-27 | `QobuzMusicApi` +290 L (the strict anti-mismatch verification/identity variants), `InnerTubeMusicApi` +173 L (match/verify hardening), download manager fixes, lyrics sync rewrite |

These four commits are exactly the lineage of everything in sections 2–4: `ac71330` built Qobuz, `92e1408` built the BotGuard/racer/instant-playback spine, `629b46e` stabilized caches/sessions, `2480e7f` hardened matching.

---

## 8. File-by-file comparisons requested

### 8.1 MainActivity

- **LastWave (150 L):** splash + edge-to-edge + notification permission; Last.fm OAuth callback capture; **link playback resolver** (`youtube.com`, `m.youtube.com`, `music.youtube.com`, `youtu.be`, `open.spotify.com`, `spotify.link` + ACTION_SEND) → instant playback of shared links; requests highest display refresh rate; rebinds scrobble listener.
- **ThePixelMusic (1311 L):** a much larger shell — Compose host with a broad `handleIntent` surface (deep links, player intents via `MainActivityIntentContract`), plus the rest of app-shell duties. It does **not** have a shared-link-to-playback resolver for arbitrary YouTube/Spotify URLs, nor the refresh-rate hint.

### 8.2 YouTube extractor

- **ThePixelMusic `YoutubeExtractor.kt` (91 L):** thin NewPipe `Downloader` over the shared OkHttp client; used by `NewPipe.init` at T+1.5 s and by Stage-3 fallback + `NewPipeUtils.getStreamUrl` (cipher deobf / n-param / signature timestamp).
- **LastWave `YouTubeStreamExtractor.kt` (137 L):** same NewPipe wrapper plus a **4 h/64-entry result cache**, Opus-preference, kbps→bps normalization, `preWarm()`, and cache pruning. NewPipe is a first-class racer in LastWave vs a janitor fallback in PixelMusic.

### 8.3 Playback service

- **LastWave `MusicPlaybackService` (822 L):** a *plain* `Service` — playback lives in the `MusicPlayer` singleton; the service only owns the foreground notification, a framework `MediaSession` bridge, widget token holder and scrobbling. Crash-isolation comments everywhere (OEM MediaSession throws are contained).
- **ThePixelMusic `MusicService` (4312 L) + `DualPlayerEngine` (1640 L):** full `MediaLibraryService` hosting a dual-ExoPlayer engine (master playerA + crossfade playerB), `resolvedUriCache`, `preCacheFirstChunk`, MediaLibrary session/browse, widget actions, per-video BotGuard token invalidation on errors. Much richer, much heavier; more single-file blast radius.

### 8.4 Application classes

- **LastWaveApplication (135 L):** CrashGuard first (before Hilt!), diagnostics installer, BotGuard init-only, orphan temp cleanup, NewPipe preWarm, sync heartbeat, widget-theme observer, and a heavily tuned Coil loader (bounded dispatcher 48/8, 16-min connection pool, 18 % mem cache, 32 MB disk, cache-headers ignored).
- **PixelMusicApplication (343 L):** the staggered warm pipeline (§5) plus ads/LastFM/album-art migration/Japanese dictionary init. More work done, with explicit thermal rationale; but no CrashGuard-style process containment before super.onCreate().

---

## 9. Scorecard

| Dimension | Winner | Why |
|---|---|---|
| InnerTube breadth & freshness | 🟣 ThePixelMusic | 16+ clients, typed models, current versions, playback tracking |
| InnerTube resilience | 🟡 LastWave | runtime config bootstrap + 400/403/429 self-heal + circuit breaker |
| PO token correctness | 🟡 LastWave | non-blocking 50 ms budget, pot on NewPipe URLs; PixelMusic's XOR fallback is invalid |
| PO token engineering hygiene | 🟣 ThePixelMusic | onAppBackgrounded memory release, permanentlyBroken state (LastWave counters with leak-safe close) |
| Fallback depth | 🟣 ThePixelMusic | 3 stages × 14 clients × quality fallbacks vs 2 racers + 1 fallback |
| First-play latency | tie | PixelMusic: multi-key URL cache; LastWave: racer + 0 ms cache hit + lazy resolve |
| Continuous-playback warmth | 🟣 ThePixelMusic | QueuePreloadManager N-ahead incl. bytes + artwork vs 1 track/4 MB |
| Hi-Res / lossless | 🟡 LastWave | full Qobuz Hi-Res primary + verified matching (PixelMusic has none) |
| Stability philosophy | 🟡 LastWave | CrashGuard, WebView-at-launch avoidance, bounded everything |
| Account integration | 🟣 ThePixelMusic | full YT Music auth surface + registerPlayback |

## 10. Recommended ports into ThePixelMusic

1. **Remove the fake `PoTokenGenerator` fallback** in `PlaybackAuthState` — return `null`; an invalid token is worse than none.
2. **50 ms PO-token budget** (LastWave pattern) everywhere a web-client mint would block; mint in background and let the next track use it.
3. **In-flight resolve dedup** (`Deferred` map) in `YoutubeHelper` to collapse concurrent resolves for the same video.
4. **Runtime InnerTube config bootstrap** from `music.youtube.com` (key/version/visitorData) + invalidate on 400/403/429, replacing the fixed `1.20260114` constants' failure mode.
5. **Client circuit breaker** (60 s cooldown map) on top of the ArchiveTune ladder.
6. **`ConfirmedUnplayable` semantics** — marker-string classification so genuinely unavailable tracks skip instead of burning the whole ladder.
7. **Dynamic `signatureTimestamp`** — already present in Stage 2; hoist it into the Stage-1 VR call too (LastWave's hardcoded 19940 shows the risk of pinning).
8. **Qobuz tier** — port `qobuz-worker-backend/` + `QobuzMusicApi.kt` and insert a 4 s-budget Qobuz branch ahead of Stage 1 for Hi-Res FLAC, keeping YouTube as the guaranteed fallback.
9. **Warm-up guards** — add `hasUsableWebView()` + timeout-leak-safe engine close to `BotGuardTokenGenerator.preWarm`.
10. **Shared-link instant playback** — LastWave's `LinkPlaybackResolver` pattern in MainActivity (youtu.be / music.youtube / Spotify links straight into the queue).

### Recommended ports into LastWave (reverse flow)

1. Dynamic `signatureTimestamp` (NewPipe helper) instead of pinned `19940`.
2. Wider client ladder (WEB_CREATOR, ANDROID_MUSIC, IPADOS, VISIONOS…).
3. `registerPlayback` (cpn + videostats) for play-count fidelity.
4. QueuePreloadManager-style N-ahead prefetch + weak-link quality plan.
5. `onAppBackgrounded` BotGuard memory release.

---

*Generated 2026-08-28 · Sources: ThePixelMusic @ `9384d50` (main), LastWave-native @ `bfe63cc` (HEAD) with commit archaeology for `ac71330`, `92e1408`, `629b46e`, `2480e7f`.*
