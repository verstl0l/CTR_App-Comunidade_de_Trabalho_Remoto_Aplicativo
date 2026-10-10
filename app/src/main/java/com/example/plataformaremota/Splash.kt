package com.example.plataformaremota

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Splash : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val emailCache = prefs.getString(KEY_EMAIL_USUARIO, "") ?: ""

        // ✅ Se tem cache, vai direto pra home
        if (emailCache.isNotEmpty()) {
            Log.d(TAG, "Email em cache: $emailCache. Indo pro MainActivity")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // ✅ Se não tem cache, espera o Firebase Auth
        Log.d(TAG, "Sem cache. Esperando Firebase Auth...")
        lifecycleScope.launch {
            val auth = FirebaseAuth.getInstance()

            // Tenta por 10s
            for (i in 1..20) {
                if (auth.currentUser != null) {
                    Log.d(TAG, "Firebase Auth OK: ${auth.currentUser?.email}")
                    val emailAuth = auth.currentUser?.email ?: ""
                    if (emailAuth.isNotEmpty()) {
                        prefs.edit().putString(KEY_EMAIL_USUARIO, emailAuth).apply()
                    }
                    startActivity(Intent(this@Splash, MainActivity::class.java))
                    finish()
                    return@launch
                }
                delay(500)
            }

            // Timeout: vai pro Login
            Log.d(TAG, "Timeout. Indo pro Login")
            startActivity(Intent(this@Splash, LoginActivity::class.java))
            finish()
        }
    }

    companion object {
        private const val TAG = "SPLASH"
        private const val PREFS_NAME = "CTR_PREFS"
        private const val KEY_EMAIL_USUARIO = "emailUsuario"
    }
}