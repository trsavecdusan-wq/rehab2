package com.rehab2.aac

data class AacV2Page(
    val version: Int,
    val pageId: String,
    val title: String,
    val promptSl: String?,
    val promptUk: String?,
    val concepts: List<AacConcept>,
    val icons: List<AacIconVariant>,
    val nodes: List<AacConversationNode>
)
