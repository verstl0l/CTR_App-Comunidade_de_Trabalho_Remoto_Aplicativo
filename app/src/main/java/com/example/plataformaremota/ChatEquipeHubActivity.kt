package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChatEquipeHubActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var equipeId: String = ""
    private var nomeEquipe: String = ""
    private var ehDono: Boolean = false

    private var carregando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_equipe_hub)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""
        equipeId = intent.getStringExtra("equipeId") ?: ""
        nomeEquipe = getString(R.string.equipe_default_nome)

        if (equipeId.isEmpty()) {
            Toast.makeText(this, getString(R.string.chat_equipe_nao_encontrada), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<Button>(R.id.btnVoltarHub).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        if (equipeId.isNotEmpty()) {
            carregando = false
            carregarDados()
        }
    }

    // ============================================================
    // CARREGAR TUDO (sequencial)
    // ============================================================
    private fun carregarDados() {
        if (carregando) return
        carregando = true

        lifecycleScope.launch {
            try {
                // 1. Dados da equipe
                val equipeDoc = db.collection("equipes").document(equipeId).get().await()
                nomeEquipe = equipeDoc.getString("nome") ?: getString(R.string.equipe_default_nome)
                val criadorEmail = equipeDoc.getString("criadorEmail") ?: ""
                ehDono = criadorEmail.equals(emailUsuario, ignoreCase = true)

                findViewById<TextView>(R.id.txtNomeEquipeHub).text = nomeEquipe

                // Botao criar grupo
                val btnCriarGrupo = findViewById<Button>(R.id.btnCriarGrupoHub)
                btnCriarGrupo.visibility = if (ehDono) View.VISIBLE else View.GONE
                btnCriarGrupo.setOnClickListener { abrirDialogNovoGrupo() }

                // 2. Chat geral
                montarChatGeral()

                // 3. Grupos
                montarGrupos()

                // 4. Individuais
                montarIndividuais()

            } catch (e: Exception) {
                Log.e("HUB", "Erro: ${e.message}")
                Toast.makeText(
                    this@ChatEquipeHubActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                carregando = false
            }
        }
    }

    // ============================================================
    // 1. CHAT GERAL
    // ============================================================
    private suspend fun montarChatGeral() {
        try {
            val container = findViewById<LinearLayout>(R.id.containerChatGeral)
            container.removeAllViews()

            val chatDoc = db.collection("chats_equipe").document(equipeId).get().await()

            val ultimaMsg = if (chatDoc.exists()) {
                chatDoc.getString("ultimaMensagem") ?: getString(R.string.chat_hub_sem_mensagem)
            } else getString(R.string.chat_hub_sem_mensagem)

            val naoLidas = if (chatDoc.exists()) {
                val mapa = chatDoc.get("naoLidas") as? Map<*, *>
                (mapa?.get(emailUsuario) as? Long)?.toInt() ?: 0
            } else 0

            val view = montarLinha("📢", getString(R.string.chat_hub_chat_geral), ultimaMsg, naoLidas)

            view.setOnClickListener {
                val i = Intent(this@ChatEquipeHubActivity, ChatEquipeActivity::class.java)
                i.putExtra("equipeId", equipeId)
                startActivity(i)
            }

            container.addView(view)
        } catch (e: Exception) {
            Log.e("HUB", "Erro chat geral: ${e.message}")
        }
    }

    // ============================================================
    // 2. GRUPOS
    // ============================================================
    private suspend fun montarGrupos() {
        try {
            val container = findViewById<LinearLayout>(R.id.containerGrupos)
            val section = findViewById<LinearLayout>(R.id.containerGruposSection)
            container.removeAllViews()

            Log.d("HUB", "Buscando grupos com equipeId=$equipeId")

            val grupos = db.collection("grupos")
                .whereEqualTo("equipeId", equipeId)
                .get()
                .await()

            Log.d("HUB", "Grupos encontrados: ${grupos.size()}")

            if (grupos.isEmpty) {
                section.visibility = View.GONE
                return
            }

            section.visibility = View.VISIBLE

            grupos.documents.forEach { doc ->
                val grupoId = doc.id
                val nomeGrupo = doc.getString("nomeGrupo") ?: getString(R.string.grupo_padrao)

                val ultimaMsgDoc = doc.reference.collection("mensagens")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .await()

                val preview = if (!ultimaMsgDoc.isEmpty) {
                    val m = ultimaMsgDoc.documents[0]
                    val nome = m.getString("nomeRemetente") ?: getString(R.string.usuario_padrao)
                    val texto = m.getString("texto") ?: ""
                    val tipo = m.getString("tipo") ?: "texto"
                    val apagada = m.getBoolean("apagada") ?: false
                    when {
                        apagada -> "$nome: ${getString(R.string.chat_hub_msg_apagada)}"
                        tipo == "foto" -> "$nome: ${getString(R.string.chat_hub_foto)}"
                        tipo == "video" -> "$nome: ${getString(R.string.chat_hub_video)}"
                        tipo == "arquivo" -> "$nome: ${getString(R.string.chat_hub_arquivo)}"
                        else -> "$nome: $texto"
                    }
                } else getString(R.string.chat_hub_sem_mensagem)

                val naoLidas = doc.reference.collection("mensagens")
                    .whereEqualTo("lida", false)
                    .get()
                    .await()
                    .documents
                    .count { it.getString("remetente") != emailUsuario }

                val view = montarLinha("💬", nomeGrupo, preview, naoLidas)

                view.setOnClickListener {
                    val i = Intent(this@ChatEquipeHubActivity, ChatGrupoActivity::class.java)
                    i.putExtra("grupoId", grupoId)
                    i.putExtra("nomeGrupo", nomeGrupo)
                    startActivity(i)
                }

                container.addView(view)
            }
        } catch (e: Exception) {
            Log.e("HUB", "Erro grupos: ${e.message}")
        }
    }

    // ============================================================
    // 3. INDIVIDUAIS
    // ============================================================
    private suspend fun montarIndividuais() {
        try {
            val container = findViewById<LinearLayout>(R.id.containerIndividuais)
            container.removeAllViews()

            val membros = db.collection("membros_equipe")
                .whereEqualTo("equipeId", equipeId)
                .get()
                .await()

            val outros = membros.documents
                .mapNotNull { doc ->
                    val email = doc.getString("email") ?: ""
                    val nome = doc.getString("nome") ?: email
                    if (email.isEmpty() || email.equals(emailUsuario, ignoreCase = true)) null
                    else Pair(email, nome)
                }
                .sortedBy { it.second.lowercase() }

            if (outros.isEmpty()) {
                val txtVazio = TextView(this@ChatEquipeHubActivity).apply {
                    text = getString(R.string.chat_hub_sem_outro_membro)
                    setTextColor(ContextCompat.getColor(this@ChatEquipeHubActivity, R.color.text_secondary))
                    textSize = 13f
                    setPadding(8, 16, 8, 16)
                }
                container.addView(txtVazio)
                return
            }

            val todosMeusChats = db.collection("chats")
                .whereArrayContains("participantes", emailUsuario)
                .get()
                .await()

            outros.forEach { (outroEmail, outroNome) ->
                val chatExistente = todosMeusChats.documents.find { doc ->
                    val parts = doc.get("participantes") as? List<*>
                    parts?.contains(outroEmail) == true
                }

                val preview = if (chatExistente != null) {
                    chatExistente.getString("ultimaMensagem") ?: getString(R.string.chat_hub_sem_mensagem)
                } else getString(R.string.chat_hub_toque_iniciar)

                val naoLidas = if (chatExistente != null) {
                    val mapa = chatExistente.get("naoLidas") as? Map<*, *>
                    (mapa?.get(emailUsuario) as? Long)?.toInt() ?: 0
                } else 0

                val view = montarLinha("👤", outroNome, preview, naoLidas)

                view.setOnClickListener {
                    val i = Intent(this@ChatEquipeHubActivity, ChatActivity::class.java)
                    i.putExtra("outroEmail", outroEmail)
                    startActivity(i)
                }

                container.addView(view)
            }
        } catch (e: Exception) {
            Log.e("HUB", "Erro individuais: ${e.message}")
        }
    }

    // ============================================================
    // MONTAR LINHA
    // ============================================================
    private fun montarLinha(icone: String, nome: String, preview: String, naoLidas: Int): View {
        val view = LayoutInflater.from(this).inflate(R.layout.item_hub_linha, null, false)

        view.findViewById<TextView>(R.id.txtIconeHub).text = icone
        view.findViewById<TextView>(R.id.txtNomeHub).text = nome
        view.findViewById<TextView>(R.id.txtPreviewHub).text = preview

        val txtBadge = view.findViewById<TextView>(R.id.txtBadgeHub)
        if (naoLidas > 0) {
            txtBadge.text = if (naoLidas > 99) "99+" else naoLidas.toString()
            txtBadge.visibility = View.VISIBLE
        } else {
            txtBadge.visibility = View.GONE
        }

        return view
    }

    // ============================================================
    // CRIAR GRUPO
    // ============================================================
    private fun abrirDialogNovoGrupo() {
        val edtNome = EditText(this).apply {
            hint = getString(R.string.chat_hub_novo_grupo_hint)
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.chat_hub_novo_grupo_titulo))
            .setView(edtNome)
            .setPositiveButton(getString(R.string.chat_hub_novo_grupo_criar)) { _, _ ->
                val nome = edtNome.text.toString().trim()
                if (nome.isEmpty()) return@setPositiveButton

                lifecycleScope.launch {
                    try {
                        val grupo = hashMapOf(
                            "equipeId" to equipeId,
                            "nomeGrupo" to nome,
                            "criadorEmail" to emailUsuario,
                            "membros" to listOf(emailUsuario),
                            "criadoEm" to System.currentTimeMillis()
                        )

                        val grupoId = db.collection("grupos").add(grupo).await().id
                        Log.d("HUB", "Grupo criado: $grupoId")

                        val userRef = db.collection("usuarios").document(emailUsuario)
                        val userDoc = userRef.get().await()
                        val ids = (userDoc.get("gruposIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                        if (grupoId !in ids) {
                            userRef.update("gruposIds", ids + grupoId).await()
                        }

                        Toast.makeText(this@ChatEquipeHubActivity, getString(R.string.chat_hub_grupo_criado), Toast.LENGTH_SHORT).show()

                        carregando = false
                        carregarDados()

                    } catch (e: Exception) {
                        Toast.makeText(
                            this@ChatEquipeHubActivity,
                            getString(R.string.erro_generico, e.message ?: ""),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }
}