package com.rehab2.aac
import android.content.Context

/** Evidence-linked review proposals. No catalogue mutation and no claim of confirmed patient intent. */
object LiveSuggestions {
    data class Proposal(val text:String,val evidence:String,val existingPath:String)
    fun review(c:Context):List<Proposal> {
        val events=LiveTestSession.snapshot()
        val catalog=(AacStarterContentV1.items()+AacHomeLayout.additions()+AacEditorStorage.loadItems(c)).associateBy{it.id}
        val proposals=ArrayList<Proposal>()
        val profile=LivePatientNotes.load(c)
        val confirmedProfile=(0 until profile.length()).map{profile.getJSONObject(it)}.filter{!it.optString("source").startsWith("OPAŽANJE")}
            .joinToString("\n"){"${it.optString("source")}: ${it.optString("text")}"}
        val concepts=listOf(Triple(listOf("voda","vode","вод"),"water","thirsty"),Triple(listOf("kava","kavo","кав"),"coffee","thirsty"),Triple(listOf("toalet","stranišč","туалет"),"wc","wc"))
        for((words,id,root) in concepts){
            val speech=events.lastOrNull{it.kind=="ai_transcript" && words.any{w->it.detail.lowercase().contains(w)}}?:continue
            val related=events.filter{it.elapsedMs>=speech.elapsedMs-30000 && it.elapsedMs<=speech.elapsedMs+120000}
            val selection=related.lastOrNull{it.kind=="tap" && it.detail==root}
            val existing=catalog.values.filter{item->item.id==id || words.any{w->item.labelSl.lowercase().contains(w)||item.labelUk.orEmpty().lowercase().contains(w)}}
            fun path(target:String):String? {
                val queue=java.util.ArrayDeque<List<String>>();AacHomeLayout.ids.forEach{queue.add(listOf(it))};val seen=HashSet<String>()
                while(queue.isNotEmpty()) { val route=queue.removeFirst();val last=route.last();if(!seen.add(last))continue
                    if(last==target)return route.joinToString(" → "){catalog[it]?.labelSl?:it}
                    catalog[last]?.children.orEmpty().filter{catalog.containsKey(it)}.forEach{queue.add(route+it)}
                };return null
            }
            val routes=existing.mapNotNull{path(it.id)}
            val confirmation=events.lastOrNull{it.kind=="review_confirmed" && it.detail.contains("${speech.elapsedMs} ms")}
            val evidence="Seja ${LiveTestSession.sessionId}; AI ${speech.elapsedMs} ms: ${speech.detail}\nIskanje: ${related.count{it.kind=="page_shown"}} prikazov; izbira ${selection?.detail?:"še ni zabeležena"}.\nPotrditev: ${confirmation?.detail?:"MANJKA; preveri z DA/NE/NE VEM ali skrbnikom"}.\nPri prilagoditvi upoštevajte potrjeni profil:\n$confirmedProfile"
            proposals+=Proposal(if(routes.isNotEmpty())"Preverite razumljivost in dostop do obstoječe poti; nova ikona še ni potrebna." else if(existing.isNotEmpty())"Obstoječi pojem je najden, pot s prve strani ni potrjena. Preglejte povezavo za drugo stran ali podikono." else "Obstoječi pojem ni najden. Po potrditvi potrebe preglejte novo podikono; prva stran ostane enaka.",evidence,routes.joinToString("\n").ifBlank{"Ni potrjene poti: ${existing.joinToString{it.id}}"})
        }
        return proposals.take(3)
    }
}
