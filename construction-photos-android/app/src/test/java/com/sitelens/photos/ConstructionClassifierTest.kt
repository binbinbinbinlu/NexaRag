package com.sitelens.photos

import org.junit.Assert.*
import org.junit.Test

class ConstructionClassifierTest {
    @Test fun constructionIsSuggestedWhileCasualPhotosAreNot() {
        assertTrue(ConstructionClassifier.classify(listOf(LabelScore("Construction", .85f))).selected)
        assertFalse(ConstructionClassifier.classify(listOf(LabelScore("Dog", .99f), LabelScore("Beach", .95f))).selected)
    }
    @Test fun ambiguousLabelsNeedHigherConfidence() {
        assertFalse(ConstructionClassifier.classify(listOf(LabelScore("Building", .65f))).selected)
        assertTrue(ConstructionClassifier.classify(listOf(LabelScore("Building", .9f))).selected)
        assertFalse(ConstructionClassifier.classify(listOf(LabelScore("Construction", .5f))).selected)
    }
    @Test fun suggestionsExplainTheirStrongestMatches() {
        val result = ConstructionClassifier.classify(listOf(LabelScore("Wood", .81f), LabelScore("Construction", .96f)))
        assertEquals("Construction · Wood", result.reason)
    }
    @Test fun manualChoicesOverrideBothFalsePositivesAndFalseNegatives() {
        val photo = Photo("key", "uri", "photo", "image/jpeg", "hash", true, "Construction")
        assertFalse(photo.copy(override = false).selected)
        assertTrue(photo.copy(suggested = false, override = true).selected)
        assertFalse(photo.copy(uploaded = true, override = true).selected)
    }
    @Test fun uploadHistoryIsScopedToAccountAndFolder() {
        val first = Destination("account-one", "one", Folder("folder-one", "Project"))
        assertNotEquals(first.scope, first.copy(accountId = "account-two").scope)
        assertNotEquals(first.scope, first.copy(folder = Folder("folder-two", "Project")).scope)
    }
}
