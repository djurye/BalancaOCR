package com.balancaocr.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.balancaocr.R
import com.balancaocr.data.BalancaRepository
import com.balancaocr.data.Measurement
import com.balancaocr.data.Session
import com.balancaocr.utils.ExcelExporter
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    private val repo by lazy { BalancaRepository(this) }
    private lateinit var session: Session
    private val measurements = mutableListOf<Measurement>()
    private lateinit var adapter: HistoryAdapter
    private val fmt = SimpleDateFormat("dd/MM/yy HH:mm:ss", Locale("pt", "BR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val sessionId = intent.getLongExtra(MainActivity.EXTRA_SESSION_ID, -1L)
        val sessionName = intent.getStringExtra(MainActivity.EXTRA_SESSION_NAME) ?: "Histórico"

        supportActionBar?.apply {
            title = sessionName
            setDisplayHomeAsUpEnabled(true)
        }

        adapter = HistoryAdapter(measurements, fmt)
        findViewById<RecyclerView>(R.id.rvHistory).apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
            adapter = this@HistoryActivity.adapter
        }

        findViewById<Button>(R.id.btnExportHistory).setOnClickListener { exportData() }

        loadData(sessionId)
    }

    private fun loadData(sessionId: Long) {
        lifecycleScope.launch {
            val sessions = repo.getAllSessions()
            session = sessions.firstOrNull { it.id == sessionId } ?: run { finish(); return@launch }
            measurements.clear()
            measurements.addAll(repo.getMeasurements(sessionId))
            adapter.notifyDataSetChanged()

            val stats = computeStats()
            findViewById<TextView>(R.id.tvStats).text = stats
        }
    }

    private fun computeStats(): String {
        if (measurements.isEmpty()) return "Sem dados"
        val values = measurements.map { it.value }
        return """
            Total: ${measurements.size} partículas
            Média: ${"%.4f".format(values.average())} ${measurements.first().unit}
            Soma:  ${"%.4f".format(values.sum())} ${measurements.first().unit}
            Mín:   ${"%.4f".format(values.min())} ${measurements.first().unit}
            Máx:   ${"%.4f".format(values.max())} ${measurements.first().unit}
        """.trimIndent()
    }

    private fun exportData() {
        lifecycleScope.launch {
            if (measurements.isEmpty()) {
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "Nenhuma leitura para exportar",
                    Snackbar.LENGTH_SHORT
                ).show()
                return@launch
            }
            val uri = ExcelExporter.export(this@HistoryActivity, session, measurements)
            if (uri != null) ExcelExporter.shareFile(this@HistoryActivity, uri)
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

class HistoryAdapter(
    private val items: List<Measurement>,
    private val fmt: SimpleDateFormat
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    inner class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val tvIdx: TextView = view.findViewById(R.id.tvIndex)
        val tvVal: TextView = view.findViewById(R.id.tvValue)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, vt: Int) =
        VH(android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false))

    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val m = items[pos]
        h.tvIdx.text = "#${m.index + 1}"
        h.tvVal.text = "${"%.4f".format(m.value)} ${m.unit}"
        h.tvTime.text = fmt.format(Date(m.timestamp))
    }
}
