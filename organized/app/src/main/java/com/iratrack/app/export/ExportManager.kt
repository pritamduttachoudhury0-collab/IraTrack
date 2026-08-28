package com.iratrack.app.export

import android.content.Context
import com.iratrack.app.data.UsageRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ExportManager {
    private const val CSV_NAME = "iratrack-usage.csv"
    private const val JSON_NAME = "iratrack-usage.json"

    fun deleteExports(context: Context) {
        File(context.cacheDir, CSV_NAME).delete()
        File(context.cacheDir, JSON_NAME).delete()
    }

    fun csv(context: Context, records: List<UsageRecord>): File {
        val file = File(context.cacheDir, CSV_NAME)
        file.bufferedWriter().use { out -> out.write(csvText(records)) }
        return file
    }

    fun json(context: Context, records: List<UsageRecord>): File {
        val file = File(context.cacheDir, JSON_NAME)
        file.bufferedWriter().use { out -> out.write(jsonText(records)) }
        return file
    }

    /**
     * Pure text builder, kept separate from file I/O so export formatting can be
     * unit tested without a Context. Exports intentionally carry only the fields
     * on [UsageRecord] itself; that type has no credential/API-key field, so
     * there is nothing here for a future contributor to accidentally add.
     */
    fun csvText(records: List<UsageRecord>): String = buildString {
        appendLine("timestamp,provider,cost_usd,input_units,output_units,total_units,unit_kind,unit_label,requests,model,cost_status")
        records.forEach {
            appendLine(
                listOf(
                    it.timestamp,
                    it.provider,
                    it.costUsd ?: "",
                    it.inputUnits ?: "",
                    it.outputUnits ?: "",
                    it.totalUnits ?: "",
                    it.unitKind,
                    csvField(it.unitLabel),
                    it.requests ?: "",
                    csvField(it.model ?: ""),
                    it.status
                ).joinToString(",")
            )
        }
    }

    fun jsonText(records: List<UsageRecord>): String {
        val array = JSONArray()
        records.forEach { record ->
            val obj = JSONObject()
            obj.put("timestamp", record.timestamp)
            obj.put("provider", record.provider)
            obj.put("costUsd", record.costUsd ?: JSONObject.NULL)
            obj.put("inputUnits", record.inputUnits ?: JSONObject.NULL)
            obj.put("outputUnits", record.outputUnits ?: JSONObject.NULL)
            obj.put("totalUnits", record.totalUnits ?: JSONObject.NULL)
            obj.put("unitKind", record.unitKind.name)
            obj.put("unitLabel", record.unitLabel)
            obj.put("requests", record.requests ?: JSONObject.NULL)
            obj.put("model", record.model ?: JSONObject.NULL)
            obj.put("costStatus", record.status.name)
            array.put(obj)
        }
        return array.toString(2)
    }

    private fun csvField(value: String): String =
        "\"${value.replace("\"", "\"\"")}\""
}
