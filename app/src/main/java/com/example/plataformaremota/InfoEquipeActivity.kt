package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class InfoEquipeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var equipeId: String = ""
    private var nomeEquipe: String = ""
    private var criadorEmail: String = ""
    private var ehDono: Boolean = false
    private var ehAdmin: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info_equipe)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""
        equipeId = intent.getStringExtra("equipeId") ?: ""

        if (equipeId.isEmpty()) {
            Toast.makeText(this, "Equipe não encontrada", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val btnVoltar = findViewById<Button>(R.id.btnVoltarInfoEquipe)
        btnVoltar.setOnClickListener { finish() }

        val btnCriarGrupo = findViewById<Button>(R.id.btnCriarGrupoInfo)
        val btnVerGrupos = findViewById<Button>(R.id.btnVerGruposInfo)
        val btnSairEquipe = findViewById<Button>(R.id.btnSairEquipeInfo)
        val btnExcluirEquipe = findViewById<Button>(R.id.btnExcluirEquipeInfo)

        carregarInfo()

        btnCriarGrupo.setOnClickListener {
            val intent = Intent(this, GruposActivity::class.java)
            intent.putExtra("equipeId", equipeId)
            startActivity(intent)
        }

        btnVerGrupos.setOnClickListener {
            val intent = Intent(this, GruposActivity::class.java)
            intent.putExtra("equipeId", equipeId)
            startActivity(intent)
        }

        btnSairEquipe.setOnClickListener {
            confirmarSairEquipe()
        }

        btnExcluirEquipe.setOnClickListener {
            confirmarExcluirEquipe()
        }
    }

    private fun carregarInfo() {
        lifecycleScope.launch {
            try {
                val equipeDoc = db.collection("equipes").document(equipeId).get().await()
                nomeEquipe = equipeDoc.getString("nome") ?: "Equipe"
                criadorEmail = equipeDoc.getString("criadorEmail") ?: ""
                ehDono = criadorEmail == emailUsuario

                findViewById<TextView>(R.id.txtNomeEquipeInfo).text = nomeEquipe
                findViewById<TextView>(R.id.txtDescricaoEquipeInfo).text =
                    equipeDoc.getString("descricao") ?: "Sem descrição"

                val membro = db.collection("membros_equipe")
                    .whereEqualTo("equipeId", equipeId)
                    .whereEqualTo("email", emailUsuario)
                    .limit(1)
                    .get()
                    .await()

                ehAdmin = !membro.isEmpty &&
                        membro.documents[0].getString("funcao") == "administrador"

                val btnExcluir = findViewById<Button>(R.id.btnExcluirEquipeInfo)
                val btnSair = findViewById<Button>(R.id.btnSairEquipeInfo)
                val btnCriarGrupo = findViewById<Button>(R.id.btnCriarGrupoInfo)

                if (ehDono) {
                    btnExcluir.visibility = View.VISIBLE
                    btnSair.visibility = View.GONE
                    btnCriarGrupo.visibility = View.VISIBLE
                } else if (ehAdmin) {
                    btnExcluir.visibility = View.GONE
                    btnSair.visibility = View.VISIBLE
                    btnCriarGrupo.visibility = View.VISIBLE
                } else {
                    btnExcluir.visibility = View.GONE
                    btnSair.visibility = View.VISIBLE
                    btnCriarGrupo.visibility = View.GONE
                }

                carregarMembros()

            } catch (e: Exception) {
                Log.e("INFO_EQUIPE", "Erro: ${e.message}")
            }
        }
    }

    private fun carregarMembros() {
        val container = findViewById<LinearLayout>(R.id.containerMembrosInfo)
        container.removeAllViews()

        lifecycleScope.launch {
            try {
                val membros = db.collection("membros_equipe")
                    .whereEqualTo("equipeId", equipeId)
                    .get()
                    .await()

                val inflater = LayoutInflater.from(this@InfoEquipeActivity)

                if (membros.isEmpty) {
                    val txtVazio = TextView(this@InfoEquipeActivity).apply {
                        text = "Nenhum membro ainda"
                        setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                        textSize = 14f
                        setPadding(0, 16, 0, 16)
                    }
                    container.addView(txtVazio)
                    return@launch
                }

                membros.documents.forEach { doc ->
                    val emailMembro = doc.getString("email") ?: ""
                    val nomeMembro = doc.getString("nome") ?: "Usuário"
                    val funcao = doc.getString("funcao") ?: "membro"

                    val view = inflater.inflate(R.layout.item_membro_info, container, false)

                    val txtNome = view.findViewById<TextView>(R.id.txtNomeMembro)
                    val txtFuncao = view.findViewById<TextView>(R.id.txtFuncaoMembro)

                    txtNome.text = nomeMembro
                    txtFuncao.text = if (funcao == "administrador") "Administrador" else "Membro"

                    view.setOnClickListener {
                        mostrarOpcoesMembro(emailMembro, nomeMembro)
                    }

                    container.addView(view)
                }

            } catch (e: Exception) {
                Log.e("INFO_EQUIPE", "Erro membros: ${e.message}")
            }
        }
    }

    private fun mostrarOpcoesMembro(email: String, nome: String) {
        val opcoes = mutableListOf<String>()

        if (email != emailUsuario) {
            opcoes.add("Conversar no PV")
            opcoes.add("Ver perfil")
        } else {
            opcoes.add("Ver meu perfil")
        }

        AlertDialog.Builder(this)
            .setTitle(nome)
            .setItems(opcoes.toTypedArray()) { _, which ->
                when (opcoes[which]) {
                    "Conversar no PV" -> abrirChatPV(email)
                    "Ver perfil" -> abrirPerfil(email)
                    "Ver meu perfil" -> {
                        startActivity(Intent(this, perfil::class.java))
                    }
                }
            }
            .show()
    }

    private fun abrirChatPV(email: String) {
        lifecycleScope.launch {
            try {
                val existente = db.collection("chats")
                    .whereArrayContains("participantes", emailUsuario)
                    .get().await()

                val chatExistente = existente.documents.find { doc ->
                    val parts = doc.get("participantes") as? List<*>
                    parts?.contains(email) == true
                }

                val chatId = if (chatExistente != null) {
                    chatExistente.id
                } else {
                    val novoChat = hashMapOf(
                        "participantes" to listOf(emailUsuario, email),
                        "ultimaMensagem" to "",
                        "atualizadoEm" to System.currentTimeMillis(),
                        "tipo" to "individual"
                    )
                    db.collection("chats").add(novoChat).await().id
                }

                val intent = Intent(this@InfoEquipeActivity, ChatActivity::class.java)
                intent.putExtra("outroEmail", email)
                intent.putExtra("chatId", chatId)
                startActivity(intent)

            } catch (e: Exception) {
                Toast.makeText(this@InfoEquipeActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun abrirPerfil(email: String) {
        val intent = Intent(this, PerfilUsuarioActivity::class.java)
        intent.putExtra("emailOutro", email)
        startActivity(intent)
    }

    private fun confirmarSairEquipe() {
        AlertDialog.Builder(this)
            .setTitle("Sair da equipe")
            .setMessage("Tem certeza que deseja sair da equipe $nomeEquipe?")
            .setPositiveButton("Sair") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val membro = db.collection("membros_equipe")
                            .whereEqualTo("equipeId", equipeId)
                            .whereEqualTo("email", emailUsuario)
                            .get().await()

                        membro.documents.forEach { doc ->
                            db.collection("membros_equipe").document(doc.id).delete().await()
                        }

                        Toast.makeText(this@InfoEquipeActivity, "Você saiu da equipe", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@InfoEquipeActivity, MainActivity::class.java))
                        finishAffinity()
                    } catch (e: Exception) {
                        Toast.makeText(this@InfoEquipeActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ============================================================
    // ✅ BUG CORRIGIDO: deleta subcoleções antes do doc raiz
    // ============================================================
    private fun confirmarExcluirEquipe() {
        AlertDialog.Builder(this)
            .setTitle("Excluir equipe")
            .setMessage("Tem certeza? Todos os dados serão removidos permanentemente.")
            .setPositiveButton("Excluir") { _, _ ->
                lifecycleScope.launch {
                    try {
                        // 1. Trabalhos
                        val trabalhos = db.collection("trabalhos").whereEqualTo("equipeId", equipeId).get().await()
                        trabalhos.documents.forEach { db.collection("trabalhos").document(it.id).delete().await() }

                        // 2. Membros
                        val membros = db.collection("membros_equipe").whereEqualTo("equipeId", equipeId).get().await()
                        membros.documents.forEach { db.collection("membros_equipe").document(it.id).delete().await() }

                        // 3. Convites
                        val convites = db.collection("convites_equipe").whereEqualTo("equipeId", equipeId).get().await()
                        convites.documents.forEach { db.collection("convites_equipe").document(it.id).delete().await() }

                        // 4. Pedidos
                        val pedidos = db.collection("pedidos_entrada").whereEqualTo("equipeId", equipeId).get().await()
                        pedidos.documents.forEach { db.collection("pedidos_entrada").document(it.id).delete().await() }

                        // 5. ✅ Mensagens do chat de equipe ANTES do doc raiz
                        val msgsEquipe = db.collection("chats_equipe").document(equipeId)
                            .collection("mensagens").get().await()
                        msgsEquipe.documents.forEach {
                            db.collection("chats_equipe").document(equipeId)
                                .collection("mensagens").document(it.id).delete().await()
                        }

                        // 6. ✅ Grupos + suas mensagens
                        val grupos = db.collection("grupos").whereEqualTo("equipeId", equipeId).get().await()
                        grupos.documents.forEach { grupoDoc ->
                            val msgsGrupo = grupoDoc.reference.collection("mensagens").get().await()
                            msgsGrupo.documents.forEach { it.reference.delete().await() }
                            grupoDoc.reference.delete().await()
                        }

                        // 7. ✅ Doc raiz do chat de equipe
                        db.collection("chats_equipe").document(equipeId).delete().await()

                        // 8. ✅ Equipe
                        db.collection("equipes").document(equipeId).delete().await()

                        Toast.makeText(this@InfoEquipeActivity, "Equipe excluída!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@InfoEquipeActivity, MainActivity::class.java))
                        finishAffinity()

                    } catch (e: Exception) {
                        Toast.makeText(this@InfoEquipeActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}