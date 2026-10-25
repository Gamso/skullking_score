package com.skullking.score.demo

import com.skullking.score.core.Bonus
import com.skullking.score.core.DeckRules
import com.skullking.score.core.ExpansionCard
import com.skullking.score.core.Pirate
import com.skullking.score.core.ScoringMode
import com.skullking.score.core.SeaMonster
import com.skullking.score.core.WinningCard
import com.skullking.score.data.backup.BackupCodec
import com.skullking.score.domain.model.DavyJonesCapture
import com.skullking.score.domain.model.Game
import com.skullking.score.domain.model.GameDetails
import com.skullking.score.domain.model.GameStatus
import com.skullking.score.domain.model.LootLink
import com.skullking.score.domain.model.Player
import com.skullking.score.domain.model.PlayerPalette
import com.skullking.score.domain.model.PlayerRound
import com.skullking.score.domain.model.Round
import com.skullking.score.domain.model.RoundDetails
import com.skullking.score.domain.model.RoundPhase
import com.skullking.score.domain.model.Trick
import com.skullking.score.domain.scoring.GameScoreCalculator
import com.skullking.score.domain.scoring.TrickAggregator
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Base de démonstration : 12 joueurs et 40 parties terminées sur plus d'un an, avec un nombre de joueurs, des modes
 * et des extensions variables. Tout est simulé de façon déterministe (graines fixes) avec les vrais calculs de
 * l'application, donc les scores sont cohérents, et le fichier produit est identique à chaque génération.
 */
object DemoData {
    private const val EXPORTED_AT = 1_792_000_000_000L
    private const val GAME_DURATION_MS = 75 * 60_000L
    private const val ROUND_GAP_MS = 5 * 60_000L
    private const val GENERATED_SEED = 4_242
    private const val GENERATED_COUNT = 30
    private const val GENERATED_STEP_DAYS = 14L

    private val NAMES =
        listOf(
            "Alice", "Bastien", "Camille", "Dylan", "Emma", "Farid",
            "Gaëlle", "Hugo", "Inès", "Jules", "Karim", "Lola",
        )

    /** Habileté relative de chaque joueur : elle influence les plis gagnés, donc les victoires et les statistiques. */
    private val SKILL = listOf(1.25, 0.9, 1.1, 0.8, 1.0, 1.15, 0.85, 1.0, 0.95, 1.05, 0.9, 1.2)

    private val ALL_CARDS = ExpansionCard.entries.toSet()

    private data class Spec(
        val date: LocalDateTime,
        val seats: List<Int>,
        val mode: ScoringMode,
        val expansion: Set<ExpansionCard>,
        val extraRounds: Int = 0,
    )

    /** Les dix parties de départ, de mai à octobre 2026 (voir `demo/README.md`). */
    private val CORE_SPECS = listOf(
        Spec(LocalDateTime.of(2026, 5, 9, 20, 30), listOf(0, 1, 2, 3, 4), ScoringMode.CLASSIC, emptySet()),
        Spec(
            LocalDateTime.of(2026, 5, 23, 21, 0),
            listOf(0, 1, 2, 3, 4, 5, 6, 7),
            ScoringMode.CLASSIC,
            setOf(ExpansionCard.SEVENS, ExpansionCard.EIGHTS, ExpansionCard.FIRST_MATE_CON),
        ),
        Spec(LocalDateTime.of(2026, 6, 13, 19, 45), listOf(2, 3, 5, 8), ScoringMode.CLASSIC, emptySet()),
        Spec(LocalDateTime.of(2026, 7, 4, 20, 15), listOf(0, 2, 4, 6, 9), ScoringMode.RASCAL, emptySet()),
        Spec(LocalDateTime.of(2026, 7, 25, 18, 30), listOf(1, 5, 7), ScoringMode.CLASSIC, emptySet(), extraRounds = 1),
        Spec(LocalDateTime.of(2026, 8, 15, 20, 0), listOf(0, 1, 3, 5, 7, 9), ScoringMode.CLASSIC, ALL_CARDS),
        Spec(
            LocalDateTime.of(2026, 9, 5, 21, 15),
            listOf(0, 1, 2, 3, 4, 5, 6, 8),
            ScoringMode.CANNONBALL,
            setOf(
                ExpansionCard.SEVENS,
                ExpansionCard.EIGHTS,
                ExpansionCard.MARY_THORNE,
                ExpansionCard.DAVY_JONES_CHEST,
                ExpansionCard.SPOTTED_RAY,
            ),
        ),
        Spec(
            LocalDateTime.of(2026, 9, 19, 20, 0),
            listOf(2, 3, 4, 7, 9),
            ScoringMode.CLASSIC,
            setOf(ExpansionCard.FIRST_MATE_CON, ExpansionCard.DAVY_JONES_CHEST, ExpansionCard.SPOTTED_RAY),
            extraRounds = 2,
        ),
        Spec(LocalDateTime.of(2026, 10, 3, 19, 30), listOf(0, 1, 2, 4, 5, 8, 9), ScoringMode.CLASSIC, ALL_CARDS),
        Spec(LocalDateTime.of(2026, 10, 17, 20, 45), listOf(0, 3, 5, 6, 7), ScoringMode.CLASSIC, emptySet()),
    )

