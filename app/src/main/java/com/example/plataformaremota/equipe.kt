package com.example.plataformaremota

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class equipe : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_equipe)

        // ✅ SALVA EQUIPE POR USUÁRIO
        val prefs = getSharedPreferences("CTR_PREFS", Context.MODE_PRIVATE)
        val email = prefs.getString("emailUsuario", "") ?: ""
        prefs.edit().putBoolean("temEquipe_$email", true).apply()

        val btnVoltar = findViewById<Button>(R.id.button8)
        btnVoltar?.setOnClickListener {
            finish()
        }

        val edtEmail = findViewById<EditText>(R.id.edtEmail)
        val btnEnviar = findButtonByText("Enviar Convite")

        btnEnviar?.setOnClickListener {
            val emailConvidado = edtEmail.text.toString().trim()
            if (emailConvidado.isEmpty()) {
                Toast.makeText(this, "Digite um email", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Convite enviado para $emailConvidado", Toast.LENGTH_SHORT).show()
                edtEmail.text.clear()
            }
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    true
                }
                R.id.nav_groups -> {
                    startActivity(Intent(this, produtos::class.java))
                    true
                }
                R.id.nav_notifications -> {
                    startActivity(Intent(this, notificacao::class.java))
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, participantes::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun findButtonByText(text: String): Button? {
        val rootView = window.decorView.rootView
        return findButtonRecursive(rootView, text)
    }

    private fun findButtonRecursive(view: android.view.View, text: String): Button? {
        if (view is Button && view.text.toString() == text) {
            return view
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val result = findButtonRecursive(view.getChildAt(i), text)
                if (result != null) return result
            }
        }
        return null
    }
}

