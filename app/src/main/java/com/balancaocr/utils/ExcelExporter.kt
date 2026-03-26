package com.balancaocr.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.balancaocr.data.Measurement
import com.balancaocr.data.Session
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.usermodel.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ExcelExporter {

    private val DATE_FMT = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))
    private val FILE_FMT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    fun export(
        context: Context,
        session: Session,
        measurements: List<Measurement>
    ): Uri? {
        return try {
            val wb = XSSFWorkbook()
            val sheet = wb.createSheet("Pesagens")

            // ── Estilos ───────────────────────────────────────────────────────
            val headerFont = wb.createFont().apply {
                bold = true
                fontHeightInPoints = 12
                color = IndexedColors.WHITE.index
            }
            val headerStyle = wb.createCellStyle().apply {
                setFont(headerFont)
                fillForegroundColor = IndexedColors.DARK_BLUE.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                alignment = HorizontalAlignment.CENTER
                borderBottom = BorderStyle.THIN
            }
            val dataStyle = wb.createCellStyle().apply {
                alignment = HorizontalAlignment.CENTER
            }
            val numStyle = wb.createCellStyle().apply {
                alignment = HorizontalAlignment.CENTER
                dataFormat = wb.createDataFormat().getFormat("0.000")
            }

            // ── Cabeçalho info sessão ─────────────────────────────────────────
            var rowIdx = 0
            sheet.createRow(rowIdx++).createCell(0).setCellValue("Sessão: ${session.name}")
            sheet.createRow(rowIdx++).createCell(0)
                .setCellValue("Exportado em: ${DATE_FMT.format(Date())}")
            sheet.createRow(rowIdx++).createCell(0)
                .setCellValue("Total de partículas: ${measurements.size}")
            rowIdx++ // linha em branco

            // ── Cabeçalho da tabela ───────────────────────────────────────────
            val headers = listOf("#", "Partícula", "Massa", "Unidade", "Data/Hora")
            val hRow = sheet.createRow(rowIdx++)
            headers.forEachIndexed { i, h ->
                hRow.createCell(i).also {
                    it.setCellValue(h)
                    it.cellStyle = headerStyle
                }
            }

            // ── Dados ─────────────────────────────────────────────────────────
            measurements.forEachIndexed { i, m ->
                val row = sheet.createRow(rowIdx++)
                row.createCell(0).also { it.setCellValue((i + 1).toDouble()); it.cellStyle = dataStyle }
                row.createCell(1).also { it.setCellValue(m.index + 1.0); it.cellStyle = dataStyle }
                row.createCell(2).also { it.setCellValue(m.value); it.cellStyle = numStyle }
                row.createCell(3).also { it.setCellValue(m.unit); it.cellStyle = dataStyle }
                row.createCell(4).also {
                    it.setCellValue(DATE_FMT.format(Date(m.timestamp)))
                    it.cellStyle = dataStyle
                }
            }

            // ── Linha de totais ───────────────────────────────────────────────
            if (measurements.isNotEmpty()) {
                rowIdx++ // linha em branco
                val statsStyle = wb.createCellStyle().apply {
                    setFont(wb.createFont().apply { bold = true })
                    dataFormat = wb.createDataFormat().getFormat("0.000")
                }
                val avg = measurements.map { it.value }.average()
                val sum = measurements.sumOf { it.value }
                val min = measurements.minOf { it.value }
                val max = measurements.maxOf { it.value }

                fun statRow(label: String, value: Double) {
                    val r = sheet.createRow(rowIdx++)
                    r.createCell(0).setCellValue(label)
                    r.createCell(2).also { it.setCellValue(value); it.cellStyle = statsStyle }
                    r.createCell(3).setCellValue(measurements.first().unit)
                }
                statRow("Média:", avg)
                statRow("Soma:", sum)
                statRow("Mínimo:", min)
                statRow("Máximo:", max)
            }

            // ── Auto-size colunas ─────────────────────────────────────────────
            headers.indices.forEach { sheet.autoSizeColumn(it) }

            // ── Salvar arquivo ────────────────────────────────────────────────
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: context.filesDir
            dir.mkdirs()
            val fileName = "pesagem_${FILE_FMT.format(Date())}.xlsx"
            val file = File(dir, fileName)

            FileOutputStream(file).use { wb.write(it) }
            wb.close()

            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun shareFile(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar planilha"))
    }
}
