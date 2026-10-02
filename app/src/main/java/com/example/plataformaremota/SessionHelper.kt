package com.example.plataformaremota

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.tasks.await

object SessionHelper {

    private const val PREFS_NAME = "CTR_PREFS"
    private const val KEY_EMAIL = "emailUsuario"
    private const val TAG = "SESSION"

    /**
     * Retorna o email do usuário logado.
     * Tenta o Firebase Auth; se null, cai pro SharedPreferences.
     */
    fun getEmailLogado(context: Context): String {
        val auth = FirebaseAuth.getInstance()
        val emailAuth = auth.currentUser?.email ?: ""
        if (emailAuth.isNotEmpty()) return emailAuth

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_EMAIL, "") ?: ""
    }

    /**
     * Aguarda o Firebase Auth restaurar (até 10s).
     *
     * Se em 10s não restaurar:
     * - Retorna o email do SharedPreferences (pra navegação)
     * - O app vai pra home, mas as queries vão falhar (PERMISSION_DENIED)
     */
    suspend fun aguardarFirebaseAuth(context: Context): String {
        val auth = FirebaseAuth.getInstance()

        // Já tem sessão ativa?
        auth.currentUser?.email?.let { email ->
            if (email.isNotEmpty()) {
                Log.d(TAG, "Já logado: $email")
                return email
            }
        }

        Log.d(TAG, "Aguardando Firebase Auth (max 10s)...")

        // Tenta em loop por 10s
        for (i in 1..20) {
            if (auth.currentUser != null) {
                Log.d(TAG, "Firebase Auth OK: ${auth.currentUser?.email}")
                return auth.currentUser?.email ?: ""
            }
            kotlinx.coroutines.delay(500)
        }

        // Não restaurou: retorna do cache
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val emailCache = prefs.getString(KEY_EMAIL, "") ?: ""
        Log.w(TAG, "Timeout 10s. Cache: $emailCache")
        return emailCache
    }
}