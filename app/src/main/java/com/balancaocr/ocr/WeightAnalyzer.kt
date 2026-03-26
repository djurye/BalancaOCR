package com.balancaocr.ocr

import android.util.Log
import java.util.LinkedList
import kotlin.math.abs

/**
 * Analisa o texto extraído pelo ML Kit e decide quando o valor da
 * balança se estabilizou para registrar automaticamente.
 *
 * Fluxo:
 *  1. parseWeight()   — extrai o número (e unidade) de um texto bruto
 *  2. onNewReading()  — alimenta o buffer de histórico e detecta estabilidade
 */
class WeightAnalyzer(
    /** Quantas leituras consecutivas iguais para considerar estável */
    private val stabilityCount: Int = 5,
    /** Variação máxima permitida entre leituras (em unidade da balança) */
    private val stabilityThreshold: Double = 0.05,
    /** Valor mínimo absoluto para ser considerado válido (evita registrar "0.00") */
    private val minValidWeight: Double = 0.01,
    /** Tempo mínimo entre dois registros consecutivos (ms) */
    private val minIntervalMs: Long = 2000L
) {

    companion object {
        private const val TAG = "WeightAnalyzer"

        // Regex: captura números decimais seguidos opcionalmente de unidade
        // Exemplos válidos: "12.34 g", "0,456 kg", "1234", "1.234,56 g"
        private val WEIGHT_REGEX = Regex(
            """(\d{1,6}[.,]?\d{0,4})\s*(g|kg|mg|lb|oz)?""",
            RegexOption.IGNORE_CASE
        )
    }

    // ── Estado interno ────────────────────────────────────────────────────────
    private val history = LinkedList<Double>()
    private var lastSavedValue: Double = -9999.0
    private var lastSaveTime: Long = 0L
    var currentUnit: String = "g"
        private set

    data class ParsedWeight(val value: Double, val unit: String)
    data class AnalysisResult(
        val parsed: ParsedWeight?,
        val isStable: Boolean,
        val shouldSave: Boolean,
        val stabilityProgress: Float   // 0..1 para barra de progresso
    )

    // ── Parsear texto bruto vindo do OCR ──────────────────────────────────────
    fun parseWeight(rawText: String): ParsedWeight? {
        if (rawText.isBlank()) return null

        // Tenta todas as ocorrências e pega o maior valor (telas de balança
        // geralmente mostram o valor principal bem grande e pode haver outros números)
        val candidates = mutableListOf<ParsedWeight>()

        WEIGHT_REGEX.findAll(rawText).forEach { match ->
            val numStr = match.groupValues[1]
                .replace(',', '.')   // normaliza vírgula decimal
                .trimEnd('.')
            val unit = match.groupValues[2].lowercase().ifBlank { "g" }
            val value = numStr.toDoubleOrNull() ?: return@forEach
            if (value > 0) candidates.add(ParsedWeight(value, unit))
        }

        if (candidates.isEmpty()) return null

        // Prefere o candidato com unidade explícita, depois o maior
        val best = candidates.maxWithOrNull(
            compareBy({ it.unit != "g" && it.unit.isNotBlank() }, { it.value })
        ) ?: return null

        currentUnit = best.unit
        return best
    }

    // ── Alimentar nova leitura e avaliar estabilidade ─────────────────────────
    fun onNewReading(parsed: ParsedWeight?): AnalysisResult {
        if (parsed == null || parsed.value < minValidWeight) {
            history.clear()
            return AnalysisResult(null, false, false, 0f)
        }

        history.addLast(parsed.value)
        if (history.size > stabilityCount) history.removeFirst()

        val progress = history.size.toFloat() / stabilityCount.toFloat()

        if (history.size < stabilityCount) {
            return AnalysisResult(parsed, false, false, progress)
        }

        // Verificar se todos os valores estão dentro do limiar
        val min = history.min()
        val max = history.max()
        val spread = max - min
        val isStable = spread <= stabilityThreshold

        if (!isStable) {
            return AnalysisResult(parsed, false, false, 0.3f)
        }

        val avg = history.average()
        val now = System.currentTimeMillis()
        val timeSinceLast = now - lastSaveTime
        val valueDiff = abs(avg - lastSavedValue)

        // Só salva se passou tempo suficiente E o valor mudou (nova partícula)
        val shouldSave = timeSinceLast >= minIntervalMs && valueDiff > stabilityThreshold

        if (shouldSave) {
            lastSavedValue = avg
            lastSaveTime = now
            history.clear()  // reseta para esperar próxima partícula
            Log.d(TAG, "✅ Valor estável salvo: $avg ${parsed.unit}")
        }

        return AnalysisResult(parsed, true, shouldSave, if (isStable) 1f else 0.7f)
    }

    fun reset() {
        history.clear()
        lastSavedValue = -9999.0
        lastSaveTime = 0L
    }
}
