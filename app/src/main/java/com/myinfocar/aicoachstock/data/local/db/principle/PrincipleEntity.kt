package com.myinfocar.aicoachstock.data.local.db.principle

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "principles")
data class PrincipleEntity(
    @PrimaryKey val id: String,
    val category: String, // PrincipleCategory.name
    val ruleText: String,
    val weight: Int,
    val isActive: Boolean,
    val orderIndex: Int,
    val createdAt: Long, // Instant.toEpochMilli
    val updatedAt: Long,
)
