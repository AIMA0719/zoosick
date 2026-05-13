package com.myinfocar.aicoachstock.data.local.db.principle

import com.myinfocar.aicoachstock.domain.model.PrincipleCategory
import com.myinfocar.aicoachstock.domain.model.TradingPrinciple
import java.time.Instant

fun PrincipleEntity.toDomain(): TradingPrinciple = TradingPrinciple(
    id = id,
    category = PrincipleCategory.valueOf(category),
    ruleText = ruleText,
    weight = weight,
    isActive = isActive,
    orderIndex = orderIndex,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun TradingPrinciple.toEntity(): PrincipleEntity = PrincipleEntity(
    id = id,
    category = category.name,
    ruleText = ruleText,
    weight = weight,
    isActive = isActive,
    orderIndex = orderIndex,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)
