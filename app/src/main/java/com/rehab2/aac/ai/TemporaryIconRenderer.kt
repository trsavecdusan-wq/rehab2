package com.rehab2.aac.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Admin-only view; never writes an image or returns an AAC item. */
object TemporaryIconRenderer {
    fun loadCategoryImage(context: Context, category: TemporaryCategory): Bitmap? {
        val name = when (category) {
            TemporaryCategory.PERSON -> "people"
            TemporaryCategory.PLACE -> "home"
            TemporaryCategory.FOOD -> "food_main_dish"
            TemporaryCategory.DRINK -> "drink_water"
            TemporaryCategory.CLOTHING -> "care_change_clothes"
            TemporaryCategory.ACTIVITY -> "activity_walk_v3"
            TemporaryCategory.HEALTH -> "help"
            TemporaryCategory.OTHER -> "need"
        }
        return runCatching { context.assets.open("NovaRehab/icons/system/$name.png").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 })
        } }.getOrNull()
    }
    fun create(context: Context, suggestion: AacSuggestion, image: Bitmap?): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        setPadding(16, 16, 16, 16); setBackgroundColor(Color.rgb(235, 239, 243))
        addView(ImageView(context).apply {
            if (image != null) setImageBitmap(image) else setImageResource(android.R.drawable.ic_menu_help)
            contentDescription = suggestion.temporaryCategory.name
        }, LinearLayout.LayoutParams(96, 96))
        addView(TextView(context).apply { text = suggestion.labelSl; textSize = 26f; setTextColor(Color.BLACK) })
        addView(TextView(context).apply { text = "ZAČASNO · ${suggestion.temporaryCategory}"; textSize = 18f; setTextColor(Color.DKGRAY) })
    }
}
