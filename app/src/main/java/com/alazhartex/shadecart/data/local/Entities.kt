package com.alazhartex.shadecart.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

object SyncStatus {
    const val PENDING = "PENDING"
    const val SYNCED = "SYNCED"
    const val CONFLICT = "CONFLICT"
    const val FAILED = "FAILED"
}

object RollStatus {
    const val IN_STOCK = "IN_STOCK"
    const val LOW_STOCK = "LOW_STOCK"
    const val RESERVED = "RESERVED"
}

@Entity(
    tableName = "fabric_types",
    indices = [Index(value = ["code"], unique = true)],
)
data class FabricType(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "code")
    val code: String,
    @ColumnInfo(name = "weight_gsm")
    val weightGsm: Int,
    @ColumnInfo(name = "width_cm")
    val widthCm: Int,
    @ColumnInfo(name = "version")
    val version: Long = 1L,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "rolls",
    foreignKeys = [
        ForeignKey(
            entity = FabricType::class,
            parentColumns = ["id"],
            childColumns = ["fabric_type_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["fabric_type_id"]),
        Index(value = ["roll_code"], unique = true),
    ],
)
data class Roll(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "roll_code")
    val rollCode: String,
    @ColumnInfo(name = "fabric_type_id")
    val fabricTypeId: String,
    @ColumnInfo(name = "shade_label")
    val shadeLabel: String,
    @ColumnInfo(name = "color_name")
    val colorName: String,
    @ColumnInfo(name = "color_hex")
    val colorHex: String,
    @ColumnInfo(name = "warehouse_shed")
    val warehouseShed: String,
    @ColumnInfo(name = "length_meters")
    val lengthMeters: Double,
    @ColumnInfo(name = "status")
    val status: String = RollStatus.IN_STOCK,
    @ColumnInfo(name = "version")
    val version: Long = 1L,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "shade_carts",
    foreignKeys = [
        ForeignKey(
            entity = FabricType::class,
            parentColumns = ["id"],
            childColumns = ["fabric_type_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["fabric_type_id"])],
)
data class ShadeCartEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "fabric_type_id")
    val fabricTypeId: String?,
    @ColumnInfo(name = "fabric_name")
    val fabricName: String,
    @ColumnInfo(name = "fabric_code")
    val fabricCode: String,
    @ColumnInfo(name = "weight_gsm")
    val weightGsm: Int,
    @ColumnInfo(name = "width_cm")
    val widthCm: Int,
    @ColumnInfo(name = "original_image_path")
    val originalImagePath: String?,
    @ColumnInfo(name = "edited_image_path")
    val editedImagePath: String?,
    @ColumnInfo(name = "edit_stack_json")
    val editStackJson: String,
    @ColumnInfo(name = "header_ocr_raw")
    val headerOcrRaw: String = "",
    @ColumnInfo(name = "sync_status")
    val syncStatus: String = SyncStatus.PENDING,
    @ColumnInfo(name = "version")
    val version: Long = 1L,
    @ColumnInfo(name = "last_synced_version")
    val lastSyncedVersion: Long = 0L,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "swatches",
    foreignKeys = [
        ForeignKey(
            entity = ShadeCartEntity::class,
            parentColumns = ["id"],
            childColumns = ["shade_cart_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FabricType::class,
            parentColumns = ["id"],
            childColumns = ["fabric_type_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = Roll::class,
            parentColumns = ["id"],
            childColumns = ["roll_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["shade_cart_id"]),
        Index(value = ["fabric_type_id"]),
        Index(value = ["roll_id"]),
    ],
)
data class SwatchEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "shade_cart_id")
    val shadeCartId: String,
    @ColumnInfo(name = "fabric_type_id")
    val fabricTypeId: String,
    @ColumnInfo(name = "roll_id")
    val rollId: String? = null,
    @ColumnInfo(name = "swatch_number")
    val swatchNumber: String,
    @ColumnInfo(name = "color_hex")
    val colorHex: String,
    @ColumnInfo(name = "color_name")
    val colorName: String = "",
    @ColumnInfo(name = "position_order")
    val positionOrder: Int,
    @ColumnInfo(name = "bbox_left")
    val bboxLeft: Float = 0f,
    @ColumnInfo(name = "bbox_top")
    val bboxTop: Float = 0f,
    @ColumnInfo(name = "bbox_right")
    val bboxRight: Float = 0f,
    @ColumnInfo(name = "bbox_bottom")
    val bboxBottom: Float = 0f,
    @ColumnInfo(name = "is_overridden")
    val isOverridden: Boolean = false,
    @ColumnInfo(name = "version")
    val version: Long = 1L,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["entity_id"]),
        Index(value = ["status"]),
    ],
)
data class SyncQueueEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "entity_type")
    val entityType: String,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "operation")
    val operation: String,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    @ColumnInfo(name = "base_version")
    val baseVersion: Long,
    @ColumnInfo(name = "status")
    val status: String = SyncStatus.PENDING,
    @ColumnInfo(name = "conflict_remote_json")
    val conflictRemoteJson: String? = null,
    @ColumnInfo(name = "conflict_remote_version")
    val conflictRemoteVersion: Long? = null,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,
    @ColumnInfo(name = "last_error")
    val lastError: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)

data class SwatchWithRoll(
    @Embedded
    val swatch: SwatchEntity,
    @Relation(
        parentColumn = "roll_id",
        entityColumn = "id",
    )
    val roll: Roll?,
)

data class ShadeCartWithDetails(
    @Embedded
    val cart: ShadeCartEntity,
    @Relation(
        parentColumn = "fabric_type_id",
        entityColumn = "id",
    )
    val fabricType: FabricType?,
    @Relation(
        entity = SwatchEntity::class,
        parentColumn = "id",
        entityColumn = "shade_cart_id",
    )
    val swatches: List<SwatchWithRoll>,
)
