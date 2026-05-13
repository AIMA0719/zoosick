package com.myinfocar.aicoachstock.ui.stockdetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 가격 라인 차트 — Compose Canvas로 직접 그리기 (외부 차트 라이브러리 X).
 *
 * prices: 시간 순 (오래된 → 최근). 가격이 양수면 정상, 음수/NaN은 skip.
 * 색상: 마지막 가격이 첫 가격보다 높으면 녹색, 낮으면 빨강.
 */
@Composable
fun PriceLineChart(
    prices: List<Double>,
    modifier: Modifier = Modifier,
) {
    val cleaned = prices.filter { it.isFinite() && it > 0 }
    if (cleaned.size < 2) {
        Box(modifier = modifier.fillMaxWidth().height(160.dp))
        return
    }
    val first = cleaned.first()
    val last = cleaned.last()
    // 한국 관례: 상승=빨강, 하락=파랑.
    val color = if (last >= first)
        com.myinfocar.aicoachstock.ui.common.KrUpRed
    else
        com.myinfocar.aicoachstock.ui.common.KrDownBlue
    val min = cleaned.min()
    val max = cleaned.max()
    val range = (max - min).takeIf { it > 0 } ?: 1.0

    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        val padX = 8f
        val padY = 16f
        val w = size.width - padX * 2
        val h = size.height - padY * 2
        val step = if (cleaned.size > 1) w / (cleaned.size - 1) else w

        val path = Path()
        cleaned.forEachIndexed { i, p ->
            val x = padX + i * step
            val y = padY + h - ((p - min) / range).toFloat() * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 4f, cap = StrokeCap.Round))

        // 시작/끝 점 작은 원.
        val startY = padY + h - ((first - min) / range).toFloat() * h
        val endY = padY + h - ((last - min) / range).toFloat() * h
        drawCircle(color = color, radius = 6f, center = Offset(padX, startY))
        drawCircle(color = color, radius = 6f, center = Offset(padX + (cleaned.size - 1) * step, endY))
    }
}
