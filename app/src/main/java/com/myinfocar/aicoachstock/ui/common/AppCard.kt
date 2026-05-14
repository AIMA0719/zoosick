package com.myinfocar.aicoachstock.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myinfocar.aicoachstock.ui.theme.AppTokens

/**
 * 토스 톤 평면 카드. 그림자 없음, 16dp 라운드, surface 배경.
 * 클릭 가능하면 ripple + 살짝 scale-down 애니메이션.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    bordered: Boolean = false,
    padding: Dp = AppTokens.space16,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(AppTokens.radius16)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && (onClick != null || onLongClick != null)) 0.98f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "cardScale",
    )

    val baseModifier = modifier
        .scale(scale)
        .clip(shape)
        .background(containerColor)
        .let { mod ->
            if (bordered) mod.border(1.dp, MaterialTheme.colorScheme.outline, shape) else mod
        }

    val clickableModifier = when {
        onLongClick != null -> baseModifier.combinedClickable(
            interactionSource = interaction,
            indication = ripple(),
            onClick = onClick ?: {},
            onLongClick = onLongClick,
        )
        onClick != null -> baseModifier.clickable(
            interactionSource = interaction,
            indication = ripple(),
            onClick = onClick,
        )
        else -> baseModifier
    }

    Box(modifier = clickableModifier.padding(padding)) {
        content()
    }
}
