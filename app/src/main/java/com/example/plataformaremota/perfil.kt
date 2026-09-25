package com.example.plataformaremota

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class perfil : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var email: String = ""
    private lateinit var imgAvatar: ImageView
    private lateinit var txtIniciais: TextView

    private val selecionarImagem = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri = result.data?.data
            if (imageUri != null) {
                fazerUploadImagem(imageUri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_perfil)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val prefs = getSharedPreferences("CTR_PREFS", Context.MODE_PRIVATE)
        email = auth.currentUser?.email ?: prefs.getString("emailUsuario", "") ?: ""

        val txtNomePerfil = findViewById<TextView>(R.id.txtNomePerfil)
        val txtProfissaoPerfil = findViewById<TextView>(R.id.txtProfissaoPerfil)
        val txtEmailPerfil = findViewById<TextView>(R.id.txtEmailPerfil)
        val txtProfissaoCard = findViewById<TextView>(R.id.txtProfissaoCard)
        val txtEquipeCard = findViewById<TextView>(R.id.txtEquipeCard)
        val btnAdicionarLink = findViewById<MaterialButton>(R.id.btnAdicionarLink)

        val cardAvatar = findViewById<MaterialCardView>(R.id.cardAvatar)
        val cardCamera = findViewById<MaterialCardView>(R.id.cardCamera)
        imgAvatar = findViewById(R.id.imgAvatar)
        txtIniciais = findViewById(R.id.txtIniciais)

        txtEmailPerfil.text = email

        carregarDados(txtNomePerfil, txtProfissaoPerfil, txtProfissaoCard, txtEquipeCard)
        carregarLinks()
        carregarFotoPerfil()

        cardAvatar.setOnClickListener { abrirGaleria() }
        cardCamera.setOnClickListener { abrirGaleria() }

        btnAdicionarLink.setOnClickListener {
            abrirDialogAdicionarLink()
        }

        val btnVoltar = findViewById<Button>(R.id.btnVoltar)
        btnVoltar.text = getString(R.string.voltar)
        btnVoltar.setOnClickListener { finish() }

        val btnMeusTrabalhos = findViewById<Button>(R.id.btnMeusTrabalhos)
        btnMeusTrabalhos.setOnClickListener {
            startActivity(Intent(this, participantes::class.java))
        }

        // Botao de favoritos
        val btnFavoritos = findViewById<Button>(R.id.btnMensagensFavoritas)
        btnFavoritos.text = getString(R.string.perfil_favoritos)
        btnFavoritos.setOnClickListener {
            startActivity(Intent(this, MensagensFavoritasActivity::class.java))
        }

        val btnSair = findViewById<Button>(R.id.btnSair)
        btnSair.text = getString(R.string.perfil_sair)
        btnSair.setOnClickListener {
            auth.signOut()
            prefs.edit()
                .putBoolean("logado", false)
                .remove("emailUsuario")
                .remove("nomeUsuario")
                .remove("profissaoUsuario")
                .apply()

            Toast.makeText(this, "Saindo da conta...", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }

        // Bottom nav em 1 linha
        configurarBottomNavigation(R.id.nav_profile)
    }

    // ============================================================
    // ABRIR GALERIA
    // ============================================================
    private fun abrirGaleria() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        selecionarImagem.launch(intent)
    }

    // ============================================================
    // UPLOAD DA FOTO (Cloudinary)
    // ============================================================
    private fun fazerUploadImagem(uri: Uri) {
        Toast.makeText(this, "Enviando foto...", Toast.LENGTH_SHORT).show()

        MediaManager.get().upload(uri)
            .unsigned("fqb729sb")
            .option("folder", "avatares/")
            .callback(object : com.cloudinary.android.callback.UploadCallback {
                override fun onStart(requestId: String?) {
                    Log.d("UPLOAD", "Iniciando upload...")
                }

                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}

                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val url = resultData?.get("secure_url") as? String
                    if (url != null) {
                        runOnUiThread { salvarUrlNoFirestore(url) }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@perfil, "Erro: URL nao encontrada", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    Log.e("UPLOAD", "Erro: ${error?.description}")
                    runOnUiThread {
                        Toast.makeText(this@perfil, "Erro no upload: ${error?.description}", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            })
            .dispatch()
    }

    // ============================================================
    // SALVA URL NO FIRESTORE
    // ============================================================
    private fun salvarUrlNoFirestore(url: String) {
        lifecycleScope.launch {
            try {
                db.collection("usuarios").document(email).update("fotoUrl", url).await()
                Glide.with(this@perfil).load(url).circleCrop().into(imgAvatar)
                txtIniciais.visibility = View.GONE
                Toast.makeText(this@perfil, "Foto atualizada", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@perfil,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ============================================================
    // CARREGA FOTO DO PERFIL
    // ============================================================
    private fun carregarFotoPerfil() {
        lifecycleScope.launch {
            try {
                val usuarioDoc = db.collection("usuarios").document(email).get().await()
                val fotoUrl = usuarioDoc.getString("fotoUrl") ?: ""
                val nome = usuarioDoc.getString("nome") ?: "ME"

                if (fotoUrl.isNotEmpty()) {
                    Glide.with(this@perfil).load(fotoUrl).circleCrop().into(imgAvatar)
                    txtIniciais.visibility = View.GONE
                } else {
                    val iniciais = nome.split(" ")
                        .take(2)
                        .map { it.firstOrNull()?.uppercase() ?: "" }
                        .joinToString("")
                    txtIniciais.text = iniciais.ifEmpty { "ME" }
                    txtIniciais.visibility = View.VISIBLE
                    imgAvatar.setImageDrawable(null)
                }
            } catch (e: Exception) {
                Log.e("PERFIL", "Erro foto: ${e.message}")
            }
        }
    }

    // ============================================================
    // CARREGA DADOS DO USUARIO
    // ============================================================
    private fun carregarDados(
        txtNomePerfil: TextView,
        txtProfissaoPerfil: TextView,
        txtProfissaoCard: TextView,
        txtEquipeCard: TextView
    ) {
        lifecycleScope.launch {
            try {
                val usuarioDoc = db.collection("usuarios").document(email).get().await()
                val nome = usuarioDoc.getString("nome") ?: "Usuário"
                val profissao = usuarioDoc.getString("profissao") ?: "Profissão"

                txtNomePerfil.text = nome
                txtProfissaoPerfil.text = profissao
                txtProfissaoCard.text = profissao

                val equipeCriador = db.collection("equipes")
                    .whereEqualTo("criadorEmail", email)
                    .limit(1)
                    .get()
                    .await()

                if (!equipeCriador.isEmpty) {
                    txtEquipeCard.text = equipeCriador.documents[0].getString("nome")
                } else {
                    val membro = db.collection("membros_equipe")
                        .whereEqualTo("email", email)
                        .limit(1)
                        .get()
                        .await()

                    if (!membro.isEmpty) {
                        val equipeId = membro.documents[0].getString("equipeId") ?: ""
                        val equipeDoc = db.collection("equipes").document(equipeId).get().await()
                        txtEquipeCard.text = equipeDoc.getString("nome") ?: "Nenhuma equipe"
                    } else {
                        txtEquipeCard.text = "Nenhuma equipe"
                    }
                }

            } catch (e: Exception) {
                Log.e("PERFIL", "Erro: ${e.message}")
            }
        }
    }

    // ============================================================
    // CARREGA OS LINKS (lista nova com migracao automatica)
    // ============================================================
    private fun carregarLinks() {
        val containerLinks = findViewById<LinearLayout>(R.id.containerLinks)
        val txtSemLinks = findViewById<TextView>(R.id.txtSemLinks)

        containerLinks.removeAllViews()

        lifecycleScope.launch {
            try {
                val usuarioDoc = db.collection("usuarios").document(email).get().await()

                val links = usuarioDoc.get("links") as? List<Map<String, String>> ?: emptyList()

                // Migracao automatica dos campos antigos
                val linkedinAntigo = usuarioDoc.getString("linkedin") ?: ""
                val githubAntigo = usuarioDoc.getString("github") ?: ""
                val portfolioAntigo = usuarioDoc.getString("portfolio") ?: ""

                val linksMigrados = mutableListOf<Map<String, String>>()
                linksMigrados.addAll(links)

                if (links.isEmpty()) {
                    if (linkedinAntigo.isNotEmpty()) {
                        linksMigrados.add(hashMapOf("tipo" to "linkedin", "url" to linkedinAntigo))
                    }
                    if (githubAntigo.isNotEmpty()) {
                        linksMigrados.add(hashMapOf("tipo" to "github", "url" to githubAntigo))
                    }
                    if (portfolioAntigo.isNotEmpty()) {
                        linksMigrados.add(hashMapOf("tipo" to "portfolio", "url" to portfolioAntigo))
                    }

                    if (linksMigrados.isNotEmpty()) {
                        db.collection("usuarios").document(email)
                            .update("links", linksMigrados).await()
                    }
                }

                if (linksMigrados.isEmpty()) {
                    txtSemLinks.visibility = View.VISIBLE
                    return@launch
                }

                txtSemLinks.visibility = View.GONE

                val inflater = LayoutInflater.from(this@perfil)

                linksMigrados.forEachIndexed { index, link ->
                    val tipo = link["tipo"] ?: "outro"
                    val url = link["url"] ?: ""

                    if (url.isEmpty()) return@forEachIndexed

                    val view = inflater.inflate(R.layout.item_link, containerLinks, false)

                    val cardLogo = view.findViewById<MaterialCardView>(R.id.cardLogoLink)
                    val txtLogo = view.findViewById<TextView>(R.id.txtLogoLink)
                    val txtUrl = view.findViewById<TextView>(R.id.txtUrlLink)
                    val btnRemover = view.findViewById<Button>(R.id.btnRemoverLink)

                    val (logo, corFundo, corTexto) = identificarLogo(url, tipo)
                    txtLogo.text = logo
                    cardLogo.setCardBackgroundColor(Color.parseColor(corFundo))
                    txtLogo.setTextColor(Color.parseColor(corTexto))

                    txtUrl.text = encurtarUrl(url)

                    view.setOnClickListener {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this@perfil, "Link inválido", Toast.LENGTH_SHORT).show()
                        }
                    }

                    btnRemover.setOnClickListener {
                        AlertDialog.Builder(this@perfil)
                            .setTitle("Remover link")
                            .setMessage("Deseja remover este link?")
                            .setPositiveButton("Sim") { _, _ ->
                                removerLink(index)
                            }
                            .setNegativeButton(R.string.cancelar, null)
                            .show()
                    }

                    containerLinks.addView(view)
                }

            } catch (e: Exception) {
                Log.e("PERFIL", "Erro links: ${e.message}")
            }
        }
    }

    // ============================================================
    // IDENTIFICA A LOGO PELO TIPO/URL
    // ============================================================
    private fun identificarLogo(url: String, tipo: String): Triple<String, String, String> {
        val urlLower = url.lowercase()

        return when {
            urlLower.contains("linkedin") || tipo == "linkedin" ->
                Triple("in", "#0A66C2", "#FFFFFF")
            urlLower.contains("github") || tipo == "github" ->
                Triple("gh", "#24292E", "#FFFFFF")
            urlLower.contains("gitlab") ->
                Triple("gl", "#FC6D26", "#FFFFFF")
            urlLower.contains("youtube") || urlLower.contains("youtu.be") ->
                Triple("▶", "#FF0000", "#FFFFFF")
            urlLower.contains("instagram") ->
                Triple("ig", "#E4405F", "#FFFFFF")
            urlLower.contains("twitter") || urlLower.contains("x.com") ->
                Triple("X", "#000000", "#FFFFFF")
            urlLower.contains("facebook") ->
                Triple("f", "#1877F2", "#FFFFFF")
            urlLower.contains("behance") ->
                Triple("Bē", "#1769FF", "#FFFFFF")
            urlLower.contains("dribbble") ->
                Triple("Dr", "#EA4C89", "#FFFFFF")
            tipo == "portfolio" ->
                Triple("web", "#3D2B27", "#F5E6D0")
            else ->
                Triple("link", "#3D2B27", "#F5E6D0")
        }
    }

    // ============================================================
    // ENCURTA A URL
    // ============================================================
    private fun encurtarUrl(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host ?: url
            val path = uri.path ?: ""
            val caminho = if (path.length > 20) path.substring(0, 20) + "..." else path
            "$host$caminho"
        } catch (e: Exception) {
            url
        }
    }

    // ============================================================
    // DIALOGO PARA ADICIONAR LINK
    // ============================================================
    private fun abrirDialogAdicionarLink() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }

        val txtTitulo = TextView(this).apply {
            text = "Cole a URL do link:"
            setTextColor(Color.parseColor("#666666"))
            textSize = 14f
            setPadding(0, 0, 0, 20)
        }

        val edtUrl = EditText(this).apply {
            hint = "https://..."
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        }

        layout.addView(txtTitulo)
        layout.addView(edtUrl)

        AlertDialog.Builder(this)
            .setTitle("Adicionar Link")
            .setView(layout)
            .setPositiveButton(R.string.salvar) { _, _ ->
                val url = edtUrl.text.toString().trim()

                if (url.isEmpty()) {
                    Toast.makeText(this, "Digite uma URL", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    Toast.makeText(this, "A URL deve comecar com http:// ou https://", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                salvarLink(url)
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    // ============================================================
    // SALVA O LINK NO FIRESTORE (lista)
    // ============================================================
    private fun salvarLink(url: String) {
        val urlLower = url.lowercase()

        val tipo = when {
            urlLower.contains("linkedin") -> "linkedin"
            urlLower.contains("github") -> "github"
            urlLower.contains("gitlab") -> "gitlab"
            urlLower.contains("youtube") -> "youtube"
            urlLower.contains("youtu.be") -> "youtube"
            urlLower.contains("instagram") -> "instagram"
            urlLower.contains("twitter") -> "twitter"
            urlLower.contains("x.com") -> "twitter"
            urlLower.contains("facebook") -> "facebook"
            urlLower.contains("behance") -> "behance"
            urlLower.contains("dribbble") -> "dribbble"
            else -> "portfolio"
        }

        lifecycleScope.launch {
            try {
                val usuarioDoc = db.collection("usuarios").document(email).get().await()
                val linksAtuais = usuarioDoc.get("links") as? List<Map<String, String>> ?: emptyList()

                val novosLinks = linksAtuais + mapOf(
                    "tipo" to tipo,
                    "url" to url
                )

                db.collection("usuarios").document(email)
                    .update("links", novosLinks).await()

                Toast.makeText(this@perfil, "Link adicionado", Toast.LENGTH_SHORT).show()
                carregarLinks()
            } catch (e: Exception) {
                Toast.makeText(
                    this@perfil,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ============================================================
    // REMOVE O LINK POR INDICE
    // ============================================================
    private fun removerLink(index: Int) {
        lifecycleScope.launch {
            try {
                val usuarioDoc = db.collection("usuarios").document(email).get().await()
                val linksAtuais = usuarioDoc.get("links") as? List<Map<String, String>> ?: emptyList()

                if (index !in linksAtuais.indices) {
                    Toast.makeText(this@perfil, "Link nao encontrado", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val novosLinks = linksAtuais.toMutableList().apply { removeAt(index) }

                db.collection("usuarios").document(email)
                    .update("links", novosLinks).await()

                Toast.makeText(this@perfil, "Link removido", Toast.LENGTH_SHORT).show()
                carregarLinks()
            } catch (e: Exception) {
                Toast.makeText(
                    this@perfil,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}