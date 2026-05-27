package com.rehab2.aac

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Faza 2A — varen IZVOZ lokalnega AAC paketa.
 *
 * Ta razred IZKLJUCNO:
 *   - prebere lokalni AAC paket iz poti v AacStoragePaths (getExternalFilesDir),
 *   - zapakira ga v ZIP z deterministicno strukturo,
 *   - shrani ZIP v filesDir/aac_exports/.
 *
 * Ta razred NE:
 *   - uvaza nicesar,
 *   - brise, preimenuje, migrira ali popravlja AAC podatkov,
 *   - bere assets/ ali SYSTEM fallback vsebine.
 * Edina datoteka, ki jo zapise, je nov ZIP v mapi aac_exports/.
 *
 * Deterministicna ZIP struktura (vedno enaka, relativne poti):
 *   aac_export_manifest.json
 *   data/aac_items.json
 *   data/profiles/<ime>.json
 *   icons/custom/<ime>
 *   icons/soca/<ime>
 *   icons/arasaac/<ime>
 * Prazne/neobstojece mape se v ZIP ne dodajajo umetno.
 */
object AacPackExporter {

    /** Verzija formata izvoza. Faza 2B (uvoz) bo to brala za zdruzljivost. */
    private const val EXPORT_VERSION = 1

    private const val EXPORTS_DIR_NAME = "aac_exports"

    /** Rezultat izvoza za prikaz uporabniku. */
    sealed class Result {
        /** Izvoz uspel. */
        data class Success(
            val zipFile: File,
            val fileCount: Int,
            val zipSizeBytes: Long
        ) : Result()

        /** Lokalnega paketa ni — to ni napaka, samo ni kaj izvoziti. */
        object NoLocalPackage : Result()

        /** Izvoz ni uspel. */
        data class Failure(val reason: String) : Result()
    }

    /**
     * Izvozi lokalni AAC paket v ZIP. Klici naj tecejo na background niti.
     */
    fun export(context: Context): Result {
        return try {
            buildExport(context)
        } catch (error: Exception) {
            Result.Failure(error.message ?: "Neznana napaka pri izvozu.")
        }
    }

    /** Mapa, kamor se shranjujejo izvozeni ZIP-i. */
    fun getExportsDir(context: Context): File {
        return File(context.filesDir, EXPORTS_DIR_NAME)
    }

    private fun buildExport(context: Context): Result {
        // -- Zberi vhodne datoteke (samo lokalni paket, read-only) --------
        val itemsFile = AacStoragePaths.getAacItemsFile(context)
        val profilesDir = AacStoragePaths.getProfilesDataDir(context)
        val customDir = AacStoragePaths.getIconsCustomDir(context)
        val socaDir = AacStoragePaths.getIconsSocaDir(context)
        val arasaacDir = AacStoragePaths.getIconsArasaacDir(context)

        // Vsak vnos: relativna pot v ZIP -> izvorna datoteka na disku.
        // LinkedHashMap ohranja vrstni red -> deterministicen ZIP.
        val entries = LinkedHashMap<String, File>()

        val hasItems = itemsFile != null && itemsFile.exists() && itemsFile.isFile
        if (hasItems && itemsFile != null) {
            entries["data/aac_items.json"] = itemsFile
        }

        var profileCount = 0
        if (profilesDir != null && profilesDir.exists() && profilesDir.isDirectory) {
            profilesDir.listFiles { file ->
                file.isFile && file.extension.equals("json", ignoreCase = true)
            }?.sortedBy { it.name.lowercase(Locale.ROOT) }?.forEach { file ->
                entries["data/profiles/${file.name}"] = file
                profileCount++
            }
        }

        addIconDir(entries, "icons/custom", customDir)
        addIconDir(entries, "icons/soca", socaDir)
        addIconDir(entries, "icons/arasaac", arasaacDir)

        // Ce ni nic lokalne vsebine -> ni kaj izvoziti (ni napaka).
        if (entries.isEmpty()) {
            return Result.NoLocalPackage
        }

        // -- Pripravi ciljno mapo in ime ZIP -----------------------------
        val exportsDir = getExportsDir(context)
        if (!exportsDir.exists() && !exportsDir.mkdirs()) {
            return Result.Failure("Mape za izvoz ni bilo mogoce ustvariti.")
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ROOT).format(Date())
        val zipFile = File(exportsDir, "NovaRehab_AAC_$timestamp.zip")

        // -- Manifest (preprost, za prihodnjo zdruzljivost) --------------
        val itemCount = if (hasItems && itemsFile != null) countItems(itemsFile) else 0
        val manifestJson = buildManifest(
            context = context,
            profileCount = profileCount,
            itemCount = itemCount
        )

        // -- Zapisi ZIP --------------------------------------------------
        var fileCount = 0
        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            // Manifest vedno prvi vnos.
            zip.putNextEntry(ZipEntry("aac_export_manifest.json"))
            zip.write(manifestJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            for ((entryPath, sourceFile) in entries) {
                if (!sourceFile.exists() || !sourceFile.isFile) continue
                zip.putNextEntry(ZipEntry(entryPath))
                sourceFile.inputStream().buffered().use { input ->
                    input.copyTo(zip)
                }
                zip.closeEntry()
                fileCount++
            }
        }

        if (!zipFile.exists() || zipFile.length() <= 0L) {
            return Result.Failure("ZIP datoteka ni bila ustvarjena.")
        }

        return Result.Success(
            zipFile = zipFile,
            fileCount = fileCount,
            zipSizeBytes = zipFile.length()
        )
    }

    /** Doda vse datoteke iz ene icon mape pod dani ZIP predpono. */
    private fun addIconDir(
        entries: LinkedHashMap<String, File>,
        zipPrefix: String,
        dir: File?
    ) {
        if (dir == null || !dir.exists() || !dir.isDirectory) return
        dir.listFiles { file -> file.isFile }
            ?.sortedBy { it.name.lowercase(Locale.ROOT) }
            ?.forEach { file ->
                entries["$zipPrefix/${file.name}"] = file
            }
    }

    /** Prebere stevilo elementov iz aac_items.json. Samo branje, brez sprememb. */
    private fun countItems(itemsFile: File): Int {
        return try {
            val json = itemsFile.readText(Charsets.UTF_8).trim()
            when {
                json.startsWith("[") -> org.json.JSONArray(json).length()
                json.startsWith("{") ->
                    JSONObject(json).optJSONArray("items")?.length() ?: 0
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun buildManifest(context: Context, profileCount: Int, itemCount: Int): String {
        val appVersion = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
        val createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).format(Date())

        // Rocno sestavljen JSON, da je izpis deterministicen in berljiv.
        return JSONObject().apply {
            put("exportVersion", EXPORT_VERSION)
            put("appVersion", appVersion)
            put("createdAt", createdAt)
            put("profiles", profileCount)
            put("items", itemCount)
        }.toString(2)
    }
}
