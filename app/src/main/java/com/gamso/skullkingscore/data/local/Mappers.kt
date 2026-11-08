package com.gamso.skullkingscore.data.local

import com.gamso.skullkingscore.core.Bonus
import com.gamso.skullkingscore.core.ExpansionCard
import com.gamso.skullkingscore.core.Pirate
import com.gamso.skullkingscore.core.ScoringMode
import com.gamso.skullkingscore.core.SeaMonster
import com.gamso.skullkingscore.core.WinningCard
import com.gamso.skullkingscore.domain.model.DavyJonesCapture
import com.gamso.skullkingscore.domain.model.Game
import com.gamso.skullkingscore.domain.model.GameDetails
import com.gamso.skullkingscore.domain.model.GameStatus
import com.gamso.skullkingscore.domain.model.LootLink
import com.gamso.skullkingscore.domain.model.Player
import com.gamso.skullkingscore.domain.model.PlayerRound
import com.gamso.skullkingscore.domain.model.Round
import com.gamso.skullkingscore.domain.model.RoundDetails
import com.gamso.skullkingscore.domain.model.RoundPhase
import com.gamso.skullkingscore.domain.model.Trick

fun PlayerEntity.toDomain() = Player(id, uuid, name, color, createdAt, archived)

fun Player.toEntity() = PlayerEntity(id, uuid, name, color, createdAt, archived)

fun encodeExpansion(cards: Set<ExpansionCard>): String = cards.sortedBy { it.ordinal }.joinToString(",") { it.name }

fun decodeExpansion(value: String): Set<ExpansionCard> = value.split(',').filter {
    it.isNotBlank()
}.mapNotNull { name -> ExpansionCard.entries.firstOrNull { it.name == name } }.toSet()

fun encodeCreatures(creatures: Set<SeaMonster>): String = creatures.sortedBy {
    it.ordinal
}.joinToString(",") { it.name }

fun decodeCreatures(value: String): Set<SeaMonster> = value.split(',').filter {
    it.isNotBlank()
}.mapNotNull { name -> SeaMonster.entries.firstOrNull { it.name == name } }.toSet()

fun GameEntity.toDomain(players: List<Player>) = Game(
    id = id,
    uuid = uuid,
    startedAt = startedAt,
    endedAt = endedAt,
    mode = ScoringMode.valueOf(mode),
    expansionCards = decodeExpansion(expansionCards),
    status = GameStatus.valueOf(status),
    players = players,
    firstDealerSeat = firstDealerSeat,
)

fun RoundEntity.toDomain() = Round(id, gameId, number, cardsDealt, isExtra, discardedTricks, RoundPhase.valueOf(phase))

fun Round.toEntity() = RoundEntity(id, gameId, number, cardsDealt, isExtra, discardedTricks, phase.name)

fun BonusEntity.toDomain() = Bonus(
    colored14 = colored14,
    black14 = black14,
    mermaidsByPirate = mermaidsByPirate,
    piratesBySkullKing = piratesBySkullKing,
    skullKingByMermaid = skullKingByMermaid,
    conCaptured = conCaptured,
    eights = eights,
    sevens = sevens,
)

fun Bonus.toEntity() = BonusEntity(
    colored14 = colored14,
    black14 = black14,
    mermaidsByPirate = mermaidsByPirate,
    piratesBySkullKing = piratesBySkullKing,
    skullKingByMermaid = skullKingByMermaid,
    conCaptured = conCaptured,
    eights = eights,
    sevens = sevens,
)

fun PlayerRoundEntity.toDomain() = PlayerRound(
    roundId = roundId,
    playerId = playerId,
    bid = bid,
    finalBid = finalBid,
    tricks = tricks,
    cannonball = cannonball,
    rascalWager = rascalWager,
    bonus = bonus.toDomain(),
    score = score,
)

fun PlayerRound.toEntity() = PlayerRoundEntity(
    roundId = roundId,
    playerId = playerId,
    bid = bid,
    finalBid = finalBid,
    tricks = tricks,
    cannonball = cannonball,
    rascalWager = rascalWager,
    bonus = bonus.toEntity(),
    score = score,
)

/** Value stored while the trick winner is not known yet. */
const val PENDING_WINNER = 0L

fun LootLinkEntity.toDomain() = LootLink(
    id = id,
    roundId = roundId,
    lootPlayerId = lootPlayerId,
    trickWinnerId = trickWinnerId.takeIf { it != PENDING_WINNER },
    createdAt = createdAt,
    cancelledAt = cancelledAt,
    trickNumber = trickNumber,
)

fun DavyJonesCaptureEntity.toDomain() =
    DavyJonesCapture(id, roundId, playerId, decodeCreatures(creatures), createdAt, cancelledAt, trickNumber)

fun TrickEntity.toDomain() = Trick(
    id = id,
    roundId = roundId,
    number = number,
    winnerId = winnerId,
    bonus = bonus.toDomain(),
    createdAt = createdAt,
    rascalWager = rascalWager,
    bidAdjustment = bidAdjustment,
    winningCard = winningCard?.let { name -> WinningCard.entries.firstOrNull { it.name == name } },
    winningPirate = winningPirate?.let { name -> Pirate.entries.firstOrNull { it.name == name } },
)

/** Everything read from the database, before assembly into games. */
data class RawData(
    val games: List<GameEntity>,
    val gamePlayers: List<GamePlayerEntity>,
    val players: List<PlayerEntity>,
    val rounds: List<RoundEntity>,
    val playerRounds: List<PlayerRoundEntity>,
    val loots: List<LootLinkEntity>,
    val captures: List<DavyJonesCaptureEntity>,
    val tricks: List<TrickEntity> = emptyList(),
)

fun RawData.assemble(): List<GameDetails> {
    val playersById = players.associate { it.id to it.toDomain() }
    val seatsByGame = gamePlayers.groupBy { it.gameId }
    val roundsByGame = rounds.groupBy { it.gameId }
    val entriesByRound = playerRounds.groupBy { it.roundId }
    val lootsByRound = loots.groupBy { it.roundId }
    val capturesByRound = captures.groupBy { it.roundId }
    val tricksByRound = tricks.groupBy { it.roundId }
    return games.map { game ->
        val gamePlayersInOrder = seatsByGame[game.id].orEmpty().sortedBy { it.seat }
            .mapNotNull { playersById[it.playerId] }
        val rounds = roundsByGame[game.id].orEmpty().sortedBy { it.number }.map { round ->
            val playerOrder = gamePlayersInOrder.map { it.id }
            RoundDetails(
                round = round.toDomain(),
                entries = entriesByRound[round.id].orEmpty().map { it.toDomain() }
                    .sortedBy { playerOrder.indexOf(it.playerId) },
                loots = lootsByRound[round.id].orEmpty().map { it.toDomain() },
                captures = capturesByRound[round.id].orEmpty().map { it.toDomain() },
                tricks = tricksByRound[round.id].orEmpty().sortedBy { it.number }.map { it.toDomain() },
            )
        }
        GameDetails(game.toDomain(gamePlayersInOrder), rounds)
    }
}
