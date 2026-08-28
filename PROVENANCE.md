# Provenance

PixelMusic is an independent, unofficial open-source fork of PixelPlayerOSS.

## Upstream Project

| Field | Value |
|---|---|
| **Name** | PixelPlayerOSS |
| **Repository** | https://github.com/PixelPlayerHQ/PixelPlayerOSS |
| **Authors** | Theo Vilardo, Duhan Yağmur Delikkulak |
| **License** | GNU General Public License v3.0 (GPL-3.0) |

## This Fork

| Field | Value |
|---|---|
| **Name** | PixelMusic |
| **Repository** | https://github.com/ianshulyadav/ThePixelMusic |
| **Maintainer** | Anshul Yadav (ianshulyadav) |
| **License** | GNU General Public License v3.0 (GPL-3.0) — same as upstream |
| **Fork started** | June 2026 |

## Ported Code

| Field | Value |
|---|---|
| **Source project** | LastWave-native |
| **Repository** | https://github.com/Clash-Projects/LastWave-native |
| **License** | GNU General Public License v3.0 (GPL-3.0) |
| **Ported in** | August 2026 |

The following components are ported (with adaptation) from LastWave-native:

- `app/src/main/java/com/unshoo/pixelmusic/data/remote/qobuz/QobuzMusicApi.kt` —
  Qobuz verified-match client (from `data/qobuz/QobuzMusicApi.kt`, commits
  `ac71330` / `2480e7f`)
- `qobuz-worker-backend/` — self-contained Qobuz Cloudflare Worker backend
  (from LastWave-native, unmodified)
- Selected streaming-resilience patterns in
  `data/remote/youtube/YoutubeHelper.kt`,
  `unshoo/.../innertube/InnerTubeRuntimeConfig.kt`, and
  `utils/potoken/BotGuardTokenGenerator.kt` — in-flight resolve dedup, client
  circuit breaker, confirmed-unplayable classification, runtime InnerTube
  config bootstrap, and BotGuard warm-up/leak guards (inspired by
  `InnerTubeMusicApi.kt` / `data/music/potoken/`, commits `92e1408` / `629b46e`)

Both projects are GPL-3.0; attribution is retained in file headers and here.

## Terms Compliance

This fork complies with the terms agreed between Anshul Yadav and Theo Vilardo:

- ✅ Started from the clean OSS (GPL) repository `PixelPlayerHQ/PixelPlayerOSS`
- ✅ No code or assets from any proprietary repository included
- ✅ GPL-3.0 license retained with original upstream copyright notice intact
- ✅ README prominently states the fork relationship and credits upstream authors
- ✅ About screen credits Theo Vilardo and Duhan Yağmur Delikkulak by name
- ✅ All source code is open and publicly available

## Disclaimer

PixelMusic is **not** affiliated with, endorsed by, or sponsored by the upstream PixelPlayerOSS project or its authors.
