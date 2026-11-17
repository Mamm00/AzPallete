package com.alazhartex.shadecart.data.sync

import com.alazhartex.shadecart.data.remote.ShadeCartApiService
import com.alazhartex.shadecart.data.repo.ShadeCartSnapshot
import com.alazhartex.shadecart.data.repo.SwatchDraft
import com.google.gson.GsonBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Verifies [RetrofitShadeCartSyncAdapter] HTTP transport, `If-Match` optimistic locking headers,
 * Gson DTO serialization, 409 Conflict parsing, and transient network fallback using OkHttp
 * [MockWebServer] (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 8.1, 8.2, & 11.3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RetrofitShadeCartSyncAdapterTest {

    private lateinit var server: MockWebServer
    private lateinit var adapter: RetrofitShadeCartSyncAdapter
    private val gson = GsonBuilder().create()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        val apiService = retrofit.create(ShadeCartApiService::class.java)
        adapter = RetrofitShadeCartSyncAdapter(
            apiService = apiService,
            stubAdapter = StubShadeCartSyncAdapter(),
            gson = gson,
            syncBaseUrl = server.url("/api/v1/").toString(),
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `pushCart sends If-Match header and maps 200 OK to Accepted`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"cart_id":"cart-1","accepted_version":4}"""),
        )

        val result = adapter.pushCart(sampleSnapshot(), expectedRemoteVersion = 3L)

        assertTrue(result is SyncPushResult.Accepted)
        assertEquals(4L, (result as SyncPushResult.Accepted).newRemoteVersion)

        val recorded = server.takeRequest()
        assertEquals("PUT", recorded.method)
        assertEquals("/api/v1/carts/cart-1", recorded.path)
        assertEquals("3", recorded.getHeader("If-Match"))
        val requestBody = recorded.body.readUtf8()
        assertTrue(requestBody.contains("Silk 180"))
        assertTrue(requestBody.contains("#102A71"))
    }

    @Test
    fun `pushCart parses HTTP 409 remote snapshot into VersionConflict`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setBody(
                    """
                    {
                      "cart_id": "cart-1",
                      "remote_cart": {
                        "id": "cart-1",
                        "fabric_type_id": "fab-silk-180",
                        "fabric_name": "Silk 180 Mill Rev",
                        "fabric_code": "SLK-180",
                        "weight_gsm": 180,
                        "width_cm": 150,
                        "edit_stack_json": "{}",
                        "version": 7,
                        "swatches": [
                          {
                            "id": "sw-5",
                            "fabric_type_id": "fab-silk-180",
                            "swatch_number": "5",
                            "color_hex": "#0F5132",
                            "color_name": "Azhar Emerald",
                            "position_order": 0,
                            "version": 3
                          }
                        ],
                        "updated_at": 1700000000000
                      }
                    }
                    """.trimIndent(),
                ),
        )

        val result = adapter.pushCart(sampleSnapshot(), expectedRemoteVersion = 3L)

        assertTrue(result is SyncPushResult.VersionConflict)
        val conflict = result as SyncPushResult.VersionConflict
        assertEquals("Silk 180 Mill Rev", conflict.remoteSnapshot.fabricName)
        assertEquals(7L, conflict.remoteSnapshot.version)
        assertEquals("#0F5132", conflict.remoteSnapshot.swatches.first().colorHex)
    }

    @Test
    fun `pushCart maps HTTP 503 to TransientFailure without throwing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("Maintenance"))

        val result = adapter.pushCart(sampleSnapshot(), expectedRemoteVersion = 3L)

        assertTrue(result is SyncPushResult.TransientFailure)
        assertTrue((result as SyncPushResult.TransientFailure).message.contains("503"))
    }

    @Test
    fun `pullCart deserializes RemoteShadeCartDto into ShadeCartSnapshot`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "id": "cart-1",
                      "fabric_type_id": "fab-silk-180",
                      "fabric_name": "Silk 180",
                      "fabric_code": "SLK-180",
                      "weight_gsm": 180,
                      "width_cm": 150,
                      "version": 5,
                      "swatches": []
                    }
                    """.trimIndent(),
                ),
        )

        val snapshot = adapter.pullCart("cart-1")
        assertNotNull(snapshot)
        assertEquals("SLK-180", snapshot!!.fabricCode)
        assertEquals(5L, snapshot.version)
    }

    @Test
    fun `checkConnection reflects health endpoint HTTP status`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        assertTrue(adapter.checkConnection())

        server.enqueue(MockResponse().setResponseCode(500))
        assertFalse(adapter.checkConnection())
    }

    private fun sampleSnapshot() = ShadeCartSnapshot(
        id = "cart-1",
        fabricTypeId = "fab-silk-180",
        fabricName = "Silk 180",
        fabricCode = "SLK-180",
        weightGsm = 180,
        widthCm = 150,
        originalImagePath = null,
        editedImagePath = null,
        editStackJson = "{}",
        headerOcrRaw = "Silk 180",
        version = 4L,
        swatches = listOf(
            SwatchDraft(
                id = "sw-5",
                fabricTypeId = "fab-silk-180",
                swatchNumber = "5",
                colorHex = "#102A71",
                colorName = "Azhar Navy",
                positionOrder = 0,
            ),
        ),
        updatedAt = 1_700_000_000_000L,
    )
}
