package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.plataformaremota.adapter.TrabalhoAdapter
import com.example.plataformaremota.data.database.AppDatabase
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var txtBoasVindas: TextView
    private lateinit var btnNovoTrabalho: Button
    private lateinit var recyclerTrabalhos: RecyclerView
    private lateinit var adapter: TrabalhoAdapter

    private var usuarioId: Int = 0

    private var nomeUsuario: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        txtBoasVindas =
            findViewById(R.id.txtBoasVindas)

        btnNovoTrabalho =
            findViewById(R.id.btnNovoTrabalho)

        recyclerTrabalhos =
            findViewById(R.id.recyclerTrabalhos)

        usuarioId = intent.getIntExtra(
            "usuarioId",
            0
        )

        nomeUsuario = intent.getStringExtra(
            "nomeUsuario"
        ) ?: ""

        txtBoasVindas.text =
            "Olá, $nomeUsuario!"

        configurarRecyclerView()

        btnNovoTrabalho.setOnClickListener {

            val intent = Intent(
                this,
                TrabalhoActivity::class.java
            )

            intent.putExtra(
                "usuarioId",
                usuarioId
            )

            startActivity(intent)
        }

        carregarTrabalhos()
    }

    private fun configurarRecyclerView() {

        adapter = TrabalhoAdapter(
            emptyList()
        )

        recyclerTrabalhos.layoutManager =
            LinearLayoutManager(this)

        recyclerTrabalhos.adapter =
            adapter
    }

    private fun carregarTrabalhos() {

        lifecycleScope.launch {

            val database =
                AppDatabase.getDatabase(
                    this@MainActivity
                )

            val trabalhos =
                database.trabalhoDao()
                    .listarTodos()

            adapter.atualizarLista(
                trabalhos
            )
        }
    }

    override fun onResume() {
        super.onResume()

        if (::adapter.isInitialized) {
            carregarTrabalhos()
        }
    }
}