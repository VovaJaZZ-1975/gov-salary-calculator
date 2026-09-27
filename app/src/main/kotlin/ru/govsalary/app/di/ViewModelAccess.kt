package ru.govsalary.app.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import ru.govsalary.app.GovSalaryApplication

/** Единая точка доступа к DI-контейнеру из Composable (используется всеми `XxxRoute` функциями). */
@Composable
fun rememberAppContainer(): AppContainer {
    val context = LocalContext.current.applicationContext as GovSalaryApplication
    return context.container
}
