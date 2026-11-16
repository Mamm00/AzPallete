package com.alazhartex.shadecart.di

import com.alazhartex.shadecart.data.repo.SyncWorkScheduler
import com.alazhartex.shadecart.data.sync.ShadeCartSyncAdapter
import com.alazhartex.shadecart.data.sync.StubShadeCartSyncAdapter
import com.alazhartex.shadecart.data.sync.WorkManagerSyncScheduler
import com.alazhartex.shadecart.domain.vision.MlKitSubjectSegmentationGateway
import com.alazhartex.shadecart.domain.vision.MlKitTextRecognitionGateway
import com.alazhartex.shadecart.domain.vision.SubjectSegmentationGateway
import com.alazhartex.shadecart.domain.vision.TextRecognitionGateway
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

    @Binds
    @Singleton
    abstract fun bindTextRecognitionGateway(impl: MlKitTextRecognitionGateway): TextRecognitionGateway

    @Binds
    @Singleton
    abstract fun bindSubjectSegmentationGateway(
        impl: MlKitSubjectSegmentationGateway,
    ): SubjectSegmentationGateway
}
