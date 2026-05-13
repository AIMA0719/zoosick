package com.myinfocar.aicoachstock.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

/**
 * 한국 증권 관례 색상 — 상승은 빨강, 하락은 파랑.
 * 토스/한투/키움 모두 동일. 서양 관례(상승=녹색)는 쓰지 않음.
 *
 * Why: HoldingsScreen/TradeListScreen이 과거 서양 관례를 따라서 같은 사용자가
 * 화면에 따라 손익 색이 반대로 보였음. 단일 컨벤션으로 통일.
 */
val KrUpRed: Color = Color(0xFFC62828)
val KrDownBlue: Color = Color(0xFF1565C0)

/**
 * 손익/등락 색상. value > 0이면 빨강(상승), < 0이면 파랑(하락), 0이면 onSurface.
 */
@Composable
@ReadOnlyComposable
fun pnlColor(value: Double?): Color = when {
    value == null -> MaterialTheme.colorScheme.onSurface
    value > 0 -> KrUpRed
    value < 0 -> KrDownBlue
    else -> MaterialTheme.colorScheme.onSurface
}
