package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class Splash : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            val auth = FirebaseAuth.getInstance()
            val prefs = getSharedPreferences("CTR_PREFS", MODE_PRIVATE)

            // ✅ BUG CORRIGIDO: checa as DUAS condições
            val logado = auth.currentUser != null && prefs.getBoolean("logado", false)

            val destino = if (logado) {
                MainActivity::class.java
            } else {
                LoginActivity::class.java
            }

            startActivity(Intent(this, destino))
            finish()
        }, 2000)
    }
}