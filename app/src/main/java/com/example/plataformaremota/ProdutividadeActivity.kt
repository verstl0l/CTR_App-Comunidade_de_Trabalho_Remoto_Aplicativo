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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProdutividadeActivity : BaseActivity() {

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
            Toast.makeText(this, getString(R.string.produtividade_equipe_nao_encontrada), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val btnVoltar = findViewById<Button>(R.id.btnVoltarProdutividade)
        btnVoltar.setOnClickListener { finish() }

        // ✅ Bottom nav centralizado na BaseActivity
        configurarBottomNavigation(R.id.nav_groups)

        carregarEstatisticas()
    }

    private fun carregarEstatisticas() {
        lifecycleScope.launch {
            try {
                val equipeDoc = db.collection("equipes").document(equipeId).get().await()
                val criadorEmail = equipeDoc.getString("criadorEmail") ?: ""
                ehDono = criadorEmail == emailUsuario

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
                findViewById<TextView>(R.id.txtTaxaConclusao).text =
                    getString(R.string.produtividade_taxa_percentual, taxa)

                // ✅ Plural: "1 trabalho" / "2 trabalhos" / "5 работ"
                findViewById<TextView>(R.id.txtDetalheTaxa).text =
                    resources.getQuantityString(
                        R.plurals.produtividade_detalhe_taxa,
                        total,
                        concluidos,
                        total
                    )

                configurarGraficoPizza(concluidos, emProgresso, pendentes)
                configurarGraficoBarras(porMembro)

                if (ehDono && porMembro.isNotEmpty()) {
                    findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardPorMembro).visibility = View.VISIBLE
                    montarListaPorMembro(porMembro, total)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erro: ${e.message}")
                Toast.makeText(
                    this@ProdutividadeActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun configurarGraficoPizza(concluidos: Int, emProgresso: Int, pendentes: Int) {
        val pieChart = findViewById<PieChart>(R.id.pieChartStatus)

        val total = concluidos + emProgresso + pendentes

        if (total == 0) {
            pieChart.clear()
            pieChart.setNoDataText(getString(R.string.produtividade_pizza_sem_dados))
            pieChart.setNoDataTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            pieChart.invalidate()
            return
        }

        // ✅ Labels traduzidos para o gráfico
        val labelConcluido = getString(R.string.status_concluido)
        val labelProgresso = getString(R.string.status_em_progresso)
        val labelPendente = getString(R.string.status_pendente)

        val entradas = mutableListOf<PieEntry>()
        if (concluidos > 0) entradas.add(PieEntry(concluidos.toFloat(), labelConcluido))
        if (emProgresso > 0) entradas.add(PieEntry(emProgresso.toFloat(), labelProgresso))
        if (pendentes > 0) entradas.add(PieEntry(pendentes.toFloat(), labelPendente))

        val cores = mutableListOf<Int>()
        if (concluidos > 0) cores.add(ContextCompat.getColor(this, R.color.chart_pizza_concluido))
        if (emProgresso > 0) cores.add(ContextCompat.getColor(this, R.color.chart_pizza_progresso))
        if (pendentes > 0) cores.add(ContextCompat.getColor(this, R.color.chart_pizza_pendente))

        val dataSet = PieDataSet(entradas, "")
        dataSet.colors = cores
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTextSize = 12f
        dataSet.sliceSpace = 3f

        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = value.toInt().toString()
        }

        val data = PieData(dataSet)
        pieChart.data = data

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

    private fun configurarGraficoBarras(porMembro: Map<String, Int>) {
        val barChart = findViewById<BarChart>(R.id.barChartMembros)

        if (porMembro.isEmpty()) {
            barChart.clear()
            barChart.setNoDataText(getString(R.string.produtividade_barras_sem_dados))
            barChart.setNoDataTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            barChart.invalidate()
            return
        }

        val ordenado = porMembro.entries.sortedByDescending { it.value }

        lifecycleScope.launch {
            try {
                val entradas = mutableListOf<BarEntry>()
                val nomes = mutableListOf<String>()

                ordenado.forEachIndexed { index, entry ->
                    val userDoc = db.collection("usuarios").document(entry.key).get().await()
                    val nome = userDoc.getString("nome") ?: entry.key.substringBefore("@")

                    val nomeCurto = nome.split(" ").firstOrNull() ?: nome
                    nomes.add(nomeCurto)

                    entradas.add(BarEntry(index.toFloat(), entry.value.toFloat()))
                }

                // ✅ Label traduzido do dataset
                val dataSet = BarDataSet(entradas, getString(R.string.produtividade_barras_label))
                dataSet.color = ContextCompat.getColor(this@ProdutividadeActivity, R.color.chart_barra_principal)
                dataSet.valueTextColor = Color.WHITE
                dataSet.valueTextSize = 12f

                dataSet.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String = value.toInt().toString()
                }

                val data = BarData(dataSet)
                data.barWidth = 0.5f
                barChart.data = data

                val xAxis = barChart.xAxis
                xAxis.valueFormatter = IndexAxisValueFormatter(nomes)
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.granularity = 1f
                xAxis.setDrawGridLines(false)
                xAxis.textColor = ContextCompat.getColor(this@ProdutividadeActivity, R.color.chart_text)
                xAxis.textSize = 11f

                val yAxisLeft = barChart.axisLeft
                yAxisLeft.axisMinimum = 0f
                yAxisLeft.granularity = 1f
                yAxisLeft.textColor = ContextCompat.getColor(this@ProdutividadeActivity, R.color.chart_text)
                yAxisLeft.gridColor = ContextCompat.getColor(this@ProdutividadeActivity, R.color.chart_grid)
                yAxisLeft.axisLineColor = ContextCompat.getColor(this@ProdutividadeActivity, R.color.chart_grid)

                barChart.axisRight.isEnabled = false
                barChart.description.isEnabled = false
                barChart.legend.isEnabled = false

                barChart.setFitBars(true)
                barChart.setScaleEnabled(false)
                barChart.invalidate()
                barChart.animateY(600)

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao montar barras: ${e.message}")
            }
        }
    }

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
                Log.e(TAG, "Erro nomes: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "PRODUTIVIDADE"
    }
}