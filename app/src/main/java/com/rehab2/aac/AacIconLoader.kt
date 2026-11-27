package com.rehab2.aac

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.Callable

/** Display-only decoding. Never modifies the source or patient image bindings. */
class AacIconLoader {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun load(resolve: () -> File?, complete: (Bitmap?) -> Unit): Future<*> {
        lateinit var task: FutureTask<Unit>
        task = FutureTask(Callable {
            if (closed || task.isCancelled) return@Callable Unit
            val bitmap = try {
                resolve()?.let { file ->
                    val key = "${file.absolutePath}:${file.lastModified()}:${file.length()}"
                    cache.get(key) ?: decode(file)?.also { if (!closed) cache.put(key, it) }
                }
            } catch (_: Exception) { null }
            if (!closed && !task.isCancelled) main.post {
                if (!closed && !task.isCancelled) complete(bitmap)
            }
            Unit
        })
        worker.execute(task)
        return task
    }

    private fun decode(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
        if (Thread.currentThread().isInterrupted) return null
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun close() {
        closed = true
        worker.shutdownNow()
        main.removeCallbacksAndMessages(null)
        cache.evictAll() // Views may still hold bitmaps; do not recycle them here.
    }
}
