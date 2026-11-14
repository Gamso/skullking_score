package com.gamso.skullkingscore.data.backup

import com.gamso.skullkingscore.domain.repository.AppSettings
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** When the automatic backup runs, and which files of the folder it replaces or deletes. */
class AutoBackupRulesTest {

    private val day = 24 * 60 * 60 * 1000L
    private val now = 100 * day
    private val enabled = AppSettings(autoBackup = true, backupFolder = "content://folder", lastBackupAt = now)

    @Test
    fun `a backup is due when there is none yet, or the last one failed or is a day old`() {
        assertTrue(isBackupDue(enabled.copy(lastBackupAt = null), now))
        assertTrue(isBackupDue(enabled.copy(lastBackupFailed = true), now))
        assertTrue(isBackupDue(enabled.copy(lastBackupAt = now - day), now))
        assertTrue(isBackupDue(enabled.copy(lastBackupAt = now - 3 * day), now))
    }

    @Test
    fun `no backup is due when the last one is recent or the feature is off`() {
        assertFalse(isBackupDue(enabled, now))
        assertFalse(isBackupDue(enabled.copy(lastBackupAt = now - day + 1), now))
        assertFalse(isBackupDue(enabled.copy(autoBackup = false, lastBackupAt = null), now))
        assertFalse(isBackupDue(enabled.copy(autoBackup = false, lastBackupFailed = true), now))
    }

    private val today: LocalDate = LocalDate.of(2026, 3, 20)

    /** One file a day, from [from] for [count] days, identified by its own name. */
    private fun files(from: LocalDate, count: Int): List<Pair<String, String>> =
        (0 until count).map { "skullking-${from.plusDays(it.toLong())}.json" }.map { it to it }

    @Test
    fun `the backup of the day is named after the date and replaces the one of the same day`() {
        val existing = files(today.minusDays(2), 3)

        val rotation = planBackupRotation(existing, today)

        assertEquals("skullking-2026-03-20.json", rotation.fileName)
        assertEquals(listOf("skullking-2026-03-20.json"), rotation.replaced)
        assertTrue(rotation.obsolete.isEmpty())
    }

    @Test
    fun `ten backups are kept with the new one, the oldest ones are deleted`() {
        // Twelve earlier days, listed in no particular order: nine are kept with today's backup.
        val existing = files(today.minusDays(12), 12).shuffled(java.util.Random(1))

        val rotation = planBackupRotation(existing, today)

        assertTrue(rotation.replaced.isEmpty())
        assertEquals(
            listOf("skullking-2026-03-10.json", "skullking-2026-03-09.json", "skullking-2026-03-08.json"),
            rotation.obsolete,
        )
    }

    @Test
    fun `an overwrite of the day does not count towards the files kept`() {
        // Today's file and nine earlier ones: today's is replaced, the nine others all stay.
        val existing = files(today.minusDays(9), 10)

        val rotation = planBackupRotation(existing, today)

        assertEquals(listOf("skullking-2026-03-20.json"), rotation.replaced)
        assertTrue(rotation.obsolete.isEmpty())
    }

    @Test
    fun `an empty folder only receives the new backup`() {
        val rotation = planBackupRotation(emptyList<Pair<String, String>>(), today)

        assertTrue(rotation.replaced.isEmpty())
        assertTrue(rotation.obsolete.isEmpty())
    }
}
