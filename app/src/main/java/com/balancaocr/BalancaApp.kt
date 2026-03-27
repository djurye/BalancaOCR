package com.balancaocr

import android.app.Application

class BalancaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inicialização segura — Room é lazy, não precisa de init aqui
    }
}
