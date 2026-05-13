package com.myinfocar.aicoachstock.domain.model

import java.time.Instant

/** 관심종목 항목. */
data class WatchListItem(
    val id: String,
    val ticker: String,
    val note: String?,
    val addedAt: Instant,
    val orderIndex: Int,
)
