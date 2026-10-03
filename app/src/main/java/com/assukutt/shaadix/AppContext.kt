package com.assukutt.shaadix

import android.app.Application
import android.content.Context

class AppContext : Application() {
    override fun onCreate() { super.onCreate(); context = applicationContext }
    companion object { lateinit var context: Context; private set }
}
