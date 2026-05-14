package com.myinfocar.aicoachstock.ui.theme

import androidx.compose.ui.graphics.Color

// 토스 톤 — 거의 흑백 + 토스 블루 1포인트.
// 한국 증권 컨벤션(상승 빨강 / 하락 파랑)은 ui/common/PnlColors.kt 로 별도 관리.

// Brand
val TossBlue = Color(0xFF3182F6)
val TossBlueDark = Color(0xFF1B64DA)
val TossBlueLight = Color(0xFFE8F1FE)

// Neutrals (light)
val NeutralBackground = Color(0xFFF7F8FA)
val NeutralSurface = Color(0xFFFFFFFF)
val NeutralSurfaceVariant = Color(0xFFF2F4F6)
val NeutralOutline = Color(0xFFE5E8EB)
val NeutralDivider = Color(0xFFEFF1F4)

val TextPrimary = Color(0xFF191F28)
val TextSecondary = Color(0xFF6B7684)
val TextTertiary = Color(0xFF8B95A1)

// Neutrals (dark)
val DarkBackground = Color(0xFF17181C)
val DarkSurface = Color(0xFF1E2026)
val DarkSurfaceVariant = Color(0xFF252830)
val DarkOutline = Color(0xFF333740)

val DarkTextPrimary = Color(0xFFE5E8EB)
val DarkTextSecondary = Color(0xFFB0B8C1)
val DarkTextTertiary = Color(0xFF8B95A1)

// Status
val SuccessGreen = Color(0xFF22A06B)
val WarningOrange = Color(0xFFF59E0B)
val ErrorRed = Color(0xFFE53935)

// 하위 호환 (기존 import 깨지지 않도록 — 사용처 없으면 다음 청소 단계에서 제거)
val Primary = TossBlue
val OnPrimary = Color.White
val PrimaryContainer = TossBlueLight
val Secondary = TossBlue
val Background = NeutralBackground
val Surface = NeutralSurface
val PrimaryDark = TossBlue
val OnPrimaryDark = Color.White
val BackgroundDark = DarkBackground
val SurfaceDark = DarkSurface
