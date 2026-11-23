package com.rehab2.aac

import android.content.Context
import org.json.JSONObject

/** Opt-in per UUID. No persisted catalogue, custom language or old subtree is rewritten. */
object AacGridV4 {
    private var cached: JSONObject? = null
    @Synchronized fun spec(c:Context):JSONObject = cached ?: JSONObject(c.assets.open("lana-grid-v4.json").bufferedReader().use{it.readText()}).also{cached=it}
    fun enabled(c:Context)=runCatching { val s=AacPatientImages.store(c);s.gridV4(s.currentPatientId()) }.getOrDefault(false)
    fun home(c:Context):List<String> { val a=spec(c).getJSONArray("home");return (0 until a.length()).map{a.getString(it)} }
    fun nodes(c:Context):List<JSONObject> { val a=spec(c).getJSONArray("nodes");return (0 until a.length()).map{a.getJSONObject(it)} }
    fun mode(c:Context,id:String):String? = if(enabled(c)) nodes(c).firstOrNull{it.getString("id")==id}?.getString("speech_mode") else null
    fun previewCatalog(c:Context,stored:Collection<AacItem>):List<AacItem> = overlay(c,stored)
    fun apply(c:Context,stored:Collection<AacItem>):List<AacItem> = if(enabled(c)) overlay(c,stored) else stored.toList()
    private fun overlay(c:Context,stored:Collection<AacItem>):List<AacItem> {
        val items=stored.associateBy{it.id}.toMutableMap();val roots=home(c)
        for(n in nodes(c)) {
            val id=n.getString("id");val old=items[id]
            val custom=old?.let{it.locked||it.userEdited||it.translationManualOverride}==true
            val sl=n.getString("speechSl");val uk=n.getString("speechUk")
            val label=n.getString("motiv");val ukLabel=n.getString("labelUk")
            val base=old ?: AacItem(id=id,labelSl=label,imagePath="",actionType="speak",targetPageId="",isRootItem=false)
            val a=n.getJSONArray("children");val children=((0 until a.length()).map{a.getString(it)}+base.children).distinct()
            items[id]=base.copy(
                labelSl=if(custom)base.labelSl else label,labelUk=if(custom)base.labelUk else ukLabel,
                labelByLanguage=if(custom)base.labelByLanguage else base.labelByLanguage+mapOf("sl" to label,"uk" to ukLabel),
                speakTextSl=if(custom)base.speakTextSl else sl,speechText=if(custom)base.speechText else sl,
                speakTextUk=if(custom)base.speakTextUk else uk,
                speechTextByLanguage=if(custom)base.speechTextByLanguage else base.speechTextByLanguage+mapOf("sl" to sl,"uk" to uk),
                children=children,opensSubicons=children.isNotEmpty(),conceptId=base.conceptId?:id,
                isRootItem=base.isRootItem||id in roots,isHiddenUntilParent=if(id in roots)false else base.isHiddenUntilParent,
                actionType="speak",targetPageId="",questionSl=null,questionUk=null,questionByLanguage=emptyMap(),followUpQuestion=null,
                guidedQuestionsEnabled=false,addsToSentence=false,speaksImmediately=n.getString("speech_mode")!="navigate_only")
        }
        return items.values.toList()
    }
    fun ordered(c:Context,items:Collection<AacItem>):List<AacItem> {
        val ids=home(c);val byId=items.associateBy{it.id}
        val previous=AacHomeLayout.homeItems(items)
        return ids.mapNotNull{byId[it]}+(previous.drop(25)+previous.take(25)).filter{it.id !in ids}
    }
}
