package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // ⬅️ INSTALA A SPLASH SCREEN OFICIAL DO ANDROID
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // Aguarda 2 segundos e vai para o Login
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }, 2000)
    }
}

