package com.project.musicapp.ui

import android.app.Application
import com.project.musicapp.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module

class MusicApp : Application() {

    override fun onCreate() {
        super.onCreate()
        /*startKoin {
            androidLogger(Level.DEBUG)
            androidContext(this@MusicApp)
            modules(
                listOf(appModule)
            )
        }*/
    }
}