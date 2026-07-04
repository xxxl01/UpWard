package com.xdl.upward.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xdl.upward.ui.api.ApiConfigEditScreen
import com.xdl.upward.ui.config.ConfigSettingsScreen
import com.xdl.upward.ui.project.ProjectDetailScreen
import com.xdl.upward.ui.project.ProjectEditScreen
import com.xdl.upward.ui.project.ProjectListScreen
import com.xdl.upward.ui.record.DailyRecordEditScreen
import com.xdl.upward.ui.record.DailyRecordListScreen
import com.xdl.upward.ui.violation.ViolationChatScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoute.PROJECT_LIST,
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        composable(AppRoute.PROJECT_LIST) {
            ProjectListScreen(
                onCreateProject = {
                    navController.navigate(AppRoute.projectEdit())
                },
                onOpenProject = { projectId ->
                    navController.navigate(AppRoute.projectDetail(projectId))
                },
                onOpenConfigSettings = {
                    navController.navigate(AppRoute.CONFIG_SETTINGS)
                },
                onOpenViolationChat = {
                    navController.navigate(AppRoute.VIOLATION_CHAT)
                }
            )
        }

        composable(AppRoute.VIOLATION_CHAT) {
            ViolationChatScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoute.CONFIG_SETTINGS) {
            ConfigSettingsScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onCreateApi = {
                    navController.navigate(AppRoute.apiConfigEdit())
                },
                onEditApi = { apiId ->
                    navController.navigate(AppRoute.apiConfigEdit(apiId))
                }
            )
        }

        composable(
            route = AppRoute.PROJECT_EDIT,
            arguments = listOf(
                navArgument("projectId") {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getLong("projectId") ?: 0L
            ProjectEditScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = AppRoute.PROJECT_DETAIL,
            arguments = listOf(
                navArgument("projectId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getLong("projectId") ?: 0L
            ProjectDetailScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
                onEditProject = {
                    navController.navigate(AppRoute.projectEdit(projectId))
                },
                onOpenDailyRecords = {
                    navController.navigate(AppRoute.dailyRecordList(projectId))
                }
            )
        }

        composable(
            route = AppRoute.DAILY_RECORD_LIST,
            arguments = listOf(
                navArgument("projectId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getLong("projectId") ?: 0L
            DailyRecordListScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
                onCreateRecord = {
                    navController.navigate(AppRoute.dailyRecordEdit(projectId))
                },
                onEditRecord = { recordId ->
                    navController.navigate(AppRoute.dailyRecordEdit(projectId, recordId))
                }
            )
        }

        composable(
            route = AppRoute.DAILY_RECORD_EDIT,
            arguments = listOf(
                navArgument("projectId") {
                    type = NavType.LongType
                },
                navArgument("recordId") {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getLong("projectId") ?: 0L
            val recordId = backStackEntry.arguments?.getLong("recordId") ?: 0L
            DailyRecordEditScreen(
                projectId = projectId,
                recordId = recordId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = AppRoute.API_CONFIG_EDIT,
            arguments = listOf(
                navArgument("apiId") {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) { backStackEntry ->
            val apiId = backStackEntry.arguments?.getLong("apiId") ?: 0L
            ApiConfigEditScreen(
                apiId = apiId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
    }
}
