package com.rehab2.radio

import android.content.Context
import android.widget.Toast
import org.json.JSONObject
import java.io.File

class RadioSettingsBackup(private val context: Context) {
    companion object {
        private var lastRestoreNotice: String? = null
    }

    fun saveInternalStationsBackup(internalFile: File) {
        runCatching {
            if (!internalFile.exists() || !internalFile.isFile || internalFile.length() <= 0L) {
                return
            }

            val content = internalFile.readText(Charsets.UTF_8)
            JSONObject(content)

            val backupFile = getBackupFile() ?: return
            backupFile.parentFile?.mkdirs()
            backupFile.writeText(content, Charsets.UTF_8)
            Toast.makeText(context, "RADIO BACKUP SAVED", Toast.LENGTH_SHORT).show()
        }
    }

    fun restoreToInternalIfNeeded(internalFile: File) {
        val internalMissingOrEmpty = !internalFile.exists() || !internalFile.isFile || internalFile.length() <= 0L
        if (!internalMissingOrEmpty) {
            lastRestoreNotice = null
            return
        }

        val backupFile = getBackupFile()
        if (backupFile == null || !backupFile.exists() || !backupFile.isFile || backupFile.length() <= 0L) {
            showRestoreNoticeOnce("RADIO BACKUP MISSING")
            return
        }

        val content = try {
            backupFile.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            showRestoreNoticeOnce("RADIO BACKUP INVALID")
            return
        }

        val isValid = try {
            val root = JSONObject(content)
            root.optJSONArray("stations") != null
        } catch (_: Exception) {
            false
        }

        if (!isValid) {
            showRestoreNoticeOnce("RADIO BACKUP INVALID")
            return
        }

        runCatching {
            internalFile.parentFile?.mkdirs()
            internalFile.writeText(content, Charsets.UTF_8)
            showRestoreNoticeOnce("RADIO BACKUP RESTORED")
        }.onFailure {
            showRestoreNoticeOnce("RADIO BACKUP INVALID")
        }
    }

    private fun showRestoreNoticeOnce(message: String) {
        if (lastRestoreNotice == message) {
            return
        }
        lastRestoreNotice = message
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun getBackupFile(): File? {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return null
        val settingsDir = File(externalFilesDir, "NovaRehab2/settings").apply { mkdirs() }
        return File(settingsDir, "radio_settings.json")
    }
}
