package com.balancaocr.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.balancaocr.data.BalancaRepository
import com.balancaocr.data.Measurement
import com.balancaocr.ocr.WeightAnalyzer
import kotlinx.coroutines.launch

class CameraViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BalancaRepository(app)

    var sessionId: Long = -1L
    val measurements = MutableLiveData<List<Measurement>>(emptyList())
    val lastSaved = MutableLiveData<Measurement?>()
    val statusText = MutableLiveData("Aguardando leitura...")
    val isCapturing = MutableLiveData(true)

    private val _list = mutableListOf<Measurement>()

    fun loadSession(id: Long) {
        sessionId = id
        viewModelScope.launch {
            _list.clear()
            _list.addAll(repo.getMeasurements(id))
            measurements.postValue(_list.toList())
        }
    }

    fun onStableReading(value: Double, unit: String) {
        if (!isCapturing.value!!) return
        viewModelScope.launch {
            val index = _list.size
            val id = repo.addMeasurement(sessionId, value, unit, index)
            val m = Measurement(id, sessionId, value, unit, System.currentTimeMillis(), index)
            _list.add(m)
            measurements.postValue(_list.toList())
            lastSaved.postValue(m)
            statusText.postValue("✅ Partícula #${index + 1}: ${"%.3f".format(value)} $unit")
        }
    }

    fun deleteLastMeasurement() {
        if (_list.isEmpty()) return
        viewModelScope.launch {
            val last = _list.removeLast()
            repo.deleteMeasurement(last)
            measurements.postValue(_list.toList())
            statusText.postValue("🗑️ Última leitura removida")
        }
    }

    fun toggleCapture() {
        isCapturing.value = !(isCapturing.value ?: true)
        statusText.value = if (isCapturing.value!!) "Captura ativada" else "⏸️ Captura pausada"
    }
}
