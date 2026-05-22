package com.rehab2

import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rehab2.aac.AacAudioPlayer
import com.rehab2.aac.AacItem
import com.rehab2.aac.AacLocalStorage
import com.rehab2.aac.AacPage
import com.rehab2.aac.AacRepository
import java.io.File

class AacCommunicatorActivity : AppCompatActivity() {
    private lateinit var repository: AacRepository
    private val pageHistory = ArrayDeque<String>()
    private lateinit var audioPlayer: AacAudioPlayer
    private lateinit var txtTitle: TextView
    private lateinit var recycler: RecyclerView
    private var currentPageId: String = "home"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aac_communicator)
        repository = AacRepository(this)
        audioPlayer = AacAudioPlayer(this)

        if (AacLocalStorage.ensureStructure(this)) {
            Toast.makeText(this, "AAC MAPE PRIPRAVLJENE", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "AAC MAP NI MOGO\u010CE USTVARITI", Toast.LENGTH_SHORT).show()
        }

        AacLocalStorage.seedBundledTestAudio(this)

        txtTitle = findViewById(R.id.txtAacTitle)
        recycler = findViewById(R.id.recyclerAacTiles)
        recycler.layoutManager = GridLayoutManager(this, 5)

        val homePage = repository.loadHomePage()
        showPage(homePage)
        showRepositoryDebugStatus()
    }

    override fun onDestroy() {
        audioPlayer.release()
        super.onDestroy()
    }

    private fun showPage(page: AacPage) {
        currentPageId = page.pageId
        txtTitle.text = buildTitleText(page.title)
        recycler.adapter = AacAdapter(page.items) { item ->
            handleItemClick(item)
        }
    }

    private fun handleItemClick(item: AacItem) {
        when (item.actionType) {
            "open_page" -> openTargetPage(item.targetPageId)
            "go_home" -> goHome()
            "go_back" -> goBack()
            else -> audioPlayer.playOrSpeak(item)
        }
    }

    private fun openTargetPage(targetPageId: String) {
        val normalizedTargetPageId = targetPageId.trim()
        if (normalizedTargetPageId.isBlank()) {
            Toast.makeText(this, "Stran ni dolo\u010Dena", Toast.LENGTH_SHORT).show()
            return
        }

        val page = repository.loadPage(normalizedTargetPageId)
        if (page == null) {
            showRepositoryDebugStatus()
            return
        }

        pageHistory.addLast(currentPageId)
        showPage(page)
    }

    private fun goHome() {
        val page = repository.loadPage("home")
        if (page == null) {
            showRepositoryDebugStatus()
            return
        }

        pageHistory.clear()
        showPage(page)
    }

    private fun goBack() {
        if (pageHistory.isEmpty()) {
            return
        }

        val previousPageId = pageHistory.last()
        val page = repository.loadPage(previousPageId)
        if (page == null) {
            showRepositoryDebugStatus()
            return
        }

        pageHistory.removeLast()
        showPage(page)
    }

    private fun buildTitleText(baseTitle: String): String {
        return if (repository.lastDebugCode == "OK") {
            baseTitle
        } else {
            "$baseTitle\n${repository.lastDebugCode}"
        }
    }

    private fun showRepositoryDebugStatus() {
        if (repository.lastDebugCode == "OK") {
            return
        }

        txtTitle.text = buildTitleText(txtTitle.text.toString().lineSequence().firstOrNull().orEmpty())
        Toast.makeText(this, repository.lastDebugStatus, Toast.LENGTH_LONG).show()
    }

    private class AacAdapter(
        private val items: List<AacItem>,
        private val onItemClick: (AacItem) -> Unit
    ) : RecyclerView.Adapter<AacAdapter.AacViewHolder>() {

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): AacViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_aac_tile, parent, false)
            return AacViewHolder(view, onItemClick)
        }

        override fun onBindViewHolder(holder: AacViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        class AacViewHolder(
            itemView: View,
            private val onItemClick: (AacItem) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
            private val image: ImageView = itemView.findViewById(R.id.imgAacTile)
            private val label: TextView = itemView.findViewById(R.id.txtAacTileLabel)

            fun bind(item: AacItem) {
                label.text = item.labelSl
                label.gravity = Gravity.CENTER
                label.setTypeface(label.typeface, Typeface.BOLD)
                image.setImageBitmap(null)

                val imageFile = item.imagePath.takeIf { it.isNotBlank() }?.let { File(it) }
                if (imageFile != null && imageFile.exists() && imageFile.isFile) {
                    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                    if (bitmap != null) {
                        image.setImageBitmap(bitmap)
                    } else {
                        image.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                } else {
                    image.setImageResource(android.R.drawable.ic_menu_gallery)
                }

                itemView.setOnClickListener { onItemClick(item) }
            }
        }
    }
}