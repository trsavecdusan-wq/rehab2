package com.rehab2.aac

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Faza 1 — validacija lokalnega AAC paketa.
 *
 * Ta diagnostika preverja IZKLJUCNO lokalni AAC paket v getExternalFilesDir()
 * (poti iz AacStoragePaths). NE preverja:
 *   - assets/ packaged demo vsebine,
 *   - APK internih fallback SYSTEM podatkov.
 * SYSTEM fallback je tukaj samo status ("na voljo"), ne predmet validacije.
 *
 * Diagnostika namenoma NE uporablja AacLocalJsonLoader za nalaganje, ker loader
 * ob napaki tiho pade na fallback in s tem SKRIJE napako. Diagnostika mora napako
 * VIDETI. Zato je tu majhna, samostojna parse-preverba, skladna z isto logiko kot
 * loader (oglati oklepaj = polje, zaviti oklepaj s kljucem items/profiles = objekt).
 * AacLocalJsonLoader se NE refaktorira.
 */
object AacContentDiagnostics {

    /** Skupna ocena lokalnega paketa za terapevta. */
    enum class Severity {
        OK,         // paket prisoten in veljaven
        WARNING,    // JSON veljaven, a manjkajo ikone/slike
        ERROR,      // pokvarjen JSON
        NO_PACKAGE  // ni lokalnega paketa — normalno zacetno stanje, NI napaka
    }

    /** Rezultat preverbe ene JSON datoteke. */
    enum class JsonState {
        OK,       // prebrana in veljavna
        MISSING,  // datoteke ni
        CORRUPT   // datoteka obstaja, a je ni mogoce razclenIti
    }

    data class Report(
        val storageUnavailable: Boolean,
        val basePath: String?,

        // AAC elementi (aac_items.json)
        val itemsJsonState: JsonState,
        val itemCount: Int,

        // Profili (NovaRehab/data/profiles/*.json)
        val profilesDirExists: Boolean,
        val validProfileCount: Int,
        val validProfileFileNames: List<String>,
        val corruptProfileFileNames: List<String>,
        val domProfileExists: Boolean,

        // Ikone — mape
        val customIconsDirExists: Boolean,
        val socaIconsDirExists: Boolean,
        val arasaacIconsDirExists: Boolean,
        val customIconFileCount: Int,
        val socaIconFileCount: Int,
        val arasaacIconFileCount: Int,

        // Ikone — manjkajoce slike, na katere se sklicujejo dejanski elementi
        val missingIconPaths: List<String>,
        val iconCheckSkipped: Boolean, // true, ce items JSON ni bil berljiv -> ikon ni bilo mogoce preveriti

        // Paket — metapodatki
        val packageTotalBytes: Long,
        val packageLastModified: Long, // epoch ms; 0 = neznano

        // Skupna ocena
        val severity: Severity
    )

    fun inspect(context: Context): Report {
        val externalFilesDir = context.getExternalFilesDir(null)
            ?: return emptyReport(storageUnavailable = true, basePath = null)

        return try {
            buildReport(context, externalFilesDir)
        } catch (_: Exception) {
            // Diagnostika ne sme nikoli sesuti aplikacije.
            emptyReport(storageUnavailable = false, basePath = externalFilesDir.absolutePath)
        }
    }

    private fun emptyReport(storageUnavailable: Boolean, basePath: String?): Report {
        return Report(
            storageUnavailable = storageUnavailable,
            basePath = basePath,
            itemsJsonState = JsonState.MISSING,
            itemCount = 0,
            profilesDirExists = false,
            validProfileCount = 0,
            validProfileFileNames = emptyList(),
            corruptProfileFileNames = emptyList(),
            domProfileExists = false,
            customIconsDirExists = false,
            socaIconsDirExists = false,
            arasaacIconsDirExists = false,
            customIconFileCount = 0,
            socaIconFileCount = 0,
            arasaacIconFileCount = 0,
            missingIconPaths = emptyList(),
            iconCheckSkipped = true,
            packageTotalBytes = 0L,
            packageLastModified = 0L,
            severity = Severity.NO_PACKAGE
        )
    }

