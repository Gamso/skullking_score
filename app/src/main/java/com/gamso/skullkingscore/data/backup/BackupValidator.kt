package com.gamso.skullkingscore.data.backup

import com.gamso.skullkingscore.core.DeckRules
import com.gamso.skullkingscore.domain.repository.InvalidBackupException

/**
 * Checks the values of a decoded backup before anything is written: numbers out of range and duplicates that the
 * database would refuse (or that would crash the statistics) make the whole file invalid.
 */
object BackupValidator {
    private const val WAGER_STEP = 10
    private val WAGERS = setOf(0, WAGER_STEP, 2 * WAGER_STEP)
    private val BID_ADJUSTMENTS = -1..1

    /** @throws InvalidBackupException on the first inconsistent value */
    fun validate(dto: BackupDto) {
        dto.games.forEach(::validateGame)
    }

    private fun validateGame(game: GameDto) {
        val where = "game ${game.uuid}"
        ensure(game.playerUuids.isNotEmpty(), where) { "no player" }
        ensure(game.playerUuids.toSet().size == game.playerUuids.size, where) { "a player is seated twice" }
        ensure(game.firstDealerSeat in game.playerUuids.indices, where) { "dealer seat ${game.firstDealerSeat}" }
        val numbers = game.rounds.map { it.number }
        ensure(numbers.toSet().size == numbers.size, where) { "two rounds with the same number" }
        game.rounds.forEach { validateRound(it, "$where, round ${it.number}") }
    }

    private fun validateRound(round: RoundDto, where: String) {
        val cards = round.cardsDealt
        ensure(round.number >= 1, where) { "round number" }
        ensure(cards in 1..DeckRules.MAX_CARDS_PER_ROUND, where) { "cards dealt $cards" }
        ensure(round.discardedTricks in 0..cards, where) { "discarded tricks ${round.discardedTricks}" }
        val players = round.entries.map { it.playerUuid }
        ensure(players.toSet().size == players.size, where) { "two entries for the same player" }
        round.entries.forEach { e ->
            ensure(e.bid in 0..cards, where) { "bid ${e.bid}" }
            ensure(e.finalBid == null || e.finalBid in 0..cards, where) { "final bid ${e.finalBid}" }
            ensure(e.tricks in 0..cards, where) { "tricks ${e.tricks}" }
            // Sum of the wagers of the tricks won: 0, 10 or 20 in a normal game, never negative.
            ensure(e.rascalWager >= 0 && e.rascalWager % WAGER_STEP == 0, where) { "Rascal wager ${e.rascalWager}" }
            checkBonus(e.bonus, where)
        }
        val trickNumbers = round.tricks.map { it.number }
        ensure(trickNumbers.toSet().size == trickNumbers.size, where) { "two tricks with the same number" }
        round.tricks.forEach { t ->
            ensure(t.number in 1..cards, where) { "trick number ${t.number}" }
            ensure(t.rascalWager in WAGERS, where) { "Rascal wager ${t.rascalWager}" }
            ensure(t.bidAdjustment in BID_ADJUSTMENTS, where) { "bid adjustment ${t.bidAdjustment}" }
            checkBonus(t.bonus, where)
        }
    }

    private fun checkBonus(bonus: BonusDto, where: String) {
        val counts = listOf(
            bonus.colored14,
            bonus.black14,
            bonus.mermaidsByPirate,
            bonus.piratesBySkullKing,
            bonus.skullKingByMermaid,
            bonus.conCaptured,
            bonus.eights,
            bonus.sevens,
        )
        ensure(counts.all { it >= 0 }, where) { "negative bonus count" }
    }

    private inline fun ensure(condition: Boolean, where: String, message: () -> String) {
        if (!condition) throw InvalidBackupException("Invalid backup, $where: ${message()}")
    }
}
