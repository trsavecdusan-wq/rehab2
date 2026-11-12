package com.rehab2.aac

import android.content.Context

internal object AacEmailRecipients {
    private const val PREFERENCES = "aac_email_recipients"
    private val keys = listOf("recipient_1", "recipient_2", "recipient_3")

    fun load(context: Context): List<String> {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return keys.map { preferences.getString(it, "").orEmpty() }
    }

    fun save(context: Context, addresses: List<String>) {
        require(addresses.size == keys.size)
        val editor = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
        keys.zip(addresses).forEach { (key, address) ->
            if (address.isEmpty()) editor.remove(key) else editor.putString(key, address)
        }
        editor.apply()
    }
}