    private val EXPANSIONS: List<Set<ExpansionCard>> = listOf(
        emptySet(),
        emptySet(),
        emptySet(),
        ALL_CARDS,
        setOf(ExpansionCard.SEVENS, ExpansionCard.EIGHTS),
        setOf(ExpansionCard.FIRST_MATE_CON, ExpansionCard.DAVY_JONES_CHEST, ExpansionCard.SPOTTED_RAY),
        setOf(ExpansionCard.MARY_THORNE, ExpansionCard.WILD_15),
    )

    private val TABLE_SIZES = listOf(3, 4, 4, 5, 5, 5, 6, 6, 7, 8)

    /** Trente parties de plus, d'ici avril 2026 : de quoi remplir l'historique, les courbes et les statistiques. */
    private val GENERATED_SPECS: List<Spec> = run {
        val rng = Random(GENERATED_SEED)
        (0 until GENERATED_COUNT).map { i ->
            val date = LocalDateTime.of(2025, 1, 11, 20, 0).plusDays(i * GENERATED_STEP_DAYS + rng.nextInt(0, 4))
            val seats = NAMES.indices.shuffled(rng).take(TABLE_SIZES[rng.nextInt(TABLE_SIZES.size)])
            val mode = when (rng.nextInt(10)) {
                in 0..5 -> ScoringMode.CLASSIC
                in 6..7 -> ScoringMode.RASCAL
                else -> ScoringMode.CANNONBALL
            }
            Spec(date, seats, mode, EXPANSIONS[rng.nextInt(EXPANSIONS.size)], if (rng.nextInt(8) == 0) 1 else 0)
        }
    }

    private val SPECS = GENERATED_SPECS + CORE_SPECS

