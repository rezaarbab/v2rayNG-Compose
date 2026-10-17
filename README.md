# Blob

v2rayNG-style VPN UI rebuilt from scratch with **Jetpack Compose** and 100% hand-drawn animated Canvas sticker characters (60fps, no images/GIFs).

## Features
- Live sticker connect button: idle / wobbling / happy bounce / sleeping
- 100 Blob + 60 Toy + ~200 flag stickers, all Canvas-rendered
- Two sticker styles + full customizer (sticker, color, size) via 1s hold on anything
- Auto flag background: server country when connected, your IP country when idle
- Per-app anti-filter selection (split-tunnel style picker)
- Reorderable bottom menu (hold a menu icon) and reorderable home sections
- Dark/light theme, 8 accents + custom hex color
- Separate speed sliders for stickers and background
- All UI strings centralized in `ui/Res.kt` for easy translation

## Build
```bash
gradle :app:assembleDebug
```
Or just let GitHub Actions do it: `.github/workflows/build.yml` produces a debug APK artifact on every push.

[![Build APK](https://github.com/rezaarbab/Blob/actions/workflows/build.yml/badge.svg)](https://github.com/rezaarbab/Blob/actions/workflows/build.yml)

## Roadmap
- Real VpnService + Xray core integration
- Subscriptions (vmess/vless/trojan links)
- Real latency/speed tests
