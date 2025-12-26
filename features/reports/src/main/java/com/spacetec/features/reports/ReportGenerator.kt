package com.spacetec.features.reports

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class DiagnosticReport(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val vin: String,
    val make: String,
    val model: String,
    val year: Int,
    val dtcs: List<ReportDTC>,
    val liveData: Map<String, String> = emptyMap(),
    val notes: String = ""
)

data class ReportDTC(val code: String, val description: String, val severity: String, val status: String)

class ReportGenerator(private val context: Context) {
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val fileFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    
    suspend fun generatePDF(report: DiagnosticReport): File = withContext(Dispatchers.IO) {
        val content = buildReportContent(report)
        val file = File(context.cacheDir, "report_${fileFormat.format(Date())}.txt")
        file.writeText(content)
        file
    }
    
    suspend fun generateCSV(report: DiagnosticReport): File = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        sb.appendLine("Code,Description,Severity,Status")
        report.dtcs.forEach { sb.appendLine("${it.code},${it.description},${it.severity},${it.status}") }
        val file = File(context.cacheDir, "dtcs_${fileFormat.format(Date())}.csv")
        file.writeText(sb.toString())
        file
    }
    
    suspend fun generateJSON(report: DiagnosticReport): File = withContext(Dispatchers.IO) {
        val json = """
        {
            "id": "${report.id}",
            "timestamp": ${report.timestamp},
            "vehicle": {"vin": "${report.vin}", "make": "${report.make}", "model": "${report.model}", "year": ${report.year}},
            "dtcs": [${report.dtcs.joinToString(",") { """{"code":"${it.code}","desc":"${it.description}"}""" }}]
        }
        """.trimIndent()
        val file = File(context.cacheDir, "report_${fileFormat.format(Date())}.json")
        file.writeText(json)
        file
    }
    
    fun shareReport(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = when {
                file.name.endsWith(".csv") -> "text/csv"
                file.name.endsWith(".json") -> "application/json"
                else -> "text/plain"
            }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share Report"))
    }
    
    private fun buildReportContent(report: DiagnosticReport) = buildString {
        appendLine("═══════════════════════════════════════")
        appendLine("       SPACETEC DIAGNOSTIC REPORT")
        appendLine("═══════════════════════════════════════")
        appendLine("Date: ${dateFormat.format(Date(report.timestamp))}")
        appendLine("VIN: ${report.vin}")
        appendLine("Vehicle: ${report.year} ${report.make} ${report.model}")
        appendLine()
        appendLine("─── DIAGNOSTIC TROUBLE CODES ───")
        if (report.dtcs.isEmpty()) appendLine("No DTCs found")
        else report.dtcs.forEach { appendLine("${it.code} - ${it.description} [${it.severity}]") }
        appendLine()
        if (report.liveData.isNotEmpty()) {
            appendLine("─── LIVE DATA SNAPSHOT ───")
            report.liveData.forEach { (k, v) -> appendLine("$k: $v") }
        }
        if (report.notes.isNotEmpty()) {
            appendLine()
            appendLine("─── NOTES ───")
            appendLine(report.notes)
        }
        appendLine()
        appendLine("═══════════════════════════════════════")
    }
}
