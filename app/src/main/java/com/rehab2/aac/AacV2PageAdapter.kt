package com.rehab2.aac

import android.util.Log

object AacV2PageAdapter {
    var lastWaterParsedModelChildrenCount: Int = -1
        private set

    var lastWaterParsedModelChildrenIds: List<String> = emptyList()
        private set

    var lastWaterMappedItemChildrenCount: Int = -1
        private set

    var lastWaterMappedItemChildrenIds: List<String> = emptyList()
        private set

    fun toAacPage(page: AacV2Page): AacPage {
        val conceptsById = page.concepts.associateBy { it.id }
        val iconsById = page.icons.associateBy { it.id }
        val childIds = page.nodes.flatMap { it.children }.toSet()
        val parentIdsByChildId = buildMap {
            page.nodes.forEach { node ->
                node.children.forEach { childId ->
                    put(childId, node.id)
                }
            }
        }

        val items = page.nodes.mapIndexed { index, node ->
            val concept = node.conceptId?.let { conceptsById[it] }
            val icon = concept?.activeIconId?.let { iconsById[it] }
            if (node.id == WATER_NODE_ID) {
                lastWaterParsedModelChildrenCount = node.children.size
                lastWaterParsedModelChildrenIds = node.children
                debugLog("TRACE water parsed model children=${node.children.size} ids=${node.children}")
            }

            val item = AacItem(
                id = node.id,
                labelSl = node.title,
                imagePath = icon?.imagePath.orEmpty(),
                audioSl = "",
                actionType = "speak",
                targetPageId = "",
                speakTextSl = node.speakTextSl,
                speakTextUk = node.speakTextUk,
                labelUk = node.titleUk,
                conceptId = node.conceptId,
                children = node.children,
                sentenceRole = node.sentenceRole,
                questionSl = node.questionSl,
                questionUk = node.questionUk,
                iconSource = icon?.source ?: IconSource.SYSTEM,
                parentId = parentIdsByChildId[node.id],
                isRootItem = node.id !in childIds,
                isHiddenUntilParent = node.id in childIds,
                priority = index
            )
            if (item.id == WATER_NODE_ID) {
                lastWaterMappedItemChildrenCount = item.children.size
                lastWaterMappedItemChildrenIds = item.children
                debugLog("TRACE water mapped AacItem children=${item.children.size} ids=${item.children}")
            }
            item
        }

        return AacPage(
            pageId = page.pageId,
            title = page.title,
            items = items
        )
    }

    private const val TAG = "AacV2PageAdapter"
    private const val WATER_NODE_ID = "water"

    private fun debugLog(message: String) {
        Log.d(TAG, message)
    }
}
