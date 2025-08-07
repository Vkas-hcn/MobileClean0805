package com.kite.folding.pape.rmobileclean.app

import android.app.Application

class CleanApp: Application() {
    companion object {
        lateinit var instance: CleanApp
    }
    override fun onCreate() {
        super.onCreate()
        instance =  this
    }
}