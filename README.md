# Al AzharTex — Digital Shade Cart (`AzPallete`)

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

**Al AzharTex Digital Shade Cart** (`com.alazhartex.shadecart`) is a production Android Kotlin + Jetpack Compose application extending the **Al AzharTex Textile Inventory Ecosystem**. It digitizes physical A4 fabric shade carts (two-column pinked fabric sample cards) into structured `ShadeCartModel` and `SwatchModel` domain records linked to `FabricType` and `Roll` inventory data using **metric units** (**GSM / g/m²** weight and **cm** width).

The architecture strictly conforms to **Clean Architecture + Unidirectional Data Flow (UDF) + Material 3** as taught in *Android Development with Jetpack Compose & Kotlin (2026 Edition)* by A.P. Pyre — see [`BOOK_COMPLIANCE_REPORT.md`](BOOK_COMPLIANCE_REPORT.md) for the complete chapter-by-chapter compliance mapping.

---

## Key Features & Screens

1. **Shade Cart Tab (`DigitalShadeCardRoute` / stateless `DigitalShadeCardScreen`)**
   - Displays digitized fabric shade carts with metric specifications (**GSM**, **cm**), optimistic-locking version tag, and sync status (`SYNCED`, `PENDING`, `CONFLICT`, `FAILED`).
   - Renders a two-column grid of custom zigzag/pinked-edge `PinkedSwatchCard` color chips displaying the swatch `#number`, normalized `#RRGGBB` HEX value, color name, and interactive **Roll inventory link** (`RL-SLK-005`, warehouse shed, length in meters).
   - Dedicated **No-Swatches Empty State** with a primary **"Add swatch manually"** CTA leading directly into `PaletteEditorRoute`.

2. **Capture Tab (`CartCaptureRoute` / stateless `CartCaptureScreen`)**
   - **CameraX (`1.6.1`)** live viewfinder + `ImageCapture` pipeline, **Android Photo Picker** gallery upload, and one-tap **Load Sample A4 Cart** (`sample_shade_cart.png`).
   - Explicit camera permission handling: displays clear educational rationale on denial and an **"Open Settings"** deep link (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`) on permanent denial.
   - Automatic downsampling (`BitmapProcessor`) capping all imported/captured images to **≤ 2048 px** on the long edge and **`RGB_565` previews** loaded asynchronously off the main thread.
   - Survives process death mid-capture via `SavedStateHandle` + temp-file restoration banner.

3. **Non-Destructive Card Editor (`CartEditRoute` / stateless `CartEditScreen`)**
   - Maintains an undoable, serializable **`EditStack`** (`cropRect`, `aspectPreset`, `rotationDegrees`, `backgroundRemoved`, `segmentationMaskPath`, `history`) **without ever overwriting the original captured file**.
   - **CanHub `android-image-cropper` (`4.7.0`)** integration supporting free-form crop and fixed-aspect presets (`Free`, `A4 Portrait 5:7`, `1:1 Square`, `4:3 Card`).
   - **On-device ML Kit Text Recognition (`16.0.1`)** parses header fields (**Fabric Name**, **Fabric Code**, **Weight GSM**, **Width cm**) and adjacent numeric swatch labels (`5`, `7`, `11`, `12`, ...), with full manual correction before saving.
   - **On-device ML Kit Subject Segmentation (`16.0.0-beta1`)** via `ModuleInstall` dynamic download prompt and **non-blocking crop-only fallback** when the model is not yet downloaded or Play Services is unavailable.
   - **Pinked-Swatch Color Sampling (`PinkedSwatchSampler` + `TwoColumnSwatchDetector`)**: detects two-column swatch bands, insets each bounding box by **18% per side** to exclude zigzag/pinked teeth and cardstock shadows, filters bright paper glare, and extracts dominant HEX colors on `Dispatchers.Default` via AndroidX `Palette` (`clearFilters()`) with a filtered median fallback.

4. **Manual Palette & HEX Editor (`PaletteEditorRoute` / stateless `PaletteEditorScreen`)**
   - Add, delete, override colors, link warehouse `Roll` records, and reorder swatches smoothly with **drag handles** and stable `LazyColumn` item keys.
   - Strict HEX input validation enforcing **`^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})$`** (`#RGB` expands cleanly to `#RRGGBB`), accompanied by a live color preview circle, inline validation error message, and strictly disabled **Apply** button while invalid.

