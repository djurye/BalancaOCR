package com.balancaocr.ocr

import android.util.Log
import java.util.LinkedList
import kotlin.math.abs

class WeightAnalyzer(
    /** Reduzido para 3 para leitura mais ágil */
    var stabilityCount: Int = 3,
    /** Margem de erro levemente maior para compensar trepidação */
    private val stabilityThreshold: Double = 0.08,
    private val minValidWeight: Double = 0.01,
    private val minIntervalMs: Long = 2500L
) {

    companion object {
        private const val TAG = "WeightAnalyzer"

        // Regex flexível: aceita letras que parecem números (B, O, S, Z, D)
        private val WEIGHT_REGEX = Regex(
            """([0-9BOSZDS]{1,6}[.,]?[0-9BOSZDS]{0,4})\s*(g|kg|mg|lb|oz)?""",
            RegexOption.IGNORE_CASE
        )
    }

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
        val stabilityProgress: Float
    )

    fun parseWeight(rawText: String): ParsedWeight? {
        if (rawText.isBlank()) return null
        val candidates = mutableListOf<ParsedWeight>()

        WEIGHT_REGEX.findAll(rawText).forEach { match ->
            // Limpeza: Converte letras de erro em números reais
            val numStr = match.groupValues[1]
                .uppercase()
                .replace('B', '8')
                .replace('O', '0')
                .replace('S', '5')
                .replace('Z', '2')
                .replace('D', '0')
                .replace(',', '.')
                .trimEnd('.')

            val unit = match.groupValues[2].lowercase().ifBlank { "g" }
            val value = numStr.toDoubleOrNull() ?: return@forEach
            if (value > 0) candidates.add(ParsedWeight(value, unit))
        }

        if (candidates.isEmpty()) return null

        // Prioriza a leitura que tenha unidade ou o maior valor encontrado
        val best = candidates.maxWithOrNull(
            compareBy({ it.unit != "g" }, { it.value })
        ) ?: return null

        currentUnit = best.unit
        return best
    }

    fun onNewReading(parsed: ParsedWeight?): AnalysisResult {
        if (parsed == null || parsed.value < minValidWeight) {
            // Não limpamos o histórico imediatamente para evitar "pulos" na barra
            if (history.isNotEmpty()) history.removeFirst()
            return AnalysisResult(null, false, false, 0f)
        }

        history.addLast(parsed.value)
        if (history.size > stabilityCount) history.removeFirst()

        val progress = history.size.toFloat() / stabilityCount.toFloat()

        if (history.size < stabilityCount) {
            return AnalysisResult(parsed, false, false, progress)
        }

        val min = history.min()
        val max = history.max()
        val isStable = (max - min) <= stabilityThreshold

        if (!isStable) {
            return AnalysisResult(parsed, false, false, 0.5f)
        }

        val avg = history.average()
        val now = System.currentTimeMillis()
        val timeSinceLast = now - lastSaveTime
        val valueDiff = abs(avg - lastSavedValue)

        // Critério de salvamento: Tempo + Mudança significativa de peso
        val shouldSave = timeSinceLast >= minIntervalMs && valueDiff > (stabilityThreshold * 2)

        if (shouldSave) {
            lastSavedValue = avg
            lastSaveTime = now
            history.clear()
            Log.d(TAG, "✅ Estabilizado e Salvo: $avg $currentUnit")
        }

        return AnalysisResult(parsed, true, shouldSave, 1f)
    }

    fun reset() {
        history.clear()
        lastSavedValue = -9999.0
        lastSaveTime = 0L
    }
}