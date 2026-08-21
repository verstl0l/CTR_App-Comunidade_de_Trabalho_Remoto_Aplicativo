package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class inicial : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_inicial)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Link para a tela de Cadastro
        val btnCadastrar = findViewById<Button>(R.id.button3)
        btnCadastrar.setOnClickListener {
            val intent = Intent(this, CadastroActivity::class.java)
            startActivity(intent)
        }

        // Link para a tela MainActivity (Entrar em Equipe)
        val btnEntrarEquipe = findViewById<Button>(R.id.button)
        btnEntrarEquipe.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        // Link para a tela TrabalhoActivity (Criar Equipe)
        val btnCriarEquipe = findViewById<Button>(R.id.button5)
        btnCriarEquipe.setOnClickListener {
            val intent = Intent(this, TrabalhoActivity::class.java)
            startActivity(intent)
        }
    }
}