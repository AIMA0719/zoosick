package com.myinfocar.aicoachstock.data.local.db.order

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 주문 영구 로그 (Stage 16 신설).
 *
 * - status: PENDING/SUBMITTED/FILLED/PARTIAL/CANCELED/REJECTED
 * - krxOrderNo: 한투 응답 ODNO (송신 후 채워짐). 정정/취소 추적 자연 키.
 * - originOrderNo: 정정/취소 주문일 때 원주문 ODNO. 신규 주문이면 null.
 * - linkedPrincipleIdsJson: 진입 시 참고한 원칙 IDs를 JSON array 문자열로 저장.
 */
@Entity(
    tableName = "orders",
    indices = [
        Index(value = ["ticker"]),
        Index(value = ["status"]),
        Index(value = ["krxOrderNo"]),
    ],
)
data class OrderEntity(
    @PrimaryKey val id: String,
    val ticker: String,
    val market: String,         // Market.name
    val side: String,           // TradeSide.name
    val orderType: String,      // OrderType.name
    val qty: Int,
    val price: Double?,
    val filledQty: Int,
    val avgFillPrice: Double?,
    val status: String,         // OrderStatus.name
    val krxOrderNo: String?,
    val krxOrderOrgNo: String?,
    val originOrderNo: String?,
    val linkedPrincipleIdsJson: String,    // "[]" 또는 JSON array
    val createdAt: Long,        // epoch millis
    val submittedAt: Long?,
    val completedAt: Long?,
    val errorMessage: String?,
    val rawMsgCd: String?,
)
