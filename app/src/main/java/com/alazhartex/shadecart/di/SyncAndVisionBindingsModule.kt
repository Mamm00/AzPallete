package com.alazhartex.shadecart.di

import com.alazhartex.shadecart.data.repo.SyncWorkScheduler
import com.alazhartex.shadecart.data.sync.ShadeCartSyncAdapter
import com.alazhartex.shadecart.data.sync.StubShadeCartSyncAdapter
import com.alazhartex.shadecart.data.sync.WorkManagerSyncScheduler
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
    abstract fun bindSyncAdapter(impl: StubShadeCartSyncAdapter): ShadeCartSyncAdapter

    @Binds
    @Singleton
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncWorkScheduler
}
