package com.rehab2.aac

/** Process-local stop latch. Persistent consent lives in LiveCaptureSettings, not here. */
class CaptureLaunchPolicy {
    var manualStopped=false; private set
    var launchAttempted=false; private set
    fun stop(){manualStopped=true}
    fun newLaunch(){manualStopped=false;launchAttempted=false}
    fun manualStart(){manualStopped=false;launchAttempted=true}
    fun autoStart(microphone:Boolean,camera:Boolean,micPermission:Boolean,cameraPermission:Boolean):Pair<Boolean,Boolean>? {
        if(manualStopped || launchAttempted)return null
        launchAttempted=true
        return Pair(microphone && micPermission,camera && cameraPermission)
    }
    fun mayCapture(visible:Boolean,sessionActive:Boolean,ready:Boolean)=visible && sessionActive && ready && !manualStopped
}
