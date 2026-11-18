package com.alazhartex.shadecart.di

import com.alazhartex.shadecart.data.prefs.AppSessionPreferencesRepository
import com.alazhartex.shadecart.data.prefs.BuyerSessionRepository
import com.alazhartex.shadecart.data.prefs.CalibrationPreferencesRepository
import com.alazhartex.shadecart.data.prefs.ThemePreferencesRepository
import com.alazhartex.shadecart.data.repo.MillInventoryRepository
import com.alazhartex.shadecart.data.repo.ShadeCartRepository
import com.alazhartex.shadecart.data.repo.SyncWorkScheduler
import com.alazhartex.shadecart.data.sync.RemoteSyncPortAdapter
import com.alazhartex.shadecart.data.sync.RetrofitShadeCartSyncAdapter
import com.alazhartex.shadecart.data.sync.ShadeCartSyncAdapter
import com.alazhartex.shadecart.data.sync.WorkManagerSyncScheduler
import com.alazhartex.shadecart.domain.repository.AppSessionPreferencesContract
import com.alazhartex.shadecart.domain.repository.ArFabricOverlayPort
import com.alazhartex.shadecart.domain.repository.BarcodeScannerGateway
import com.alazhartex.shadecart.domain.repository.BuyerSessionContract
import com.alazhartex.shadecart.domain.repository.CalibrationPreferencesContract
import com.alazhartex.shadecart.domain.repository.MillInventoryRepositoryContract
import com.alazhartex.shadecart.domain.repository.NaturalLanguageQueryGateway
import com.alazhartex.shadecart.domain.repository.RemoteSyncPort
import com.alazhartex.shadecart.domain.repository.ShadeCartRepositoryContract
import com.alazhartex.shadecart.domain.repository.SpectrophotometerBlePort
import com.alazhartex.shadecart.domain.repository.SyncDebugController
import com.alazhartex.shadecart.domain.repository.ThemePreferencesContract
import com.alazhartex.shadecart.domain.vision.GeminiNanoQueryGateway
import com.alazhartex.shadecart.domain.vision.MlKitBarcodeScannerGateway
import com.alazhartex.shadecart.domain.vision.NoOpArFabricOverlayPort
import com.alazhartex.shadecart.domain.vision.NoOpSpectrophotometerBlePort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncAndVisionBindingsModule {

    @Binds
    @Singleton
    abstract fun bindShadeCartRepository(impl: ShadeCartRepository): ShadeCartRepositoryContract

    @Binds
    @Singleton
    abstract fun bindMillInventoryRepository(impl: MillInventoryRepository): MillInventoryRepositoryContract

    @Binds
    @Singleton
    abstract fun bindThemePreferences(impl: ThemePreferencesRepository): ThemePreferencesContract

    @Binds
    @Singleton
    abstract fun bindCalibrationPreferences(impl: CalibrationPreferencesRepository): CalibrationPreferencesContract

    @Binds
    @Singleton
    abstract fun bindBuyerSession(impl: BuyerSessionRepository): BuyerSessionContract

    @Binds
    @Singleton
    abstract fun bindAppSessionPreferences(impl: AppSessionPreferencesRepository): AppSessionPreferencesContract

    @Binds
    @Singleton
    abstract fun bindSyncAdapter(impl: RetrofitShadeCartSyncAdapter): ShadeCartSyncAdapter

    @Binds
    @Singleton
    abstract fun bindRemoteSyncPort(impl: RemoteSyncPortAdapter): RemoteSyncPort

    @Binds
    @Singleton
    abstract fun bindSyncDebugController(impl: RetrofitShadeCartSyncAdapter): SyncDebugController

    @Binds
    @Singleton
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncWorkScheduler

    @Binds
    @Singleton
    abstract fun bindBarcodeScannerGateway(impl: MlKitBarcodeScannerGateway): BarcodeScannerGateway

    @Binds
    @Singleton
    abstract fun bindNaturalLanguageQueryGateway(impl: GeminiNanoQueryGateway): NaturalLanguageQueryGateway

    @Binds
    @Singleton
    abstract fun bindBlePort(impl: NoOpSpectrophotometerBlePort): SpectrophotometerBlePort

    @Binds
    @Singleton
    abstract fun bindArOverlayPort(impl: NoOpArFabricOverlayPort): ArFabricOverlayPort
}
