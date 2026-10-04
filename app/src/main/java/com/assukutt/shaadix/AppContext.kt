package com.assukutt.shaadix

import android.app.Application
import android.content.Context
import com.assukutt.shaadix.data.AppContainer

class AppContext : Application() {
    lateinit var container: AppContainer
        private set
    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        container = AppContainer(applicationContext)
    }
    companion object { lateinit var context: Context; private set }
}

