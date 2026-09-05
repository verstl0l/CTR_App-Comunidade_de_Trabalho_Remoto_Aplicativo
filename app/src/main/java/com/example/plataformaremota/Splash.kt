package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // Vai para a tela de LOGIN
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }, 2000)
    }
}