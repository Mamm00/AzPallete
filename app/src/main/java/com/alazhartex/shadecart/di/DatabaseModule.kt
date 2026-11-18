package com.alazhartex.shadecart.di

import android.content.Context
import androidx.room.Room
import com.alazhartex.shadecart.data.local.AlAzharTexDatabase
import com.alazhartex.shadecart.data.local.FabricTypeDao
import com.alazhartex.shadecart.data.local.RollDao
import com.alazhartex.shadecart.data.local.ShadeCartDao
import com.alazhartex.shadecart.data.local.SwatchDao
import com.alazhartex.shadecart.data.local.SyncQueueDao
import com.alazhartex.shadecart.data.repo.RoomShadeCartStore
import com.alazhartex.shadecart.data.repo.ShadeCartStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger-Hilt database module provisioning the singleton Room database and its DAOs
 * (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 7.4 & Chapter 10.2).
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AlAzharTexDatabase = Room.databaseBuilder(
        context,
        AlAzharTexDatabase::class.java,
        "alazhartex_shadecart.db",
    ).build()

    @Provides
    fun provideFabricTypeDao(db: AlAzharTexDatabase): FabricTypeDao = db.fabricTypeDao()

    @Provides
    fun provideRollDao(db: AlAzharTexDatabase): RollDao = db.rollDao()

    @Provides
    fun provideShadeCartDao(db: AlAzharTexDatabase): ShadeCartDao = db.shadeCartDao()

    @Provides
    fun provideSwatchDao(db: AlAzharTexDatabase): SwatchDao = db.swatchDao()

    @Provides
    fun provideSyncQueueDao(db: AlAzharTexDatabase): SyncQueueDao = db.syncQueueDao()
}

/**
 * Hilt binding associating the Room-backed [RoomShadeCartStore] with [ShadeCartStore]
 * (Pyre, Chapter 10.2).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class StoreBindingsModule {
    @Binds
    @Singleton
    abstract fun bindShadeCartStore(impl: RoomShadeCartStore): ShadeCartStore
}
