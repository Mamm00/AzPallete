package com.alazhartex.shadecart.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Retrofit REST service interface for synchronizing Al AzharTex Digital Shade Carts with a
 * remote mill inventory backend.
 *
 * Uses Kotlin `suspend` functions and `retrofit2.Response` wrappers so HTTP status codes
 * (including `409 Conflict`) map explicitly to structured sync results
 * (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 8.1, 8.2, & 10.2).
 */
interface ShadeCartApiService {

    /** Verifies backend reachability before draining queued shade cart updates. */
    @GET("health")
    suspend fun checkHealth(): Response<Unit>

    /** Fetches the latest authoritative remote snapshot for [cartId]. */
    @GET("carts/{cartId}")
    suspend fun getCart(
        @Path("cartId") cartId: String,
    ): Response<RemoteShadeCartDto>

    /**
     * Pushes a local shade cart revision with an optimistic `If-Match` version header.
     * Returns `200 OK` on acceptance or `409 Conflict` with remote snapshot when stale.
     */
    @PUT("carts/{cartId}")
    suspend fun pushCart(
        @Path("cartId") cartId: String,
        @Header("If-Match") expectedVersion: Long,
        @Body request: RemotePushRequestDto,
    ): Response<RemotePushResponseDto>
}
