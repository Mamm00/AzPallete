package com.alazhartex.shadecart.di

import android.content.Context
import androidx.room.Room
import com.alazhartex.shadecart.data.local.AlAzharTexDatabase
import com.alazhartex.shadecart.data.local.CutLogDao
import com.alazhartex.shadecart.data.local.DyeLotDao
import com.alazhartex.shadecart.data.local.FabricTypeDao
import com.alazhartex.shadecart.data.local.PriceTierDao
import com.alazhartex.shadecart.data.local.ReservationDao
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
    )
        .addMigrations(AlAzharTexDatabase.MIGRATION_1_2)
        .build()

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

    @Provides
    fun provideDyeLotDao(db: AlAzharTexDatabase): DyeLotDao = db.dyeLotDao()

    @Provides
    fun provideReservationDao(db: AlAzharTexDatabase): ReservationDao = db.reservationDao()

    @Provides
    fun provideCutLogDao(db: AlAzharTexDatabase): CutLogDao = db.cutLogDao()

    @Provides
    fun providePriceTierDao(db: AlAzharTexDatabase): PriceTierDao = db.priceTierDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StoreBindingsModule {
    @Binds
    @Singleton
    abstract fun bindShadeCartStore(impl: RoomShadeCartStore): ShadeCartStore
}
