package com.rehab2.aac
/** Serializes ownership transfer with stop; a late native callback cannot acquire a stopped resource. */
class CaptureResourceGate {
    @Volatile var generation=0
        private set
    @Synchronized fun stop(release:()->Unit){generation++;release()}
    @Synchronized fun open(epoch:Int,acquire:()->Unit):Boolean {
        if(epoch!=generation)return false
        acquire();return true
    }
    @Synchronized fun release(action:()->Unit){action()}
}
