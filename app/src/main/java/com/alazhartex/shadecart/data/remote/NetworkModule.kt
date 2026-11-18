package com.alazhartex.shadecart.data.remote

import com.alazhartex.shadecart.BuildConfig
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt qualifier marking the configured remote sync base URL (`BuildConfig.SYNC_BASE_URL`).
 *
 * When blank (default offline-first build), the sync adapter operates strictly against the local
 * offline Room queue + deterministic stub without attempting external network calls.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SyncBaseUrl

/**
 * Dagger-Hilt module provisioning Retrofit, OkHttp, HttpLoggingInterceptor, and Gson converter
 * instances as application singletons.
 *
 * Enforces explicit timeouts and debug-only HTTP logging per security and networking best practices
 * (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 8.1, 8.2, 10.2, & 12.1).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TIMEOUT_SECONDS = 15L
    private const val OFFLINE_PLACEHOLDER_BASE_URL = "https://offline.alazhartex.invalid/api/v1/"

    /** Provides the configured remote sync endpoint URL (empty string for offline-first mode). */
    @Provides
    @SyncBaseUrl
    fun provideSyncBaseUrl(): String = BuildConfig.SYNC_BASE_URL.trim()

    /** Provides a singleton [Gson] serializer instance for Retrofit DTO conversion. */
    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    /**
     * Provides an OkHttp [HttpLoggingInterceptor] gated on [BuildConfig.DEBUG] so sensitive
     * payload headers/bodies are never logged in release builds (Pyre, Chapter 8.2 & 12.1).
     */
    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

    /**
     * Provides a configured [OkHttpClient] with 15-second connect/read/write timeouts
     * (Pyre, Chapter 8.2 Best Practice #1).
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    /**
     * Provides the singleton [Retrofit] instance wired with [GsonConverterFactory].
     * Uses [OFFLINE_PLACEHOLDER_BASE_URL] when [syncBaseUrl] is unconfigured so Retrofit
     * creation never throws on an empty base URL (Pyre, Chapter 8.1 & 10.2).
     */
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson,
        @SyncBaseUrl syncBaseUrl: String,
    ): Retrofit {
        val resolvedBaseUrl = if (syncBaseUrl.isBlank()) {
            OFFLINE_PLACEHOLDER_BASE_URL
        } else if (syncBaseUrl.endsWith("/")) {
            syncBaseUrl
        } else {
            "$syncBaseUrl/"
        }
        return Retrofit.Builder()
            .baseUrl(resolvedBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    /** Provides the singleton [ShadeCartApiService] REST client. */
    @Provides
    @Singleton
    fun provideShadeCartApiService(retrofit: Retrofit): ShadeCartApiService =
        retrofit.create(ShadeCartApiService::class.java)
}
