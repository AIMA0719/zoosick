package com.myinfocar.aicoachstock.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.myinfocar.aicoachstock.ui.theme.AppTokens

/**
 * 토스식 리스트 아이템. AppCard 안에 좌측 (아이콘 또는 emoji) + 텍스트 +
 * trailing chevron을 배치한 클릭 가능한 row.
 */
@Composable
fun SettingsTile(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    leadingEmoji: String? = null,
    showChevron: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    AppCard(onClick = onClick, padding = AppTokens.space16, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                icon != null -> {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(AppTokens.space24),
                    )
                    Spacer(Modifier.width(AppTokens.space16))
                }

                leadingEmoji != null -> {
                    Text(
                        leadingEmoji,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.width(AppTokens.space16))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppTokens.space2)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                description?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing?.invoke()
            if (showChevron && trailing == null) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
