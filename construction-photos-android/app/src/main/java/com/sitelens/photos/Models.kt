package com.sitelens.photos

data class Photo(
    val key: String, val uri: String, val name: String, val mime: String,
    val hash: String, val suggested: Boolean, val reason: String,
    val override: Boolean? = null, val uploaded: Boolean = false, val error: String? = null,
    val takenAtMillis: Long? = null, val reupload: Boolean = false
) {
    val selected: Boolean get() = if (uploaded) reupload else (override ?: suggested)
}

data class Folder(val id: String, val name: String)
data class Destination(val accountId: String, val accountName: String, val folder: Folder) {
    val scope: String get() = "$accountId:${folder.id}"
}
data class LabelScore(val text: String, val confidence: Float)
data class Suggestion(val selected: Boolean, val reason: String)

object ConstructionClassifier {
    // General-purpose labels are suggestions, not a trained construction-site classifier.
    private val specific = setOf("construction", "construction site", "brick", "brickwork", "scaffolding", "bulldozer", "excavator", "crane", "power tool", "tool", "tools", "cement", "concrete")
    private val broad = setOf("building", "roof", "roofing", "wall", "lumber", "wood", "architecture", "floor", "plumbing")
    fun classify(labels: List<LabelScore>): Suggestion {
        val matches = labels.filter {
            val text = it.text.lowercase(java.util.Locale.ROOT)
            (text in specific && it.confidence >= .60f) || (text in broad && it.confidence >= .80f)
        }.sortedByDescending { it.confidence }
        return Suggestion(matches.isNotEmpty(), matches.take(2).joinToString(" · ") { it.text }.ifEmpty { "No construction match" })
    }
}
