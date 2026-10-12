package com.rehab2.aac
import com.rehab2.patient.*
import java.io.File
import org.json.JSONObject

fun main(args: Array<String>) {
    fun fail(f: () -> Unit) { check(runCatching(f).isFailure) }
    val base=File(args[0]).apply { mkdirs() }
    val profiles=PatientProfileCatalog(File(base,"patient_profile.json"))
    val female=profiles.active().copy(visualSex=PatientGender.FEMALE,grammaticalGender=PatientGender.FEMALE)
    profiles.save(female)
    val male=profiles.create("B").copy(visualSex=PatientGender.MALE,grammaticalGender=PatientGender.MALE)
    profiles.save(male)
    for(lang in listOf("sl","uk")) {
        val fp=AacVoiceResolver.resolve(AacSpeechRole.PATIENT,lang,female)
        val fg=AacVoiceResolver.resolve(AacSpeechRole.GUIDE,lang,female)
        val mp=AacVoiceResolver.resolve(AacSpeechRole.PATIENT,lang,male)
        val mg=AacVoiceResolver.resolve(AacSpeechRole.GUIDE,lang,male)
        check(fp.gender==AacVoiceGender.FEMALE && fg.gender==AacVoiceGender.MALE && fp.voiceId!=fg.voiceId)
        check(mp.gender==AacVoiceGender.MALE && mg.gender==AacVoiceGender.FEMALE && mp.voiceId!=mg.voiceId)
        check(AacVoiceResolver.resolve(AacSpeechRole.SYSTEM,lang,female)==fg)
    }
    println("A PASS female PATIENT; B PASS female GUIDE; C PASS male PATIENT; D PASS male GUIDE")
    val customized=female.copy(patientVoice="custom-p",guideVoice="custom-g",patientVoiceByLanguage=mapOf("uk" to "uk-p"),
        guideVoiceByLanguage=mapOf("sl" to "sl-g"),patientAndroidVoiceByLanguage=mapOf("sl" to "device-sl"))
    profiles.save(customized); profiles.select(customized.id)
    check(profiles.active()==customized)
    check(AacVoiceResolver.resolve(AacSpeechRole.PATIENT,"uk",profiles.active()).voiceId=="uk-p")
    check(AacVoiceResolver.resolve(AacSpeechRole.PATIENT,"sl",profiles.active()).voiceId=="custom-p")
    check(AacVoiceResolver.resolve(AacSpeechRole.GUIDE,"sl",profiles.active()).voiceId=="sl-g")
    val target=java.util.Locale.forLanguageTag("sl-SI")
    val local=AacVoiceCandidate("local-a",target,false,400)
    val second=local.copy(name="local-b",quality=300)
    val network=local.copy(name="network",network=true,quality=500)
    val same=local.copy(name="regional",locale=java.util.Locale.forLanguageTag("sl-AT"))
    val patient=AacVoiceResolver.resolve(AacSpeechRole.PATIENT,"sl",female)
    val guide=AacVoiceResolver.resolve(AacSpeechRole.GUIDE,"sl",female)
    fun candidates(v:AacResolvedVoice, voices:List<AacVoiceCandidate>)=AacVoiceResolver.resolveVoiceCandidates(v,target,voices)
    check(candidates(patient.copy(androidVoiceId="network"),listOf(local,network)).first()==network)
    println("T1 PASS requested voice first")
    check(candidates(patient.copy(androidVoiceId="missing"),listOf(same,network,second,local))==listOf(local,second,network,same))
    check(candidates(patient,listOf(second,local))==candidates(patient,listOf(local,second)))
    check(candidates(patient,listOf(local.copy(name="z"),local.copy(name="a"))).first().name=="a")
    println("T2 PASS exact locale; local, quality and name ranking; stable order")
    check(candidates(patient,listOf(same)).single()==same)
    println("T3 PASS same language")
    val attempts=mutableListOf<String>()
    val selected=AacVoiceResolver.selectVoice(listOf(local,second), { attempts.add(it.name); if(it==local) -1 else 0 }, { error("unexpected language fallback") }, { error("unexpected engine fallback") })
    check(selected.voiceName==second.name && attempts==listOf(local.name,second.name))
    println("T4 PASS setVoice ERROR then SUCCESS")
    check(AacVoiceResolver.selectVoice(listOf(local),{-1},{0},{error("unexpected")}).path==AacVoiceFallback.LANGUAGE_DEFAULT)
    println("T5 PASS language default")
    var restored=false
    check(AacVoiceResolver.selectVoice(listOf(local),{-1},{-2},{restored=true}).path==AacVoiceFallback.ENGINE_DEFAULT && restored)
    check(AacVoiceResolver.selectVoice(listOf(local),{error("engine exception")},{error("engine exception")},{error("engine exception")}).path==AacVoiceFallback.ENGINE_DEFAULT)
    println("T6 PASS engine default graceful; engine exceptions contained")
    check(candidates(patient,listOf(same))==candidates(guide,listOf(same)))
    println("T7 PASS one voice shared")
    check(candidates(patient,listOf(local,second)).first()!=candidates(guide,listOf(local,second)).first())
    check(candidates(patient.copy(oppositeAndroidVoiceId=local.name),listOf(local,second)).first()==second)
    check(candidates(guide.copy(oppositeAndroidVoiceId=second.name),listOf(local,second)).first()==local)
    check(candidates(AacVoiceResolver.resolve(AacSpeechRole.SYSTEM,"sl",female),listOf(local,second))==candidates(guide,listOf(local,second)))
    println("T8 PASS PATIENT/GUIDE contrast; SYSTEM uses GUIDE/default policy")
    profiles.save(female)
    val item=AacItem("tired","UTRUJENA SEM","system/tired.png",actionType="speak",targetPageId="",speakTextSl="Utrujena sem.")
    check(AacQuestionResolver.resolve(item,"sl",PatientGender.FEMALE)=="Ali si utrujena?")
    check(AacQuestionResolver.resolve(item,"sl",PatientGender.MALE)=="Ali si utrujen?")
    val authored=item.copy(questionSlFemale="F",questionSlMale="M",questionSlNeutral="N",questionSl="L",
        questionUkFemale="UF",questionUkMale="UM",questionUkNeutral="UN")
    check(AacQuestionResolver.resolve(authored,"sl",PatientGender.MALE)=="M")
    check(AacQuestionResolver.resolve(authored.copy(questionSlMale=null),"sl",PatientGender.MALE)=="N")
    check(AacQuestionResolver.resolve(authored.copy(questionSlMale=null,questionSlNeutral=null),"sl",PatientGender.MALE)=="L")
    check(AacQuestionResolver.resolve(authored,"uk",PatientGender.MALE)=="UM")
    check(AacQuestionResolver.guided("Kaj bi jedla?","sl",PatientGender.MALE)=="Kaj bi jedel?")
    val v2=AacV2JsonParser.parse(JSONObject("""{"version":2,"pageId":"test","nodes":[{"id":"tired","title":"Tired","questionSlFemale":"VF","questionSlMale":"VM","questionSlNeutral":"VN","questionUkFemale":"UF","children":[]}]}"""))
    val parsed=AacV2PageAdapter.toAacPage(v2).items.single()
    check(AacQuestionResolver.resolve(parsed,"sl",PatientGender.MALE)=="VM")
    check(AacQuestionResolver.resolve(parsed,"sl",PatientGender.NEUTRAL)=="VN")
    check(AacQuestionResolver.resolve(parsed,"uk",PatientGender.FEMALE)=="UF")
    val protectedItem=item.copy(locked=true,userEdited=true,imageOwner="THERAPIST",
        patientManualImages=mapOf(female.id to AacManualImage("patient/manual.png",IconSource.PATIENT)),
        questionSlFemale="authored",speechSlFemale="custom patient speech")
    val merged=MainMerge802.mergeMainAacFallbackItem(item,protectedItem,false)
    check(merged.locked && merged.userEdited && merged.imageOwner=="THERAPIST")
    check(merged.patientManualImages==protectedItem.patientManualImages && merged.questionSlFemale=="authored" && merged.speechSlFemale=="custom patient speech")
    println("E PASS female question; F PASS male question; SL/UK specific-neutral-legacy fallback")
    check(AacSpeechResolver.resolve(item,"sl",PatientGender.FEMALE)=="Utrujena sem.")
    check(AacSpeechResolver.resolve(item,"sl",PatientGender.MALE)=="Utrujen sem.")
    check(AacLabelResolver.resolve(item,"sl",PatientGender.MALE)=="UTRUJEN SEM")
    for(text in listOf("Boli me trebuh.","Potrebujem medicinsko sestro.","Prosim za vodo.","Moram v toaleto."))
        for(gender in PatientGender.values()) check(AacSpeechResolver.resolve(item.copy(speechSl=text),"sl",gender)==text)
    check(AacPatientGrammar.resolve("Rad bi vodo.","sl",PatientGender.FEMALE)=="Rada bi vodo.")
    check(AacPatientGrammar.resolve("Rada bi vodo.","sl",PatientGender.MALE)=="Rad bi vodo.")
    check(AacPatientGrammar.resolve("Rada bi vodo.","sl",PatientGender.NEUTRAL)=="Rada bi vodo.")
    check(item.iconSource==IconSource.SYSTEM && AacVoiceResolver.resolve(AacSpeechRole.PATIENT,"sl",female).gender==AacVoiceGender.FEMALE)
    println("G PASS neutral speech; H PASS SYSTEM image with PATIENT voice")
    fun inspect(f: File): PatientImageCheck = runCatching {
        val image=javax.imageio.ImageIO.read(f) ?: return@runCatching PatientImageCheck(false)
        var transparent=false; var nonempty=false
        for(y in 0 until image.height) for(x in 0 until image.width) {
            val alpha=image.getRGB(x,y) ushr 24
            if(alpha<255) transparent=true
            if(alpha>0) nonempty=true
        }
        PatientImageCheck(true,"PNG",image.width,image.height,image.colorModel.hasAlpha(),transparent,nonempty)
    }.getOrDefault(PatientImageCheck(false))
    fun png(name: String,color: Int,width: Int=512): File {
        val f=File(base,name)
        val image=java.awt.image.BufferedImage(width,512,java.awt.image.BufferedImage.TYPE_INT_ARGB)
        for(x in 20 until width-20) for(y in 20..490) image.setRGB(x,y,color)
        javax.imageio.ImageIO.write(image,"png",f);return f
    }
    val one=png("one.png",0xffff0000.toInt());val two=png("two.png",0xff0000ff.toInt());val three=png("three.png",0xff00ff00.toInt())
    val catalog=PatientIconCatalog(File(base,"icons/patient"),::inspect,{id->profiles.list().any {it.id==id}},{id->item.takeIf {id==it.id}})
    catalog.setReference(female.id,one);catalog.setReference(male.id,two)
    fun request(pid: String): PatientIconGenerationRequest = PatientIconGenerationRequest(java.util.UUID.randomUUID().toString(),pid,item.id,item.id,
        "Utrujena","Втомлена","Utrujena sem.","Я втомлена.",catalog.reference(pid)!!.name,PatientGender.FEMALE,"feeling",
        PatientIconGenerationTemplate.EMOTION,123456L,grammaticalGender=PatientGender.FEMALE)
    fun ready(pid: String)=request(pid).also {catalog.create(it)}
    val r=ready(female.id)
    check(PatientIconGenerationWorkflow(catalog).generate(r).status==GenerationBackendStatus.CONFIGURATION_REQUIRED)
    check(catalog.candidate(female.id,r.requestId)==null)
    fun transport(image: File,pid: String=female.id,itemId: String=item.id)=object:PatientIconGenerationTransport {
        override fun generate(request:PatientIconGenerationRequest,referenceImage:File)=PatientIconGenerationResult(
            GenerationBackendStatus.GENERATED,"fixture-generation",request.requestId,pid,itemId,image,mapOf("width" to "512"))
    }
    fail {PatientIconGenerationWorkflow(catalog,transport(one)).generate(r.copy(meaning="wrong"))}
    fail {PatientIconGenerationWorkflow(catalog,transport(one,male.id)).generate(r)}
    fail {PatientIconGenerationWorkflow(catalog,transport(one,itemId="water")).generate(r)}
    PatientIconGenerationWorkflow(catalog,transport(one)).generate(r)
    check(catalog.active(female.id,item.id)==null)
    println("K PASS candidate never auto-applies")
    catalog.reject(female.id,r.requestId)
    check(catalog.active(female.id,item.id)==null)
    fail {catalog.approve(female.id,r.requestId,true,PatientIconReplacement.REPLACE)}
    println("L PASS rejection leaves AAC unchanged")
    val approved=ready(female.id)
    PatientIconGenerationWorkflow(catalog,transport(two)).generate(approved)
    fail {catalog.approve(male.id,approved.requestId,true,PatientIconReplacement.REPLACE)}
    fail {catalog.approve(female.id,approved.requestId,false,PatientIconReplacement.REPLACE)}
    check(catalog.approve(female.id,approved.requestId,true,PatientIconReplacement.REPLACE))
    val active=catalog.active(female.id,item.id)!!
    check(catalog.active(male.id,item.id)==null)
    println("M PASS approval requires matching patient/item and review")
    val seen=mutableListOf<String>()
    fun image(profile:PatientProfile, tile:AacItem=item)=AacImageResolver.resolve(tile,profile.id,profile.visualSex,profiles.legacyOwner(),
        {one},{catalog.active(profile.id,tile.id)},{path->seen.add(path);one.takeIf {path=="system/tired.png"}},"system/tired.png",{inspect(it).valid})
    check(image(female)==active && image(male)==one && image(female)==active)
    check("system/state_tired_male.png" in seen)
    check(image(female,item.copy(imageOwner="THERAPIST"))==one)
    println("I PASS patient image wins; J PASS gender SYSTEM then neutral fallback; manual protection")
    val stale=ready(female.id);val fresh=ready(female.id)
    catalog.importCandidate(female.id,stale.requestId,three);catalog.importCandidate(female.id,fresh.requestId,one)
    catalog.approve(female.id,fresh.requestId,true,PatientIconReplacement.REPLACE)
    fail {catalog.approve(female.id,stale.requestId,true,PatientIconReplacement.REPLACE)}
    val changed=ready(female.id);catalog.importCandidate(female.id,changed.requestId,three)
    catalog.setReference(female.id,three)
    fail {catalog.approve(female.id,changed.requestId,true,PatientIconReplacement.REPLACE)}
    val invalid=ready(female.id)
    fail {catalog.importCandidate(female.id,invalid.requestId,png("bad-size.png",1,20))}
    fail {catalog.setReference("../escape",one)}
    check(catalog.candidate(male.id,approved.requestId)==null)
    check(catalog.active(male.id,item.id)==null)
    println("N PASS profile isolation, stale revision, reference replacement, invalid image and traversal rejected")
    for(n in 3..6) check(AacFixedTopRow.rowIds(n)==listOf("yes","dont_understand","no","thank_you","help","wc").take(n))
    println("Q PASS fixed first row 3x3 through 6x6")
}
