# Al AzharTex — Digital Shade Cart Module

**Al AzharTex Digital Shade Cart** (`com.alazhartex.shadecart`) is an Android Kotlin + Jetpack Compose module extending the **Al AzharTex Textile Inventory Ecosystem** (Idea #8). It digitizes physical A4 fabric shade carts (like two-column pinked fabric sample cards) into structured `ShadeCartEntity` and `SwatchEntity` records linked to `FabricType` and `Roll` inventory data using **metric units** (**GSM / g/m²** weight and **cm** width).

---

## Key Features & Screens

1. **Shade Cart Tab (`DigitalShadeCardScreen`)**
   - Displays digitized fabric shade carts with metric specifications (**GSM**, **cm**), optimistic-locking version tag, and sync status (`SYNCED`, `PENDING`, `CONFLICT`).
   - Renders a two-column grid of custom zigzag/pinked-edge `PinkedSwatchCard` color chips displaying the swatch `#number`, normalized `#RRGGBB` HEX value, color name, and interactive **Roll inventory link** (`RL-SLK-005`, warehouse shed, length in meters).
   - Dedicated **No-Swatches Empty State** with a primary **"Add swatch manually"** CTA leading directly into `PaletteEditorScreen`.

2. **Capture Tab (`CartCaptureScreen`)**
   - **CameraX (`1.6.1`)** live viewfinder + `ImageCapture` pipeline, **Android Photo Picker** gallery upload, and one-tap **Load Sample A4 Cart** (`sample_shade_cart.png`).
   - Explicit camera permission handling: displays clear educational rationale on denial and an **"Open Settings"** deep link (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`) on permanent denial.
   - Automatic downsampling (`BitmapProcessor`) capping all imported/captured images to **≤ 2048 px** on the long edge and **`RGB_565` previews** off the main thread.
   - Survives process death mid-capture via `SavedStateHandle` + temp-file restoration banner.

3. **Non-Destructive Card Editor (`CartEditScreen`)**
   - Maintains an undoable, serializable **`EditStack`** (`cropRect`, `aspectPreset`, `rotationDegrees`, `backgroundRemoved`, `segmentationMaskPath`, `history`) **without ever overwriting the original captured file**.
   - **CanHub `android-image-cropper` (`4.7.0`)** integration supporting free-form crop and fixed-aspect presets (`Free`, `A4 Portrait 5:7`, `1:1 Square`, `4:3 Card`).
   - **On-device ML Kit Text Recognition (`16.0.1`)** parses header fields (**Fabric Name**, **Fabric Code**, **Weight GSM**, **Width cm**) and adjacent numeric swatch labels (`5`, `7`, `11`, `12`, ...), with full manual correction before saving.
   - **On-device ML Kit Subject Segmentation (`16.0.0-beta1`)** via `ModuleInstall` dynamic download prompt and **non-blocking crop-only fallback** when the model is not yet downloaded or Play Services is unavailable.
   - **Pinked-Swatch Color Sampling (`PinkedSwatchSampler` + `TwoColumnSwatchDetector`)**: detects two-column swatch bands, insets each bounding box by **18% per side** to exclude zigzag/pinked teeth and cardstock shadows, filters bright paper glare, and extracts dominant HEX colors on `Dispatchers.Default` via AndroidX `Palette` (`clearFilters()`) with a filtered median fallback.

4. **Manual Palette & HEX Editor (`PaletteEditorScreen`)**
   - Add, delete, override colors, link warehouse `Roll` records, and reorder swatches smoothly with **drag handles**.
   - Strict HEX input validation enforcing **`^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})$`** (`#RGB` expands cleanly to `#RRGGBB`), accompanied by a live color preview circle, inline validation error message, and strictly disabled **Apply** button while invalid.

5. **Concurrency, Optimistic Locking, WorkManager Sync & Theming**
   - **Room (`2.8.5`)** local persistence with `FabricType` and optional nullable `Roll` foreign keys, plus exported schemas (`app/schemas/`).
   - **Concurrency**: All state-mutating repository operations acquire a Kotlin `Mutex` and execute atomically inside `RoomDatabase.withTransaction`.
   - **Optimistic Locking & Conflict UX**: Both `ShadeCartEntity` and `SwatchEntity` carry a monotonic `version` column. Stale writes or remote sync version mismatches record an unresolved conflict inside `sync_queue` and surface a non-blocking **Conflict Banner + Snackbar** letting the user pick **Keep Local** or **Use Remote**.
   - **Background Sync**: `SyncWorker` (`WorkManager 2.10.0` + `HiltWorker`) pushes queued mutations through the pluggable **`ShadeCartSyncAdapter`** interface (backed by `StubShadeCartSyncAdapter`, including a conflict simulation trigger in Settings).
   - **Brand & Material 3 Theming**: Incorporates the **Al AzharTex** circular navy/crimson emblem (`alazhartex_logo.png`), light/dark/system switching persisted in DataStore, **Android 12+ (API 31+) Dynamic Color**, and 4 custom accent palettes (`Al Azhar Navy & Crimson`, `Emerald & Sand Gold`, `Royal Cobalt`, `Terracotta & Slate`) usable as API < 31 fallback or user override.