    val players: List<Player> = NAMES.mapIndexed { index, name ->
        Player(
            id = index + 1L,
            uuid = "demo-player-%02d".format(index + 1),
            name = name,
            color = PlayerPalette.colors[index],
            createdAt = LocalDateTime.of(2026, 4, 20, 18, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
    }

    fun generate(): List<GameDetails> {
        val ids = Ids()
        return SPECS.mapIndexed { index, spec -> simulateGame(index, spec, ids) }
    }

    /** Contenu du fichier `demo/skullking-demo.json`. */
    fun json(): String = BackupCodec.encode(generate(), EXPORTED_AT) + "\n"

    private class Ids {
        private var round = 0L
        private var trick = 0L
        private var event = 0L
        fun round() = ++round
        fun trick() = ++trick
        fun event() = ++event
    }

    private fun simulateGame(index: Int, spec: Spec, ids: Ids): GameDetails {
        val rng = Random(20_260_000 + index)
        val seated = spec.seats.map { players[it] }
        val startedAt = spec.date.toInstant(ZoneOffset.UTC).toEpochMilli()
        val game = Game(
            id = index + 1L,
            uuid = "demo-game-%02d".format(index + 1),
            startedAt = startedAt,
            endedAt = startedAt + GAME_DURATION_MS,
            mode = spec.mode,
            expansionCards = spec.expansion,
            status = GameStatus.FINISHED,
            davyJonesRequiresSuccess = true,
            players = seated,
            firstDealerSeat = index % seated.size,
        )
        var previous: Int? = null
        val rounds = (1..DeckRules.PLANNED_ROUNDS + spec.extraRounds).map { number ->
            val cards = DeckRules.suggestedCards(number, previous, seated.size, spec.expansion)
            previous = cards
            simulateRound(game, number, cards, rng, ids, startedAt + number * ROUND_GAP_MS)
        }
        val details = GameDetails(game, rounds)
        // Les scores sont mis en cache comme le fait l'application.
        val scored = GameScoreCalculator.calculate(details)
        return details.copy(
            rounds = details.rounds.zip(scored.rounds) { round, result ->
                round.copy(
                    entries = round.entries.map {
                        it.copy(score = result.breakdowns.getValue(it.playerId).total)
                    },
                )
            },
        )
    }

    /** Cartes encore disponibles dans la manche : une carte n'existe qu'en nombre limité. */
    private class Budget(expansion: Set<ExpansionCard>) {
        var colored14 = DeckRules.MAX_COLORED_14
        var black14 = DeckRules.MAX_BLACK_14
        var skullKingByMermaid = DeckRules.MAX_SKULL_KING
        var mermaids = DeckRules.MAX_MERMAIDS
        var pirates = DeckRules.maxPirates(expansion)
        var con = if (ExpansionCard.FIRST_MATE_CON in expansion) DeckRules.MAX_CON else 0
        var sevens = if (ExpansionCard.SEVENS in expansion) DeckRules.MAX_SEVENS else 0
        var eights = if (ExpansionCard.EIGHTS in expansion) DeckRules.MAX_EIGHTS else 0
    }

    private fun gauss(rng: Random): Double = rng.nextDouble() + rng.nextDouble() + rng.nextDouble() - 1.5

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun simulateRound(game: Game, number: Int, cards: Int, rng: Random, ids: Ids, time: Long): RoundDetails {
        val seated = game.players
        val roundId = ids.round()
        val skill = seated.map { SKILL[(it.id - 1).toInt()] }

        // Annonces : proches de la part « attendue » de chaque joueur, avec du bruit.
        val bids = seated.mapIndexed { i, _ ->
            val expected = cards * skill[i] / skill.sum()
            (expected + gauss(rng) * 1.6).roundToInt().coerceIn(0, cards)
        }

        val budget = Budget(game.expansionCards)
        val pirates = Pirate.entries.filter { it.requires == null || it.requires in game.expansionCards }
        val tricks = (1..cards).map { n ->
            val winner = if (rng.nextDouble() < 0.03) {
                null // pli détruit par le Kraken
            } else {
                val weights = seated.indices.map { skill[it] * (0.6 + bids[it]) }
                var pick = rng.nextDouble() * weights.sum()
                seated[weights.indices.first { i -> (pick - weights[i]).also { pick = it } <= 0 }]
            }
            if (winner == null) {
                Trick(ids.trick(), roundId, n, null, Bonus.NONE, time + n * 1_000L)
            } else {
                val card = pickCard(rng, game.expansionCards)
                val pirate = if (card == WinningCard.PIRATE) pirates.random(rng) else null
                var colored14 = 0
                var black14 = 0
                var mermaidsByPirate = 0
                var piratesBySkullKing = 0
                var skullKingByMermaid = 0
                var con = 0
                var eights = 0
                var sevens = 0
                when (card) {
                    WinningCard.SKULL_KING -> {
                        piratesBySkullKing = minOf(rng.nextInt(3), budget.pirates)
                        budget.pirates -= piratesBySkullKing
                    }

                    WinningCard.MERMAID -> if (budget.skullKingByMermaid > 0 && rng.nextDouble() < 0.35) {
                        skullKingByMermaid = 1
                        budget.skullKingByMermaid--
                    }

                    WinningCard.PIRATE -> if (budget.mermaids > 0 && rng.nextDouble() < 0.35) {
                        mermaidsByPirate = 1
                        budget.mermaids--
                    }

                    else -> Unit
                }
                val canCaptureCon = card == WinningCard.SKULL_KING || card == WinningCard.MERMAID
                if (canCaptureCon && budget.con > 0 && rng.nextDouble() < 0.25) {
                    con = 1
                    budget.con--
                }
                if (budget.colored14 > 0 && rng.nextDouble() < 0.10) {
                    colored14 = 1
                    budget.colored14--
                }
                if (budget.black14 > 0 && rng.nextDouble() < 0.04) {
                    black14 = 1
                    budget.black14--
                }
                if (budget.eights > 0 && rng.nextDouble() < 0.07) {
                    eights = 1
                    budget.eights--
                }
                if (budget.sevens > 0 && rng.nextDouble() < 0.07) {
                    sevens = 1
                    budget.sevens--
                }
                Trick(
                    id = ids.trick(),
                    roundId = roundId,
                    number = n,
                    winnerId = winner.id,
                    bonus = Bonus(
                        colored14 = colored14,
                        black14 = black14,
                        mermaidsByPirate = mermaidsByPirate,
                        piratesBySkullKing = piratesBySkullKing,
                        skullKingByMermaid = skullKingByMermaid,
                        conCaptured = con,
                        eights = eights,
                        sevens = sevens,
                    ),
                    createdAt = time + n * 1_000L,
                    rascalWager = if (pirate == Pirate.RASCAL) listOf(0, 10, 20).random(rng) else 0,
                    bidAdjustment = if (pirate == Pirate.HARRY &&
                        rng.nextDouble() < 0.6
                    ) {
                        listOf(-1, 1).random(rng)
                    } else {
                        0
                    },
                    winningCard = card,
                    winningPirate = pirate,
                )
            }
        }

        // Les résultats de la manche se déduisent des plis, exactement comme dans l'application.
        val totals = TrickAggregator.aggregate(tricks)
        val entries = seated.mapIndexed { i, player ->
            val mine = totals.perPlayer[player.id]
            PlayerRound(
                roundId = roundId,
                playerId = player.id,
                bid = bids[i],
                finalBid = mine?.bidAdjustment?.takeIf { it != 0 }?.let { (bids[i] + it).coerceIn(0, cards) },
                tricks = mine?.tricks ?: 0,
                cannonball = game.mode == ScoringMode.CANNONBALL && rng.nextDouble() < 0.25,
                rascalWager = mine?.rascalWager ?: 0,
                bonus = mine?.bonus ?: Bonus.NONE,
                score = 0,
            )
        }

        val wonTricks = tricks.filter { it.winnerId != null }
        val loots = if (wonTricks.isNotEmpty() && rng.nextDouble() < 0.28) {
            List(1 + if (rng.nextDouble() < 0.15) 1 else 0) {
                val trick = wonTricks.random(rng)
                LootLink(
                    id = ids.event(),
                    roundId = roundId,
                    lootPlayerId = seated.random(rng).id,
                    trickWinnerId = trick.winnerId,
                    createdAt = time + trick.number * 1_000L + 500,
                    cancelledAt = null,
                    trickNumber = trick.number,
                )
            }
        } else {
            emptyList()
        }
        val captures = if (ExpansionCard.DAVY_JONES_CHEST in game.expansionCards && rng.nextDouble() < 0.2) {
            val available = SeaMonster.entries.filter {
                it != SeaMonster.SPOTTED_RAY || ExpansionCard.SPOTTED_RAY in game.expansionCards
            }
            listOf(
                DavyJonesCapture(
                    id = ids.event(),
                    roundId = roundId,
                    playerId = seated.random(rng).id,
                    creatures = available.shuffled(rng).take(1 + rng.nextInt(2)).toSet(),
                    createdAt = time + 900,
                    cancelledAt = null,
                    trickNumber = tricks.random(rng).number,
                ),
            )
        } else {
            emptyList()
        }

        return RoundDetails(
            round = Round(
                id = roundId,
                gameId = game.id,
                number = number,
                cardsDealt = cards,
                isExtra = number > DeckRules.PLANNED_ROUNDS,
                discardedTricks = totals.destroyed,
                phase = RoundPhase.SCORED,
            ),
            entries = entries,
            loots = loots,
            captures = captures,
            tricks = tricks,
        )
    }

    private fun pickCard(rng: Random, expansion: Set<ExpansionCard>): WinningCard {
        val weights = mapOf(
            WinningCard.MERMAID to 10,
            WinningCard.SKULL_KING to 8,
            WinningCard.FIRST_MATE to if (ExpansionCard.FIRST_MATE_CON in expansion) 4 else 0,
            WinningCard.PIRATE to 28,
            WinningCard.OTHER to 50,
        )
        var pick = rng.nextInt(weights.values.sum())
        return weights.entries.first { (_, weight) -> (pick - weight).also { pick = it } < 0 }.key
    }
}
