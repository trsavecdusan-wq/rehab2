package com.rehab2.aac

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.*
import android.util.Base64
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** No foreground service: captures exist only on visible communicator/test screens. Raw retention is opt-in. */
object LiveCapture : Application.ActivityLifecycleCallbacks {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private var registered = false
    private var activityCount=0
    private val policy=CaptureLaunchPolicy()
    private val audioWorker=Executors.newSingleThreadExecutor()
    private var sessionPatient=""
    @Volatile private var sessionReady = false
    @Volatile private var sendAi = false
    @Volatile private var keepRaw = false
    private var app: Context? = null
    private var visible = WeakReference<Activity>(null)
    @Volatile private var generation = 0
    private val resources=CaptureResourceGate()
    private val captureGeneration get()=resources.generation
    @Volatile private var remoteId = ""
    @Volatile private var remoteToken = ""
    @Volatile private var mic = false
    @Volatile private var camera = false
    @Volatile private var recording: AudioRecord? = null
    private var cameraDevice: CameraDevice? = null
    private var cameraSession: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var cameraThread: HandlerThread? = null
    @Volatile var status = "Backend in zajem še nista preverjena."
        private set
    private val audioGate=CaptureAudioGate { SystemClock.elapsedRealtime() }
    @Volatile var audioVerified = false
        private set
    @Volatile var cameraVerified = false
        private set
    var retentionDays = 0
    fun initialize(application:Application) {
        app=application
        io.execute { LivePatientNotes.purgeExpired(application) }
        if(!registered){application.registerActivityLifecycleCallbacks(this);registered=true}
        main.post(object:Runnable {override fun run(){
            visible.get()?.let { if(allowed(it)) { LiveTestSession.validate(it); LiveTestSession.attach(it) } }
            main.postDelayed(this,1000)
        }})
    }
    fun manualStop(){policy.stop();stop()}
    fun settingsChanged(c:Context) {
        val s=LiveCaptureSettings.load(c)
        if(LiveTestSession.active){
            // Revocation is immediate, including the in-flight request. Never auto-start a disabled modality.
            if(!s.microphone)mic=false
            if(!s.camera)camera=false
            if(!s.sendAi){
                sendAi=false
                val id=remoteId;val token=remoteToken;remoteId="";remoteToken=""
                if(id.isNotEmpty())deleteRemote(c,id,token)
            }
            keepRaw=s.keepRaw
            stopCapture();LiveBackend.cancel()
            if(!mic && !camera){LiveTestSession.stop()}else resumeCapture()
        }
    }
    fun speechEvent(kind: String) { audioGate.event(kind) }
    fun check(c: Context, done: (String) -> Unit) {
        io.execute {
            val result = runCatching { val r=LiveBackend.request(c,"/v1/health"); check(r.optBoolean("ready") && r.optInt("protocol")==1); "Backend povezan; mikrofon in kamera še nista preverjena z zajemom." }
                .getOrElse { it.message ?: "Povezava ni uspela." }
            main.post { status=result; done(result) }
        }
    }
    fun start(activity: Activity, microphone: Boolean, useCamera: Boolean, done: (Boolean) -> Unit) {
        stop(); policy.manualStart()
        app = activity.applicationContext; visible = WeakReference(activity)
        if (!registered) { activity.application.registerActivityLifecycleCallbacks(this); registered = true }
        LiveTestSession.start(activity)
        sessionPatient=AacPatientImages.store(activity).currentPatientId()
        mic = microphone; camera = useCamera; audioVerified = false; cameraVerified = false
        val settings=LiveCaptureSettings.load(activity);sendAi=settings.sendAi;keepRaw=settings.keepRaw
        val epoch = ++generation
        if ((!mic && !camera) || !sendAi) { sessionReady=true;status = "Lokalna seja; brez pošiljanja AI. Čakam zajem.";resumeCapture();done(true);return }
        status = "Preverjam povezavo; zajem še ni vključen."
        io.execute {
            var createdId = ""; var createdToken = ""
            val result = runCatching {
                val health=LiveBackend.request(activity,"/v1/health")
                check(health.optBoolean("ready") && health.optInt("protocol")==1)
                check(epoch == generation)
                val response=LiveBackend.request(activity,"/v1/sessions",JSONObject().put("consent",true).put("replace",true)
                    .put("microphone",microphone).put("camera",useCamera).put("language",AacLanguageResolver.readSelectedLanguageCode(activity)))
                createdId=response.getString("id");createdToken=response.getString("token")
            }
            main.post {
                if (epoch != generation || !LiveTestSession.active) {
                    if(createdId.isNotEmpty()) deleteRemote(activity,createdId,createdToken)
                } else if (result.isSuccess) {
                    remoteId=createdId;remoteToken=createdToken
                    sessionReady=true
                    LiveTestSession.record("external_consent", "OpenAI prek nastavljenega backenda; mikrofon=$mic, kamera=$camera; brez trajne hrambe posnetkov")
                    status="Povezava preverjena; čakam prvi zajem."; resumeCapture(); done(true)
                } else { status="Povezava ni uspela. Zajem je izključen; lokalni dogodki ostanejo na voljo."; done(false) }
            }
        }
    }
    fun stop() {
        generation++; stopCapture(); LiveBackend.cancel()
        sessionReady=false
        val id=remoteId;val token=remoteToken;remoteId="";remoteToken=""
        app?.let { if(id.isNotEmpty())deleteRemote(it,id,token) }
        mic=false;camera=false;status="Zajem ustavljen."
    }
    private fun deleteRemote(c: Context,id: String,token: String) {
        // Separate worker allows deletion to abort an in-flight provider request immediately.
        Thread { runCatching { LiveBackend.request(c,"/v1/sessions/$id",null,token,false) }.onFailure {
            main.post { if(remoteId.isEmpty() && !sessionReady)status="Zajem ustavljen. Strežnik ni dosegljiv za izbris; seja samodejno poteče v največ 30 minutah. Surovih posnetkov ne hrani." }
        } }.start()
    }
    private fun allowed(a: Activity) = a.javaClass.simpleName in setOf("MainActivity","AacCommunicatorActivity","LiveCaptureActivity","LiveTestActivity")
    private fun valid(epoch: Int) = epoch==captureGeneration && sessionReady && LiveTestSession.active
    private fun resumeCapture() {
        val a=visible.get() ?: return
        LiveTestSession.validate(a)
        if (!policy.mayCapture(allowed(a),LiveTestSession.active,sessionReady)) return
        if(LiveTestSession.elapsedMs()>=1800000){LiveTestSession.stop();return}
        stopCapture(); val epoch=captureGeneration
        if(mic) {
            if(ContextCompat.checkSelfPermission(a,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) startAudio(epoch)
            else { mic=false; status="Mikrofon nima dovoljenja; kamera in komunikator ostaneta na voljo." }
        }
        if(camera) {
            if(ContextCompat.checkSelfPermission(a,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) startCamera(a,epoch)
            else { camera=false; status="Kamera nima dovoljenja; mikrofon in komunikator ostaneta na voljo." }
        }
        main.post(object:Runnable{override fun run(){
            if(!valid(epoch))return
            app?.let { LiveTestSession.validate(it) }
            if(LiveTestSession.elapsedMs()>=1800000) { LiveTestSession.stop(); return }
            main.postDelayed(this,1000)
        }})
    }
    private fun stopCapture() = resources.stop {
        val rec=recording;recording=null;runCatching { rec?.stop() }
        runCatching { cameraSession?.close() };cameraSession=null
        runCatching { cameraDevice?.close() };cameraDevice=null
        runCatching { reader?.close() };reader=null
        cameraThread?.quitSafely();cameraThread=null
    }
    private fun startAudio(epoch:Int) {
        audioWorker.execute {
            var rec: AudioRecord? = null
            try {
                val min=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
                check(min>0)
                resources.open(epoch) {
                    if(valid(epoch)) {
                        val created=AudioRecord(MediaRecorder.AudioSource.MIC,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(min,8192))
                        rec=created;check(created.state==AudioRecord.STATE_INITIALIZED)
                        recording=created;created.startRecording();check(created.recordingState==AudioRecord.RECORDSTATE_RECORDING)
                    }
                }
                val recorder=rec ?: return@execute
                val buffer=ByteArray(4096)
                while(valid(epoch)) {
                    val start=LiveTestSession.elapsedMs();val speech=audioGate.begin()
                    val data=ByteArrayOutputStream()
                    while(valid(epoch) && data.size()<384000) {
                        val n=recorder.read(buffer,0,minOf(buffer.size,384000-data.size()));check(n>0)
                        data.write(buffer,0,n)
                    }
                    if(!valid(epoch))break
                    if(!audioGate.accepts(speech)) { LiveTestSession.record("audio_excluded","Govor aplikacije ali varnostni premor; segment zavržen."); continue }
                    val pcm=data.toByteArray()
                    val wav=ByteBuffer.allocate(44+pcm.size).order(ByteOrder.LITTLE_ENDIAN)
                        .put("RIFF".toByteArray()).putInt(36+pcm.size).put("WAVEfmt ".toByteArray()).putInt(16)
                        .putShort(1).putShort(1).putInt(16000).putInt(32000).putShort(2).putShort(16)
                        .put("data".toByteArray()).putInt(pcm.size).put(pcm).array()
                    submit(epoch,"audio",JSONObject().put("wav",Base64.encodeToString(wav,Base64.NO_WRAP))
                        .put("appSpeechOverlap",false).put("startMs",start).put("endMs",LiveTestSession.elapsedMs()))
                }
            } catch(_:Exception) { if(valid(epoch)) { audioVerified=false;status="Napaka mikrofona; komunikator ostaja uporaben."; LiveTestSession.record("capture_error","microphone") } }
            finally { resources.release { runCatching { rec?.stop() };runCatching { rec?.release() };if(recording===rec)recording=null } }
        }
    }
    @Suppress("DEPRECATION", "MissingPermission")
    private fun startCamera(c:Context,epoch:Int) {
        try {
            resources.open(epoch) {
            val manager=c.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id=manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_FRONT }
                ?: throw IllegalStateException("Sprednja kamera ni na voljo")
            val chars=manager.getCameraCharacteristics(id)
            val size=chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!.getOutputSizes(ImageFormat.JPEG)
                .filter { it.width<=1280 && it.height<=1280 }.minByOrNull { kotlin.math.abs(it.width*it.height-640*480) }
                ?: throw IllegalStateException("Ni podprte majhne slike")
            val thread=HandlerThread("live-camera").apply { start() };cameraThread=thread;val handler=Handler(thread.looper)
            val images=ImageReader.newInstance(size.width,size.height,ImageFormat.JPEG,2);reader=images
            val frames=ArrayList<String>();var start=0L
            images.setOnImageAvailableListener({ source ->
                runCatching { source.acquireLatestImage()?.use { image ->
                    if(!valid(epoch))return@use
                    if(frames.isEmpty())start=LiveTestSession.elapsedMs()
                    val bytes=ByteArray(image.planes[0].buffer.remaining());image.planes[0].buffer.get(bytes)
                    if(bytes.size<=400000)frames.add(Base64.encodeToString(bytes,Base64.NO_WRAP))
                    if(frames.size==3) {
                        submit(epoch,"camera",JSONObject().put("frames",JSONArray(frames)).put("startMs",start).put("endMs",LiveTestSession.elapsedMs()))
                        frames.clear()
                    }
                } }
            },handler)
            manager.openCamera(id,object:CameraDevice.StateCallback(){
                override fun onOpened(device:CameraDevice){
                    if(!resources.open(epoch) { runCatching {
                    cameraDevice=device
                    device.createCaptureSession(listOf(images.surface),object:CameraCaptureSession.StateCallback(){
                        override fun onConfigured(session:CameraCaptureSession){
                            if(!resources.open(epoch) { runCatching {
                            cameraSession=session
                            val request=device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                addTarget(images.surface);set(CaptureRequest.JPEG_QUALITY,60.toByte())
                                set(CaptureRequest.JPEG_ORIENTATION,chars.get(CameraCharacteristics.SENSOR_ORIENTATION)?:0)
                            }.build()
                            handler.post(object:Runnable{override fun run(){
                                if(!valid(epoch))return
                                frames.clear()
                                for(i in 0..2)handler.postDelayed({resources.open(epoch){runCatching { session.capture(request,null,handler) }}},i*500L)
                                handler.postDelayed(this,20000)
                            }})
                            }.onFailure{session.close();device.close();cameraVerified=false;status="Napaka priprave kamere."} })session.close()
                        }
                        override fun onConfigureFailed(session:CameraCaptureSession){session.close();device.close();if(valid(epoch)){cameraVerified=false;status="Napaka: kamera ni pripravljena."}}
                    },main)
                    }.onFailure{device.close();cameraVerified=false;status="Napaka odpiranja kamere."} })device.close()
                }
                override fun onDisconnected(device:CameraDevice){device.close();if(valid(epoch)){cameraVerified=false;status="Napaka: kamera odklopljena."}}
                override fun onError(device:CameraDevice,error:Int){device.close();if(valid(epoch)){cameraVerified=false;status="Napaka kamere; komunikator ostaja uporaben."}}
            },main)
            }
        }catch(_:Exception){cameraVerified=false;status="Napaka: kamera ni dosegljiva; komunikator ostaja uporaben.";LiveTestSession.record("capture_error","camera")}
    }
    private fun submit(epoch:Int,kind:String,body:JSONObject) {
        if(!valid(epoch))return
        if(!sendAi){if(kind=="audio")audioVerified=true else cameraVerified=true}
        LiveTestSession.record("capture_${kind}","${body.optLong("startMs")}–${body.optLong("endMs")} ms; dejanski zajem; brez razlage pomena")
        if(keepRaw) app?.let { c -> runCatching {
            if(kind=="audio")LivePatientNotes.saveRaw(c,sessionPatient,"wav",Base64.decode(body.getString("wav"),Base64.NO_WRAP))
            else for(i in 0 until body.getJSONArray("frames").length()) LivePatientNotes.saveRaw(c,sessionPatient,"jpg",Base64.decode(body.getJSONArray("frames").getString(i),Base64.NO_WRAP))
        } }
        if(!sendAi){status="Lokalni zajem preverjen • MIK $audioVerified • KAMERA $cameraVerified • brez pošiljanja AI";return}
        if(!busy.compareAndSet(false,true)){LiveTestSession.record("capture_skipped","Storitev zasedena; brez čakalne vrste.");return}
        val id=remoteId;val token=remoteToken
        io.execute {
            try {
                if(!valid(epoch))return@execute
                val result=LiveBackend.request(app!!,"/v1/sessions/$id/$kind",body,token)
                if(!valid(epoch) || id!=remoteId)return@execute
                check(!result.optBoolean("simulated",true))
                if(kind=="audio")audioVerified=true else cameraVerified=true
                val description=if(kind=="camera")when(result.optString("text")){
                    "head_up_down"->"Premik glave gor/dol; pomen ni potrjen."
                    "head_left_right"->"Premik glave levo/desno; pomen ni potrjen."
                    "head_turn"->"Obrat glave; pomen ni potrjen."
                    "gaze_away"->"Možen pogled stran; pomen ni potrjen."
                    else->"Ni jasno opaznega dogodka; to ne pomeni odgovora NE."
                }else result.optString("text").ifBlank{"Nerazumljiv ali tih odziv. Preverite z DA / NE / NE VEM ali skrbnikom."}
                val detail="${body.optLong("startMs")}–${body.optLong("endMs")} ms; vir AI; govorec NEZNAN; NEPOTRJENO: $description"
                LiveTestSession.observation(if(kind=="audio")"ai_transcript" else "ai_visible",detail,body.optLong("startMs"))
                status="Povezano • mikrofon ${if(mic) if(audioVerified) "zajem preverjen" else "čakam segment" else "izključen"} • kamera ${if(camera) if(cameraVerified) "zajem preverjen" else "čakam slike" else "izključena"} • pošiljanje OpenAI"
            }catch(_:Exception){if(valid(epoch)){status="Napaka povezave; segment zavržen. Komunikator deluje; naslednji zajem poskusi znova.";LiveTestSession.record("network_error",kind)}}
            finally {busy.set(false)}
        }
    }
    fun indicator() = "USTAVI SPREMLJANJE / ЗУПИНИТИ • ${if(mic) "MIK ${if(audioVerified) "✓" else "?"} " else ""}${if(camera) "KAMERA ${if(cameraVerified) "✓" else "?"} " else ""}${if(sendAi) "→ AI" else "lokalno"}${if(!sessionReady || status.startsWith("Napaka")) " • NAPAKA / ČAKANJE" else ""}"
    override fun onActivityResumed(a:Activity){
        visible=WeakReference(a)
        LiveTestSession.validate(a)
        if(!allowed(a))return
        if(LiveTestSession.active)resumeCapture()
        else if(a.javaClass.simpleName in setOf("MainActivity","AacCommunicatorActivity")) {
            val s=LiveCaptureSettings.load(a)
            val decision=policy.autoStart(s.microphone,s.camera,ContextCompat.checkSelfPermission(a,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED,
                ContextCompat.checkSelfPermission(a,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) ?: return
            val (m,cam)=decision
            if(m||cam)start(a,m,cam){} else if(s.microphone||s.camera)status="Spremljanje ni vključeno: dovoljenje je zavrnjeno. Komunikator je na voljo."
        }
    }
    override fun onActivityPaused(a:Activity){if(visible.get()===a){visible.clear();stopCapture();LiveBackend.cancel()}}
    override fun onActivityCreated(a:Activity,b:Bundle?){
        if(activityCount==0 && b==null && a.intent?.action==android.content.Intent.ACTION_MAIN)policy.newLaunch()
        activityCount++
    }
    override fun onActivityStarted(a:Activity){}
    override fun onActivityStopped(a:Activity){}
    override fun onActivitySaveInstanceState(a:Activity,b:Bundle){}
    override fun onActivityDestroyed(a:Activity){
        activityCount=(activityCount-1).coerceAtLeast(0)
        if(activityCount==0 && !a.isChangingConfigurations && LiveTestSession.active)LiveTestSession.stop()
    }
}
