package com.rehab2

import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rehab2.aac.AacAudioPlayer
import com.rehab2.aac.AacItem
import com.rehab2.aac.AacRepository
import java.io.File

class AacCommunicatorActivity : AppCompatActivity() {
    private val repository = AacRepository()
    private lateinit var audioPlayer: AacAudioPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aac_communicator)
        audioPlayer = AacAudioPlayer(this)

        val txtTitle: TextView = findViewById(R.id.txtAacTitle)
        val recycler: RecyclerView = findViewById(R.id.recyclerAacTiles)
        val page = repository.loadHomePage()

        txtTitle.text = page.title
        recycler.layoutManager = GridLayoutManager(this, 5)
        recycler.adapter = AacAdapter(page.items) { item ->
            audioPlayer.playOrSpeak(item)
        }
    }

    override fun onDestroy() {
        audioPlayer.release()
        super.onDestroy()
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
