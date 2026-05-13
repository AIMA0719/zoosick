package com.myinfocar.aicoachstock.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.myinfocar.aicoachstock.ui.poc.KisWsPocScreen
import com.myinfocar.aicoachstock.ui.poc.LlmPocScreen
import com.myinfocar.aicoachstock.ui.principle.PrincipleEditScreen
import com.myinfocar.aicoachstock.ui.principle.PrincipleListScreen
import com.myinfocar.aicoachstock.ui.reflection.ReflectionScreen
import com.myinfocar.aicoachstock.ui.settings.SettingsScreen
import com.myinfocar.aicoachstock.ui.trade.TradeEditScreen
import com.myinfocar.aicoachstock.ui.trade.TradeListScreen
import com.myinfocar.aicoachstock.ui.watchlist.WatchListScreen

object AppRoutes {
    const val PRINCIPLES = "principles"
    const val PRINCIPLE_EDIT = "principles/edit?id={id}"
    const val TRADES = "trades"
    const val TRADE_EDIT = "trades/edit?id={id}"
    const val WATCHLIST = "watchlist"
    const val COACH = "coach"
    const val SETTINGS = "settings"
    const val LLM_POC = "settings/llm-poc"
    const val KIS_WS_POC = "settings/kis-ws-poc"
    const val REFLECTION = "reflections/{tradeId}"
    const val ARG_ID = "id"
    const val ARG_TRADE_ID = "tradeId"

    fun principleEdit(id: String? = null): String =
        if (id == null) "principles/edit" else "principles/edit?id=$id"

    fun tradeEdit(id: String? = null): String =
        if (id == null) "trades/edit" else "trades/edit?id=$id"

    fun reflection(tradeId: String): String = "reflections/$tradeId"
}

/** 하단 BottomNav에 표시되는 탭. 다른 라우트(예: 편집 화면)은 BottomNav 숨김. */
enum class BottomTab(
    val route: String,
    val title: String,
    val icon: ImageVector,
) {
    PRINCIPLES(AppRoutes.PRINCIPLES, "원칙", Icons.Default.VerifiedUser),
    TRADES(AppRoutes.TRADES, "매매", Icons.AutoMirrored.Filled.List),
    WATCHLIST(AppRoutes.WATCHLIST, "관심", Icons.Default.Star),
    COACH(AppRoutes.COACH, "코치", Icons.Default.SmartToy);

    companion object {
        fun fromRoute(route: String?): BottomTab? =
            entries.firstOrNull { it.route == route }
    }
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = AppRoutes.PRINCIPLES,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(AppRoutes.PRINCIPLES) {
            PrincipleListScreen(
                onAddClick = { navController.navigate(AppRoutes.principleEdit()) },
                onEditClick = { id -> navController.navigate(AppRoutes.principleEdit(id)) },
                onSettingsClick = { navController.navigate(AppRoutes.SETTINGS) },
            )
        }
        composable(
            route = AppRoutes.PRINCIPLE_EDIT,
            arguments = listOf(
                navArgument(AppRoutes.ARG_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            PrincipleEditScreen(
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(AppRoutes.TRADES) {
            TradeListScreen(
                onAddClick = { navController.navigate(AppRoutes.tradeEdit()) },
                onEditClick = { id -> navController.navigate(AppRoutes.tradeEdit(id)) },
                onReflectClick = { id -> navController.navigate(AppRoutes.reflection(id)) },
                onSettingsClick = { navController.navigate(AppRoutes.SETTINGS) },
            )
        }
        composable(
            route = AppRoutes.TRADE_EDIT,
            arguments = listOf(
                navArgument(AppRoutes.ARG_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            TradeEditScreen(
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.WATCHLIST) {
            WatchListScreen(
                onSettingsClick = { navController.navigate(AppRoutes.SETTINGS) },
            )
        }
        composable(AppRoutes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenLlmPoc = { navController.navigate(AppRoutes.LLM_POC) },
                onOpenKisWsPoc = { navController.navigate(AppRoutes.KIS_WS_POC) },
            )
        }
        composable(AppRoutes.LLM_POC) {
            LlmPocScreen(onBack = { navController.popBackStack() })
        }
        composable(AppRoutes.KIS_WS_POC) {
            KisWsPocScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = AppRoutes.REFLECTION,
            arguments = listOf(
                navArgument(AppRoutes.ARG_TRADE_ID) {
                    type = NavType.StringType
                },
            ),
        ) {
            ReflectionScreen(onBack = { navController.popBackStack() })
        }
        composable(AppRoutes.COACH) {
            PlaceholderScreen("코치 채팅", "곧 추가됩니다 — Gemma + CoachSession")
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
