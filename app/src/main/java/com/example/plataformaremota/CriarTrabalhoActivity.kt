package com.example.plataformaremota

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CriarTrabalhoActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_criar_trabalho)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        val email = auth.currentUser?.email ?: ""

        val edtTitulo = findViewById<EditText>(R.id.edtTituloTrabalho)
        val edtDescricao = findViewById<EditText>(R.id.edtDescricaoTrabalho)
        val edtCategoria = findViewById<EditText>(R.id.edtCategoriaTrabalho)
        val edtPrazo = findViewById<EditText>(R.id.edtPrazoTrabalho)
        val btnPublicar = findViewById<Button>(R.id.btnPublicarTrabalho)
        val btnVoltar = findViewById<Button>(R.id.btnVoltarCriarTrabalho)

        btnVoltar.setOnClickListener { finish() }

        // ✅ CALENDÁRIO + HORÁRIO no campo de prazo
        edtPrazo.isFocusable = false
        edtPrazo.isClickable = true
        edtPrazo.setOnClickListener {
            abrirCalendarioHorario(edtPrazo)
        }

        btnPublicar.setOnClickListener {
            val titulo = edtTitulo.text.toString().trim()
            val descricao = edtDescricao.text.toString().trim()
            val categoria = edtCategoria.text.toString().trim()
            val prazo = edtPrazo.text.toString().trim()

            if (titulo.isEmpty() || descricao.isEmpty() || categoria.isEmpty() || prazo.isEmpty()) {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnPublicar.isEnabled = false
            btnPublicar.text = "PUBLICANDO..."

            lifecycleScope.launch {
                try {
                    val equipeIdIntent = intent.getStringExtra("equipeId")

                    val equipeId = if (equipeIdIntent != null) {
                        equipeIdIntent
                    } else {
                        val equipe = db.collection("equipes")
                            .whereEqualTo("criadorEmail", email)
                            .limit(1)
                            .get()
                            .await()

                        if (equipe.isEmpty) {
                            Toast.makeText(this@CriarTrabalhoActivity, "Crie uma equipe primeiro", Toast.LENGTH_SHORT).show()
                            btnPublicar.isEnabled = true
                            btnPublicar.text = "PUBLICAR TRABALHO"
                            return@launch
                        }
                        equipe.documents[0].id
                    }

                    val trabalho = hashMapOf(
                        "titulo" to titulo,
                        "descricao" to descricao,
                        "categoria" to categoria,
                        "prazo" to prazo,
                        "equipeId" to equipeId,
                        "criadorEmail" to email,
                        "responsavelEmail" to email,
                        "status" to "pendente",
                        "criadoEm" to System.currentTimeMillis()
                    )

                    val trabalhoId = db.collection("trabalhos").add(trabalho).await().id

                    val membros = db.collection("membros_equipe")
                        .whereEqualTo("equipeId", equipeId)
                        .get()
                        .await()

                    val emailsParaNotificar = membros.documents
                        .mapNotNull { it.getString("email") }
                        .filter { it != email }

                    if (emailsParaNotificar.isNotEmpty()) {
                        val nomeEquipe = db.collection("equipes").document(equipeId)
                            .get().await().getString("nome") ?: "Equipe"

                        val nomeRemetente = db.collection("usuarios").document(email)
                            .get().await().getString("nome") ?: email

                        NotificacaoHelper.notificarNovoTrabalhoParaMembros(
                            emailsMembros = emailsParaNotificar,
                            remetente = email,
                            nomeRemetente = nomeRemetente,
                            trabalhoId = trabalhoId,
                            tituloTrabalho = titulo,
                            nomeEquipe = nomeEquipe
                        )
                    }

                    Log.d("CRIAR_TRABALHO", "Trabalho publicado com prazo: $prazo")
                    Toast.makeText(this@CriarTrabalhoActivity, "Trabalho publicado!", Toast.LENGTH_SHORT).show()
                    finish()

                } catch (e: Exception) {
                    Log.e("CRIAR_TRABALHO", "Erro: ${e.message}")
                    Toast.makeText(this@CriarTrabalhoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                    btnPublicar.isEnabled = true
                    btnPublicar.text = "PUBLICAR TRABALHO"
                }
            }
        }

        configurarBottomNavigation()
    }

    // ============================================================
    // ✅ CALENDÁRIO + HORÁRIO
    // ============================================================
    private fun abrirCalendarioHorario(edtPrazo: EditText) {
        val calendario = Calendar.getInstance()

        val datePicker = DatePickerDialog(
            this,
            { _, ano, mes, dia ->
                calendario.set(Calendar.YEAR, ano)
                calendario.set(Calendar.MONTH, mes)
                calendario.set(Calendar.DAY_OF_MONTH, dia)

                val timePicker = TimePickerDialog(
                    this,
                    { _, hora, minuto ->
                        calendario.set(Calendar.HOUR_OF_DAY, hora)
                        calendario.set(Calendar.MINUTE, minuto)

                        val formato = SimpleDateFormat(
                            "dd/MM/yyyy 'às' HH:mm",
                            Locale("pt", "BR")
                        )
                        edtPrazo.setText(formato.format(calendario.time))
                    },
                    calendario.get(Calendar.HOUR_OF_DAY),
                    calendario.get(Calendar.MINUTE),
                    true
                )
                timePicker.show()
            },
            calendario.get(Calendar.YEAR),
            calendario.get(Calendar.MONTH),
            calendario.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    private fun configurarBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, MainActivity::class.java)); finish(); true }
                R.id.nav_chat -> { startActivity(Intent(this, ListaConversasActivity::class.java)); finish(); true }
                R.id.nav_groups -> { startActivity(Intent(this, MinhasEquipesActivity::class.java)); finish(); true }
                R.id.nav_notifications -> { startActivity(Intent(this, NotificacoesActivity::class.java)); finish(); true }
                R.id.nav_profile -> { startActivity(Intent(this, perfil::class.java)); finish(); true }
                else -> false
            }
        }
    }
}