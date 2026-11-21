import com.rehab2.aac.*
import java.io.File

fun main(args: Array<String>) {
    var checks = 0
    fun verify(ok: Boolean) { checks++; check(ok) { "Home check $checks" } }
    val expected = listOf("yes","no","thank_you","understand","dont_understand","wc","pain","cold","bad","tired",
        "thirsty","hungry","hot","good","sleepy","family_group","friends_group","wait","slower","faster",
        "need","i_want","correct_message","lets_go","angry")
    val originals = AacStarterContentV1.items()
    verify(originals.map { it.id }.distinct().size == originals.size)
    val home = AacHomeLayout.homeItems(originals)
    val fixed = AacFixedTopRow.rowItems(originals, 5)
    val dynamic = AacFixedTopRow.dynamicItems(home, originals, 5)
    val visible = fixed + dynamic.take(AacFixedTopRow.dynamicCapacity(5))
    verify(visible.map { it.id } == expected)
    verify("help" !in visible.map { it.id } && home.any { it.id == "help" })
    val byId = originals.associateBy { it.id }
    visible.forEachIndexed { index, item ->
        val decorated = AacHomeLayout.decorate(item)
        verify(decorated.labelSl == AacHomeLayout.labels[index])
        verify(decorated.labelByLanguage["uk"] == AacHomeLayout.ukLabels[index])
        verify(!decorated.speakTextSl.isNullOrBlank())
        verify(!decorated.speechTextByLanguage["uk"].isNullOrBlank())
        verify(decorated.children == byId.getValue(item.id).children)
        verify(decorated.children.all { it in byId })
        verify(File(args[0], AacHomeLayout.systemImage(item.id)!!).isFile)
    }
    // Overlay must not mutate personalized speech, image, relationships, or stored placements.
    val custom = byId.getValue("need").copy(userEdited = true, imagePath = "private/photo.png", audioSl = "private/voice.wav",
        speakTextSl = "Moj osebni govor.", speechText = "Moj osebni govor.", children = listOf("my-private-item"),
        placements = listOf(AacPlacement("my-page", 17)))
    val result = AacHomeLayout.decorate(custom)
    verify(result.imagePath == custom.imagePath && result.audioSl == custom.audioSl)
    verify(result.speakTextSl == custom.speakTextSl && result.speechText == custom.speechText)
    verify(result.children == custom.children && result.placements == custom.placements)
    verify(custom.labelSl == byId.getValue("need").labelSl)
    // Switching the overlay off restores the former row, without editing the stored catalog.
    AacFixedTopRow.reviewedLayout = false
    verify(AacFixedTopRow.rowItems(originals, 5).map { it.id } == listOf("yes","dont_understand","no","thank_you","help"))
    AacFixedTopRow.reviewedLayout = true
    verify(AacFixedTopRow.rowItems(originals, 5).map { it.id } == expected.take(5))
    // Both first-row meanings and page two are stable under different catalog iteration order.
    verify(AacHomeLayout.ordered(originals.reversed()).map { it.id } == expected)
    verify(dynamic.drop(20).any { it.id == "help" })
    println("PASS: $checks compiled catalog/layout checks; SL/UK positions, images, links, personal fields and reversible row")
}
