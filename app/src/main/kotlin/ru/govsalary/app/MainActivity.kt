package ru.govsalary.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ru.govsalary.app.ui.navigation.GovSalaryApp
import ru.govsalary.app.ui.theme.AppThemeMode
import ru.govsalary.app.ui.theme.GovSalaryTheme
import ru.govsalary.data.local.datastore.AppTheme

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // корректная адаптация под жесты/вырезы на любом форм-факторе (п.5)

        val container = (application as GovSalaryApplication).container

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val settings by container.settingsUseCases.observe().collectAsState(initial = null)

            val themeMode = when (settings?.theme) {
                AppTheme.LIGHT -> AppThemeMode.LIGHT
                AppTheme.DARK -> AppThemeMode.DARK
                else -> AppThemeMode.SYSTEM
            }

            GovSalaryTheme(themeMode = themeMode) {
                GovSalaryApp(windowWidthSizeClass = windowSizeClass.widthSizeClass)
            }
        }
    }
}
