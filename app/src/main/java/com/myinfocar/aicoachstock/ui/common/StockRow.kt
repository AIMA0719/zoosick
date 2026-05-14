package com.myinfocar.aicoachstock.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myinfocar.aicoachstock.domain.model.Market
import com.myinfocar.aicoachstock.ui.theme.AppTokens

/**
 * 표준 종목 리스트 행 (토스 종목 행 스타일).
 *
 * 레이아웃:
 *  [좌] 종목명 (Bold)
 *       종목코드 · (거래소) (회색 작은 글씨)
 *  [우] 현재가 (Bold, 등락에 따라 색)
 *       등락률 (같은 색)
 *
 * price 가 null 이면 우측에 "—" 표시.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun StockRow(
    name: String,
    ticker: String,
    market: Market,
    price: Double?,
    changePct: Double?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    exchangeLabel: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val priceColor = pnlColor(changePct)
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTokens.radius12))
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = AppTokens.space16, vertical = AppTokens.space12)
            .heightIn(min = AppTokens.rowHeightCompact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            val tickerSub = buildString {
                append(ticker)
                if (!exchangeLabel.isNullOrBlank()) append(" · ").append(exchangeLabel)
                append(" · ").append(if (market == Market.KR) "KR" else "US")
            }
            Text(
                tickerSub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            AnimatedVisibility(
                visible = price != null,
                enter = fadeIn(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(120)),
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = price?.let { formatPriceForMarket(it, market) } ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = priceColor,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = changePct?.let { "%+.2f%%".format(it) } ?: " ",
                        style = MaterialTheme.typography.labelMedium,
                        color = priceColor,
                    )
                }
            }
            if (price == null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .size(width = 70.dp, height = 18.dp),
                ) {
                    SkeletonShimmer(Modifier.fillMaxWidth().height(18.dp))
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .size(width = 50.dp, height = 14.dp),
                ) {
                    SkeletonShimmer(Modifier.fillMaxWidth().height(14.dp))
                }
            }
        }

        trailing?.let {
            Spacer(Modifier.width(AppTokens.space8))
            it()
        }
    }
}

private fun formatPriceForMarket(value: Double, market: Market): String = when (market) {
    Market.KR -> "%,d원".format(value.toLong())
    Market.US -> "$${"%.2f".format(value)}"
}
