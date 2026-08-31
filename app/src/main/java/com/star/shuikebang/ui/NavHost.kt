package com.star.shuikebang.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.star.shuikebang.ui.detail.SessionDetailScreen
import com.star.shuikebang.ui.history.HistoryScreen
import com.star.shuikebang.ui.home.HomeScreen

@Composable
fun ShuikebangNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToHistory = { navController.navigate("history") },
                onNavigateToDetail = { sessionId -> navController.navigate("detail/$sessionId") }
            )
        }
        composable("history") {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onSessionClick = { sessionId -> navController.navigate("detail/$sessionId") }
            )
        }
        composable(
            "detail/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
            SessionDetailScreen(
                sessionId = sessionId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
