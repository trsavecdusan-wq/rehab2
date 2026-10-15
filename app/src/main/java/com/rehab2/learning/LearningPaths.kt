package com.rehab2.learning

import android.content.Context
import java.io.File

/** Internal storage only; not getExternalFilesDir and not NovaRehab/. */
object LearningPaths {
    const val DIR_NAME = "learning_v1"
    fun directory(context: Context): File = File(context.filesDir, DIR_NAME)
}
