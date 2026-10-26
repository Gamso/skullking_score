# Skull King Score

[![CI](https://github.com/Gamso/skullking_score/actions/workflows/ci.yml/badge.svg)](https://github.com/Gamso/skullking_score/actions/workflows/ci.yml)

An Android score pad for the **Skull King** card game: enter each player's bid and tricks, and the app computes the
points (bonuses included) for the chosen scoring mode, replacing the paper score sheet.

<p align="center">
  <img src="docs/screenshots/home.png" width="19%" alt="Home screen" />
  <img src="docs/screenshots/new-game.png" width="19%" alt="New game: players in table order and dealer" />
  <img src="docs/screenshots/scoreboard.png" width="19%" alt="Scoreboard" />
  <img src="docs/screenshots/score-curve.png" width="19%" alt="Score curve of a game" />
  <img src="docs/screenshots/statistics.png" width="19%" alt="Player statistics" />
</p>

## Features

- 100% offline, a single phone on the table held by the "scribe"
- Base game (White Whale, Kraken, Loots, Tigress) and the **2026 expansion** (First Mate, Mary Thorne, 7/8/0-14,
  Wild 15, Spotted Ray, Davy Jones' Locker, Walk the Plank, Last Salvo)
- 2 to 8 players (9 with the expansion), flexible rounds (editable number of cards, extra rounds)
- Scoring modes: classic, Rascal, and Rascal + Cannonball (15 x N points)
- Live trick entry (winner, card, captured cards) that pre-fills the results, with undo / redo
- Live events (Loot, Davy Jones' Locker) attached to the trick, cancellable log, non-blocking consistency warnings
- Dealer tracking: the table order is the list order, and the dealer moves on every round
- Game history with search and filters, expandable cards
- Statistics per player and scoring mode: records, trend chart, bid success by round size, favorite card,
  head-to-head between two players, filters by number of players, expansion and players present
- Card statistics: tricks won by card and pirate, bid success when a card wins, when cards win in a round, captured
  bonuses, Rascal wagers and Harry adjustments, Loots, Davy Jones' Locker, Kraken, expansion versus base game
- Sharing as images: podium, scoreboard, score curve, highlights, and per-player statistics
- Backup: JSON / CSV export, import with merge of same-name profiles, optional automatic backup to a folder
- English and French (follows the Android language), light / dark theme, tablet and landscape layouts, TalkBack support
- Built-in help with search

<p align="center">
  <img src="docs/screenshots/history.png" width="24%" alt="History with an expanded game" />
  <img src="docs/screenshots/card-statistics.png" width="24%" alt="Card statistics" />
  <img src="docs/screenshots/statistics-image.png" width="24%" alt="Shareable statistics image of a player" />
</p>

## Architecture

| Module         | Role                                                                                         |
| -------------- | -------------------------------------------------------------------------------------------- |
| `core-scoring` | Rules, modes, `RoundScorer`, validation. Pure Kotlin, no Android dependency, tested with JUnit |
| `app`          | Compose application, organized in `domain` / `data` / `ui` layers                            |

Inside `app`:

- `domain`: models, repository interfaces, use cases, game scoring, statistics, undo history, CSV export
- `data`: Room 3 (entities, DAOs), repositories, JSON backup, automatic backup, settings
- `ui`: Jetpack Compose screens (Material 3) with their ViewModels, Compose navigation, image rendering for sharing
- `di`: Hilt modules

The score is never typed in: it is recomputed from the entries (bid, tricks, captured cards, events) and cached for
display. Correcting a past round recomputes the whole game.

Stack: Kotlin 2.4, Jetpack Compose, Hilt, Room 3, kotlinx.serialization. `minSdk` 26.

## Build

Requirements: JDK 17 or later (Android Studio's JBR works) and the Android SDK (`compileSdk` 37).

```bash
./gradlew :app:assembleDebug      # debug APK: app/build/outputs/apk/debug/
./gradlew :app:installDebug       # install on a device or an emulator
```

## Quality

```bash
./gradlew ktlintCheck             # code style (Ktlint); ktlintFormat fixes it automatically
./gradlew detekt                  # static analysis (Detekt), configured in config/detekt/detekt.yml
./gradlew :app:lintDebug          # Android Lint
./gradlew :core-scoring:test :app:testDebugUnitTest

# Instrumented tests (emulator or connected phone): data layer, import / export, end-to-end flows
./gradlew :app:connectedDebugAndroidTest
```

Style rules live in [`.editorconfig`](.editorconfig). Detekt 2 is used in its alpha version, the only one compatible
with Kotlin 2.4 at the moment.

### Code coverage

JaCoCo reports are generated for the unit tests and for the instrumented tests:

```bash
./gradlew :core-scoring:jacocoTestReport                 # core-scoring/build/reports/jacoco/test/
./gradlew :app:createDebugUnitTestCoverageReport         # app/build/reports/coverage/test/debug/
./gradlew :app:createDebugAndroidTestCoverageReport      # needs an emulator or a phone
```

## Demo data

[`demo/skullking-demo.json`](demo/README.md) contains 12 players and 40 finished games (3 to 8 players, with or
without the expansion, in the three modes). Import it from **History → Import**. The procedure and how to regenerate
it are described in [`demo/README.md`](demo/README.md).

## Continuous integration

| Workflow                                                           | Trigger         | Content                                                                         |
| ------------------------------------------------------------------ | --------------- | ------------------------------------------------------------------------------- |
| [`ci.yml`](.github/workflows/ci.yml)                               | push `main`, PR | Ktlint, Detekt, Android Lint, unit tests with coverage, instrumented tests (emulator, cached AVD), test reports as checks, then debug APK as an artifact |
| [`dependency-review.yml`](.github/workflows/dependency-review.yml) | PR              | Flags newly added dependencies that have known vulnerabilities                  |
| [`release.yml`](.github/workflows/release.yml)                     | tag `v*`        | Checks, release APK + AAB, GitHub release publication                          |

Third-party actions are pinned to a commit SHA; Dependabot keeps them up to date.
The instrumented-tests job can be turned off by setting the repository variable `SKIP_INSTRUMENTED_TESTS` to `true`.

## License

[MIT](LICENSE)
