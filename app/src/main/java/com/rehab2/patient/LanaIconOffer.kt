package com.rehab2.patient

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import com.rehab2.LanaGridImportActivity

/** Once per installation, not once per patient. No download happens without confirmation. */
object LanaIconOffer {
    fun showOnce(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val prefs = activity.getSharedPreferences("personal_package_offers",0)
        val key = "offered_" + LanaGridImportActivity.HASHES[0]
        if (prefs.getBoolean(key,false)) return
        if (!prefs.edit().putBoolean(key,true).commit()) return
        AlertDialog.Builder(activity).setTitle("PRENESI LANINE OSEBNE IKONE")
            .setMessage("Pripravljena je nova mreža v4 z 72 slikovnimi predlogi v dveh paketih. Prenos je ločen od namestitve. V naslednjem koraku preveri, da je izbran Lanin profil. Mrežo, slike in zamenjave potrdiš v predogledu. Pozneje je prenos na voljo v nastavitvah osebnih ikon.")
            .setPositiveButton("PRENESI LANINE OSEBNE IKONE") { _, _ ->
                activity.startActivity(Intent(activity,LanaGridImportActivity::class.java).putExtra("offerLana",true))
            }.setNegativeButton("POZNEJE",null).show()
    }
}