---

## Architecture & Library Choices

### Dependency Injection — **Hilt (`2.59.2`)**
- Chosen over Koin for compile-time graph verification, native `@HiltViewModel` + `SavedStateHandle` support in `hilt-navigation-compose`, and `HiltWorkerFactory` integration with `WorkManager`.

### Cropping Library — **CanHub `android-image-cropper` (`4.7.0`)**
- **License**: Apache-2.0 (100% open source, self-hostable).
- **Rationale**: Maintained successor to ArthurHub's cropper with Kotlin APIs, embedded `CropImageView` usable inside Compose `AndroidView`, explicit free/fixed aspect-ratio control, and coordinate readback (`cropRect` / `wholeImageRect`) so `CartEditViewModel` records normalized crop coordinates inside `EditStack` instead of mutating original files.

### AI / Vision Libraries — **Google ML Kit (Closed-Source Google-Hosted Models)**
- **Text Recognition**: `com.google.mlkit:text-recognition:16.0.1` (bundled Latin text model running 100% on-device).
- **Subject Segmentation**: `com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1` (dynamically downloaded on-device via `ModuleInstall` API).
- **Disclosure & Rationale**: ML Kit's pre-trained model weights and Google Play Services `ModuleInstall` delivery are **closed-source, Google-hosted components** (rather than FOSS weights). ML Kit was specifically chosen so all OCR and foreground segmentation run **locally on-device without sending customer fabric shade cards to external cloud AI APIs**, while strictly adhering to the requirement to use **no AI services outside ML Kit**. When the segmentation module is unavailable or un-downloaded, `CartEditScreen` prompts to download it and falls back cleanly to crop-only mode.

### Reference Physical Shade Cart
- Reference layout derived for the project: [`docs/reference/shade_cart_reference.png`](docs/reference/shade_cart_reference.png) (and bundled in assets as `sample_shade_cart.png`). It represents an A4 portrait `Silk 180` (`SLK-180`, `180 GSM`, `150 cm`) two-column card with 8 zigzag/pinked swatches (`5`, `7`, `11`, `12`, `14`, `18`, `21`, `24`).

---

## Project Structure

```
app/src/main/java/com/alazhartex/shadecart/
├── AlAzharTexApplication.kt         # HiltWorkerFactory, StrictMode (debug), initial WorkManager sync
├── MainActivity.kt                  # Edge-to-edge host + M3 AlAzharTexTheme
├── di/                              # AppModule, DatabaseModule, SyncAndVisionBindingsModule
├── data/
│   ├── local/                       # Room entities, DAOs, AlAzharTexDatabase, DatabaseSeeder
│   ├── model/                       # Non-destructive EditStack & NormalizedRect
│   ├── prefs/                       # ThemePreferencesRepository (DataStore)
│   ├── repo/                        # ShadeCartRepository (Mutex + withTransaction + optimistic locking)
│   └── sync/                        # ShadeCartSyncAdapter, StubShadeCartSyncAdapter, SyncWorker
├── domain/
│   ├── imaging/                     # BitmapProcessor (<=2048px, RGB_565), PinkedSwatchSampler, TwoColumnSwatchDetector
│   ├── validation/                  # HexColorValidator (^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})$)
│   └── vision/                      # ShadeCartOcrParser, TextRecognitionGateway, SubjectSegmentationGateway
└── ui/
    ├── components/                  # BrandLogoBadge, PinkedSwatchCard, ConflictBanner, Rgb565PreviewImage, ThemeSettingsSheet
    ├── navigation/                  # NavRoutes
    ├── screens/                     # capture/, edit/, shadecard/, palette/, AlAzharTexShadeCartApp
    └── theme/                       # AccentPalettes, AlAzharTexTheme
```

---

## Reproducing Edge Cases

