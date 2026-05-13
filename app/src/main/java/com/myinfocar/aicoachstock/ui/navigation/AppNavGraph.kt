package com.myinfocar.aicoachstock.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
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
import com.myinfocar.aicoachstock.ui.alert.PriceAlertScreen
import com.myinfocar.aicoachstock.ui.coach.CoachChatScreen
import com.myinfocar.aicoachstock.ui.coach.CoachListScreen
import com.myinfocar.aicoachstock.ui.entry.EntryChecklistScreen
import com.myinfocar.aicoachstock.ui.holdings.HoldingsScreen
import com.myinfocar.aicoachstock.ui.home.HomeScreen
import com.myinfocar.aicoachstock.ui.poc.KisWsPocScreen
import com.myinfocar.aicoachstock.ui.poc.LlmPocScreen
import com.myinfocar.aicoachstock.ui.research.ResearchScreen
import com.myinfocar.aicoachstock.ui.search.StockSearchScreen
import com.myinfocar.aicoachstock.ui.stockdetail.StockDetailScreen
import com.myinfocar.aicoachstock.ui.principle.PrincipleEditScreen
import com.myinfocar.aicoachstock.ui.principle.PrincipleListScreen
import com.myinfocar.aicoachstock.ui.reflection.ReflectionScreen
import com.myinfocar.aicoachstock.ui.settings.SettingsScreen
import com.myinfocar.aicoachstock.ui.trade.TradeEditScreen
import com.myinfocar.aicoachstock.ui.trade.TradeListScreen
import com.myinfocar.aicoachstock.ui.watchlist.WatchListScreen

object AppRoutes {
    const val HOME = "home"
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
    const val COACH_CHAT = "coach/chat/{sessionId}"
    const val ENTRY_CHECKLIST = "entry"
    const val STOCK_SEARCH = "stocks/search"
    const val PRICE_ALERTS = "alerts"
    const val RESEARCH = "research"
    const val HOLDINGS = "holdings"
    const val STOCK_DETAIL = "stocks/{ticker}"
    const val ARG_TICKER = "ticker"
    const val ARG_ID = "id"
    const val ARG_TRADE_ID = "tradeId"
    const val ARG_SESSION_ID = "sessionId"

    fun principleEdit(id: String? = null): String =
        if (id == null) "principles/edit" else "principles/edit?id=$id"

    fun tradeEdit(id: String? = null): String =
        if (id == null) "trades/edit" else "trades/edit?id=$id"

    fun reflection(tradeId: String): String = "reflections/$tradeId"

    fun coachChat(sessionId: String): String = "coach/chat/$sessionId"

    fun stockDetail(ticker: String): String = "stocks/$ticker"
}

/** 하단 BottomNav에 표시되는 탭. 다른 라우트(예: 편집 화면)은 BottomNav 숨김. */
enum class BottomTab(
    val route: String,
    val title: String,
    val icon: ImageVector,
) {
    HOME(AppRoutes.HOME, "홈", Icons.Default.Home),
    WATCHLIST(AppRoutes.WATCHLIST, "관심", Icons.Default.Star),
    TRADES(AppRoutes.TRADES, "매매", Icons.AutoMirrored.Filled.List),
    COACH(AppRoutes.COACH, "코치", Icons.Default.SmartToy),
    PRINCIPLES(AppRoutes.PRINCIPLES, "원칙", Icons.Default.VerifiedUser),
    SETTINGS(AppRoutes.SETTINGS, "설정", Icons.Default.Settings);

    companion object {
        fun fromRoute(route: String?): BottomTab? =
            entries.firstOrNull { it.route == route }
    }
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = AppRoutes.HOME,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(AppRoutes.HOME) {
            HomeScreen(
                onOpenStock = { ticker -> navController.navigate(AppRoutes.stockDetail(ticker)) },
                onOpenHoldings = { navController.navigate(AppRoutes.HOLDINGS) },
                onOpenWatchlist = {
                    navController.navigate(AppRoutes.WATCHLIST) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(AppRoutes.PRINCIPLES) {
            PrincipleListScreen(
                onAddClick = { navController.navigate(AppRoutes.principleEdit()) },
                onEditClick = { id -> navController.navigate(AppRoutes.principleEdit(id)) },
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
                onSearchClick = { navController.navigate(AppRoutes.STOCK_SEARCH) },
                onItemClick = { ticker -> navController.navigate(AppRoutes.stockDetail(ticker)) },
            )
        }
        composable(AppRoutes.SETTINGS) {
            SettingsScreen(
                onBack = null, // BottomTab으로 진입 — 뒤로가기 아이콘 숨김
                onOpenLlmPoc = { navController.navigate(AppRoutes.LLM_POC) },
                onOpenKisWsPoc = { navController.navigate(AppRoutes.KIS_WS_POC) },
                onOpenEntryChecklist = { navController.navigate(AppRoutes.ENTRY_CHECKLIST) },
                onOpenPriceAlerts = { navController.navigate(AppRoutes.PRICE_ALERTS) },
                onOpenStockSearch = { navController.navigate(AppRoutes.STOCK_SEARCH) },
                onOpenResearch = { navController.navigate(AppRoutes.RESEARCH) },
                onOpenHoldings = { navController.navigate(AppRoutes.HOLDINGS) },
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
            CoachListScreen(
                onOpenSession = { id -> navController.navigate(AppRoutes.coachChat(id)) },
            )
        }
        composable(
            route = AppRoutes.COACH_CHAT,
            arguments = listOf(
                navArgument(AppRoutes.ARG_SESSION_ID) { type = NavType.StringType },
            ),
        ) {
            CoachChatScreen(onBack = { navController.popBackStack() })
        }
        composable(AppRoutes.ENTRY_CHECKLIST) {
            EntryChecklistScreen(onBack = { navController.popBackStack() })
        }
        composable(AppRoutes.STOCK_SEARCH) {
            StockSearchScreen(onBack = { navController.popBackStack() })
        }
        composable(AppRoutes.PRICE_ALERTS) {
            PriceAlertScreen(onBack = { navController.popBackStack() })
        }
        composable(AppRoutes.RESEARCH) {
            ResearchScreen(onBack = { navController.popBackStack() })
        }
        composable(AppRoutes.HOLDINGS) {
            HoldingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = AppRoutes.STOCK_DETAIL,
            arguments = listOf(navArgument(AppRoutes.ARG_TICKER) { type = NavType.StringType }),
        ) {
            StockDetailScreen(onBack = { navController.popBackStack() })
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
