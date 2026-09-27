package ru.govsalary.app

import android.app.Application
import ru.govsalary.app.di.AppContainer

class GovSalaryApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
