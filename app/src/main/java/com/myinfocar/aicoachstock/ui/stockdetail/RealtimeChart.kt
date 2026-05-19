package com.myinfocar.aicoachstock.ui.stockdetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myinfocar.aicoachstock.domain.model.Candle
import com.myinfocar.aicoachstock.domain.model.ChartType
import com.myinfocar.aicoachstock.ui.common.KrDownBlue
import com.myinfocar.aicoachstock.ui.common.KrUpRed
import kotlin.math.max
import kotlin.math.min

/**
 * Stage 15 실시간 차트 통합 컴포넌트.
 *
 * - 한 Canvas 안에 캔들(또는 라인) + 이동평균선 + 거래량 + 크로스헤어.
 * - 영역 비율: 상단 70% 캔들+MA, 하단 25% 거래량, 사이 5% 갭.
 * - 한국 관례: 상승 빨강(KrUpRed), 하락 파랑(KrDownBlue), 동가 회색.
 *
 * candles: 시간 순(과거 → 최근).
 * chartType: LINE / CANDLE 토글.
 * maPeriods: 이동평균 기간 리스트 (기본 5/20/60). 빈 리스트면 미표시.
 * crosshairIndex / onCrosshairChange: 외부 state로 호이스팅. null이면 비표시.
 * height: 차트 전체 높이. 기본 240dp (토스 수준).
 */
@Composable
fun RealtimeChart(
    candles: List<Candle>,
    modifier: Modifier = Modifier,
    chartType: ChartType = ChartType.CANDLE,
    maPeriods: List<Int> = listOf(5, 20, 60),
    crosshairIndex: Int? = null,
    onCrosshairChange: ((Int?) -> Unit)? = null,
    height: Dp = 240.dp,
) {
    if (candles.size < 2) {
        Box(modifier = modifier.fillMaxWidth().height(height))
        return
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(candles.size) {
                val cb = onCrosshairChange ?: return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        cb(indexFromX(offset.x, size.width.toFloat(), candles.size))
                    },
                    onDrag = { change, _ ->
                        cb(indexFromX(change.position.x, size.width.toFloat(), candles.size))
                        change.consume()
                    },
                    onDragEnd = { cb(null) },
                    onDragCancel = { cb(null) },
                )
            },
    ) {
        val padX = 8f
        val padY = 8f
        val w = size.width - padX * 2
        val totalH = size.height - padY * 2
        val candleH = totalH * 0.70f
        val volumeH = totalH * 0.25f
        val candleBottom = padY + candleH
        val volumeTop = padY + totalH * 0.75f
        val volumeBottom = padY + totalH

        val priceMin = candles.minOf { it.low }
        val priceMax = candles.maxOf { it.high }
        val priceRange = (priceMax - priceMin).takeIf { it > 0 } ?: 1.0
        val volumeMax = candles.maxOf { it.volume }.coerceAtLeast(1L)

        val step = w / candles.size
        val barWidth = (step * 0.7f).coerceAtLeast(1f)

        fun yPrice(p: Double): Float =
            candleBottom - ((p - priceMin) / priceRange).toFloat() * candleH

        // 가로 그리드 5등분 (캔들 영역 안)
        val gridColor = Color(0x14191F28)
        for (i in 0..4) {
            val y = padY + candleH * i / 4f
            drawLine(gridColor, Offset(padX, y), Offset(padX + w, y), strokeWidth = 0.8f)
        }

        // 캔들 / 라인
        if (chartType == ChartType.LINE) {
            val path = Path()
            candles.forEachIndexed { i, c ->
                val x = padX + i * step + step / 2
                val y = yPrice(c.close)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val lineColor = if (candles.last().close >= candles.first().close) KrUpRed else KrDownBlue
            drawPath(path, lineColor, style = Stroke(width = 3f))
        } else {
            candles.forEachIndexed { i, c ->
                val x = padX + i * step + step / 2
                val openY = yPrice(c.open)
                val closeY = yPrice(c.close)
                val highY = yPrice(c.high)
                val lowY = yPrice(c.low)
                val color = candleColor(c)
                // 심지
                drawLine(color, Offset(x, highY), Offset(x, lowY), strokeWidth = 1.5f)
                // 박스
                val top = min(openY, closeY)
                val bottom = max(openY, closeY)
                val rectH = (bottom - top).coerceAtLeast(1.5f)
                drawRect(
                    color = color,
                    topLeft = Offset(x - barWidth / 2, top),
                    size = Size(barWidth, rectH),
                )
            }
        }

        // 이동평균선
        val maColors = listOf(MA_5, MA_20, MA_60)
        maPeriods.forEachIndexed { idx, period ->
            if (candles.size < period || period <= 1) return@forEachIndexed
            val maColor = maColors.getOrElse(idx) { Color(0xFF6B7684) }
            val path = Path()
            var started = false
            for (i in (period - 1) until candles.size) {
                val avg = candles.subList(i - period + 1, i + 1).map { it.close }.average()
                val x = padX + i * step + step / 2
                val y = yPrice(avg)
                if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
            }
            drawPath(path, maColor, style = Stroke(width = 2f))
        }

        // 거래량 막대
        candles.forEachIndexed { i, c ->
            val x = padX + i * step + step / 2
            val barH = (c.volume.toFloat() / volumeMax) * volumeH
            val color = candleColor(c).copy(alpha = 0.5f)
            drawRect(
                color = color,
                topLeft = Offset(x - barWidth / 2, volumeBottom - barH),
                size = Size(barWidth, barH),
            )
        }

        // 캔들/거래량 영역 경계선
        drawLine(
            Color(0x14191F28),
            Offset(padX, volumeTop),
            Offset(padX + w, volumeTop),
            strokeWidth = 0.8f,
        )

        // 크로스헤어
        crosshairIndex?.let { idx ->
            if (idx !in candles.indices) return@let
            val c = candles[idx]
            val x = padX + idx * step + step / 2
            val y = yPrice(c.close)
            val cross = Color(0xFF6B7684)
            drawLine(cross, Offset(x, padY), Offset(x, padY + totalH), strokeWidth = 1f)
            drawLine(cross, Offset(padX, y), Offset(padX + w, y), strokeWidth = 1f)
            drawCircle(Color(0xFF191F28), 4f, Offset(x, y))
        }
    }
}

private fun candleColor(c: Candle): Color = when {
    c.close > c.open -> KrUpRed
    c.close < c.open -> KrDownBlue
    else -> Color(0xFF6B7684)
}

private fun indexFromX(x: Float, totalWidth: Float, count: Int): Int? {
    if (count <= 0 || totalWidth <= 0f) return null
    val padX = 8f
    val w = totalWidth - padX * 2
    if (w <= 0f) return null
    val step = w / count
    if (step <= 0f) return null
    return (((x - padX) / step).toInt()).coerceIn(0, count - 1)
}

private val MA_5 = Color(0xFFFCD34D)
private val MA_20 = Color(0xFFFB923C)
private val MA_60 = Color(0xFF8B5CF6)
