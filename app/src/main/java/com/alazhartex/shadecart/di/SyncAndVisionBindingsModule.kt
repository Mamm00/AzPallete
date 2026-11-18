package com.alazhartex.shadecart.di

import com.alazhartex.shadecart.data.prefs.ThemePreferencesRepository
import com.alazhartex.shadecart.data.repo.ShadeCartRepository
import com.alazhartex.shadecart.data.repo.SyncWorkScheduler
import com.alazhartex.shadecart.data.sync.RetrofitShadeCartSyncAdapter
import com.alazhartex.shadecart.data.sync.ShadeCartSyncAdapter
import com.alazhartex.shadecart.data.sync.WorkManagerSyncScheduler
import com.alazhartex.shadecart.domain.repository.ShadeCartRepositoryContract
import com.alazhartex.shadecart.domain.repository.SyncDebugController
import com.alazhartex.shadecart.domain.repository.ThemePreferencesContract
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger-Hilt abstract bindings connecting domain repository contracts and sync gateways to their
 * concrete data-layer implementations (`Room`, `DataStore`, `Retrofit + OkHttp`, `WorkManager`)
 * (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 10.1 & Chapter 10.2).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncAndVisionBindingsModule {

    /** Binds [ShadeCartRepository] to the pure Kotlin [ShadeCartRepositoryContract]. */
    @Binds
    @Singleton
    abstract fun bindShadeCartRepository(impl: ShadeCartRepository): ShadeCartRepositoryContract

    /** Binds [ThemePreferencesRepository] to [ThemePreferencesContract]. */
    @Binds
    @Singleton
    abstract fun bindThemePreferences(impl: ThemePreferencesRepository): ThemePreferencesContract

    /** Binds [RetrofitShadeCartSyncAdapter] to [ShadeCartSyncAdapter]. */
    @Binds
    @Singleton
    abstract fun bindSyncAdapter(impl: RetrofitShadeCartSyncAdapter): ShadeCartSyncAdapter

    /** Binds [RetrofitShadeCartSyncAdapter] to [SyncDebugController] for debug conflict drills. */
    @Binds
    @Singleton
    abstract fun bindSyncDebugController(impl: RetrofitShadeCartSyncAdapter): SyncDebugController

    /** Binds [WorkManagerSyncScheduler] to [SyncWorkScheduler]. */
    @Binds
    @Singleton
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncWorkScheduler
}
