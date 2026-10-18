package com.example.plataformaremota

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CriarTrabalhoActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // ✅ Cache local de membros da equipe (pra usar no dropdown)
    private var membrosEquipe: List<Pair<String, String>> = emptyList() // (email, nome)
    private var equipeIdCarregada: String = ""

    // ✅ Participante selecionado
    private var participanteEmail: String = ""
    private var participanteNome: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_criar_trabalho)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        val email = auth.currentUser?.email ?: ""

        val edtTitulo = findViewById<EditText>(R.id.edtTituloTrabalho)
        val edtDescricao = findViewById<EditText>(R.id.edtDescricaoTrabalho)
        val edtCategoria = findViewById<EditText>(R.id.edtCategoriaTrabalho)
        val edtParticipante = findViewById<EditText>(R.id.edtParticipanteTrabalho)
        val edtPrazo = findViewById<EditText>(R.id.edtPrazoTrabalho)
        val btnPublicar = findViewById<Button>(R.id.btnPublicarTrabalho)
        val btnVoltar = findViewById<Button>(R.id.btnVoltarCriarTrabalho)

        btnVoltar.setOnClickListener { finish() }

        // ============================================================
        // CALENDÁRIO
        // ============================================================
        edtPrazo.isFocusable = false
        edtPrazo.isClickable = true
        edtPrazo.setOnClickListener {
            abrirCalendarioHorario(edtPrazo)
        }

        // ============================================================
        // DROPDOWN DE PARTICIPANTE
        // ============================================================
        edtParticipante.isFocusable = false
        edtParticipante.isClickable = true
        edtParticipante.setOnClickListener {
            if (membrosEquipe.isEmpty()) {
                Toast.makeText(
                    this,
                    getString(R.string.criar_trabalho_equipe_sem_membros),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            abrirSeletorParticipante(edtParticipante)
        }

        // ============================================================
        // CARREGAR MEMBROS (async)
        // ============================================================
        lifecycleScope.launch {
            try {
                val equipeIdIntent = intent.getStringExtra("equipeId")

                equipeIdCarregada = if (equipeIdIntent != null) {
                    equipeIdIntent
                } else {
                    val equipe = db.collection("equipes")
                        .whereEqualTo("criadorEmail", email)
                        .limit(1)
                        .get()
                        .await()

                    if (equipe.isEmpty) {
                        Toast.makeText(
                            this@CriarTrabalhoActivity,
                            getString(R.string.criar_trabalho_criar_equipe_primeiro),
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                        return@launch
                    }
                    equipe.documents[0].id
                }

                // Carrega membros
                val snap = db.collection("membros_equipe")
                    .whereEqualTo("equipeId", equipeIdCarregada)
                    .get()
                    .await()

                membrosEquipe = snap.documents.mapNotNull { doc ->
                    val e = doc.getString("email") ?: return@mapNotNull null
                    val n = doc.getString("nome") ?: e
                    Pair(e, n)
                }.sortedBy { it.second.lowercase() }

                Log.d(TAG, "Membros carregados: ${membrosEquipe.size}")

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar membros: ${e.message}")
            }
        }

        // ============================================================
        // PUBLICAR
        // ============================================================
        btnPublicar.setOnClickListener {
            val titulo = edtTitulo.text.toString().trim()
            val descricao = edtDescricao.text.toString().trim()
            val categoria = edtCategoria.text.toString().trim()
            val prazo = edtPrazo.text.toString().trim()

            if (titulo.isEmpty() || descricao.isEmpty() || categoria.isEmpty() || prazo.isEmpty()) {
                Toast.makeText(
                    this,
                    getString(R.string.criar_trabalho_preencher_campos),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (participanteEmail.isEmpty()) {
                Toast.makeText(
                    this,
                    getString(R.string.criar_trabalho_selecione_participante),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            btnPublicar.isEnabled = false
            btnPublicar.text = getString(R.string.criar_trabalho_publicando)

            lifecycleScope.launch {
                try {
                    val equipeId = equipeIdCarregada

                    if (equipeId.isEmpty()) {
                        Toast.makeText(
                            this@CriarTrabalhoActivity,
                            getString(R.string.criar_trabalho_criar_equipe_primeiro),
                            Toast.LENGTH_SHORT
                        ).show()
                        btnPublicar.isEnabled = true
                        btnPublicar.text = getString(R.string.criar_trabalho_btn_publicar)
                        return@launch
                    }

                    // ✅ Salva com participanteEmail + responsavelEmail (compatibilidade)
                    val trabalho = hashMapOf(
                        "titulo" to titulo,
                        "descricao" to descricao,
                        "categoria" to categoria,
                        "prazo" to prazo,
                        "equipeId" to equipeId,
                        "criadorEmail" to email,
                        "participanteEmail" to participanteEmail,
                        "participanteNome" to participanteNome,
                        "responsavelEmail" to participanteEmail, // compatibilidade
                        "status" to "pendente",
                        "criadoEm" to System.currentTimeMillis()
                    )

                    val trabalhoId = db.collection("trabalhos").add(trabalho).await().id

                    // ============================================================
                    // NOTIFICAR MEMBROS (exceto criador e participante)
                    // ============================================================
                    val membros = db.collection("membros_equipe")
                        .whereEqualTo("equipeId", equipeId)
                        .get()
                        .await()

                    val emailsParaNotificar = membros.documents
                        .mapNotNull { it.getString("email") }
                        .filter { it != email && it != participanteEmail }

                    if (emailsParaNotificar.isNotEmpty()) {
                        val nomeEquipe = db.collection("equipes").document(equipeId)
                            .get().await().getString("nome")
                            ?: getString(R.string.criar_trabalho_equipe_fallback)

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

                    // ✅ Notifica o participante separadamente
                    try {
                        val nomeRemetente = db.collection("usuarios").document(email)
                            .get().await().getString("nome") ?: email
                        val nomeEquipe = db.collection("equipes").document(equipeId)
                            .get().await().getString("nome")
                            ?: getString(R.string.criar_trabalho_equipe_fallback)

                        NotificacaoHelper.criar(
                            destinatario = participanteEmail,
                            tipo = "novo_trabalho_participante",
                            titulo = getString(R.string.criar_trabalho_notif_participante_titulo, titulo),
                            mensagem = getString(R.string.criar_trabalho_notif_participante_msg, nomeRemetente, nomeEquipe),
                            referenciaId = trabalhoId,
                            referenciaTipo = "trabalho",
                            remetente = email,
                            nomeRemetente = nomeRemetente
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao notificar participante: ${e.message}")
                    }

                    Log.d(TAG, "Trabalho publicado com prazo: $prazo para $participanteEmail")
                    Toast.makeText(
                        this@CriarTrabalhoActivity,
                        getString(R.string.criar_trabalho_publicado_sucesso),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()

                } catch (e: Exception) {
                    Log.e(TAG, "Erro: ${e.message}")
                    Toast.makeText(
                        this@CriarTrabalhoActivity,
                        getString(R.string.erro_generico, e.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                    btnPublicar.isEnabled = true
                    btnPublicar.text = getString(R.string.criar_trabalho_btn_publicar)
                }
            }
        }

        configurarBottomNavigation(R.id.nav_home)
    }

    // ============================================================
    // SELETOR DE PARTICIPANTE
    // ============================================================
    private fun abrirSeletorParticipante(edtParticipante: EditText) {
        val nomes = membrosEquipe.map { "${it.second} (${it.first})" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.criar_trabalho_selecionar_participante))
            .setItems(nomes) { _, which ->
                val (email, nome) = membrosEquipe[which]
                participanteEmail = email
                participanteNome = nome
                edtParticipante.setText(nome)
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    // ============================================================
    // CALENDARIO + HORARIO
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

                        val formatoData = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        val formatoHora = SimpleDateFormat("HH:mm", Locale.getDefault())

                        val dataStr = formatoData.format(calendario.time)
                        val horaStr = formatoHora.format(calendario.time)

                        edtPrazo.setText(
                            getString(R.string.criar_trabalho_data_hora_formato, dataStr, horaStr)
                        )
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

    companion object {
        private const val TAG = "CRIAR_TRABALHO"
    }
}