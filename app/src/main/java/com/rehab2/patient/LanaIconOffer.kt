package com.rehab2.patient

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import com.rehab2.PersonalIconImportActivity

/** Once per installation, not once per patient. No download happens without confirmation. */
object LanaIconOffer {
    fun showOnce(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val prefs = activity.getSharedPreferences("personal_package_offers",0)
        val key = "offered_" + VerifiedPackageDownload.LANA_SHA256
        if (prefs.getBoolean(key,false)) return
        if (!prefs.edit().putBoolean(key,true).commit()) return
        AlertDialog.Builder(activity).setTitle("PRENESI LANINE OSEBNE IKONE")
            .setMessage("Pripravljen je paket 23 osebnih slik. Prenos je ločen od namestitve. V naslednjem koraku preveri, da je izbran Lanin profil. Slike in zamenjave potrdiš v predogledu. Pozneje je prenos na voljo v nastavitvah osebnih ikon.")
            .setPositiveButton("PRENESI LANINE OSEBNE IKONE") { _, _ ->
                activity.startActivity(Intent(activity,PersonalIconImportActivity::class.java).putExtra("offerLana",true))
            }.setNegativeButton("POZNEJE",null).show()
    }
}
