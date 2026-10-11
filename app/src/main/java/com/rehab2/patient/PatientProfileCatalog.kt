package com.rehab2.patient

import com.rehab2.aac.PatientGender
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Extends the existing patient_profile.json. Communication profiles remain separate. */
class PatientProfileCatalog(private val file: File) {
    companion object { private val lock = Any() }
    private fun root(): JSONObject {
        val backup=File(file.parentFile,file.name+".bak")
        val source=if(file.isFile) file else backup
        return if(source.isFile) JSONObject(source.readText()) else JSONObject()
    }
    private fun decode(o: JSONObject): PatientProfile = PatientProfile(
        id=o.optString("id","legacy"), displayName=o.optString("displayName","Obstoječi pacient"),
        visualSex=PatientGender.parse(o.optString("visualSex")), grammaticalGender=PatientGender.parse(o.optString("grammaticalGender")),
        gender=o.optString("gender"), paralyzedSide=o.optString("paralyzedSide"), dominantHand=o.optString("dominantHand"),
        aphasiaLevel=o.optString("aphasiaLevel"), communicationLevel=o.optString("communicationLevel"),
        primaryLanguage=o.optString("primaryLanguage","sl"), secondaryLanguage=o.optString("secondaryLanguage"), personalityMode=o.optString("personalityMode"))
    private fun profiles(root: JSONObject): List<PatientProfile> = root.optJSONArray("visualPatientProfiles")?.let { a ->
        (0 until a.length()).map { decode(a.getJSONObject(it)) }
    } ?: listOf(decode(root))
    fun list(): List<PatientProfile> = synchronized(lock) { profiles(root()) }
    fun active(): PatientProfile = synchronized(lock) {
        val r=root(); val all=profiles(r); all.single { it.id==r.optString("activeVisualPatientId","legacy") }
    }
    fun legacyOwner(): String = synchronized(lock) { root().optString("legacyImageOwnerId","legacy") }
    private fun encode(p: PatientProfile) = JSONObject().put("id",p.id).put("displayName",p.displayName)
        .put("visualSex",p.visualSex.name).put("grammaticalGender",p.grammaticalGender.name)
        .put("gender",p.gender).put("paralyzedSide",p.paralyzedSide).put("dominantHand",p.dominantHand)
        .put("aphasiaLevel",p.aphasiaLevel).put("communicationLevel",p.communicationLevel)
        .put("primaryLanguage",p.primaryLanguage).put("secondaryLanguage",p.secondaryLanguage).put("personalityMode",p.personalityMode)
    private fun write(r: JSONObject) {
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        val temp=File(file.parentFile,file.name+".tmp"); val backup=File(file.parentFile,file.name+".bak")
        temp.outputStream().use { it.write(r.toString(2).toByteArray(Charsets.UTF_8)); it.fd.sync() }
        if(file.exists()) { check(!backup.exists() || backup.delete()); check(file.renameTo(backup)) }
        if(!temp.renameTo(file)) { backup.renameTo(file); error("Profile write failed") }; backup.delete()
    }
    fun save(profile: PatientProfile) = synchronized(lock) {
        require(profile.id.matches(Regex("[A-Za-z0-9_-]{1,80}")) && profile.displayName.isNotBlank())
        val r=root(); val all=profiles(r).filterNot { it.id==profile.id }+profile
        r.put("visualPatientProfiles",JSONArray(all.map(::encode))).put("legacyImageOwnerId",r.optString("legacyImageOwnerId","legacy"))
        write(r)
    }
    fun create(name: String): PatientProfile = PatientProfile(id=UUID.randomUUID().toString(),displayName=name.trim()).also { save(it) }
    fun select(id: String) = synchronized(lock) {
        val r=root(); require(profiles(r).any { it.id==id }); r.put("activeVisualPatientId",id); write(r)
    }
}
