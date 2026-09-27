package ru.govsalary.app.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import ru.govsalary.app.ui.screens.analytics.AnalyticsRoute
import ru.govsalary.app.ui.screens.calculator.CalculatorRoute
import ru.govsalary.app.ui.screens.calendar.CalendarRoute
import ru.govsalary.app.ui.screens.dashboard.DashboardRoute
import ru.govsalary.app.ui.screens.forecast.ForecastRoute
import ru.govsalary.app.ui.screens.goals.GoalsRoute
import ru.govsalary.app.ui.screens.history.HistoryRoute
import ru.govsalary.app.ui.screens.savings.SavingsRoute
import ru.govsalary.app.ui.screens.settings.SettingsRoute
import ru.govsalary.app.ui.screens.whatif.WhatIfRoute

/**
 * Все экраны приложения (мастер-промпт Фазы 4, п.6-7). `onPhoneBottomBar` отмечает 4 пункта,
 * которые показаны напрямую в Bottom Navigation на телефоне; остальные 6 — под пунктом "Ещё".
 * На планшете (NavigationRail/Drawer) показаны ВСЕ 10 пунктов согласно п.7 мастер-промпта.
 */
enum class Destination(val route: String, val label: String, val icon: ImageVector, val onPhoneBottomBar: Boolean) {
    DASHBOARD("dashboard", "Главная", Icons.Filled.Home, true),
    CALCULATOR("calculator", "Расчёт", Icons.Filled.Calculate, true),
    ANALYTICS("analytics", "Аналитика", Icons.Filled.Analytics, true),
    GOALS("goals", "Цели", Icons.Filled.Flag, true),
    CALENDAR("calendar", "Календарь", Icons.Filled.CalendarMonth, false),
    HISTORY("history", "История", Icons.Filled.History, false),
    SAVINGS("savings", "Накопления", Icons.Filled.Savings, false),
    FORECAST("forecast", "Прогноз", Icons.Filled.TrendingUp, false),
    WHATIF("whatif", "What-If", Icons.Filled.ShowChart, false),
    SETTINGS("settings", "Настройки", Icons.Filled.Settings, false),
}

private const val MORE_ROUTE = "more"

/**
 * Экраны читают текущий класс ширины окна отсюда (вместо протаскивания параметра через каждый
 * NavHost.composable) — так экран внутри LazyColumn/вложенной навигации тоже знает, что он
 * на планшете, не требуя явной передачи через весь граф (мастер-промпт Фазы 4, п.5, 9, 13, 14, 15).
 */
val LocalWindowWidthSizeClass = compositionLocalOf { WindowWidthSizeClass.Compact }

/**
 * Точка входа UI. Выбор Bottom Navigation / NavigationRail / Drawer основан на
 * WindowWidthSizeClass (мастер-промпт Фазы 4, п.5: "не растягивать телефонный интерфейс").
 */
@Composable
fun GovSalaryApp(windowWidthSizeClass: WindowWidthSizeClass) {
    CompositionLocalProvider(LocalWindowWidthSizeClass provides windowWidthSizeClass) {
        val navController = rememberNavController()
        when (windowWidthSizeClass) {
            WindowWidthSizeClass.Expanded -> ExpandedLayout(navController)
            WindowWidthSizeClass.Medium -> MediumLayout(navController)
            else -> CompactLayout(navController)
        }
    }
}

@Composable
private fun CompactLayout(navController: NavHostController) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.filter { it.onPhoneBottomBar }.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = { navigateTopLevel(navController, destination.route) },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
                NavigationBarItem(
                    selected = currentRoute == MORE_ROUTE,
                    onClick = { navigateTopLevel(navController, MORE_ROUTE) },
                    icon = { Icon(Icons.Filled.MoreHoriz, contentDescription = "Ещё") },
                    label = { Text("Ещё") },
                )
            }
        },
    ) { padding -> AppNavHost(navController, Modifier.padding(padding)) }
}

@Composable
private fun MediumLayout(navController: NavHostController) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    Row {
        NavigationRail {
            Destination.entries.forEach { destination ->
                NavigationRailItem(
                    selected = currentRoute == destination.route,
                    onClick = { navigateTopLevel(navController, destination.route) },
                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                    label = { Text(destination.label) },
                )
            }
        }
        Scaffold { padding -> AppNavHost(navController, Modifier.padding(padding)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpandedLayout(navController: NavHostController) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    // Постоянно открытая панель — на широком экране (Expanded) это стандартный паттерн Material 3
    // для "постоянной" навигации, а не временной модалки (drawerState здесь не закрывается).
    val drawerState = rememberDrawerState(DrawerValue.Open)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Destination.entries.forEach { destination ->
                    NavigationDrawerItem(
                        label = { Text(destination.label) },
                        selected = currentRoute == destination.route,
                        icon = { Icon(destination.icon, contentDescription = null) },
                        onClick = { navigateTopLevel(navController, destination.route) },
                    )
                }
            }
        },
    ) {
        Scaffold { padding -> AppNavHost(navController, Modifier.padding(padding)) }
    }
}

private fun navigateTopLevel(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppNavHost(navController: NavHostController, modifier: Modifier) {
    NavHost(navController = navController, startDestination = Destination.DASHBOARD.route, modifier = modifier) {
        composable(Destination.DASHBOARD.route) { DashboardRoute(navController) }
        composable(Destination.CALCULATOR.route) { CalculatorRoute() }
        composable(Destination.ANALYTICS.route) { AnalyticsRoute() }
        composable(Destination.GOALS.route) { GoalsRoute(navController) }
        composable(Destination.CALENDAR.route) { CalendarRoute() }
        composable(Destination.HISTORY.route) { HistoryRoute() }
        composable(Destination.SAVINGS.route) { SavingsRoute() }
        composable(Destination.FORECAST.route) { ForecastRoute() }
        composable(Destination.WHATIF.route) { WhatIfRoute() }
        composable(Destination.SETTINGS.route) { SettingsRoute() }
        composable(MORE_ROUTE) { MoreScreen(navController) }
    }
}

@Composable
private fun MoreScreen(navController: NavHostController) {
    val moreDestinations = Destination.entries.filter { !it.onPhoneBottomBar }
    LazyColumn {
        items(moreDestinations) { destination ->
            ListItem(
                headlineContent = { Text(destination.label) },
                leadingContent = { Icon(destination.icon, contentDescription = null) },
                modifier = Modifier.clickable { navigateTopLevel(navController, destination.route) },
            )
        }
    }
}
