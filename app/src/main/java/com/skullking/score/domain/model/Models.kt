package com.skullking.score.domain.model

import com.skullking.score.core.Bonus
import com.skullking.score.core.ExpansionCard
import com.skullking.score.core.Pirate
import com.skullking.score.core.ScoringMode
import com.skullking.score.core.SeaMonster
import com.skullking.score.core.WinningCard

data class Player(
    val id: Long,
    val uuid: String,
    val name: String,
    /** Couleur ARGB. */
    val color: Long,
    val createdAt: Long,
    /** Masqué dans les listes de sélection, sans perdre ses statistiques. */
    val archived: Boolean = false,
)

enum class GameStatus { IN_PROGRESS, FINISHED }

enum class RoundPhase { BIDDING, PLAYING, SCORED }

data class Game(
    val id: Long,
    val uuid: String,
    val startedAt: Long,
    val endedAt: Long?,
    val mode: ScoringMode,
    val expansionCards: Set<ExpansionCard>,
    val status: GameStatus,
    /** Option sur le point ouvert de la règle : le +20 du Casier de Davy Jones exige l'annonce réussie. */
    val davyJonesRequiresSuccess: Boolean,
    /** Joueurs dans l'ordre autour de la table. */
    val players: List<Player>,
    /** Place du donneur de la première manche ; le donneur avance d'une place à chaque manche. */
    val firstDealerSeat: Int = 0,
) {
    /** Donneur de la manche [roundNumber] (à partir de 1). */
    fun dealerFor(roundNumber: Int): Player? =
        if (players.isEmpty()) null else players[Math.floorMod(firstDealerSeat + roundNumber - 1, players.size)]
}

data class Round(
    val id: Long,
    val gameId: Long,
    val number: Int,
    val cardsDealt: Int,
    val isExtra: Boolean,
    val discardedTricks: Int,
    val phase: RoundPhase,
)

data class PlayerRound(
    val roundId: Long,
    val playerId: Long,
    val bid: Int,
    /** Annonce après correction (Harry le Géant) ; `null` tant qu'elle n'a pas été modifiée. */
    val finalBid: Int?,
    val tricks: Int,
    val cannonball: Boolean,
    val rascalWager: Int,
    val bonus: Bonus,
    /** Score calculé, mis en cache pour l'affichage (jamais saisi). */
    val score: Int,
) {
    val effectiveBid: Int get() = finalBid ?: bid
}

data class LootLink(
    val id: Long,
    val roundId: Long,
    val lootPlayerId: Long,
    /** `null` tant que le pli en cours n'est pas terminé (ou s'il a été détruit). */
    val trickWinnerId: Long?,
    val createdAt: Long,
    /** Renseigné quand l'événement a été annulé ; le journal en garde la trace. */
    val cancelledAt: Long?,
    /** Pli pendant lequel le Butin a été joué ; `null` si inconnu. */
    val trickNumber: Int? = null,
) {
    val isActive: Boolean get() = cancelledAt == null
}

data class DavyJonesCapture(
    val id: Long,
    val roundId: Long,
    val playerId: Long,
    val creatures: Set<SeaMonster>,
    val createdAt: Long,
    val cancelledAt: Long?,
    val trickNumber: Int? = null,
) {
    val isActive: Boolean get() = cancelledAt == null
}

/** Un pli saisi en direct : son gagnant (`null` = pli détruit par le Kraken) et les cartes qu'il a capturées. */
data class Trick(
    val id: Long,
    val roundId: Long,
    val number: Int,
    val winnerId: Long?,
    val bonus: Bonus,
    val createdAt: Long,
    /** Pari de Rascal le Flambeur (0, 10 ou 20) fait par le gagnant du pli. */
    val rascalWager: Int = 0,
    /** Annonce du gagnant ajustée par Harry le Géant joué dans ce pli : -1, 0 ou +1. */
    val bidAdjustment: Int = 0,
    val winningCard: WinningCard? = null,
    val winningPirate: Pirate? = null,
)

/** Ce que la saisie d'un pli apporte : gagnant (`null` = pli détruit), cartes gagnées et réglages éventuels. */
data class TrickDraft(
    val winnerId: Long?,
    val bonus: Bonus = Bonus.NONE,
    val rascalWager: Int = 0,
    val bidAdjustment: Int = 0,
    val winningCard: WinningCard? = null,
    val winningPirate: Pirate? = null,
)

data class RoundDetails(
    val round: Round,
    val entries: List<PlayerRound>,
    val loots: List<LootLink>,
    val captures: List<DavyJonesCapture>,
    /** Plis saisis, triés par numéro. */
    val tricks: List<Trick> = emptyList(),
) {
    /** Numéro du pli en cours de jeu. */
    val currentTrickNumber: Int get() = tricks.size + 1

    val activeLoots: List<LootLink> get() = loots.filter { it.isActive }
    val activeCaptures: List<DavyJonesCapture> get() = captures.filter { it.isActive }

    fun entry(playerId: Long): PlayerRound? = entries.firstOrNull { it.playerId == playerId }
}

data class GameDetails(
    val game: Game,
    /** Triées par numéro de manche. */
    val rounds: List<RoundDetails>,
) {
    fun player(id: Long): Player? = game.players.firstOrNull { it.id == id }
}

/** Résumé pour l'historique : les totaux viennent des scores mis en cache. */
data class GameSummary(val game: Game, val totals: Map<Long, Int>) {
    val winners: List<Player>
        get() {
            val best = totals.values.maxOrNull() ?: return emptyList()
            return game.players.filter { totals[it.id] == best }
        }
}

/** Où reprendre une partie. */
sealed interface GameStep {
    data class Prepare(val roundNumber: Int) : GameStep
    data class Bids(val roundId: Long) : GameStep
    data class Play(val roundId: Long) : GameStep
    data object Scoreboard : GameStep
    data object Finished : GameStep
}

fun GameDetails.nextStep(): GameStep {
    if (game.status == GameStatus.FINISHED) return GameStep.Finished
    val last = rounds.lastOrNull()?.round ?: return GameStep.Prepare(1)
    return when (last.phase) {
        RoundPhase.BIDDING -> GameStep.Bids(last.id)
        RoundPhase.PLAYING -> GameStep.Play(last.id)
        RoundPhase.SCORED -> GameStep.Scoreboard
    }
}
