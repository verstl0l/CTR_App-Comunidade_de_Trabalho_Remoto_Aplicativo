package com.example.plataformaremota

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class BuscarEquipesActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var todasEquipes: List<com.google.firebase.firestore.DocumentSnapshot> = emptyList()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.aplicarIdioma(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_buscar_equipes)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""

        val btnVoltar = findViewById<Button>(R.id.btnVoltarBusca)
        btnVoltar.setOnClickListener { finish() }

        val edtBuscar = findViewById<EditText>(R.id.edtBuscarEquipe)

        edtBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filtrarEquipes(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        carregarEquipes()
        configurarBottomNavigation()
    }

    private fun carregarEquipes() {
        lifecycleScope.launch {
            try {
                val equipes = db.collection("equipes")
                    .whereEqualTo("privada", false)
                    .get()
                    .await()

                val equipesDisponiveis = equipes.documents.filter { doc ->
                    val criadorEmail = doc.getString("criadorEmail") ?: ""
                    criadorEmail != emailUsuario
                }

                todasEquipes = equipesDisponiveis
                mostrarEquipes(equipesDisponiveis)

            } catch (e: Exception) {
                Log.e("BUSCAR_EQUIPES", "Erro: ${e.message}")
                Toast.makeText(this@BuscarEquipesActivity, getString(R.string.erro_generico, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun filtrarEquipes(termo: String) {
        if (termo.isEmpty()) {
            mostrarEquipes(todasEquipes)
            return
        }

        val filtradas = todasEquipes.filter { doc ->
            val nome = doc.getString("nome") ?: ""
            nome.lowercase().contains(termo.lowercase())
        }

        mostrarEquipes(filtradas)
    }

    private fun mostrarEquipes(equipes: List<com.google.firebase.firestore.DocumentSnapshot>) {
        val container = findViewById<LinearLayout>(R.id.containerEquipes)
        container.removeAllViews()

        if (equipes.isEmpty()) {
            val txtVazio = TextView(this).apply {
                text = getString(R.string.buscar_equipes_vazio)
                setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                textSize = 14f
                setPadding(0, 60, 0, 60)
                gravity = android.view.Gravity.CENTER
            }
            container.addView(txtVazio)
            return
        }

        val inflater = LayoutInflater.from(this)

        equipes.forEach { doc ->
            val equipeId = doc.id
            val nome = doc.getString("nome") ?: getString(R.string.equipe_default_nome)
            val descricao = doc.getString("descricao") ?: getString(R.string.equipe_sem_descricao)
            val criador = doc.getString("criadorEmail") ?: ""

            val view = inflater.inflate(R.layout.item_equipe, container, false)

            view.findViewById<TextView>(R.id.txtNomeEquipeItem).text = nome
            view.findViewById<TextView>(R.id.txtDescricaoEquipeItem).text = descricao
            view.findViewById<TextView>(R.id.txtCriadorEquipeItem).text = getString(R.string.buscar_equipes_criado_por, criador)

            view.findViewById<Button>(R.id.btnPedirEntrar).setOnClickListener {
                verificarPedidoExistente(equipeId, nome)
            }

            container.addView(view)
        }
    }

    private fun verificarPedidoExistente(equipeId: String, nomeEquipe: String) {
        lifecycleScope.launch {
            try {
                val pedidos = db.collection("pedidos_entrada")
                    .whereEqualTo("equipeId", equipeId)
                    .whereEqualTo("emailSolicitante", emailUsuario)
                    .get()
                    .await()

                val pendente = pedidos.documents.any {
                    it.getString("status") == "pendente"
                }

                if (pendente) {
                    Toast.makeText(this@BuscarEquipesActivity, getString(R.string.buscar_equipes_pedido_ja_enviado), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val intent = Intent(this@BuscarEquipesActivity, PedirEntradaActivity::class.java)
                intent.putExtra("equipeId", equipeId)
                intent.putExtra("nomeEquipe", nomeEquipe)
                startActivity(intent)

            } catch (e: Exception) {
                Toast.makeText(this@BuscarEquipesActivity, getString(R.string.erro_generico, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun configurarBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_chat -> {
                    startActivity(Intent(this, ListaConversasActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_groups -> {
                    startActivity(Intent(this, MinhasEquipesActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_notifications -> {
                    startActivity(Intent(this, NotificacoesActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, perfil::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}