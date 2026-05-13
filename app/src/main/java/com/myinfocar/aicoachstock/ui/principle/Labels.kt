package com.myinfocar.aicoachstock.ui.principle

import com.myinfocar.aicoachstock.domain.model.PrincipleCategory

fun PrincipleCategory.label(): String = when (this) {
    PrincipleCategory.ENTRY -> "진입"
    PrincipleCategory.EXIT -> "청산"
    PrincipleCategory.RISK -> "자금관리"
    PrincipleCategory.PSYCHE -> "심리"
}
