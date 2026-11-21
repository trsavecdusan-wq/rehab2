package com.rehab2.aac

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Editable caregiver evidence belongs only to the current private patient profile. */
object LivePatientNotes {
    private fun dir(c: Context): File {
        val id = AacPatientImages.store(c).currentPatientId()
        require(id.matches(Regex("[A-Za-z0-9_-]+")))
        return File(c.noBackupFilesDir, "live-patients/$id").apply { mkdirs() }
    }
    fun load(c: Context): JSONArray = runCatching { JSONArray(File(dir(c), "profile.json").readText()) }.getOrElse { JSONArray() }
    fun add(c: Context, text: String, source: String, evidence: String = "") {
        require(text.isNotBlank())
        val rows = load(c); require(rows.length() < 100) { "Največ 100 zapisov; popravite ali odstranite stare." }
        rows.put(JSONObject().put("id", UUID.randomUUID().toString()).put("text", text.take(2000)).put("source", source)
            .put("evidence", evidence.take(2000)).put("updatedAt", System.currentTimeMillis()))
        File(dir(c), "profile.json").writeText(rows.toString(2))
    }
    fun replace(c: Context, index: Int, text: String?) {
        val rows = load(c); if (index !in 0 until rows.length()) return
        if (text == null) rows.remove(index) else rows.getJSONObject(index).put("previous", rows.getJSONObject(index).optString("text"))
            .put("text", text.take(2000)).put("updatedAt", System.currentTimeMillis()).put("correctionSource", "caregiver")
        File(dir(c), "profile.json").writeText(rows.toString(2))
    }
    fun saveReport(c: Context, days: Int, report: String) {
        if (days !in listOf(7, 30)) return
        val file = File(dir(c), "session-${System.currentTimeMillis()}.json")
        file.writeText(JSONObject().put("expires", System.currentTimeMillis() + days * 86400000L).put("report", report).toString())
        reports(c)
    }
    fun reports(c: Context): List<File> = dir(c).listFiles().orEmpty().filter { it.name.startsWith("session-") }.filter {
        val valid = runCatching { JSONObject(it.readText()).getLong("expires") > System.currentTimeMillis() }.getOrDefault(false)
        if (!valid) it.delete(); valid
    }.sortedByDescending { it.name }.also { rows -> rows.drop(30).forEach { it.delete() } }.take(30)
    fun deleteReports(c: Context) { reports(c).forEach { it.delete() } }
    @Synchronized fun saveRaw(c:Context,patient:String,ext:String,bytes:ByteArray) {
        if(patient!=AacPatientImages.store(c).currentPatientId() || !LiveCaptureSettings.load(c).keepRaw)return
        require(patient.matches(Regex("[A-Za-z0-9_-]+")))
        val folder=File(c.noBackupFilesDir,"live-patients/$patient/raw").apply{mkdirs()}
        folder.listFiles().orEmpty().filter{it.lastModified()<System.currentTimeMillis()-86400000L}.forEach{it.delete()}
        if(folder.listFiles().orEmpty().sumOf{it.length()}+bytes.size>20_000_000L)return
        File(folder,"${System.currentTimeMillis()}-${UUID.randomUUID()}.$ext").writeBytes(bytes)
    }
    @Synchronized fun deleteRaw(c:Context) { File(dir(c),"raw").listFiles().orEmpty().forEach{it.delete()} }
    fun purgeExpired(c:Context) {
        File(c.noBackupFilesDir,"live-patients").listFiles().orEmpty().filter{it.isDirectory}.forEach { patient ->
            File(patient,"raw").listFiles().orEmpty().filter{it.lastModified()<System.currentTimeMillis()-86400000L}.forEach{it.delete()}
            patient.listFiles().orEmpty().filter{it.name.startsWith("session-")}.forEach { file ->
                if(runCatching{JSONObject(file.readText()).getLong("expires")<=System.currentTimeMillis()}.getOrDefault(true))file.delete()
            }
        }
    }
}