    private fun buildReport(context: Context, externalFilesDir: File): Report {
        val itemsFile = AacStoragePaths.getAacItemsFile(context)
        val profilesDir = AacStoragePaths.getProfilesDataDir(context)
        val customIconsDir = AacStoragePaths.getIconsCustomDir(context)
        val socaIconsDir = AacStoragePaths.getIconsSocaDir(context)
        val arasaacIconsDir = AacStoragePaths.getIconsArasaacDir(context)

        // -- AAC elementi --------------------------------------------------
        val itemsParse = parseItems(itemsFile)
        val itemsJsonState = itemsParse.state
        val itemCount = itemsParse.items.size

        // -- Profili -------------------------------------------------------
        val profilesDirExists = profilesDir?.exists() == true && profilesDir.isDirectory
        val validProfileFileNames = mutableListOf<String>()
        val corruptProfileFileNames = mutableListOf<String>()
        if (profilesDirExists) {
            profilesDir?.listFiles { file ->
                file.isFile && file.extension.equals("json", ignoreCase = true)
            }?.sortedBy { it.name.lowercase() }?.forEach { file ->
                if (isJsonStructureValid(file)) {
                    validProfileFileNames.add(file.name)
                } else {
                    corruptProfileFileNames.add(file.name)
                }
            }
        }
        val domProfileExists = validProfileFileNames.any { it.equals("dom.json", ignoreCase = true) }

        // -- Ikone — mape in stevila --------------------------------------
        val customIconFileCount = countFilesIn(customIconsDir)
        val socaIconFileCount = countFilesIn(socaIconsDir)
        val arasaacIconFileCount = countFilesIn(arasaacIconsDir)

        // -- Ikone — manjkajoce, na katere se sklicuje dejanska vsebina ----
        val missingIconPaths = mutableListOf<String>()
        var iconCheckSkipped = false
        if (itemsJsonState == JsonState.OK) {
            itemsParse.items.forEach { ref ->
                val path = ref.imagePath.trim()
                if (path.isEmpty() || ref.source == IconSource.SYSTEM) {
                    return@forEach // SYSTEM ikone so vgrajene v APK, nimajo datoteke
                }
                val resolved = AacStoragePaths.resolveIconFile(context, path, ref.source)
                val exists = resolved?.exists() == true && resolved.isFile
                if (!exists && !missingIconPaths.contains(path)) {
                    missingIconPaths.add(path)
                }
            }
        } else {
            // Items JSON ni berljiv -> referenc na ikone ni mogoce preveriti.
            iconCheckSkipped = true
        }

        // -- Paket — velikost in datum zadnje spremembe -------------------
        val packageRoot = File(externalFilesDir, "NovaRehab")
        val packageTotalBytes = directorySizeBytes(packageRoot)
        val packageLastModified = directoryLastModified(packageRoot)

        // -- Skupna ocena -------------------------------------------------
        val hasAnyLocalContent = itemsJsonState != JsonState.MISSING ||
            validProfileFileNames.isNotEmpty() ||
            corruptProfileFileNames.isNotEmpty()

        val severity = when {
            !hasAnyLocalContent -> Severity.NO_PACKAGE
            itemsJsonState == JsonState.CORRUPT || corruptProfileFileNames.isNotEmpty() -> Severity.ERROR
            missingIconPaths.isNotEmpty() -> Severity.WARNING
            else -> Severity.OK
        }

        return Report(
            storageUnavailable = false,
            basePath = externalFilesDir.absolutePath,
            itemsJsonState = itemsJsonState,
            itemCount = itemCount,
            profilesDirExists = profilesDirExists,
            validProfileCount = validProfileFileNames.size,
            validProfileFileNames = validProfileFileNames,
            corruptProfileFileNames = corruptProfileFileNames,
            domProfileExists = domProfileExists,
            customIconsDirExists = customIconsDir?.exists() == true && customIconsDir.isDirectory,
            socaIconsDirExists = socaIconsDir?.exists() == true && socaIconsDir.isDirectory,
            arasaacIconsDirExists = arasaacIconsDir?.exists() == true && arasaacIconsDir.isDirectory,
            customIconFileCount = customIconFileCount,
            socaIconFileCount = socaIconFileCount,
            arasaacIconFileCount = arasaacIconFileCount,
            missingIconPaths = missingIconPaths,
            iconCheckSkipped = iconCheckSkipped,
            packageTotalBytes = packageTotalBytes,
            packageLastModified = packageLastModified,
            severity = severity
        )
    }

