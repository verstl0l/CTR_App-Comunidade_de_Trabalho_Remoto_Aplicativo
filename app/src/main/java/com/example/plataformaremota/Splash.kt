package com.example.plataformaremota

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class Splash : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val prefs = getSharedPreferences("CTR_PREFS", Context.MODE_PRIVATE)
        val emailCache = prefs.getString("emailUsuario", "") ?: ""

        // ✅ Se tem cache, vai direto pra home
        if (emailCache.isNotEmpty()) {
            Log.d("SPLASH", "Email em cache: $emailCache. Indo pro MainActivity")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // ✅ Se não tem cache, espera o Firebase Auth
        Log.d("SPLASH", "Sem cache. Esperando Firebase Auth...")
        lifecycleScope.launch {
            val auth = FirebaseAuth.getInstance()

            // Tenta por 10s
            for (i in 1..20) {
                if (auth.currentUser != null) {
                    Log.d("SPLASH", "Firebase Auth OK: ${auth.currentUser?.email}")
                    val emailAuth = auth.currentUser?.email ?: ""
                    if (emailAuth.isNotEmpty()) {
                        prefs.edit().putString("emailUsuario", emailAuth).apply()
                    }
                    startActivity(Intent(this@Splash, MainActivity::class.java))
                    finish()
                    return@launch
                }
                kotlinx.coroutines.delay(500)
            }

            // Timeout: vai pro Login
            Log.d("SPLASH", "Timeout. Indo pro Login")
            startActivity(Intent(this@Splash, LoginActivity::class.java))
            finish()
        }
    }
}