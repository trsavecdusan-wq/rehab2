package com.rehab2.aac

object AacV2PageAdapter {
    fun toAacPage(page: AacV2Page): AacPage {
        val conceptsById = page.concepts.associateBy { it.id }
        val iconsById = page.icons.associateBy { it.id }

        val items = page.nodes.map { node ->
            val concept = node.conceptId?.let { conceptsById[it] }
            val icon = concept?.activeIconId?.let { iconsById[it] }

            AacItem(
                id = node.id,
                labelSl = node.title,
                imagePath = icon?.imagePath.orEmpty(),
                audioSl = "",
                actionType = "speak",
                targetPageId = "",
                speakTextSl = node.speakTextSl,
                speakTextUk = node.speakTextUk,
                conceptId = node.conceptId,
                children = node.children,
                sentenceRole = node.sentenceRole,
                questionSl = node.questionSl,
                questionUk = node.questionUk
            )
        }

        return AacPage(
            pageId = page.pageId,
            title = page.title,
            items = items
        )
    }
}
