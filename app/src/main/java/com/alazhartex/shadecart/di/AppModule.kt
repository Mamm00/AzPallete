package com.alazhartex.shadecart.di

import android.content.Context
import com.alazhartex.shadecart.data.repo.ShadeCartRepository
import com.alazhartex.shadecart.data.repo.ShadeCartStore
import com.alazhartex.shadecart.data.repo.SyncWorkScheduler
import com.alazhartex.shadecart.data.sync.ShadeCartSyncAdapter
import com.alazhartex.shadecart.domain.imaging.BitmapProcessor
import com.alazhartex.shadecart.domain.imaging.PinkedSwatchSampler
import com.alazhartex.shadecart.domain.imaging.TwoColumnSwatchDetector
import com.alazhartex.shadecart.domain.vision.MlKitSubjectSegmentationGateway
import com.alazhartex.shadecart.domain.vision.MlKitTextRecognitionGateway
import com.alazhartex.shadecart.domain.vision.ShadeCartOcrParser
import com.alazhartex.shadecart.domain.vision.SubjectSegmentationGateway
import com.alazhartex.shadecart.domain.vision.TextRecognitionGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideBitmapProcessor(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): BitmapProcessor = BitmapProcessor(
        context = context,
        ioDispatcher = ioDispatcher,
    )

    @Provides
    @Singleton
    fun providePinkedSwatchSampler(
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    ): PinkedSwatchSampler = PinkedSwatchSampler(defaultDispatcher = defaultDispatcher)

    @Provides
    @Singleton
    fun provideTwoColumnSwatchDetector(
        sampler: PinkedSwatchSampler,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    ): TwoColumnSwatchDetector = TwoColumnSwatchDetector(
        sampler = sampler,
        defaultDispatcher = defaultDispatcher,
    )

    @Provides
    @Singleton
    fun provideShadeCartOcrParser(): ShadeCartOcrParser = ShadeCartOcrParser()

    @Provides
    @Singleton
    fun provideTextRecognitionGateway(
        parser: ShadeCartOcrParser,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): TextRecognitionGateway = MlKitTextRecognitionGateway(
        parser = parser,
        ioDispatcher = ioDispatcher,
    )

    @Provides
    @Singleton
    fun provideSubjectSegmentationGateway(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): SubjectSegmentationGateway = MlKitSubjectSegmentationGateway(
        context = context,
        ioDispatcher = ioDispatcher,
    )

    @Provides
    @Singleton
    fun provideShadeCartRepository(
        store: ShadeCartStore,
        syncAdapter: ShadeCartSyncAdapter,
        syncScheduler: SyncWorkScheduler,
        json: Json,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): ShadeCartRepository = ShadeCartRepository(
        store = store,
        syncAdapter = syncAdapter,
        syncScheduler = syncScheduler,
        json = json,
        ioDispatcher = ioDispatcher,
    )
}