1. **Camera Permission Denied & Permanent Denial ("Open Settings")**
   - Open the **Capture** tab and deny the runtime Camera permission once -> `CartCaptureScreen` displays the permission rationale card with **"Allow Camera"** (while **Upload Photo** and **Load Sample A4 Cart** remain usable).
   - Deny a second time (or select *"Don't ask again"*) -> the screen switches to permanent-denial mode with an **"Open Settings"** button that opens Android's App Details settings (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`).

2. **ML Kit Segmentation Model Not Downloaded (Crop-Only Fallback)**
   - Capture or load a shade cart on a device/emulator where Play Services has not yet downloaded the Subject Segmentation module -> `CartEditScreen` shows a non-blocking prompt (**"Download Model"** / **"Crop-Only Mode"**).
   - Tapping **"Download Model"** triggers `ModuleInstall.installModules(...)` with progress; tapping **"Crop-Only Mode"** or operating offline continues normal free/fixed-aspect cropping and OCR extraction without blocking.

3. **No Swatches Detected / Empty State**
   - Launch on a fresh database (zero digitized carts) OR crop an image to an empty white margin and run **Detect OCR & Swatches**.
   - On the **Shade Cart** tab, `NoSwatchesEmptyState` displays **"No Swatches Yet"** and a primary **"Add swatch manually"** button that provisions a draft cart and opens `PaletteEditorScreen`.

4. **Invalid HEX Color Input**
   - Navigate to the **Palette** tab (or tap **Palette Editor** on a cart).
   - Type `#GGGGGG`, `102A71` (missing `#`), or `#1234` into the **HEX Color** field -> the live preview circle switches to an error badge, inline validation error text appears, and the **Apply** button is strictly disabled until a valid `#RGB` or `#RRGGBB` string is entered.

5. **Concurrent Edit / Optimistic Locking Conflict**
   - Tap the top-right **Palette/Sync Settings** icon (`ColorLens`) -> tap **"Simulate Conflict"** -> tap **"Sync Now"** (or test two stale-version saves via `ShadeCartRepositoryConflictTest`).
   - Both a top **Conflict Banner** and a **Snackbar** appear showing version numbers (`local v1 vs remote v2`) and letting you choose **Keep Local** or **Use Remote**.

6. **Process Death During Capture / Edit**
   - Start capturing or load a sample image so `CartCaptureScreen` or `CartEditScreen` has an active file path.
   - Background the app (`HOME`) and terminate the process from ADB:
     ```bash
     adb shell am kill com.alazhartex.shadecart
     ```
   - Re-open the app from Recents -> `SavedStateHandle` restores the active screen state, non-destructive `EditStack` JSON, OCR fields, and temp capture file recovery banner (**"In-Progress Capture Restored · Resume / Discard"**).

---

## Build & Test Commands

```bash
# Make wrapper executable if needed
chmod +x gradlew

# Run JVM unit tests (HEX validation, optimistic locking & Mutex conflicts, OCR parser, pinked sampling)
./gradlew testDebugUnitTest

# Run Android Lint (Thread safety / WrongThread & NewApi checks enabled as errors)
./gradlew lintDebug

# Assemble Debug APK & AndroidTest APK
./gradlew assembleDebug assembleDebugAndroidTest

# Run Compose UI Instrumented Tests on a connected emulator/device
./gradlew connectedDebugAndroidTest
```

---

## Assumptions

1. **Session Branch Name**: Work is committed on `arena/39d7b421-azpallete` rather than creating a separate `feature/digital-shade-cart` branch, per the Arena session branch constraint.
2. **Al AzharTex Inventory Schema Alignment**: `FabricType` and `Roll` entities mirror the existing Al AzharTex warehouse domain (`wh-alwa`, `wh-showroom`, `wh-625`, `wh-zibda`, `wh-leon`, `wh-azouz`, seeded fabrics `Silk 180`, `Warsaw L`, `Pirlanta`, `Rotana 180`, `Lexus 150`) with nullable `Roll` linkage (`rollId`) on `SwatchEntity`.
3. **Metric Units Only**: All fabric width and weight inputs/outputs use `cm` and `GSM` (`g/m²`), and warehouse roll lengths use meters (`m`).
4. **Remote Sync Backend**: No live Al AzharTex cloud endpoint exists in this repository; `StubShadeCartSyncAdapter` models remote sync version tracking cleanly behind `ShadeCartSyncAdapter` so a Retrofit/Supabase/Firebase implementation can be swapped via Hilt in `SyncAndVisionBindingsModule`.
5. **Physical Shade Cart Reference**: Since no external `shade_cart_reference.png` existed in the starter repo, [`docs/reference/shade_cart_reference.png`](docs/reference/shade_cart_reference.png) was generated to represent a canonical A4 two-column pinked-swatch fabric card for calibration and tests.
