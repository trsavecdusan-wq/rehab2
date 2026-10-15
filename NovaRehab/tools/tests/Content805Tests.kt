package com.rehab2.aac
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

fun main(args: Array<String>) {
    val base=File(args[0]).apply { deleteRecursively(); mkdirs() }

    // A: atomic aac_items.json write with .bak / .corrupt, and .bak recovery on load
    val file=File(base,"aac_items.json")
    val bak=File(file.path+".bak"); val tmp=File(file.path+".tmp"); val corrupt=File(file.path+".corrupt")
    fun doc(id:String)="""{"items":[{"id":"$id"}]}"""
    AacContentBootstrap.writeItemsFileAtomically(file,doc("v1"))
    check(file.readText()==doc("v1") && !bak.exists() && !tmp.exists())
    AacContentBootstrap.writeItemsFileAtomically(file,doc("v2"))
    check(file.readText()==doc("v2") && bak.readText()==doc("v1") && !tmp.exists())
    println("A1 PASS first write; second write keeps previous file in .bak")
    file.writeText("{broken")
    AacContentBootstrap.writeItemsFileAtomically(file,doc("v3"))
    check(file.readText()==doc("v3") && bak.readText()==doc("v1") && corrupt.readText()=="{broken")
    file.writeText("")
    AacContentBootstrap.writeItemsFileAtomically(file,doc("v4"))
    check(file.readText()==doc("v4") && bak.readText()==doc("v1") && corrupt.readText()=="")
    println("A2 PASS corrupt/empty file goes to .corrupt, good .bak is not overwritten")
    tmp.writeText("stale")
    AacContentBootstrap.writeItemsFileAtomically(file,doc("v5"))
    check(file.readText()==doc("v5") && bak.readText()==doc("v4") && !tmp.exists())
    println("A3 PASS stale .tmp from an interrupted write is replaced")
    val fallback=listOf(AacItem("fallback_only","PRIVZETO","",actionType="speak",targetPageId=""))
    fun ids(raw:AacContentBootstrap.RawItemsJson)=(0 until raw.itemsArray.length()).map { raw.itemsArray.getJSONObject(it).getString("id") }
    for(damaged in listOf("{broken","","[]","""{"items":[]}""")) {
        file.writeText(damaged); bak.writeText(doc("good")); corrupt.delete()
        val loaded=AacContentBootstrap.loadItemsJson(file,fallback)
        check(ids(loaded)==listOf("good") && !loaded.createdFromFallback) { "damaged=$damaged ids=${ids(loaded)}" }
        check(file.readText()==doc("good") && corrupt.readText()==damaged && bak.readText()==doc("good"))
        AacContentBootstrap.writeItemsFileAtomically(file,doc("after_save"))
        check(file.readText()==doc("after_save") && bak.readText()==doc("good"))
    }
    file.writeText("{broken"); bak.writeText("also broken")
    val noBackup=AacContentBootstrap.loadItemsJson(file,fallback)
    check(ids(noBackup)==listOf("fallback_only") && noBackup.createdFromFallback && file.readText()=="{broken")
    file.writeText(doc("main")); bak.writeText(doc("older"))
    check(ids(AacContentBootstrap.loadItemsJson(file,fallback))==listOf("main"))
    println("A4 PASS damaged main + good .bak loads .bak (not fallback), damaged file kept in .corrupt, .bak still good after save; no valid .bak keeps fallback")

    // B: detach hot_feeling / cold_feeling from POČUTJE and TEŽAVA on an existing device
    val fixture=JSONArray("""[
      {"id":"yes","labelSl":"DA","fixedTopRowPosition":0},
      {"id":"feeling","labelSl":"MOJE POČUTJE","userEdited":true,"locked":true,"opensSubicons":true,"isRootItem":true,
       "children":["good","tired","cold_feeling","hot_feeling","cold","hot"],"placements":[{"pageId":"page_1","position5x5":14}]},
      {"id":"problem","isRootItem":true,"children":["pain","cold","hot","hot_feeling"],"placements":[{"pageId":"page_1","position5x5":6}]},
      {"id":"tired","labelSl":"UTRUJENA SEM","isRootItem":true,"visibleUnderIds":["feeling"],"placements":[{"pageId":"page_1","position5x5":10}]},
      {"id":"hot","labelSl":"VROČE MI JE","speakTextSl":"Vroče mi je.","speechText":"Vroče mi je.","isRootItem":false,"visibleUnderIds":["feeling","problem"]},
      {"id":"cold","labelSl":"MRAZ MI JE","speakTextSl":"Mraz mi je.","speechText":"Mraz mi je.","isRootItem":false,"visibleUnderIds":["feeling","problem"]},
      {"id":"hot_feeling","labelSl":"ROČNO","speakTextSl":"Ročni govor.","userEdited":true,"imagePath":"custom/mine.png","isRootItem":false,"visibleUnderIds":["feeling","problem"]},
      {"id":"cold_feeling","labelSl":"MRZLO MI JE","speakTextSl":"Mrzlo mi je.","visibleUnderIds":["feeling","problem"]}
    ]""")
    fun byId(a:JSONArray,id:String)=(0 until a.length()).map { a.getJSONObject(it) }.single { it.getString("id")==id }
    val expected=JSONArray(fixture.toString())
    byId(expected,"feeling").put("children",JSONArray(listOf("good","tired","cold","hot")))
    byId(expected,"problem").put("children",JSONArray(listOf("pain","cold","hot")))
    byId(expected,"hot_feeling").remove("visibleUnderIds")
    byId(expected,"cold_feeling").apply { remove("visibleUnderIds"); put("isRootItem",false) }
    // Android org.json has no JSONObject.similar(): compare key sets and values key by key.
    fun same(a:JSONObject,b:JSONObject):Boolean {
        val keys=a.keys().asSequence().toSet()
        return keys==b.keys().asSequence().toSet() && keys.all { k -> a.get(k).toString()==b.get(k).toString() }
    }
    val items=JSONArray(fixture.toString())
    check(AacContentBootstrap.repairFeelingTemperatureDuplicates(items)==7)
    check(items.length()==expected.length())
    for(i in 0 until expected.length()) {
        val want=expected.getJSONObject(i); val got=byId(items,want.getString("id"))
        check(same(got,want)) { "${want.getString("id")}: $got" }
    }
    // Read the repaired file with AacLocalJsonLoader's own (private) item parser.
    val parseItem=AacLocalJsonLoader::class.java.getDeclaredMethod("parseItem",JSONObject::class.java,Int::class.javaPrimitiveType).apply { isAccessible=true }
    val repairedFile=File(base,"repaired_aac_items.json")
    AacContentBootstrap.writeItemsFileAtomically(repairedFile,JSONObject().put("items",items).toString(2))
    val reread=JSONObject(repairedFile.readText()).getJSONArray("items")
    val loadedItems=(0 until reread.length()).mapNotNull { parseItem.invoke(AacLocalJsonLoader,reread.getJSONObject(it),it) as AacItem? }
    val loadedDup=loadedItems.filter { it.id in setOf("hot_feeling","cold_feeling") }
    check(loadedDup.size==2 && loadedDup.all { !it.isRootItem && it.visibleUnderIds.isEmpty() })
    check(setOf("hot_feeling","cold_feeling").all { byId(reread,it).has("isRootItem") })
    // MainActivity root rule (lines 1047, 1646, 3036) and page_1 placements.
    val rootShown=loadedItems.filter { it.isRootItem && !it.isHiddenUntilParent }.map { it.id }
    val onPageOne=loadedItems.filter { item -> item.placements.any { it.pageId=="page_1" } }.map { it.id }
    check(loadedDup.none { it.id in rootShown || it.id in onPageOne }) { "root=$rootShown page_1=$onPageOne" }
    check(rootShown.containsAll(listOf("feeling","problem","tired")) && onPageOne.toSet()==setOf("feeling","problem","tired"))
    // Same rule as MainActivity.mainAacChildrenFor: explicit children + items listing the parent in visibleUnderIds.
    fun shownUnder(a:JSONArray,parentId:String):Set<String> {
        val all=(0 until a.length()).map { a.getJSONObject(it) }
        val explicit=byId(a,parentId).optJSONArray("children")?.let { c -> (0 until c.length()).map { c.getString(it) } }.orEmpty()
        val byVisible=all.filter { o -> o.optJSONArray("visibleUnderIds")?.let { v -> (0 until v.length()).any { v.getString(it)==parentId } }==true }.map { it.getString("id") }
        return (explicit+byVisible).toSet()
    }
    val dup=setOf("hot_feeling","cold_feeling")
    for(i in 0 until items.length()) check(shownUnder(items,items.getJSONObject(i).getString("id")).none { it in dup })
    check(dup.all { !byId(items,it).optBoolean("isRootItem",true) })
    check(shownUnder(items,"feeling").containsAll(listOf("hot","cold")) && shownUnder(items,"problem").containsAll(listOf("hot","cold")))
    println("B1 PASS only parents' children, duplicates' visibleUnderIds and isRootItem changed; duplicates shown under no parent and not root; edited fields, first row and page_1 intact")
    check(AacContentBootstrap.repairFeelingTemperatureDuplicates(items)==0)
    println("B2 PASS second run makes 0 changes")
    val starterList=AacStarterContentV1.items()
    val starter=starterList.associateBy { it.id }
    for(parent in starterList) {
        val shown=parent.children+starterList.filter { parent.id in it.visibleUnderIds }.map { it.id }
        check(shown.none { it in dup }) { "starter ${parent.id} shows a duplicate" }
    }
    for(id in dup) check(starter.getValue(id).visibleUnderIds.isEmpty() && !starter.getValue(id).isRootItem && starter.getValue(id).placements.isEmpty())
    for(parent in listOf("feeling","problem")) {
        check("hot" in starter.getValue(parent).children && "cold" in starter.getValue(parent).children)
        check(parent in starter.getValue("hot").visibleUnderIds && parent in starter.getValue("cold").visibleUnderIds)
    }
    check(starter.getValue("hot").speakTextSl=="Vroče mi je." && starter.getValue("cold").speakTextSl=="Mraz mi je.")
    println("B3 PASS starter: duplicates kept, under no parent and not root; hot/cold stay under feeling and problem with unchanged speech")
    println("Content805Tests OK")
}
