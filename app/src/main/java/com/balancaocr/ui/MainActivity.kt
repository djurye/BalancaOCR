package com.balancaocr.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.balancaocr.R
import com.balancaocr.data.BalancaRepository
import com.balancaocr.data.Session
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val repo by lazy { BalancaRepository(this) }
    private val sessions = mutableListOf<Session>()
    private lateinit var adapter: SessionAdapter

    companion object {
        const val REQ_CAMERA = 100
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_SESSION_NAME = "session_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adapter = SessionAdapter(sessions,
            onOpen = { openCamera(it) },
            onHistory = { openHistory(it) },
            onDelete = { confirmDelete(it) }
        )
        findViewById<RecyclerView>(R.id.rvSessions).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(
            R.id.fabNewSession
        ).setOnClickListener { showNewSessionDialog() }

        checkPermissions()
        loadSessions()
    }

    override fun onResume() {
        super.onResume()
        loadSessions()
    }

    private fun loadSessions() {
        lifecycleScope.launch {
            sessions.clear()
            sessions.addAll(repo.getAllSessions())
            adapter.notifyDataSetChanged()
            findViewById<TextView>(R.id.tvEmpty).visibility =
                if (sessions.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun showNewSessionDialog() {
        val input = EditText(this).apply {
            hint = "Nome da sessão (ex: Lote A)"
            setPadding(40, 20, 40, 20)
        }
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
        input.setText("Sessão ${fmt.format(Date())}")

        AlertDialog.Builder(this)
            .setTitle("Nova Sessão de Pesagem")
            .setView(input)
            .setPositiveButton("Criar e Abrir Câmera") { _, _ ->
                val name = input.text.toString().trim().ifBlank { "Sessão" }
                lifecycleScope.launch {
                    val id = repo.createSession(name)
                    openCamera(Session(id = id, name = name))
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun openCamera(session: Session) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            checkPermissions(); return
        }
        startActivity(Intent(this, CameraActivity::class.java).apply {
            putExtra(EXTRA_SESSION_ID, session.id)
            putExtra(EXTRA_SESSION_NAME, session.name)
        })
    }

    private fun openHistory(session: Session) {
        startActivity(Intent(this, HistoryActivity::class.java).apply {
            putExtra(EXTRA_SESSION_ID, session.id)
            putExtra(EXTRA_SESSION_NAME, session.name)
        })
    }

    private fun confirmDelete(session: Session) {
        AlertDialog.Builder(this)
            .setTitle("Excluir sessão?")
            .setMessage("\"${session.name}\" e todas as ${session.count} leituras serão removidas.")
            .setPositiveButton("Excluir") { _, _ ->
                lifecycleScope.launch { repo.deleteSession(session); loadSessions() }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun checkPermissions() {
        val perms = mutableListOf(Manifest.permission.CAMERA)
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_CAMERA)
        }
    }
}

// ── Adapter simples para a lista de sessões ───────────────────────────────────
class SessionAdapter(
    private val items: List<Session>,
    private val onOpen: (Session) -> Unit,
    private val onHistory: (Session) -> Unit,
    private val onDelete: (Session) -> Unit
) : RecyclerView.Adapter<SessionAdapter.VH>() {

    inner class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvSessionName)
        val tvCount: TextView = view.findViewById(R.id.tvSessionCount)
        val tvDate: TextView = view.findViewById(R.id.tvSessionDate)
        val btnOpen: Button = view.findViewById(R.id.btnOpen)
        val btnHistory: Button = view.findViewById(R.id.btnHistory)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    private val fmt = SimpleDateFormat("dd/MM/yy HH:mm", Locale("pt", "BR"))

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH =
        VH(android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val s = items[pos]
        h.tvName.text = s.name
        h.tvCount.text = "${s.count} partículas"
        h.tvDate.text = fmt.format(Date(s.createdAt))
        h.btnOpen.setOnClickListener { onOpen(s) }
        h.btnHistory.setOnClickListener { onHistory(s) }
        h.btnDelete.setOnClickListener { onDelete(s) }
    }
}
