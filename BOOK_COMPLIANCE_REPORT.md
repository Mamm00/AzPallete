# Book Compliance Report — *Android Development with Jetpack Compose & Kotlin (2026 Edition)*

**Project**: `Al AzharTex Digital Shade Cart` (`com.alazhartex.shadecart`)  
**Reference Authority**: A.P. Pyre, *Android Development with Jetpack Compose & Kotlin (2026 Edition)* (180 pp.)  
**Target Branch / Release**: `arena/39d7b421-azpallete` (`v1.1.0`, `versionCode = 3`)

---

## 1. Executive Summary

The `AzPallete` repository has been audited and upgraded to comply strictly with A.P. Pyre's 2026 architecture, Jetpack Compose, state management, Hilt DI, persistence, networking, testing, and release-hardening guidelines while preserving 100% of the Al AzharTex Digital Shade Cart textile workflows (`CameraX`, `CanHub Cropper`, on-device `ML Kit`, two-column pinked-swatch color sampling, optimistic locking, and `WorkManager` sync).

All changes were implemented through incremental milestone commits (`4035c51`, `b57fc94`, `231c6f6`, `8db0cef`, `112af1a`, and this documentation milestone), keeping every build and test boundary cohesive and verifiable.

---

## 2. Chapter-by-Chapter Compliance Mapping

