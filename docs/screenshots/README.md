# Screenshots

The screenshots of the [README](../../README.md) are taken on an emulator (1080 × 2400, Android 15 or later), in
English, in the light theme, with the [demo data](../../demo/README.md). Retake them whenever a screen they show
changes, so that the README always shows the current app.

## Prepare the emulator

1. Build and install the debug APK, then set the language and the theme:

   ```bash
   ./gradlew :app:assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell cmd locale set-app-locales com.skullking.score --locales en
   adb shell cmd uimode night no
   ```

   The instrumented tests uninstall the app when they finish: install it again after running them.

2. Freeze the status bar with the system demo mode (08:30, full Wi-Fi and battery):

   ```bash
   adb shell settings put global sysui_demo_allowed 1
   adb shell am broadcast -a com.android.systemui.demo -e command enter
   adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0830
   adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
   adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e fully true -e mobile hide
   adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
   ```

   Leave it with `adb shell am broadcast -a com.android.systemui.demo -e command exit`.

3. Import the demo data: `adb push demo/skullking-demo.json /sdcard/Download/`, then in the app
   **History → Import** and pick `skullking-demo.json` (40 games, 12 players).

## Take a screenshot

```bash
python scripts/screenshot.py <name>
```

It saves `docs/screenshots/<name>.png` and covers the notification icons that the emulator keeps in the status bar
(the demo mode does not hide them). It needs `adb` and Pillow (`pip install pillow`).

## The screenshots

A game in progress is needed first: **New game** with Alice, Bastien, Camille, Dylan and Emma (in this order),
classic scoring, expansion enabled with every card. Play rounds 1 to 4 with any bids and winners, then:

| File                   | Screen                                                                                       |
|------------------------|----------------------------------------------------------------------------------------------|
| `new-game.png`         | New game, scrolled to the five players in seating order (taken before starting the game)      |
| `expansion.png`        | New game, scrolled to the expansion cards, all enabled (taken before starting the game)       |
| `bids.png`             | Round 5 bids: Alice 2, Bastien 1, Camille 0, Dylan 1, Emma 2                                  |
| `trick-entry.png`      | Trick 4 of round 5: Bastien wins with a mermaid, one suit 14 and the Skull King captured      |
| `play.png`             | Round state dialog (top bar icon of the trick entry), after tricks 1 to 3: Alice with the Skull King and a pirate captured, Emma with Juanita, Dylan with another card |
| `scoreboard.png`       | Ranking tab of the scoreboard, once round 5 is confirmed                                      |
| `score-curve.png`      | Curve tab, round 3 tapped                                                                     |
| `home.png`             | Home screen with the game in progress                                                         |
| `history.png`          | History, game of Oct 17, 2026 expanded                                                        |
| `podium.png`           | That game opened: its podium                                                                  |
| `statistics.png`       | Statistics, Players tab, classic mode                                                         |
| `card-statistics.png`  | Statistics, Cards tab, classic mode                                                           |
| `head-to-head.png`     | Head-to-head Farid against Hugo, scrolled to the result                                       |
| `statistics-image.png` | The image shared from Farid's statistics card, see below                                      |
| `help.png`             | Help, Cards tab                                                                               |

`statistics-image.png` is the generated image itself, not a screenshot: tap the share icon of Farid's card, close
the share sheet, then copy the image from the app's cache:

```bash
adb exec-out run-as com.skullking.score cat cache/exports/skullking-stats-7.png > docs/screenshots/statistics-image.png
```

The number in the file name is the player's id: list `cache/exports` with `adb shell run-as com.skullking.score ls
cache/exports` if it differs.
