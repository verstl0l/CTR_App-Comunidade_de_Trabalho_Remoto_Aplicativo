package com.example.plataformaremota

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProdutividadeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var equipeId: String = ""
    private var ehDono: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_produtividade)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""
        equipeId = intent.getStringExtra("equipeId") ?: ""

        if (equipeId.isEmpty()) {
            Toast.makeText(this, "Equipe nao encontrada", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val btnVoltar = findViewById<Button>(R.id.btnVoltarProdutividade)
        btnVoltar.setOnClickListener { finish() }

        configurarBottomNavigation()
        carregarEstatisticas()
    }

    // ============================================================
    // CARREGAR ESTATISTICAS
    // ============================================================
    private fun carregarEstatisticas() {
        lifecycleScope.launch {
            try {
                // 1. Verifica se e dono
                val equipeDoc = db.collection("equipes").document(equipeId).get().await()
                val criadorEmail = equipeDoc.getString("criadorEmail") ?: ""
                ehDono = criadorEmail == emailUsuario

                // 2. Busca todos os trabalhos da equipe
                val trabalhos = db.collection("trabalhos")
                    .whereEqualTo("equipeId", equipeId)
                    .get()
                    .await()

                val total = trabalhos.size()
                var concluidos = 0
                var emProgresso = 0
                var pendentes = 0

                val porMembro = mutableMapOf<String, Int>()

                trabalhos.documents.forEach { doc ->
                    val status = doc.getString("status") ?: "pendente"
                    val responsavel = doc.getString("responsavelEmail") ?: ""

                    when (status) {
                        "concluido" -> concluidos++
                        "em_progresso" -> emProgresso++
                        else -> pendentes++
                    }

                    if (responsavel.isNotEmpty()) {
                        porMembro[responsavel] = (porMembro[responsavel] ?: 0) + 1
                    }
                }

                // 3. Atualiza a UI de texto
                findViewById<TextView>(R.id.txtTotalTrabalhos).text = total.toString()
                findViewById<TextView>(R.id.txtQtdConcluido).text = concluidos.toString()
                findViewById<TextView>(R.id.txtQtdProgresso).text = emProgresso.toString()
                findViewById<TextView>(R.id.txtQtdPendente).text = pendentes.toString()

                if (total > 0) {
                    findViewById<ProgressBar>(R.id.barConcluido).progress = (concluidos * 100) / total
                    findViewById<ProgressBar>(R.id.barProgresso).progress = (emProgresso * 100) / total
                    findViewById<ProgressBar>(R.id.barPendente).progress = (pendentes * 100) / total
                }

                val taxa = if (total > 0) (concluidos * 100) / total else 0
                findViewById<TextView>(R.id.txtTaxaConclusao).text = "$taxa%"
                findViewById<TextView>(R.id.txtDetalheTaxa).text = "$concluidos de $total trabalhos"

                // 4. Popula o grafico de PIZZA
                configurarGraficoPizza(concluidos, emProgresso, pendentes)

                // 5. Popula o grafico de BARRAS
                configurarGraficoBarras(porMembro)

                // 6. Card "Por Membro" (texto) - so dono ve
                if (ehDono && porMembro.isNotEmpty()) {
                    findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardPorMembro).visibility = View.VISIBLE
                    montarListaPorMembro(porMembro, total)
                }

            } catch (e: Exception) {
                Log.e("PRODUTIVIDADE", "Erro: ${e.message}")
                Toast.makeText(this@ProdutividadeActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ============================================================
    // GRAFICO DE PIZZA
    // ============================================================
    private fun configurarGraficoPizza(concluidos: Int, emProgresso: Int, pendentes: Int) {
        val pieChart = findViewById<PieChart>(R.id.pieChartStatus)

        val total = concluidos + emProgresso + pendentes

        if (total == 0) {
            pieChart.clear()
            pieChart.setNoDataText("Nenhum trabalho ainda")
            pieChart.setNoDataTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            pieChart.invalidate()
            return
        }

        // Entradas (fatias)
        val entradas = mutableListOf<PieEntry>()
        if (concluidos > 0) entradas.add(PieEntry(concluidos.toFloat(), "Concluido"))
        if (emProgresso > 0) entradas.add(PieEntry(emProgresso.toFloat(), "Em Progresso"))
        if (pendentes > 0) entradas.add(PieEntry(pendentes.toFloat(), "Pendente"))

        // Cores das fatias
        val cores = mutableListOf<Int>()
        if (concluidos > 0) cores.add(ContextCompat.getColor(this, R.color.chart_pizza_concluido))
        if (emProgresso > 0) cores.add(ContextCompat.getColor(this, R.color.chart_pizza_progresso))
        if (pendentes > 0) cores.add(ContextCompat.getColor(this, R.color.chart_pizza_pendente))

        val dataSet = PieDataSet(entradas, "")
        dataSet.colors = cores
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTextSize = 12f
        dataSet.sliceSpace = 3f

        // Formatter: mostra "N" (quantidade) em vez de percentual
        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return value.toInt().toString()
            }
        }

        val data = PieData(dataSet)
        pieChart.data = data

        // Configuracoes visuais
        pieChart.description.isEnabled = false
        pieChart.isDrawHoleEnabled = true
        pieChart.setHoleColor(ContextCompat.getColor(this, R.color.bg_surface))
        pieChart.setHoleRadius(55f)
        pieChart.setTransparentCircleRadius(60f)
        pieChart.setEntryLabelColor(Color.WHITE)
        pieChart.setEntryLabelTextSize(11f)
        pieChart.legend.textColor = ContextCompat.getColor(this, R.color.chart_legend)
        pieChart.legend.textSize = 12f
        pieChart.legend.verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM
        pieChart.legend.horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER
        pieChart.legend.orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
        pieChart.legend.isWordWrapEnabled = true
        pieChart.setUsePercentValues(false)

        pieChart.invalidate()
        pieChart.animateY(600)
    }

    // ============================================================
    // GRAFICO DE BARRAS
    // ============================================================
    private fun configurarGraficoBarras(porMembro: Map<String, Int>) {
        val barChart = findViewById<BarChart>(R.id.barChartMembros)

        if (porMembro.isEmpty()) {
            barChart.clear()
            barChart.setNoDataText("Nenhum membro com trabalhos")
            barChart.setNoDataTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            barChart.invalidate()
            return
        }

        // Ordena por quantidade (maior primeiro)
        val ordenado = porMembro.entries.sortedByDescending { it.value }

        // Precisa buscar nomes dos membros
        lifecycleScope.launch {
            try {
                val entradas = mutableListOf<BarEntry>()
                val nomes = mutableListOf<String>()

                ordenado.forEachIndexed { index, entry ->
                    val userDoc = db.collection("usuarios").document(entry.key).get().await()
                    val nome = userDoc.getString("nome") ?: entry.key.substringBefore("@")

                    // Nome curto (primeiro nome)
                    val nomeCurto = nome.split(" ").firstOrNull() ?: nome
                    nomes.add(nomeCurto)

                    entradas.add(BarEntry(index.toFloat(), entry.value.toFloat()))
                }

                val dataSet = BarDataSet(entradas, "Trabalhos")
                dataSet.color = ContextCompat.getColor(this@ProdutividadeActivity, R.color.chart_barra_principal)
                dataSet.valueTextColor = Color.WHITE
                dataSet.valueTextSize = 12f

                dataSet.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }

                val data = BarData(dataSet)
                data.barWidth = 0.5f
                barChart.data = data

                // Eixo X: nomes dos membros
                val xAxis = barChart.xAxis
                xAxis.valueFormatter = IndexAxisValueFormatter(nomes)
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.granularity = 1f
                xAxis.setDrawGridLines(false)
                xAxis.textColor = ContextCompat.getColor(this@ProdutividadeActivity, R.color.chart_text)
                xAxis.textSize = 11f

                // Eixo Y: numeros
                val yAxisLeft = barChart.axisLeft
                yAxisLeft.axisMinimum = 0f
                yAxisLeft.granularity = 1f
                yAxisLeft.textColor = ContextCompat.getColor(this@ProdutividadeActivity, R.color.chart_text)
                yAxisLeft.gridColor = ContextCompat.getColor(this@ProdutividadeActivity, R.color.chart_grid)
                yAxisLeft.axisLineColor = ContextCompat.getColor(this@ProdutividadeActivity, R.color.chart_grid)

                barChart.axisRight.isEnabled = false

                // Legendas e descricao
                barChart.description.isEnabled = false
                barChart.legend.isEnabled = false

                barChart.setFitBars(true)
                barChart.setScaleEnabled(false)
                barChart.invalidate()
                barChart.animateY(600)

            } catch (e: Exception) {
                Log.e("PRODUTIVIDADE", "Erro ao montar barras: ${e.message}")
            }
        }
    }

    // ============================================================
    // LISTA POR MEMBRO (texto)
    // ============================================================
    private fun montarListaPorMembro(porMembro: Map<String, Int>, total: Int) {
        val container = findViewById<LinearLayout>(R.id.containerPorMembro)
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)

        val ordenado = porMembro.entries.sortedByDescending { it.value }

        lifecycleScope.launch {
            try {
                ordenado.forEach { (emailMembro, qtd) ->
                    val userDoc = db.collection("usuarios").document(emailMembro).get().await()
                    val nome = userDoc.getString("nome") ?: emailMembro

                    val view = inflater.inflate(R.layout.item_produtividade_membro, container, false)

                    view.findViewById<TextView>(R.id.txtNomeMembroProd).text = nome
                    view.findViewById<TextView>(R.id.txtQtdMembroProd).text = qtd.toString()

                    val progresso = if (total > 0) (qtd * 100) / total else 0
                    view.findViewById<ProgressBar>(R.id.barMembroProd).progress = progresso

                    container.addView(view)
                }
            } catch (e: Exception) {
                Log.e("PRODUTIVIDADE", "Erro nomes: ${e.message}")
            }
        }
    }

    // ============================================================
    // BOTTOM NAVIGATION
    // ============================================================
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

        lifecycleScope.launch {
            BadgeHelper.atualizarBadgeChat(this@ProdutividadeActivity, bottomNav)
        }
    }
}