| Book Chapter / Section | Book Requirement & Rationale | Codebase Component(s) | Status |
| :--- | :--- | :--- | :--- |
| **Ch. 1.1–1.4, App. A.1–A.2** | 100% Jetpack Compose UI (`ComponentActivity` + `setContent`, zero XML screen layouts), UDF, and separation of stateful `*Route` wrappers from stateless `*Screen` composables | `MainActivity.kt`, `DigitalShadeCardRoute` / `DigitalShadeCardScreen`, `CartCaptureRoute` / `CartCaptureScreen`, `CartEditRoute` / `CartEditScreen`, `PaletteEditorRoute` / `PaletteEditorScreen` | **Compliant** |
| **Ch. 1.3, App. A.3** | Kotlin DSL Gradle build scripts + centralized `gradle/libs.versions.toml` version catalog + KSP annotation processing | `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml` | **Compliant** |
| **Ch. 2.1–2.4, 5.1–5.4** | Material 3 `Scaffold`, `TopAppBar`, `NavigationBar`, `ColorScheme` (`lightColorScheme` / `darkColorScheme` / `dynamicLightColorScheme` / `dynamicDarkColorScheme`), explicit `Typography`, rounded `Shapes`, light/dark `@Preview`s | `ui/theme/Theme.kt`, `ui/theme/AccentPalettes.kt`, `ui/theme/Type.kt`, `ui/screens/AlAzharTexShadeCartApp.kt`, `ui/components/PinkedSwatchCard.kt`, `ui/components/ConflictBanner.kt` | **Compliant** |
| **Ch. 3.5, 10.2** | Structured concurrency on `viewModelScope` and explicit injected `CoroutineDispatcher`s (`@IoDispatcher`, `@DefaultDispatcher`) | `di/AppModule.kt`, all ViewModels, `ShadeCartRepository`, `DatabaseSeeder`, `BitmapProcessor` | **Compliant** |
| **Ch. 6.1–6.3, App. A.1** | `@HiltViewModel` exposing immutable `StateFlow` (`.asStateFlow()` / `.stateIn(WhileSubscribed)`), collected in UI via `collectAsStateWithLifecycle()`, zero `LiveData` | `AppShellViewModel`, `DigitalShadeCardViewModel`, `CartCaptureViewModel`, `CartEditViewModel`, `PaletteEditorViewModel`, `MainActivity.kt` | **Compliant** |
| **Ch. 6.4 (Rule #1)** | `NavHost` and `rememberNavController()` hosted strictly inside UI composables; ViewModels never hold `NavController` references | `ui/navigation/NavRoutes.kt`, `AlAzharTexShadeCartApp.kt` | **Compliant** |
| **Ch. 7.1, 10.1–10.2** | Hilt DI (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@Module`, `@Provides`, `@Binds`) + 3-layer Clean Architecture (`domain/` models, repository interfaces, `operator fun invoke` `usecase/`s; `data/` mappers & implementations; `ui/` presentation) | `AlAzharTexApplication.kt`, `di/{AppModule,DatabaseModule,SyncAndVisionBindingsModule}.kt`, `domain/model/ShadeCartDomainModels.kt`, `domain/repository/ShadeCartDomainRepositories.kt`, `domain/usecase/*` | **Compliant** |
| **Ch. 7.3–7.4, 9.2–9.4, 10.3** | Offline-first Room persistence (`@Database`, `@Entity`, `@Dao` returning reactive `Flow`, `suspend` mutations inside `withTransaction`) + exported schemas | `data/local/{AlAzharTexDatabase,Entities,Daos,DatabaseSeeder}.kt`, `data/repo/{RoomShadeCartStore,ShadeCartRepository,ShadeCartDomainMappers}.kt`, `app/schemas/` | **Compliant** |
| **Ch. 8.1–8.3, 10.2** | Retrofit `suspend fun` service interface + `GsonConverterFactory` + OkHttp (`15s` timeouts + `BuildConfig.DEBUG`-gated `HttpLoggingInterceptor`) + explicit HTTP error / `409 Conflict` mapping | `data/remote/{ShadeCartApiService,ShadeCartApiModels,NetworkModule}.kt`, `data/sync/RetrofitShadeCartSyncAdapter.kt`, `AsyncResult` | **Compliant** |
| **Ch. 10.4** | Preferences DataStore (`.data.map`, `.edit`, zero `SharedPreferences`) behind domain contract | `data/prefs/ThemePreferencesRepository.kt`, `domain/repository/ThemePreferencesContract`, `domain/usecase/ThemePreferencesUseCases.kt` | **Compliant** |
| **Ch. 11.1–11.2** | Stable `key = { ... }` across `LazyColumn` / `LazyRow` items, `RGB_565` off-main-thread image decoding via `produceState`, zero heavy computation inside composables | `DigitalShadeCardScreen.kt`, `PaletteEditorScreen.kt`, `Rgb565PreviewImage.kt`, `PinkedSwatchSampler.kt` | **Compliant** |
| **Ch. 11.3** | JVM unit tests using `StandardTestDispatcher`, `Dispatchers.setMain`/`resetMain`, `runTest`, `advanceUntilIdle()`, `MockWebServer`, and pure domain fakes | `PaletteEditorViewModelTest.kt`, `RetrofitShadeCartSyncAdapterTest.kt`, `ShadeCartUseCasesTest.kt`, `ShadeCartRepositoryConflictTest.kt`, `HexColorValidatorTest.kt`, `PinkedSwatchSamplerTest.kt`, `ShadeCartOcrParserTest.kt` | **Compliant** |
| **Ch. 11.4** | Jetpack Compose UI tests using `createComposeRule()`, `createAndroidComposeRule()`, `createEmptyComposeRule()`, and `Modifier.testTag()` | `StatelessShadeCardScreenUiTest.kt`, `HappyPathUiTest.kt`, `NoSwatchesEmptyStateUiTest.kt`, `PermissionDeniedUiTest.kt` | **Compliant** |
| **Ch. 12.1–12.4** | Production release hardening (`isMinifyEnabled = true`, `isShrinkResources = true`, `proguard-android-optimize.txt`), R8 Retrofit/Gson/Room rules, `BuildConfig.DEBUG`-gated logging and QA `"Simulate Conflict"` button, env-var release signing, Apache-2.0 `LICENSE`, updated `README.md` | `app/build.gradle.kts`, `app/proguard-rules.pro`, `NetworkModule.kt`, `ThemeSettingsSheet.kt`, `LICENSE`, `README.md` | **Compliant** |

---

## 3. Domain-Specific Library Extensions & Disclosures

The following specialized libraries are required by the **Al AzharTex Digital Shade Cart** hardware/vision workflows and remain integrated cleanly behind Clean Architecture boundaries without violating book conventions:

1. **CameraX (`1.6.1`)** (`camera-compose`, `camera-lifecycle`, `camera-camera2`) — Live A4 card viewfinder and `ImageCapture` pipeline isolated inside `CartCaptureScreen` with explicit rationale & App Settings deep link handling.
2. **CanHub `android-image-cropper` (`4.7.0`)** — Apache-2.0 open-source interactive cropping widget embedded cleanly inside `CartEditScreen` via Compose `AndroidView`, writing normalized non-destructive crop coordinates into `EditStack`.
3. **Google ML Kit (`text-recognition:16.0.1` & `play-services-mlkit-subject-segmentation:16.0.0-beta1`)** — On-device OCR and optional foreground segmentation behind `TextRecognitionGateway` and `SubjectSegmentationGateway`. As disclosed in `README.md`, ML Kit models and Play Services `ModuleInstall` delivery are closed-source Google-hosted binaries chosen specifically so fabric cards are processed locally on-device without external AI cloud calls, with full crop-only fallback when offline.
4. **AndroidX `Palette` (`1.0.0`) & `ExifInterface` (`1.4.0`)** — Deterministic EXIF normalization and pinked-swatch inner color extraction inside `domain/imaging/` on `Dispatchers.Default` / `Dispatchers.IO`.
5. **WorkManager (`2.10.0`) + `@HiltWorker`** — Guaranteed connected-network background execution of `TriggerSyncUseCase` via `SyncWorker` & `WorkManagerSyncScheduler`.

---

## 4. Verification Summary

- **Presentation-to-Data Isolation Check**: Verified zero imports of `data.{local,repo,prefs,sync,remote}` inside `ui/` or `MainActivity.kt` — all ViewModels depend exclusively on `domain/usecase/`, `domain/model/`, and domain gateways.
- **CI & Release Pipeline**: Verified via GitHub Actions (`Android CI — Al AzharTex Shade Cart` running `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, and API 35 Android Emulator `connectedDebugAndroidTest`, plus `Release — Al AzharTex Shade Cart` compiling R8-minified `assembleRelease` and verifying APK signing via `apksigner`).
