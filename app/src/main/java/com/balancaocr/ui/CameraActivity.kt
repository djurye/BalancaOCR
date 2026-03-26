package com.balancaocr.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.balancaocr.R
import com.balancaocr.camera.ScaleImageAnalyzer
import com.balancaocr.data.Measurement
import com.balancaocr.ocr.WeightAnalyzer
import com.balancaocr.utils.ExcelExporter
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CameraActivity"
    }

    private lateinit var vm: CameraViewModel
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private lateinit var tvReading: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvCount: TextView
    private lateinit var pbStability: ProgressBar
    private lateinit var btnPause: Button
    private lateinit var btnUndo: Button
    private lateinit var btnExport: Button
    private lateinit var overlayView: View
    private lateinit var rvMeasurements: RecyclerView
    private lateinit var measureAdapter: MeasurementAdapter

    private val analyzer = WeightAnalyzer(
        stabilityCount = 6,
        stabilityThreshold = 0.02,
        minValidWeight = 0.01,
        minIntervalMs = 2500L
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        val sessionId = intent.getLongExtra(MainActivity.EXTRA_SESSION_ID, -1L)
        val sessionName = intent.getStringExtra(MainActivity.EXTRA_SESSION_NAME) ?: "Sessão"

        supportActionBar?.apply {
            title = sessionName
            setDisplayHomeAsUpEnabled(true)
        }

        vm = ViewModelProvider(this)[CameraViewModel::class.java]
        vm.loadSession(sessionId)

        bindViews()
        setupRecyclerView()
        observeViewModel()

        cameraExecutor = Executors.newSingleThreadExecutor()
        startCamera()
    }

    private fun bindViews() {
        previewView = findViewById(R.id.previewView)
        tvReading = findViewById(R.id.tvReading)
        tvStatus = findViewById(R.id.tvStatus)
        tvCount = findViewById(R.id.tvCount)
        pbStability = findViewById(R.id.pbStability)
        btnPause = findViewById(R.id.btnPause)
        btnUndo = findViewById(R.id.btnUndo)
        btnExport = findViewById(R.id.btnExport)
        overlayView = findViewById(R.id.overlayCapture)
        rvMeasurements = findViewById(R.id.rvMeasurements)

        btnPause.setOnClickListener { vm.toggleCapture() }
        btnUndo.setOnClickListener { vm.deleteLastMeasurement() }
        btnExport.setOnClickListener { exportData() }
    }

    private fun setupRecyclerView() {
        measureAdapter = MeasurementAdapter(mutableListOf())
        rvMeasurements.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvMeasurements.adapter = measureAdapter
    }

    private fun observeViewModel() {
        vm.statusText.observe(this) { tvStatus.text = it }

        vm.measurements.observe(this) { list ->
            tvCount.text = "${list.size} partículas"
            measureAdapter.update(list)
            if (list.isNotEmpty()) rvMeasurements.smoothScrollToPosition(list.size - 1)
        }

        vm.lastSaved.observe(this) { m ->
            m ?: return@observe
            // Flash de confirmação
            overlayView.visibility = View.VISIBLE
            overlayView.animate().alpha(0f).setDuration(600).withEndAction {
                overlayView.visibility = View.GONE
                overlayView.alpha = 1f
            }.start()
        }

        vm.isCapturing.observe(this) { capturing ->
            btnPause.text = if (capturing) "⏸ Pausar" else "▶ Retomar"
            btnPause.setBackgroundColor(
                ContextCompat.getColor(this, if (capturing) R.color.colorPause else R.color.colorResume)
            )
        }
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ScaleImageAnalyzer(analyzer) { result, raw ->
                        runOnUiThread { handleAnalysisResult(result, raw) }
                    })
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao iniciar câmera", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleAnalysisResult(result: WeightAnalyzer.AnalysisResult, rawText: String) {
        // Atualiza barra de estabilidade
        pbStability.progress = (result.stabilityProgress * 100).toInt()

        if (result.parsed != null) {
            tvReading.text = "${"%.3f".format(result.parsed.value)} ${result.parsed.unit}"
            tvReading.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (result.isStable) R.color.colorStable else R.color.colorReading
                )
            )
        } else {
            tvReading.text = "---"
            tvReading.setTextColor(ContextCompat.getColor(this, R.color.colorReading))
        }

        if (result.shouldSave && result.parsed != null) {
            vm.onStableReading(result.parsed.value, result.parsed.unit)
        }
    }

    private fun exportData() {
        lifecycleScope.launch {
            val sessionId = vm.sessionId
            val measurements = vm.measurements.value ?: emptyList()

            if (measurements.isEmpty()) {
                Snackbar.make(previewView, "Nenhuma leitura para exportar", Snackbar.LENGTH_SHORT).show()
                return@launch
            }

            // Busca sessão
            val sessions = com.balancaocr.data.BalancaRepository(this@CameraActivity).getAllSessions()
            val session = sessions.firstOrNull { it.id == sessionId } ?: return@launch

            val uri = ExcelExporter.export(this@CameraActivity, session, measurements)
            if (uri != null) {
                ExcelExporter.shareFile(this@CameraActivity, uri)
            } else {
                Snackbar.make(previewView, "Erro ao gerar planilha", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

// ── Adapter da lista lateral ──────────────────────────────────────────────────
class MeasurementAdapter(private val items: MutableList<Measurement>) :
    RecyclerView.Adapter<MeasurementAdapter.VH>() {

    inner class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val tvIndex: TextView = view.findViewById(R.id.tvIndex)
        val tvValue: TextView = view.findViewById(R.id.tvValue)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, vt: Int) =
        VH(android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_measurement, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val m = items[pos]
        h.tvIndex.text = "#${m.index + 1}"
        h.tvValue.text = "${"%.3f".format(m.value)} ${m.unit}"
    }

    fun update(list: List<Measurement>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }
}
