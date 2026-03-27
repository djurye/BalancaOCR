package com.balancaocr.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val repo by lazy { BalancaRepository(this) }
    private val sessions = mutableListOf<Session>()
    private lateinit var adapter: SessionAdapter
    private lateinit var rvSessions: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var fabNew: FloatingActionButton

    companion object {
        const val REQ_CAMERA = 100
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_SESSION_NAME = "session_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rvSessions = findViewById(R.id.rvSessions)
        tvEmpty = findViewById(R.id.tvEmpty)
        fabNew = findViewById(R.id.fabNewSession)

        adapter = SessionAdapter(
            sessions,
            onOpen = { openCamera(it) },
            onHistory = { openHistory(it) },
            onDelete = { confirmDelete(it) }
        )

        rvSessions.layoutManager = LinearLayoutManager(this)
        rvSessions.adapter = adapter

        fabNew.setOnClickListener { showNewSessionDialog() }

        checkPermissions()
        loadSessions()
    }

    override fun onResume() {
        super.onResume()
        loadSessions()
    }

    private fun loadSessions() {
        lifecycleScope.launch {
            try {
                sessions.clear()
                sessions.addAll(repo.getAllSessions())
                adapter.notifyDataSetChanged()
                tvEmpty.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Erro ao carregar sessões: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showNewSessionDialog() {
        val input = EditText(this).apply {
            hint = "Nome da sessão (ex: Lote A)"
            setPadding(60, 30, 60, 30)
            val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
            setText("Sessão ${fmt.format(Date())}")
        }

        AlertDialog.Builder(this)
            .setTitle("Nova Sessão de Pesagem")
            .setView(input)
            .setPositiveButton("Criar e Abrir Câmera") { _, _ ->
                val name = input.text.toString().trim().ifBlank { "Sessão" }
                lifecycleScope.launch {
                    try {
                        val id = repo.createSession(name)
                        openCamera(Session(id = id, name = name))
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Erro ao criar sessão", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun openCamera(session: Session) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            checkPermissions()
            return
        }
        val intent = Intent(this, CameraActivity::class.java).apply {
            putExtra(EXTRA_SESSION_ID, session.id)
            putExtra(EXTRA_SESSION_NAME, session.name)
        }
        startActivity(intent)
    }

    private fun openHistory(session: Session) {
        val intent = Intent(this, HistoryActivity::class.java).apply {
            putExtra(EXTRA_SESSION_ID, session.id)
            putExtra(EXTRA_SESSION_NAME, session.name)
        }
        startActivity(intent)
    }

    private fun confirmDelete(session: Session) {
        AlertDialog.Builder(this)
            .setTitle("Excluir sessão?")
            .setMessage("\"${session.name}\" e todas as ${session.count} leituras serão removidas.")
            .setPositiveButton("Excluir") { _, _ ->
                lifecycleScope.launch {
                    try {
                        repo.deleteSession(session)
                        loadSessions()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Erro ao excluir", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun checkPermissions() {
        val missing = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.CAMERA)
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_CAMERA)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA &&
            grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(this, "Permissão de câmera necessária para usar o app", Toast.LENGTH_LONG).show()
        }
    }
}

// ── Adapter ───────────────────────────────────────────────────────────────────
class SessionAdapter(
    private val items: List<Session>,
    private val onOpen: (Session) -> Unit,
    private val onHistory: (Session) -> Unit,
    private val onDelete: (Session) -> Unit
) : RecyclerView.Adapter<SessionAdapter.VH>() {

    private val fmt = SimpleDateFormat("dd/MM/yy HH:mm", Locale("pt", "BR"))

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvSessionName)
        val tvCount: TextView = view.findViewById(R.id.tvSessionCount)
        val tvDate: TextView = view.findViewById(R.id.tvSessionDate)
        val btnOpen: Button = view.findViewById(R.id.btnOpen)
        val btnHistory: Button = view.findViewById(R.id.btnHistory)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_session, parent, false)
        return VH(v)
    }

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
