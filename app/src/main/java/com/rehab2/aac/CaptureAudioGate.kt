package com.rehab2.aac
/** Discard an entire segment if application speech overlaps it, including a one-second echo tail. */
class CaptureAudioGate(private val now:()->Long) {
    private var epoch=0L
    private var speaking=false
    private var quietAfter=0L
    @Synchronized fun event(kind:String){
        if(kind=="speech_will_start" || kind=="sound_start"){speaking=true;epoch++}
        if(kind in listOf("playback_end","speech_cancelled","speech_error")){speaking=false;epoch++;quietAfter=now()+1000}
    }
    @Synchronized fun begin():Long? = if(!speaking && now()>=quietAfter)epoch else null
    @Synchronized fun accepts(start:Long?):Boolean = start!=null && start==epoch && !speaking && now()>=quietAfter
}