5. **Clean Architecture, Optimistic Locking, Retrofit/WorkManager Sync & Material 3 Theming**
   - **3-Layer Clean Architecture**:
     - `domain/` — pure Kotlin `model/` classes (`ShadeCartModel`, `SwatchModel`, `WarehouseRollModel`, `ThemeSettings`, `AsyncResult`), `repository/` contracts (`ShadeCartRepositoryContract`, `ThemePreferencesContract`, `SyncDebugController`), and single-responsibility `operator fun invoke` `usecase/` classes.
     - `data/` — `local/` Room (`2.8.5`) entities/DAOs/schemas, `prefs/` DataStore Preferences, `remote/` Retrofit (`2.11.0`) + OkHttp (`4.12.0`) + `GsonConverterFactory` + `NetworkModule`, and `sync/` (`RetrofitShadeCartSyncAdapter` with offline-first stub fallback + Hilt `WorkManager` `SyncWorker`).
     - `ui/` — `*Route` stateful wrappers collecting `StateFlow` via `collectAsStateWithLifecycle()`, stateless `*Screen` composables with light/dark `@Preview`s, and `NavHost` strictly inside UI composables.
   - **Concurrency & Optimistic Locking**: All mutations acquire a Kotlin `Mutex` and execute inside `RoomDatabase.withTransaction`. Stale writes or remote HTTP `409 Conflict` responses record an unresolved conflict inside `sync_queue` and surface a **Conflict Banner + Snackbar** (**Keep Local** / **Use Remote**).
   - **Release Hardening**: R8 code shrinking (`isMinifyEnabled = true`), resource shrinking (`isShrinkResources = true`), debug-gated `HttpLoggingInterceptor` & QA conflict simulation tools (`BuildConfig.DEBUG`), and CI release keystore signing (`ALAZHARTEX_KEYSTORE_*`).

---

## Architecture & Library Choices

### Dependency Injection — **Hilt (`2.59.2`)**
- Compile-time DI graph validation via `AppModule`, `DatabaseModule`, `NetworkModule`, `StoreBindingsModule`, and `SyncAndVisionBindingsModule`.
- Native `@HiltViewModel` + `SavedStateHandle` support and `HiltWorkerFactory` integration with `WorkManager`.

### Networking — **Retrofit (`2.11.0`) + OkHttp (`4.12.0`) + Gson (`2.11.0`)**
- `ShadeCartApiService` declares `suspend fun` endpoints (`GET health`, `GET carts/{cartId}`, `PUT carts/{cartId}` with `If-Match` version header).
- `NetworkModule` configures `15s` connect/read/write timeouts, `GsonConverterFactory`, and `BuildConfig.DEBUG`-gated `HttpLoggingInterceptor`.
- `RetrofitShadeCartSyncAdapter` maps `2xx`, `409 Conflict`, and network failures to `SyncPushResult` — falling back seamlessly to `StubShadeCartSyncAdapter` when `BuildConfig.SYNC_BASE_URL` is blank (default offline-first build).

### Cropping Library — **CanHub `android-image-cropper` (`4.7.0`)**
- **License**: Apache-2.0 (100% open source, self-hostable).
- **Rationale**: Maintained successor to ArthurHub's cropper with embedded `CropImageView` inside Compose `AndroidView`, fixed/free aspect ratios, and non-destructive normalized `cropRect` coordinate readback into `EditStack`.

### AI / Vision Libraries — **Google ML Kit (Closed-Source Google-Hosted Models)**
- **Text Recognition**: `com.google.mlkit:text-recognition:16.0.1` (on-device Latin OCR).
- **Subject Segmentation**: `com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1` (on-demand via `ModuleInstall`).
- **Disclosure & Rationale**: ML Kit's model weights and Play Services `ModuleInstall` delivery are closed-source, Google-hosted components. ML Kit executes all OCR and foreground segmentation **locally on-device** without uploading customer fabric shade cards to external AI APIs, while maintaining a clean crop-only fallback when offline or un-downloaded.

---

## Project Structure