    // -- Minimalna, samostojna parse-preverba ----------------------------
    // (skladna z logiko AacLocalJsonLoader, a NE refaktorira loaderja)

    /** Lahka referenca na ikono iz items JSON; samo za diagnostiko. */
    private data class IconRef(val imagePath: String, val source: IconSource)

    private data class ItemsParseResult(val state: JsonState, val items: List<IconRef>)

    private fun parseItems(itemsFile: File?): ItemsParseResult {
        if (itemsFile == null || !itemsFile.exists() || !itemsFile.isFile) {
            return ItemsParseResult(JsonState.MISSING, emptyList())
        }
        return try {
            val json = itemsFile.readText(Charsets.UTF_8).trim()
            val array: JSONArray = when {
                json.startsWith("[") -> JSONArray(json)
                json.startsWith("{") -> JSONObject(json).optJSONArray("items") ?: JSONArray()
                else -> return ItemsParseResult(JsonState.CORRUPT, emptyList())
            }
            val refs = buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val id = obj.optString("id").trim()
                    if (id.isBlank()) continue
                    add(
                        IconRef(
                            imagePath = obj.optString("imagePath").trim(),
                            source = parseIconSource(obj.optString("iconSource"))
                        )
                    )
                }
            }
            ItemsParseResult(JsonState.OK, refs)
        } catch (_: Exception) {
            ItemsParseResult(JsonState.CORRUPT, emptyList())
        }
    }

    /** Vrne true, ce je JSON datoteko mogoce razclenIti kot objekt ali polje. */
    private fun isJsonStructureValid(file: File): Boolean {
        return try {
            val json = file.readText(Charsets.UTF_8).trim()
            when {
                json.startsWith("[") -> { JSONArray(json); true }
                json.startsWith("{") -> { JSONObject(json); true }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun parseIconSource(value: String?): IconSource {
        return when (value?.trim()?.uppercase()) {
            "SOCA" -> IconSource.SOCA
            "ARASAAC" -> IconSource.ARASAAC
            "PATIENT", "PATIENT_PHOTO" -> IconSource.PATIENT_PHOTO
            "CUSTOM", "CUSTOM_PHOTO" -> IconSource.CUSTOM_PHOTO
            "PLACE", "PLACE_PHOTO" -> IconSource.PLACE_PHOTO
            "SYSTEM" -> IconSource.SYSTEM
            else -> IconSource.SYSTEM
        }
    }

    private fun countFilesIn(dir: File?): Int {
        if (dir == null || !dir.exists() || !dir.isDirectory) return 0
        return dir.listFiles()?.count { it.isFile } ?: 0
    }

    private fun directorySizeBytes(root: File): Long {
        if (!root.exists()) return 0L
        var total = 0L
        root.walkTopDown().forEach { file ->
            if (file.isFile) total += file.length()
        }
        return total
    }

    private fun directoryLastModified(root: File): Long {
        if (!root.exists()) return 0L
        var newest = 0L
        root.walkTopDown().forEach { file ->
            if (file.isFile && file.lastModified() > newest) newest = file.lastModified()
        }
        return newest
    }
}
