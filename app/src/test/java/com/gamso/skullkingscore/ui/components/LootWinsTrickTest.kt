package com.gamso.skullkingscore.ui.components

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** The trick entry warns when the player who played a Loot is also entered as the winner of the trick. */
class LootWinsTrickTest {

    @Test
    fun `the Loot player entered as the winner is flagged`() {
        assertTrue(lootWinsTrick(winnerId = 1, lootPlayerIds = listOf(1L, 2L)))
    }

    @Test
    fun `another winner, no winner or no Loot are not flagged`() {
        assertFalse(lootWinsTrick(winnerId = 3, lootPlayerIds = listOf(1L, 2L)))
        assertFalse(lootWinsTrick(winnerId = null, lootPlayerIds = listOf(1L)))
        assertFalse(lootWinsTrick(winnerId = 1, lootPlayerIds = emptyList()))
    }
}
