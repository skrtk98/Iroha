package io.github.skrtk98.iroha.engine.user

import org.junit.Assert.assertEquals
import org.junit.Test

class LearningStoreNotificationTest {
    @Test
    fun notifiesOnlyWhenSomethingIsLearned() {
        val store = LearningStore()
        var count = 0
        store.onLearned = { count++ }
        store.learn("", "語", 1, 1)
        assertEquals(0, count)
        store.learn("ご", "語", 1, 1)
        assertEquals(1, count)
    }
}
