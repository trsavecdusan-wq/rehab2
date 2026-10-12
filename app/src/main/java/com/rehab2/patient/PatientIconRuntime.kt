package com.rehab2.patient

import android.content.Context
import android.graphics.BitmapFactory
import com.rehab2.aac.*
import java.io.File

object PatientIconRuntime {
    private val starterItems by lazy { AacStarterContentV1.items() }
    private val starterById by lazy { starterItems.associateBy { it.id } }
    fun stamp(context: Context): String = runCatching {
        listOfNotNull(AacStoragePaths.getPatientProfileFile(context), AacStoragePaths.getIconsPatientDir(context)?.let { File(it,"patient_icon_catalog_v1.json") })
            .joinToString(":") { if(it.isFile) PatientIconCatalog.hash(it) else "absent" }
    }.getOrDefault("unavailable")
    fun profiles(context: Context) = PatientProfileCatalog(requireNotNull(AacStoragePaths.getPatientProfileFile(context)))
    fun inspect(file: File): PatientImageCheck = runCatching {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeFile(file.path,bounds)
        if(bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096 || file.length()>16L*1024*1024) return PatientImageCheck(false)
        val bitmap=BitmapFactory.decodeFile(file.path) ?: return PatientImageCheck(false)
        try {
            var transparent=false; var nonempty=false
            val row=IntArray(bitmap.width)
            for(y in 0 until bitmap.height) {
                bitmap.getPixels(row,0,bitmap.width,0,y,bitmap.width,1)
                for(pixel in row) { val alpha= pixel ushr 24; if(alpha<255) transparent=true; if(alpha>0) nonempty=true }
            }
            PatientImageCheck(true,when(bounds.outMimeType) { "image/png" -> "PNG"; "image/jpeg" -> "JPEG"; "image/webp" -> "WEBP"; else -> "" },bitmap.width,bitmap.height,bitmap.hasAlpha(),transparent,nonempty)
        } finally { bitmap.recycle() }
    }.getOrDefault(PatientImageCheck(false))
    fun catalog(context: Context, items: List<AacItem> = AacEditorStorage.loadItems(context)): PatientIconCatalog {
        val profiles=profiles(context)
        val byId=(starterItems+items).associateBy { it.id }
        return PatientIconCatalog(requireNotNull(AacStoragePaths.getIconsPatientDir(context)),::inspect,
            { id -> profiles.list().any { it.id==id } },byId::get)
    }
    fun bind(context: Context) {
        val app=context.applicationContext
        AacGenderContext.gender={ runCatching { profiles(app).active().grammaticalGender }.getOrDefault(PatientGender.NEUTRAL) }
    }
    fun image(context: Context, item: AacItem, patientId: String? = null): File? = runCatching {
        val store=profiles(context); val profile=patientId?.let { id -> store.list().single { it.id==id } } ?: store.active()
        val explicitOwner=item.imageOwner?.uppercase().orEmpty()
        val neutral=AacPhase1SystemIcons.paths[item.id]
            ?: item.imagePath.takeIf { item.iconSource==IconSource.SYSTEM && !item.locked && !item.userEdited &&
                explicitOwner in setOf("", "SYSTEM") && it.startsWith("system/") }
            ?: starterById[item.id]?.imagePath.orEmpty()
        AacImageResolver.resolve(item,profile.id,profile.visualSex,store.legacyOwner(),
            { AacStoragePaths.resolveIconFile(context,item.imagePath,item.iconSource) },
            { catalog(context, listOf(item)).active(profile.id,item.id) },
            { path -> AacStoragePaths.resolveIconFile(context,path,IconSource.SYSTEM) },neutral,
            { it.isFile && inspect(it).let { v -> v.valid && v.nonEmpty } },
            { item.patientManualImages[profile.id]?.let { image -> AacStoragePaths.resolveIconFile(context,image.imagePath,image.iconSource) } })
    }.getOrElse {
        val fallback=AacStoragePaths.resolveIconFile(context,AacPhase1SystemIcons.paths[item.id] ?: "system/other.png",IconSource.SYSTEM)
        fallback?.takeIf { f -> inspect(f).valid }
    }
}
