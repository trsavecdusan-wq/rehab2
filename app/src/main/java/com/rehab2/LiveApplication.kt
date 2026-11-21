package com.rehab2
import android.app.Application
import com.rehab2.aac.LiveCapture
class LiveApplication : Application() {
    override fun onCreate() { super.onCreate(); LiveCapture.initialize(this) }
}
