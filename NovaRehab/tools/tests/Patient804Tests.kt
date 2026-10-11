package com.rehab2.aac

import com.rehab2.patient.*
import java.io.File
import java.io.IOException
import java.util.UUID
import org.json.JSONObject

fun main(args:Array<String>) {
    val root=File(args[0]).apply { mkdirs() }
    val profile=PatientProfile(id="test-a",grammaticalGender=PatientGender.FEMALE,visualSex=PatientGender.FEMALE)
    val item=AacItem("tired","Utrujena sem.","system/tired.png",actionType="speak",targetPageId="",speakTextSl="Utrujena sem.")
    fun png(name:String,color:Int):File {
        val image=java.awt.image.BufferedImage(512,512,java.awt.image.BufferedImage.TYPE_INT_ARGB)
        for(x in 20..490) for(y in 20..490) image.setRGB(x,y,color)
        return File(root,name).also { javax.imageio.ImageIO.write(image,"png",it) }
    }
    val reference=png("synthetic-reference.png",0xffff0000.toInt())
    val candidate=png("synthetic-candidate.png",0xff00ff00.toInt())
    fun inspect(file:File):PatientImageCheck=runCatching {
        val image=javax.imageio.ImageIO.read(file) ?: return@runCatching PatientImageCheck(false)
        var transparent=false;var nonempty=false
        for(x in 0 until image.width) for(y in 0 until image.height) {
            val alpha=image.getRGB(x,y) ushr 24; if(alpha<255) transparent=true; if(alpha>0) nonempty=true
        }
        PatientImageCheck(true,"PNG",image.width,image.height,image.colorModel.hasAlpha(),transparent,nonempty)
    }.getOrDefault(PatientImageCheck(false))
    val catalog=PatientIconCatalog(File(root,"icons"),::inspect,{it in setOf("test-a","test-b")},{item.takeIf { tile -> tile.id==it }})
    fun fail(action:()->Unit) { check(runCatching(action).isFailure) }
    fun request(pid:String="test-a")=PatientIconRequestBuilder.create(profile.copy(id=pid),item,catalog.reference(pid)?.name.orEmpty(),PatientIconGenerationTemplate.EMOTION)
    fail { catalog.create(request()) }
    check(catalog.active("test-a",item.id)==null)
    println("B PASS missing reference blocked; no live change")
    catalog.setReference("test-a",reference);catalog.setReference("test-b",reference)
    fun ready(pid:String="test-a")=request(pid).also { catalog.create(it) }
    val config=PatientGenerationBackendConfig("https://backend.invalid/generate",false,"synthetic-installation")
    var calls=0;var lastMetadata=JSONObject()
    var mode="valid"
    val fake=PatientGenerationHttpClient { _,_,metadata,_,cancel ->
        calls++;lastMetadata=metadata
        if(mode=="offline") throw IOException("fixture")
        if(mode=="cancel") cancel.cancel()
        val bytes=when(mode) { "invalid-image" -> ByteArray(40); "decode" -> candidate.readBytes().take(26).toByteArray(); else -> candidate.readBytes() }
        val headers=mapOf("Content-Type" to (if(mode=="mime") "text/html" else "image/png"),
            "X-Generation-Id" to UUID.randomUUID().toString(),"X-Request-Id" to (if(mode=="request") "wrong" else metadata.getString("requestId")),
            "X-Patient-Id" to (if(mode=="patient") "wrong" else metadata.getString("patientId")),
            "X-Item-Id" to (if(mode=="item") "wrong" else metadata.getString("itemId")),
            "X-Content-SHA256" to (if(mode=="hash") "wrong" else java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }),"X-Created-At" to "123456")
        PatientGenerationHttpResponse(when(mode) { "auth" -> 401; "server" -> 500; "redirect" -> 302; else -> 200 },headers,bytes)
    }
    val temporary=File(root,"temporary")
    fun transport(c:PatientGenerationCancellation,conf:PatientGenerationBackendConfig=config)=HttpPatientIconGenerationTransport(conf,PatientGenerationSession { null },temporary,c,fake)
    fun generate(r:PatientIconGenerationRequest,c:PatientGenerationCancellation=PatientGenerationCancellation(),conf:PatientGenerationBackendConfig=config)=PatientIconGenerationWorkflow(catalog,transport(c,conf)).generate(r,c)
    check(generate(ready(),conf=config.copy(endpoint="")).status==GenerationBackendStatus.CONFIGURATION_REQUIRED && calls==0)
    check(generate(ready(),conf=config.copy(requireAuth=true)).status==GenerationBackendStatus.CONFIGURATION_REQUIRED && calls==0)
    check(generate(ready(),conf=config.copy(endpoint="http://backend.invalid")).status==GenerationBackendStatus.CONFIGURATION_REQUIRED && calls==0)
    check(!config.copy(endpoint="https://user:secret@backend.invalid").validEndpoint())
    check(!config.copy(endpoint="https://backend.invalid/?token=x").validEndpoint())
    println("A PASS endpoint/auth not configured; no network; HTTPS required")
    val first=ready();val response=generate(first)
    check(response.status==GenerationBackendStatus.SUCCESS)
    check(catalog.requests("test-a").single { it.getString("requestId")==first.requestId }.getString("status")=="REVIEW_REQUIRED")
    check(catalog.candidate("test-a",first.requestId)!=null && catalog.active("test-a",item.id)==null)
    check(temporary.walkTopDown().none { it.isFile })
    check(lastMetadata.getString("patientId")!="test-a" && lastMetadata.getString("patientId").length==64)
    for(key in listOf("referenceImagePath","grammaticalGender","profile","history","gps","contacts","audio","video")) check(!lastMetadata.has(key))
    println("C PASS validated response -> REVIEW_REQUIRED; D PASS no auto-apply; minimal opaque request; temp cleanup")
    fail { catalog.approve("test-b",first.requestId,true,PatientIconReplacement.REPLACE) }
    fail { catalog.approve("test-a",first.requestId,false,PatientIconReplacement.REPLACE) }
    check(catalog.approve("test-a",first.requestId,true,PatientIconReplacement.REPLACE))
    val active=catalog.active("test-a",item.id)
    println("E PASS explicit same-patient/item approval; O PASS cross-profile rejected")
    val rejected=ready();generate(rejected);catalog.reject("test-a",rejected.requestId)
    check(catalog.active("test-a",item.id)==active)
    println("F PASS rejection leaves live AAC unchanged")
    val again=ready();val againResult=generate(again)
    check(again.requestId!=first.requestId && againResult.generationId!=response.generationId)
    check(catalog.candidate("test-a",again.requestId)!=catalog.candidate("test-a",first.requestId))
    catalog.approve("test-a",again.requestId,true,PatientIconReplacement.REPLACE,true)
    check(catalog.revisions("test-a",item.id).size==2 && catalog.revisions("test-a",item.id).count { it.getBoolean("active") }==1)
    check(catalog.candidate("test-a",first.requestId)!!.exists())
    println("G PASS regenerate has distinct IDs/files; revision retains old image")
    for((m,label) in listOf("patient" to "H","item" to "I","invalid-image" to "J","decode" to "decoded image validation","request" to "request mismatch","hash" to "hash mismatch","mime" to "MIME mismatch")) {
        mode=m;val r=ready();check(generate(r).status==GenerationBackendStatus.INVALID_RESPONSE)
        check(catalog.requests("test-a").single { it.getString("requestId")==r.requestId }.getString("status")=="REJECTED_INVALID")
        check(catalog.candidate("test-a",r.requestId)==null)
        println("$label PASS invalid candidate rejected")
    }
    for((m,status) in listOf("offline" to GenerationBackendStatus.NETWORK_ERROR,"auth" to GenerationBackendStatus.AUTH_ERROR,
        "server" to GenerationBackendStatus.SERVER_ERROR,"redirect" to GenerationBackendStatus.SERVER_ERROR,"cancel" to GenerationBackendStatus.CANCELLED)) {
        val previous=catalog.active("test-a",item.id);mode=m;check(generate(ready()).status==status)
        check(catalog.active("test-a",item.id)==previous)
    }
    val cancelled=PatientGenerationCancellation().apply { cancel() };val before=calls
    val cancelledRequest=ready()
    check(generate(cancelledRequest,cancelled).status==GenerationBackendStatus.CANCELLED && calls==before)
    check(catalog.requests("test-a").single { it.getString("requestId")==cancelledRequest.requestId }.getString("status")=="CANCELLED")
    println("K PASS offline/HTTP/auth/redirect/cancel graceful; live unchanged")
    mode="valid"
    // The actual catalog independently decodes every image before accepting it (covered above and by 802 tests).
    val malformed=File(root,"bad-image.png").apply { writeBytes(candidate.readBytes().take(26).toByteArray()) }
    val invalidRequest=ready();fail { catalog.importCandidate("test-a",invalidRequest.requestId,malformed) }
    for((source,label) in listOf(IconSource.PATIENT to "L",IconSource.CUSTOM to "M",IconSource.CUSTOM to "N")) {
        val tile=item.copy(iconSource=source,imageOwner=if(label=="N") "THERAPIST" else source.name,imagePath="manual.png")
        val resolved=AacImageResolver.resolve(tile,"test-a",PatientGender.FEMALE,"test-a",{reference},{candidate},{candidate},"system/tired.png",{true})
        check(resolved==reference);println("$label PASS manual ${tile.imageOwner} priority")
    }
    val patient=AacVoiceResolver.resolve(AacSpeechRole.PATIENT,"sl",profile)
    val guide=AacVoiceResolver.resolve(AacSpeechRole.GUIDE,"sl",profile)
    check(patient.gender==AacVoiceGender.FEMALE && guide.gender==AacVoiceGender.MALE)
    check(AacSpeechResolver.resolve(item,"sl",PatientGender.MALE)=="Utrujen sem.")
    println("R PASS voice roles and grammar unchanged; full T1-T8 run in Patient802Tests")
    for(n in 3..6) check(AacFixedTopRow.rowIds(n)==listOf("yes","dont_understand","no","thank_you","help","wc").take(n))
    println("S PASS fixed first row")
    val body=first.copy(generationTemplate=PatientIconGenerationTemplate.BODY_PAIN,bodyRegion="arm",bodySide=PatientBodySide.LEFT,view=PatientBodyView.FRONT)
    val bodyWire=PatientIconRequestBuilder.wire(body,"opaque");check(bodyWire.getString("bodySide")=="LEFT" && bodyWire.getString("constraints").contains("patient LEFT is viewer RIGHT"))
    fail { PatientIconRequestBuilder.wire(body.copy(bodySide=null),"opaque") }
    for(template in PatientIconGenerationTemplate.values()) {
        val prompt=PatientIconRequestBuilder.wire(body.copy(generationTemplate=template),"opaque").getString("constraints")
        for(term in listOf("adult","identity","watermark","cartoon","irrelevant objects","crop artefacts")) check(prompt.contains(term))
    }
    check(PatientIconRequestBuilder.wire(first.copy(itemId="cannot_breathe"),"opaque").getString("constraints").contains("NOT choking"))
    check(PatientIconRequestBuilder.wire(first.copy(itemId="chest_pain"),"opaque").getString("constraints").contains("NOT abdominal pain"))
    check(PatientIconRequestBuilder.wire(first.copy(generationTemplate=PatientIconGenerationTemplate.GENERAL_PAIN),"opaque").getString("constraints").contains("not only headache"))
    println("TEMPLATES PASS body side, general pain, breathing distress and chest pain")
    println("804 A-S core PASS; P/Q source/APK checks run by validator")
}
