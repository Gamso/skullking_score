package com.gamso.skullkingscore.data.backup

import com.gamso.skullkingscore.demo.DemoData
import com.gamso.skullkingscore.domain.repository.InvalidBackupException
import com.gamso.skullkingscore.domain.stats.RoundSize
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.Json
import org.junit.Test

class BackupValidatorTest {

    private val entryAnn = EntryDto(playerUuid = "ann", bid = 1, tricks = 1, rascalWager = 10)
    private val entryBob = EntryDto(playerUuid = "bob", bid = 0, tricks = 0)

    private val round = RoundDto(
        number = 1,
        cardsDealt = 1,
        phase = "SCORED",
        entries = listOf(entryAnn, entryBob),
        tricks = listOf(TrickDto(number = 1, winnerUuid = "ann", rascalWager = 10, bidAdjustment = 1)),
    )

    private val game = GameDto(
        uuid = "g1",
        startedAt = 1_000,
        mode = "CLASSIC",
        status = "FINISHED",
        firstDealerSeat = 1,
        playerUuids = listOf("ann", "bob"),
        rounds = listOf(round),
    )

    private fun backup(game: GameDto) = BackupDto(
        formatVersion = BackupCodec.FORMAT_VERSION,
        exportedAt = 0,
        players = listOf(PlayerDto("ann", "Ann", 0, 0), PlayerDto("bob", "Bob", 0, 0)),
        games = listOf(game),
    )

    private fun withRound(transform: (RoundDto) -> RoundDto) = backup(game.copy(rounds = listOf(transform(round))))

    private fun assertInvalid(dto: BackupDto) {
        assertFailsWith<InvalidBackupException> { BackupValidator.validate(dto) }
    }

    @Test
    fun aConsistentBackupIsAccepted() {
        BackupValidator.validate(backup(game))
        // Every game of the demo file, written by the app itself, passes too.
        BackupValidator.validate(BackupCodec.decode(DemoData.json()))
    }

    @Test
    fun theDecoderRejectsAFileWithInvalidValues() {
        val json = Json.encodeToString(BackupDto.serializer(), withRound { it.copy(cardsDealt = 0) })
        assertFailsWith<InvalidBackupException> { BackupCodec.decode(json) }
    }

    @Test
    fun duplicatesRefusedByTheDatabaseAreDetectedBeforehand() {
        assertInvalid(backup(game.copy(rounds = listOf(round, round))))
        assertInvalid(withRound { it.copy(tricks = it.tricks + it.tricks) })
        assertInvalid(withRound { it.copy(entries = it.entries + entryAnn) })
        assertInvalid(backup(game.copy(playerUuids = listOf("ann", "ann"))))
    }

    @Test
    fun theNumberOfCardsAndTheRoundNumberAreBounded() {
        assertInvalid(withRound { it.copy(cardsDealt = 0) })
        assertInvalid(withRound { it.copy(cardsDealt = 11) })
        assertInvalid(withRound { it.copy(number = 0) })
        assertInvalid(withRound { it.copy(discardedTricks = 2) })
        assertInvalid(withRound { it.copy(discardedTricks = -1) })
    }

    @Test
    fun bidsTricksAndBonusesAreBounded() {
        assertInvalid(withRound { it.copy(entries = listOf(entryAnn.copy(bid = -1), entryBob)) })
        assertInvalid(withRound { it.copy(entries = listOf(entryAnn.copy(bid = 2), entryBob)) })
        assertInvalid(withRound { it.copy(entries = listOf(entryAnn.copy(finalBid = 2), entryBob)) })
        assertInvalid(withRound { it.copy(entries = listOf(entryAnn.copy(tricks = -1), entryBob)) })
        assertInvalid(withRound { it.copy(entries = listOf(entryAnn.copy(tricks = 2), entryBob)) })
        assertInvalid(withRound { it.copy(entries = listOf(entryAnn.copy(rascalWager = 15), entryBob)) })
        assertInvalid(
            withRound { it.copy(entries = listOf(entryAnn.copy(bonus = BonusDto(colored14 = -1)), entryBob)) },
        )
    }

    @Test
    fun trickSettingsAreBounded() {
        val trick = round.tricks.single()
        assertInvalid(withRound { it.copy(tricks = listOf(trick.copy(number = 0))) })
        assertInvalid(withRound { it.copy(tricks = listOf(trick.copy(number = 2))) })
        assertInvalid(withRound { it.copy(tricks = listOf(trick.copy(rascalWager = 30))) })
        assertInvalid(withRound { it.copy(tricks = listOf(trick.copy(bidAdjustment = 2))) })
        assertInvalid(withRound { it.copy(tricks = listOf(trick.copy(bonus = BonusDto(sevens = -1)))) })
    }

    @Test
    fun theDealerMustBeSeatedAtTheTable() {
        assertInvalid(backup(game.copy(firstDealerSeat = 2)))
        assertInvalid(backup(game.copy(firstDealerSeat = -1)))
        assertInvalid(backup(game.copy(playerUuids = emptyList(), firstDealerSeat = 0)))
    }

    @Test
    fun aRoundWithoutCardsDoesNotCrashTheStatistics() {
        assertEquals(RoundSize.SMALL, RoundSize.of(0))
    }
}
