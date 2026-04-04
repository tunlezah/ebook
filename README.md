# ShelfWise

A lightweight, high-performance Android eBook reader optimized for low-powered hardware.

## Features

- **Multi-format support**: EPUB, PDF, and MOBI/AZW3
- **Smart library management**: Grid and list views, search, auto-scanning
- **Wireless file transfer**: Built-in HTTP server for uploading books from any browser on the local network
- **Reading customization**: Multiple themes (light/sepia/dark), 6 reading fonts, adjustable font size, line spacing, and margins
- **Blue light filter**: Reduce eye strain during night reading
- **Performance optimized**: Designed for low-RAM tablets like Lenovo Tab M8
- **Battery efficient**: No background services unless explicitly started
- **Offline-first**: Everything works without internet

## Target Devices

- **Primary**: Lenovo Tab M8 (Android 13) — 2-3 GB RAM, MediaTek Helio A22
- **Secondary**: Moto g04 (Android 14)

## Architecture

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| Architecture | MVVM |
| UI | XML Views + ViewBinding |
| Database | Room (SQLite) |
| Image loading | Coil (RGB_565) |
| Navigation | Jetpack Navigation Component |
| File transfer | NanoHTTPD (embedded HTTP server) |
| EPUB rendering | Custom parser + WebView |
| PDF rendering | Android PdfRenderer |
| MOBI support | Convert-to-EPUB pipeline |
| DI | Manual (AppContainer) |
| Storage | SAF (Storage Access Framework) |

## Why HTTP Instead of SMB?

No mature Java/Kotlin SMB server library exists. All available libraries (jcifs-ng, smbj) are client-only. The SMB protocol specification spans thousands of pages, making a lightweight embedded implementation impractical.

Our HTTP-based approach offers:
- Zero setup on the PC side (any browser works)
- Beautiful drag-and-drop upload UI
- File listing and download
- Runs as a foreground service with auto-stop timer

## Building

### Prerequisites

- Android Studio Hedgehog (2023.1) or later
- JDK 17
- Android SDK 34

### Build Debug APK

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Run Tests

```bash
./gradlew testDebugUnitTest
```

## Permissions

| Permission | Purpose |
|-----------|---------|
| `INTERNET` | HTTP file transfer server |
| `ACCESS_WIFI_STATE` | Get device IP for server URL |
| `ACCESS_NETWORK_STATE` | Check connectivity |
| `FOREGROUND_SERVICE` | Keep server running |
| `POST_NOTIFICATIONS` | Server status notification (Android 13+) |
| `WAKE_LOCK` | Prevent CPU sleep during transfers |

**No storage permissions required** — uses SAF (Storage Access Framework) for file access.

## Reading Fonts

All fonts are open-source (OFL licensed):

- **Literata** (default) — Designed for digital reading
- **Source Serif 4** — Adobe's open-source serif
- **Noto Serif** — Google's universal font family
- **Merriweather** — Optimized for screen readability
- **Lora** — Balanced text font
- **Source Sans 3** — Clean sans-serif option

## Limitations

1. **MOBI support**: Basic metadata extraction; full reading requires conversion to EPUB format internally
2. **PDF text selection**: Not available (uses bitmap rendering for performance)
3. **File transfer**: Requires same Wi-Fi network; no authentication (local network only)
4. **Fixed-layout EPUB**: Not fully supported (optimized for reflowable content)

## License

MIT
