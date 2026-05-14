package com.myinfocar.aicoachstock.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.myinfocar.aicoachstock.ui.theme.AppTokens

/**
 * 리스트 스크린 공통 로딩 플레이스홀더. CircularProgressIndicator 대신
 * 토스 톤 shimmer 카드를 N개 쌓아 보여준다.
 */
@Composable
fun ListLoadingSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 5,
    itemHeight: Dp = AppTokens.rowHeight,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AppTokens.space16, vertical = AppTokens.space12),
        verticalArrangement = Arrangement.spacedBy(AppTokens.space8),
    ) {
        repeat(count) {
            SkeletonShimmer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight),
                cornerRadius = AppTokens.radius16,
            )
        }
    }
}
