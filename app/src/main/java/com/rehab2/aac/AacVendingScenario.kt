package com.rehab2.aac

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object AacVendingScenario {
    const val PROFILE_ID = "avtomat"
    private const val CODES_FILE = "NovaRehab/data/scenarios/vending_codes.json"
    private const val DEFAULT_LOCATION_ID = "default_location"
    private const val DRINKS_MACHINE_ID = "drinks_machine"
    private const val COFFEE_TEA_MACHINE_ID = "coffee_tea_machine"

    val supportedProducts = listOf(
        Product("drink_fanta", "FANTA", DRINKS_MACHINE_ID),
        Product("drink_coca_cola", "COCA COLA", DRINKS_MACHINE_ID),
        Product("drink_pepsi", "PEPSI", DRINKS_MACHINE_ID),
        Product("water", "VODA", DRINKS_MACHINE_ID),
        Product("juice", "SOK", DRINKS_MACHINE_ID),
        Product("coffee", "KAVA", COFFEE_TEA_MACHINE_ID),
        Product("tea", "CAJ", COFFEE_TEA_MACHINE_ID),
        Product("drink_milk", "MLEKO", COFFEE_TEA_MACHINE_ID)
    )

    private val supportedProductIds = supportedProducts.map { product -> product.itemId }.toSet()

    data class Product(
        val itemId: String,
        val label: String,
        val machineId: String
    )

    data class Config(
        val activeLocationId: String,
        val locations: List<Location>
    )

    data class Location(
        val id: String,
        val labelSl: String,
        val activeMachineId: String,
        val machines: List<Machine>
    )

    data class Machine(
        val id: String,
        val labelSl: String,
        val type: String,
        val codes: Map<String, String>
    )

    fun codesFile(context: Context): File? {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return null
        return File(externalFilesDir, CODES_FILE)
    }

    fun loadConfig(context: Context): Config {
        return readLocalConfig(context) ?: defaultConfig()
    }

    fun activeLocation(context: Context): Location {
        val config = loadConfig(context)
        return config.locations.firstOrNull { location -> location.id == config.activeLocationId }
            ?: config.locations.first()
    }

    fun saveCode(context: Context, machineId: String, itemId: String, code: String): Boolean {
        val safeItemId = itemId.trim()
        val safeMachineId = machineId.trim()
        if (safeItemId !in supportedProductIds || safeMachineId.isBlank()) {
            return false
        }
        val currentConfig = readLocalConfig(context) ?: defaultConfig()
        val normalizedCode = normalizeCode(code)
        val updatedLocations = currentConfig.locations.map { location ->
            location.copy(
                machines = location.machines.map { machine ->
                    if (location.id == currentConfig.activeLocationId && machine.id == safeMachineId) {
                        val updatedCodes = machine.codes.toMutableMap()
                        updatedCodes[safeItemId] = normalizedCode
                        machine.copy(codes = updatedCodes)
                    } else {
                        machine
                    }
                }
            )
        }
        return writeConfig(context, currentConfig.copy(locations = updatedLocations))
    }

    fun codeFor(context: Context, item: AacItem): String? {
        val product = supportedProducts.firstOrNull { product -> product.itemId == item.id } ?: return null
        val location = activeLocation(context)
        val machine = location.machines.firstOrNull { candidate -> candidate.id == product.machineId } ?: return null
        return machine.codes[item.id]?.takeIf { code -> code.isNotBlank() }
    }

    fun machineFor(context: Context, item: AacItem): Machine? {
        val product = supportedProducts.firstOrNull { product -> product.itemId == item.id } ?: return null
        return activeLocation(context).machines.firstOrNull { machine -> machine.id == product.machineId }
    }

    fun productsForMachine(machineId: String): List<Product> {
        return supportedProducts.filter { product -> product.machineId == machineId }
    }

    fun canHandle(item: AacItem): Boolean {
        return item.id in supportedProductIds
    }

    fun codePromptFor(context: Context, item: AacItem): String? {
        return codeFor(context, item)?.let { code -> "Koda: $code" }
    }

    fun speechFor(context: Context, item: AacItem): String {
        val productName = productNameFor(item)
        val code = codeFor(context, item)
        return if (code.isNullOrBlank()) {
            "Prosim, pomagajte mi izbrati $productName."
        } else {
            "Prosim, pomagajte mi izbrati $productName. Koda je $code."
        }
    }

    private fun defaultConfig(): Config {
        return Config(
            activeLocationId = DEFAULT_LOCATION_ID,
            locations = listOf(
                Location(
                    id = DEFAULT_LOCATION_ID,
                    labelSl = "Trenutna lokacija",
                    activeMachineId = DRINKS_MACHINE_ID,
                    machines = listOf(
                        Machine(
                            id = DRINKS_MACHINE_ID,
                            labelSl = "Avtomat za pijače",
                            type = "DRINKS",
                            codes = mapOf(
                                "drink_fanta" to "B07",
                                "drink_coca_cola" to "B08",
                                "drink_pepsi" to "B09",
                                "water" to "A01",
                                "juice" to ""
                            )
                        ),
                        Machine(
                            id = COFFEE_TEA_MACHINE_ID,
                            labelSl = "Avtomat za kavo in čaj",
                            type = "COFFEE_TEA",
                            codes = mapOf(
                                "coffee" to "",
                                "tea" to "",
                                "drink_milk" to ""
                            )
                        )
                    )
                )
            )
        )
    }

    private fun productNameFor(item: AacItem): String {
        return when (item.id) {
            "drink_fanta" -> "Fanto"
            "drink_coca_cola" -> "Coca Colo"
            "drink_pepsi" -> "Pepsi"
            "water" -> "vodo"
            "coffee" -> "kavo"
            "tea" -> "čaj"
            else -> item.labelSl.trim().lowercase().replaceFirstChar { first ->
                if (first.isLowerCase()) first.titlecase() else first.toString()
            }
        }
    }

    private fun readLocalConfig(context: Context): Config? {
        val file = codesFile(context) ?: return null
        return try {
            if (!file.exists()) {
                return null
            }
            parseConfig(JSONObject(file.readText(Charsets.UTF_8))).takeIf { config ->
                config.locations.isNotEmpty()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseConfig(json: JSONObject): Config {
        val locations = buildList {
            val locationsArray = json.optJSONArray("locations") ?: JSONArray()
            for (index in 0 until locationsArray.length()) {
                locationsArray.optJSONObject(index)?.let { locationJson ->
                    parseLocation(locationJson)?.let(::add)
                }
            }
        }
        return Config(
            activeLocationId = json.optString("activeLocationId").ifBlank { DEFAULT_LOCATION_ID },
            locations = locations
        )
    }

    private fun parseLocation(json: JSONObject): Location? {
        val id = json.optString("id").trim().ifBlank { return null }
        val machines = buildList {
            val machinesArray = json.optJSONArray("machines") ?: JSONArray()
            for (index in 0 until machinesArray.length()) {
                machinesArray.optJSONObject(index)?.let { machineJson ->
                    parseMachine(machineJson)?.let(::add)
                }
            }
        }
        return Location(
            id = id,
            labelSl = json.optString("labelSl").trim().ifBlank { id },
            activeMachineId = json.optString("activeMachineId").trim(),
            machines = machines
        )
    }

    private fun parseMachine(json: JSONObject): Machine? {
        val id = json.optString("id").trim().ifBlank { return null }
        val codesJson = json.optJSONObject("codes") ?: JSONObject()
        val codes = buildMap {
            supportedProductIds.forEach { itemId ->
                put(itemId, normalizeCode(codesJson.optString(itemId)))
            }
        }
        return Machine(
            id = id,
            labelSl = json.optString("labelSl").trim().ifBlank { id },
            type = json.optString("type").trim(),
            codes = codes
        )
    }

    private fun writeConfig(context: Context, config: Config): Boolean {
        val file = codesFile(context) ?: return false
        return try {
            file.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) {
                    return false
                }
            }
            file.writeText(toJson(config).toString(2), Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun toJson(config: Config): JSONObject {
        return JSONObject()
            .put("activeLocationId", config.activeLocationId)
            .put("locations", JSONArray().apply {
                config.locations.forEach { location ->
                    put(JSONObject()
                        .put("id", location.id)
                        .put("labelSl", location.labelSl)
                        .put("activeMachineId", location.activeMachineId)
                        .put("machines", JSONArray().apply {
                            location.machines.forEach { machine ->
                                put(JSONObject()
                                    .put("id", machine.id)
                                    .put("labelSl", machine.labelSl)
                                    .put("type", machine.type)
                                    .put("codes", JSONObject().apply {
                                        machine.codes.forEach { (itemId, code) ->
                                            put(itemId, normalizeCode(code))
                                        }
                                    })
                                )
                            }
                        })
                    )
                }
            })
    }

    private fun normalizeCode(code: String): String {
        return code.trim().uppercase().replace(Regex("\\s+"), "")
    }
}
