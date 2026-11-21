package com.rehab2.aac
import android.content.Context
/** Preferences survive restart/update, but are scoped to a patient. They do not grant Android permissions. */
object LiveCaptureSettings {
    data class Settings(val microphone:Boolean=false,val camera:Boolean=false,val sendAi:Boolean=false,val keepRaw:Boolean=false)
    private fun prefs(c:Context)=c.getSharedPreferences("live_capture_828_"+AacPatientImages.store(c).currentPatientId(),Context.MODE_PRIVATE)
    fun load(c:Context):Settings = runCatching { val p=prefs(c);Settings(p.getBoolean("microphone",false),p.getBoolean("camera",false),p.getBoolean("sendAi",false),p.getBoolean("keepRaw",false)) }.getOrDefault(Settings())
    fun save(c:Context,s:Settings) { prefs(c).edit().putBoolean("microphone",s.microphone).putBoolean("camera",s.camera).putBoolean("sendAi",s.sendAi).putBoolean("keepRaw",s.keepRaw).commit() }
}