```
app/src/main/java/com/alazhartex/shadecart/
├── AlAzharTexApplication.kt         # @HiltAndroidApp, HiltWorkerFactory, debug StrictMode, initial seeding
├── MainActivity.kt                  # @AndroidEntryPoint single-activity Compose host + AlAzharTexTheme
├── di/                              # AppModule, DatabaseModule, StoreBindingsModule, SyncAndVisionBindingsModule
├── domain/
│   ├── model/                       # Pure Kotlin domain models (ShadeCartModel, SwatchModel, ThemeSettings, AsyncResult)
│   ├── repository/                  # Clean Architecture contracts (ShadeCartRepositoryContract, ThemePreferencesContract)
│   ├── usecase/                     # Single-responsibility UseCases (Observe*, CreateOrGet*, Save*, Update*, Resolve*, TriggerSync*)
│   ├── imaging/                     # BitmapProcessor (<=2048px, RGB_565), PinkedSwatchSampler, TwoColumnSwatchDetector
│   ├── validation/                  # HexColorValidator (^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})$)
│   └── vision/                      # ShadeCartOcrParser, TextRecognitionGateway, SubjectSegmentationGateway
├── data/
│   ├── local/                       # Room entities, DAOs, AlAzharTexDatabase, DatabaseSeeder
│   ├── model/                       # Non-destructive EditStack & NormalizedRect
│   ├── prefs/                       # ThemePreferencesRepository (DataStore Preferences)
│   ├── remote/                      # NetworkModule, ShadeCartApiService, Gson DTOs
│   ├── repo/                        # ShadeCartRepository, RoomShadeCartStore, ShadeCartDomainMappers
│   └── sync/                        # ShadeCartSyncAdapter, RetrofitShadeCartSyncAdapter, StubShadeCartSyncAdapter, SyncWorker
└── ui/
    ├── components/                  # BrandLogoBadge, PinkedSwatchCard, ConflictBanner, Rgb565PreviewImage, ThemeSettingsSheet
    ├── navigation/                  # NavRoutes
    ├── screens/                     # *Route + stateless *Screen pairs (capture/, edit/, shadecard/, palette/) & AppShellViewModel
    └── theme/                       # Type.kt (AlAzharTypography), AccentPalettes, AlAzharTexTheme
```

---

## Build & Test Commands

```bash
# Make wrapper executable if needed
chmod +x gradlew

# Run JVM unit tests (Retrofit MockWebServer, UseCases, ViewModel StateFlow + StandardTestDispatcher,
# HEX validation, optimistic locking & Mutex conflicts, OCR parser, pinked sampling)
./gradlew testDebugUnitTest

# Run Android Lint (Thread safety / WrongThread & NewApi checks enabled as errors)
./gradlew lintDebug

# Assemble Debug APK & AndroidTest APK
./gradlew assembleDebug assembleDebugAndroidTest

# Assemble R8-minified, resource-shrunk Release APK
./gradlew assembleRelease

# Run Compose UI Instrumented Tests on a connected emulator/device
./gradlew connectedDebugAndroidTest
```

---

## License

Licensed under the **Apache License, Version 2.0** — see [`LICENSE`](LICENSE) for full text.

---

## Assumptions

1. **Session Branch Name**: Work is committed incrementally on `arena/39d7b421-azpallete` per the Arena session branch constraint.
2. **Al AzharTex Inventory Schema Alignment**: `FabricType` and `Roll` entities align with the Al AzharTex warehouse domain (`wh-alwa`, `wh-showroom`, `wh-625`, `wh-zibda`, `wh-leon`, `wh-azouz`, seeded fabrics `Silk 180`, `Warsaw L`, `Pirlanta`, `Rotana 180`, `Lexus 150`) with nullable `Roll` linkage (`rollId`) on swatches.
3. **Metric Units Only**: All fabric width and weight inputs/outputs use `cm` and `GSM` (`g/m²`), and warehouse roll lengths use meters (`m`).
4. **Offline-First & Remote Sync Endpoint**: By default (`BuildConfig.SYNC_BASE_URL == ""`), `RetrofitShadeCartSyncAdapter` operates offline-first via the local Room queue + `StubShadeCartSyncAdapter`. Supplying `ALAZHARTEX_SYNC_BASE_URL` at build time routes sync calls directly through `Retrofit + OkHttp + Gson` (`PUT carts/{cartId}`, `GET carts/{cartId}`, `GET health`).
5. **Physical Shade Cart Reference**: [`docs/reference/shade_cart_reference.png`](docs/reference/shade_cart_reference.png) represents an A4 two-column pinked-swatch fabric card (`Silk 180`, `SLK-180`, `180 GSM`, `150 cm`) used for calibration and tests.
