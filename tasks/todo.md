# ShelfWise - Android eBook Reader with Wireless Transfer

## Architecture Decisions

### Research Summary
| Component | Choice | Rationale |
|-----------|--------|-----------|
| EPUB | Custom parser + WebView | Readium 30-50MB RAM too heavy; custom approach ~10-20MB |
| PDF | Android PdfRenderer | Zero APK cost, lowest memory, built-in API 21+ |
| MOBI | Convert-to-EPUB pipeline | No native MOBI renderer exists |
| File Transfer | NanoHTTPD HTTP server | No Java SMB server library exists; HTTP is universal |
| UI Framework | XML Views + ViewBinding | Lower memory than Compose on low-end devices |
| Image Loading | Coil | Kotlin-native, smaller than Glide, ~1500 methods |
| Architecture | MVVM + Room + Flow | Standard, well-tested pattern |
| DI | Manual (AppContainer) | Zero overhead, sufficient for ~10 classes |
| Storage | SAF (Storage Access Framework) | Zero permissions needed, Android 13/14 compliant |
| Navigation | Navigation Component | Handles saved state/process death correctly |
| Fonts | Literata (default), Noto Serif, Source Sans 3, Merriweather, Lora | All OFL licensed |

### Why Not SMB?
No mature Java/Kotlin SMB server library exists. jcifs-ng and smbj are client-only.
The SMB protocol is extremely complex (thousands of pages of spec). Instead, we use
NanoHTTPD to serve a web-based file transfer UI that works from any browser on the
local network - zero setup on the PC side.

## Implementation Plan

- [x] Phase 1: Research
- [x] Phase 2: Project Setup (Gradle, manifest, dependencies)
- [x] Phase 3: Data Layer (Room, models, repository)
- [x] Phase 4: Library Scanner (SAF, metadata extraction)
- [x] Phase 5: Library UI (grid/list, search)
- [x] Phase 6: EPUB Reader
- [x] Phase 7: PDF Reader
- [x] Phase 8: MOBI Support
- [x] Phase 9: Reading Settings (themes, fonts, typography)
- [x] Phase 10: HTTP File Server (wireless transfer)
- [x] Phase 11: Settings & Preferences
- [x] Phase 12: Testing
- [x] Phase 13: CI/CD
- [x] Phase 14: Documentation

## Project Structure

```
app/src/main/java/com/shelfwise/app/
├── EBookApp.kt                          # Application class
├── di/AppContainer.kt                   # Manual dependency injection
├── data/
│   ├── db/                              # Room database
│   │   ├── AppDatabase.kt
│   │   ├── BookDao.kt
│   │   └── entity/BookEntity.kt
│   ├── model/Book.kt                    # Domain models
│   └── repository/BookRepository.kt     # Data access layer
├── scanner/
│   ├── BookScanner.kt                   # SAF-based file scanner
│   └── metadata/                        # Format-specific parsers
│       ├── EpubMetadataExtractor.kt
│       ├── PdfMetadataExtractor.kt
│       ├── MobiMetadataExtractor.kt
│       └── MetadataResult.kt
├── reader/epub/EpubParser.kt            # EPUB content parser
├── server/HttpFileServer.kt             # NanoHTTPD file server
├── service/FileServerService.kt         # Foreground service
├── ui/
│   ├── MainActivity.kt
│   ├── library/                         # Book grid/list
│   ├── reader/epub/                     # EPUB WebView reader
│   ├── reader/pdf/                      # PDF PdfRenderer reader
│   ├── detail/BookDetailFragment.kt
│   └── settings/
│       ├── SettingsFragment.kt
│       └── TransferFragment.kt
└── util/
    ├── Extensions.kt
    └── PreferencesManager.kt
```

## Lessons Learned
- No Java/Kotlin SMB server library exists; HTTP is the practical alternative
- SAF's DocumentFile.listFiles() is extremely slow; use DocumentsContract queries directly
- RGB_565 bitmap config halves memory usage for book covers
- PdfRenderer requires a seekable file descriptor, not just an InputStream
- MOBI format has no viable native renderer; conversion to EPUB is the standard approach
- Readium's 30-50MB RAM overhead is too heavy for Lenovo Tab M8; custom WebView rendering is lighter
