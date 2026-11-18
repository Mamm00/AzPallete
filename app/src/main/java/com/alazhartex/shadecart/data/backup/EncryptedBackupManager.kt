package com.alazhartex.shadecart.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.alazhartex.shadecart.data.local.AlAzharTexDatabase
import com.alazhartex.shadecart.data.local.CutLogEntity
import com.alazhartex.shadecart.data.local.DyeLotEntity
import com.alazhartex.shadecart.data.local.FabricType
import com.alazhartex.shadecart.data.local.PriceTierEntity
import com.alazhartex.shadecart.data.local.ReservationEntity
import com.alazhartex.shadecart.data.local.Roll
import com.alazhartex.shadecart.data.local.ShadeCartEntity
import com.alazhartex.shadecart.data.local.SwatchEntity
import com.alazhartex.shadecart.di.IoDispatcher
import com.alazhartex.shadecart.domain.export.ShadeCartFormatters
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupManifest(
    val formatVersion: Int = 2,
    val createdAtMillis: Long,
    val cartCount: Int,
    val rollCount: Int,
    val payloadSha256: String,
)

@Serializable
data class BackupFabricTypeDto(
    val id: String,
    val name: String,
    val code: String,
    val weightGsm: Int,
    val widthCm: Int,
    val version: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupRollDto(
    val id: String,
    val rollCode: String,
    val fabricTypeId: String,
    val shadeLabel: String,
    val colorName: String,
    val colorHex: String,
    val warehouseShed: String,
    val lengthMeters: Double,
    val availableMeters: Double,
    val lotCode: String,
    val lowStockThresholdMeters: Double,
    val qrPayload: String,
    val status: String,
    val version: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupSwatchDto(
    val id: String,
    val shadeCartId: String,
    val fabricTypeId: String,
    val rollId: String?,
    val swatchNumber: String,
    val colorHex: String,
    val colorName: String,
    val positionOrder: Int,
    val bboxLeft: Float,
    val bboxTop: Float,
    val bboxRight: Float,
    val bboxBottom: Float,
    val isOverridden: Boolean,
    val labL: Double,
    val labA: Double,
    val labB: Double,
    val weaveTag: String,
    val sheenScore: Float,
    val pricePerMeter: Double?,
    val currencyCode: String,
    val version: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupCartDto(
    val id: String,
    val fabricTypeId: String?,
    val fabricName: String,
    val fabricCode: String,
    val weightGsm: Int,
    val widthCm: Int,
    val originalImagePath: String?,
    val editedImagePath: String?,
    val editStackJson: String,
    val headerOcrRaw: String,
    val calibrationProfileId: String?,
    val ambientLux: Float,
    val qrDeepLink: String,
    val isArchived: Boolean,
    val syncStatus: String,
    val version: Long,
    val lastSyncedVersion: Long,
    val updatedAt: Long,
    val swatches: List<BackupSwatchDto>,
)

@Serializable
data class BackupLotDto(
    val id: String,
    val rollId: String,
    val lotCode: String,
    val dyeDate: Long,
    val masterDeltaE: Double,
    val operatorName: String,
    val notes: String,
    val version: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupReservationDto(
    val id: String,
    val rollId: String,
    val swatchId: String,
    val metersReserved: Double,
    val reservedBy: String,
    val note: String,
    val status: String,
    val createdAt: Long,
    val expiresAt: Long,
)

@Serializable
data class BackupCutLogDto(
    val id: String,
    val rollId: String,
    val swatchId: String,
    val metersCut: Double,
    val pieceCount: Int,
    val operatorName: String,
    val createdAt: Long,
)

@Serializable
data class BackupPriceTierDto(
    val id: String,
    val fabricTypeId: String,
    val tierName: String,
    val minMeters: Double,
    val pricePerMeter: Double,
    val currencyCode: String,
    val updatedAt: Long,
)

@Serializable
data class BackupDatabasePayload(
    val fabrics: List<BackupFabricTypeDto>,
    val rolls: List<BackupRollDto>,
    val carts: List<BackupCartDto>,
    val lots: List<BackupLotDto>,
    val reservations: List<BackupReservationDto>,
    val cutLogs: List<BackupCutLogDto>,
    val priceTiers: List<BackupPriceTierDto>,
)

data class BackupExportSummary(
    val bytesWritten: Long,
    val cartCount: Int,
    val rollCount: Int,
    val timestampMillis: Long,
)

data class BackupRestoreSummary(
    val cartCount: Int,
    val rollCount: Int,
    val restoredAtMillis: Long,
)

/**
 * Pure JVM AES-256-GCM + PBKDF2WithHmacSHA256 + ZIP packager/unpackager (C5a) with deterministic
 * checksum verification so JVM unit tests verify full backup round-trip without Android dependencies.
 */
object PureBackupCrypto {
    private const val MAGIC = "AZPBK02"
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val KEY_BITS = 256
    private const val ITERATIONS = 65_536
    private const val GCM_TAG_BITS = 128

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun sealToZipBytes(payload: BackupDatabasePayload, passphrase: String, nowMillis: Long = System.currentTimeMillis()): ByteArray {
        require(passphrase.length >= 4) { "Passphrase must be at least 4 characters." }
        val payloadBytes = json.encodeToString(payload).toByteArray(StandardCharsets.UTF_8)
        val checksum = ShadeCartFormatters.sha256Hex(payloadBytes)
        val manifest = BackupManifest(
            createdAtMillis = nowMillis,
            cartCount = payload.carts.size,
            rollCount = payload.rolls.size,
            payloadSha256 = checksum,
        )
        val manifestBytes = json.encodeToString(manifest).toByteArray(StandardCharsets.UTF_8)

        val rng = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rng.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rng.nextBytes(it) }
        val secretKey = deriveKey(passphrase, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(payloadBytes)

        val envelope = ByteArrayOutputStream().apply {
            write(MAGIC.toByteArray(StandardCharsets.US_ASCII))
            write(salt)
            write(iv)
            write(encrypted)
        }.toByteArray()

        val zipOut = ByteArrayOutputStream()
        ZipOutputStream(zipOut).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestBytes)
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("database.enc"))
            zos.write(envelope)
            zos.closeEntry()
        }
        return zipOut.toByteArray()
    }

    fun openFromZipBytes(zipBytes: ByteArray, passphrase: String): Pair<BackupManifest, BackupDatabasePayload> {
        var manifestBytes: ByteArray? = null
        var envelopeBytes: ByteArray? = null

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "manifest.json" -> manifestBytes = zis.readBytes()
                    "database.enc" -> envelopeBytes = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val manifest = json.decodeFromString<BackupManifest>(
            (manifestBytes ?: error("Missing manifest.json in backup archive")).toString(StandardCharsets.UTF_8),
        )
        val envelope = envelopeBytes ?: error("Missing database.enc in backup archive")
        val magicBytes = MAGIC.toByteArray(StandardCharsets.US_ASCII)
        require(envelope.size > magicBytes.size + SALT_LEN + IV_LEN) { "Corrupted backup header." }
        val header = envelope.copyOfRange(0, magicBytes.size)
        require(header.contentEquals(magicBytes)) { "Unsupported backup file format." }

        val saltStart = magicBytes.size
        val ivStart = saltStart + SALT_LEN
        val cipherStart = ivStart + IV_LEN
        val salt = envelope.copyOfRange(saltStart, ivStart)
        val iv = envelope.copyOfRange(ivStart, cipherStart)
        val cipherText = envelope.copyOfRange(cipherStart, envelope.size)

        val secretKey = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plainBytes = cipher.doFinal(cipherText)

        val actualSha = ShadeCartFormatters.sha256Hex(plainBytes)
        require(actualSha == manifest.payloadSha256) { "Backup SHA-256 integrity check failed." }

        val payload = json.decodeFromString<BackupDatabasePayload>(plainBytes.toString(StandardCharsets.UTF_8))
        return manifest to payload
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}

/**
 * C5a: Android SAF URI backup/restore manager writing encrypted `.azpallete.zip` snapshots from Room
 * and restoring them atomically inside `AlAzharTexDatabase.withTransaction`.
 */
@Singleton
class EncryptedBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AlAzharTexDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun exportEncryptedZipToUri(targetUri: Uri, passphrase: String): Result<BackupExportSummary> =
        withContext(ioDispatcher) {
            runCatching {
                val now = System.currentTimeMillis()
                val fabrics = database.fabricTypeDao().getAll().map {
                    BackupFabricTypeDto(it.id, it.name, it.code, it.weightGsm, it.widthCm, it.version, it.updatedAt)
                }
                val rolls = database.rollDao().observeAll().first().map {
                    BackupRollDto(
                        id = it.id,
                        rollCode = it.rollCode,
                        fabricTypeId = it.fabricTypeId,
                        shadeLabel = it.shadeLabel,
                        colorName = it.colorName,
                        colorHex = it.colorHex,
                        warehouseShed = it.warehouseShed,
                        lengthMeters = it.lengthMeters,
                        availableMeters = it.availableMeters,
                        lotCode = it.lotCode,
                        lowStockThresholdMeters = it.lowStockThresholdMeters,
                        qrPayload = it.qrPayload,
                        status = it.status,
                        version = it.version,
                        updatedAt = it.updatedAt,
                    )
                }
                val carts = database.shadeCartDao().getAllWithDetailsIncludingArchived().map { full ->
                    val c = full.cart
                    BackupCartDto(
                        id = c.id,
                        fabricTypeId = c.fabricTypeId,
                        fabricName = c.fabricName,
                        fabricCode = c.fabricCode,
                        weightGsm = c.weightGsm,
                        widthCm = c.widthCm,
                        originalImagePath = c.originalImagePath,
                        editedImagePath = c.editedImagePath,
                        editStackJson = c.editStackJson,
                        headerOcrRaw = c.headerOcrRaw,
                        calibrationProfileId = c.calibrationProfileId,
                        ambientLux = c.ambientLux,
                        qrDeepLink = c.qrDeepLink,
                        isArchived = c.isArchived,
                        syncStatus = c.syncStatus,
                        version = c.version,
                        lastSyncedVersion = c.lastSyncedVersion,
                        updatedAt = c.updatedAt,
                        swatches = full.swatches.map { sr ->
                            val s = sr.swatch
                            BackupSwatchDto(
                                id = s.id,
                                shadeCartId = s.shadeCartId,
                                fabricTypeId = s.fabricTypeId,
                                rollId = s.rollId,
                                swatchNumber = s.swatchNumber,
                                colorHex = s.colorHex,
                                colorName = s.colorName,
                                positionOrder = s.positionOrder,
                                bboxLeft = s.bboxLeft,
                                bboxTop = s.bboxTop,
                                bboxRight = s.bboxRight,
                                bboxBottom = s.bboxBottom,
                                isOverridden = s.isOverridden,
                                labL = s.labL,
                                labA = s.labA,
                                labB = s.labB,
                                weaveTag = s.weaveTag,
                                sheenScore = s.sheenScore,
                                pricePerMeter = s.pricePerMeter,
                                currencyCode = s.currencyCode,
                                version = s.version,
                                updatedAt = s.updatedAt,
                            )
                        },
                    )
                }
                val lots = database.dyeLotDao().observeAll().first().map {
                    BackupLotDto(it.id, it.rollId, it.lotCode, it.dyeDate, it.masterDeltaE, it.operatorName, it.notes, it.version, it.updatedAt)
                }
                val reservations = database.reservationDao().observeAll().first().map {
                    BackupReservationDto(it.id, it.rollId, it.swatchId, it.metersReserved, it.reservedBy, it.note, it.status, it.createdAt, it.expiresAt)
                }
                val cutLogs = database.cutLogDao().observeAll().first().map {
                    BackupCutLogDto(it.id, it.rollId, it.swatchId, it.metersCut, it.pieceCount, it.operatorName, it.createdAt)
                }
                val priceTiers = database.priceTierDao().observeAll().first().map {
                    BackupPriceTierDto(it.id, it.fabricTypeId, it.tierName, it.minMeters, it.pricePerMeter, it.currencyCode, it.updatedAt)
                }

                val payload = BackupDatabasePayload(
                    fabrics = fabrics,
                    rolls = rolls,
                    carts = carts,
                    lots = lots,
                    reservations = reservations,
                    cutLogs = cutLogs,
                    priceTiers = priceTiers,
                )
                val zipBytes = PureBackupCrypto.sealToZipBytes(payload, passphrase, now)
                context.contentResolver.openOutputStream(targetUri)?.use { os ->
                    os.write(zipBytes)
                    os.flush()
                } ?: error("Cannot open SAF output stream for backup target.")

                BackupExportSummary(
                    bytesWritten = zipBytes.size.toLong(),
                    cartCount = carts.size,
                    rollCount = rolls.size,
                    timestampMillis = now,
                )
            }
        }

    suspend fun restoreFromEncryptedZipUri(sourceUri: Uri, passphrase: String): Result<BackupRestoreSummary> =
        withContext(ioDispatcher) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                    ?: error("Cannot read selected backup archive.")
                val (manifest, payload) = PureBackupCrypto.openFromZipBytes(bytes, passphrase)
                database.withTransaction {
                    database.fabricTypeDao().upsertAll(
                        payload.fabrics.map {
                            FabricType(it.id, it.name, it.code, it.weightGsm, it.widthCm, it.version, it.updatedAt)
                        },
                    )
                    database.rollDao().upsertAll(
                        payload.rolls.map {
                            Roll(
                                id = it.id,
                                rollCode = it.rollCode,
                                fabricTypeId = it.fabricTypeId,
                                shadeLabel = it.shadeLabel,
                                colorName = it.colorName,
                                colorHex = it.colorHex,
                                warehouseShed = it.warehouseShed,
                                lengthMeters = it.lengthMeters,
                                availableMeters = it.availableMeters,
                                lotCode = it.lotCode,
                                lowStockThresholdMeters = it.lowStockThresholdMeters,
                                qrPayload = it.qrPayload,
                                status = it.status,
                                version = it.version,
                                updatedAt = it.updatedAt,
                            )
                        },
                    )
                    for (cartDto in payload.carts) {
                        database.shadeCartDao().upsert(
                            ShadeCartEntity(
                                id = cartDto.id,
                                fabricTypeId = cartDto.fabricTypeId,
                                fabricName = cartDto.fabricName,
                                fabricCode = cartDto.fabricCode,
                                weightGsm = cartDto.weightGsm,
                                widthCm = cartDto.widthCm,
                                originalImagePath = cartDto.originalImagePath,
                                editedImagePath = cartDto.editedImagePath,
                                editStackJson = cartDto.editStackJson,
                                headerOcrRaw = cartDto.headerOcrRaw,
                                calibrationProfileId = cartDto.calibrationProfileId,
                                ambientLux = cartDto.ambientLux,
                                qrDeepLink = cartDto.qrDeepLink,
                                isArchived = cartDto.isArchived,
                                syncStatus = cartDto.syncStatus,
                                version = cartDto.version,
                                lastSyncedVersion = cartDto.lastSyncedVersion,
                                updatedAt = cartDto.updatedAt,
                            ),
                        )
                        database.swatchDao().deleteForCart(cartDto.id)
                        database.swatchDao().upsertAll(
                            cartDto.swatches.map { s ->
                                SwatchEntity(
                                    id = s.id,
                                    shadeCartId = s.shadeCartId,
                                    fabricTypeId = s.fabricTypeId,
                                    rollId = s.rollId,
                                    swatchNumber = s.swatchNumber,
                                    colorHex = s.colorHex,
                                    colorName = s.colorName,
                                    positionOrder = s.positionOrder,
                                    bboxLeft = s.bboxLeft,
                                    bboxTop = s.bboxTop,
                                    bboxRight = s.bboxRight,
                                    bboxBottom = s.bboxBottom,
                                    isOverridden = s.isOverridden,
                                    labL = s.labL,
                                    labA = s.labA,
                                    labB = s.labB,
                                    weaveTag = s.weaveTag,
                                    sheenScore = s.sheenScore,
                                    pricePerMeter = s.pricePerMeter,
                                    currencyCode = s.currencyCode,
                                    version = s.version,
                                    updatedAt = s.updatedAt,
                                )
                            },
                        )
                    }
                    database.dyeLotDao().upsertAll(
                        payload.lots.map {
                            DyeLotEntity(it.id, it.rollId, it.lotCode, it.dyeDate, it.masterDeltaE, it.operatorName, it.notes, it.version, it.updatedAt)
                        },
                    )
                    database.reservationDao().upsertAll(
                        payload.reservations.map {
                            ReservationEntity(it.id, it.rollId, it.swatchId, it.metersReserved, it.reservedBy, it.note, it.status, it.createdAt, it.expiresAt)
                        },
                    )
                    database.cutLogDao().upsertAll(
                        payload.cutLogs.map {
                            CutLogEntity(it.id, it.rollId, it.swatchId, it.metersCut, it.pieceCount, it.operatorName, it.createdAt)
                        },
                    )
                    database.priceTierDao().upsertAll(
                        payload.priceTiers.map {
                            PriceTierEntity(it.id, it.fabricTypeId, it.tierName, it.minMeters, it.pricePerMeter, it.currencyCode, it.updatedAt)
                        },
                    )
                }
                BackupRestoreSummary(
                    cartCount = manifest.cartCount,
                    rollCount = manifest.rollCount,
                    restoredAtMillis = System.currentTimeMillis(),
                )
            }
        }
}
