# Qobuz Hi-Res Tier — Deploy & Configure

PixelMusic can stream **verified lossless / Hi-Res FLAC from Qobuz as the primary
audio source**, falling back to YouTube Music whenever Qobuz cannot verify an
exact match (port of LastWave-native's Qobuz subsystem, GPL-3.0).

## How it works

```
getSongPlayerUrl()
  ├─ local file / download? → play locally
  ├─ QobuzMusicApi.resolveStream()   ← 4 s budget, strict verification
  │     ├─ verified match → direct FLAC CDN URL (16/44.1 → 24/192)
  │     └─ no match / timeout / worker down → continue ↓
  └─ YouTube ladder (ANDROID_VR → ArchiveTune clients → NewPipe)   ← guaranteed
```

Strict anti-mismatch verification (never plays a cover/live/remix by accident):

- normalized title equality + "identity variant" sets must match
  (live/acoustic/karaoke/instrumental/tribute/cover/remix/mashup/demo/slowed/
  reverb/sped-up/nightcore/radio edit/extended)
- artist verified against performer / album-artist / performing-role credits
  (songwriter credits can never fake a match)
- duration within ±8 s of the YouTube track (when known)

## 1. Deploy the worker

The `qobuz-worker-backend/` directory is a self-contained Cloudflare Worker
(zero npm dependencies, pure JS):

```bash
cd qobuz-worker-backend
cp wrangler.toml.example wrangler.toml   # optional vars (all optional)
npx wrangler login
npx wrangler deploy
```

Or run it locally anywhere with Node (no Wrangler needed):

```bash
cp .env.example .env   # edit DEFAULT_QUALITY / API_AUTH_KEY
node server.js         # serves the worker on http://localhost:8787
```

Full backend documentation: `qobuz-worker-backend/SETUP.md` and the built-in
`/` docs page once deployed.

**Recommended:** set `API_AUTH_KEY` in the worker env so only your app can use
it, and `DEFAULT_QUALITY=27` for Hi-Res.

## 2. Configure the app

Set these in `local.properties` (or as environment variables) and build:

```properties
QOBUZ_BACKEND_URL=https://your-worker.your-subdomain.workers.dev
QOBUZ_API_KEY=your-api-auth-key        # only if you set API_AUTH_KEY on the worker
```

They compile into `BuildConfig.QOBUZ_BACKEND_URL` / `BuildConfig.QOBUZ_API_KEY`.
When `QOBUZ_BACKEND_URL` is empty (the default), the Qobuz tier is fully
disabled and behaviour is identical to before — YouTube only.

## Cost / latency notes

- Qobuz resolution runs with a **4 s budget ahead of the YouTube ladder**; a
  slow or down worker never delays playback more than 4 s (usually 0 — the
  budget applies only when the worker is configured).
- Successful Qobuz URLs are cached under both `<videoId>_qobuz` and
  `<videoId>_high` in the stream LRU; the player shows FLAC bit depth/rate.
- Track matching uses `/api/search` (limit 15) + `/api/track/{id}/url`.

## Provenance

App client (`QobuzMusicApi.kt`) and the `qobuz-worker-backend/` directory are
ported from [LastWave-native](https://github.com/Clash-Projects/LastWave-native)
(commits `ac71330`, `92e1408`, `2480e7f`), GPL-3.0 — license-compatible with
this project. See PROVENANCE.md.
