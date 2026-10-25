# Demo database

`skullking-demo.json` is a backup in the app's format (JSON export, version 1) that contains:

- **12 players**: Alice, Bastien, Camille, Dylan, Emma, Farid, Gaëlle, Hugo, Inès, Jules, Karim, Lola;
- **40 finished games** from January 2025 to October 2026, with a varying number of players (3 to 8), the three
  scoring modes, tables with or without the expansion, round by round and trick by trick (winner, winning card and
  pirate, captured cards, Rascal wager, Harry adjustment, Loots, Davy Jones' Locker);
- more than 400 rounds, i.e. about 2,500 tricks: enough to fill the history (search and filters), the charts, the
  statistics and the head-to-head.

The first thirty games (January 2025 to April 2026) are generated deterministically. The next ten are described one
by one:

| #  | Date       | Players | Mode                     | Expansion                      | Rounds          |
| -- | ---------- | ------- | ------------------------ | ------------------------------ | --------------- |
| 31 | 2026-05-09 | 5       | Classic                  | no                             | 10              |
| 32 | 2026-05-23 | 8       | Classic                  | 7, 8, First Mate               | 10              |
| 33 | 2026-06-13 | 4       | Classic                  | no                             | 10              |
| 34 | 2026-07-04 | 5       | Rascal                   | no                             | 10              |
| 35 | 2026-07-25 | 3       | Classic                  | no                             | 11 (1 extra)    |
| 36 | 2026-08-15 | 6       | Classic                  | full                           | 10              |
| 37 | 2026-09-05 | 8       | Rascal + Cannonball      | partial (Davy Jones, Ray…)     | 10              |
| 38 | 2026-09-19 | 5       | Classic                  | First Mate, Davy Jones, Ray    | 12 (2 extra)    |
| 39 | 2026-10-03 | 7       | Classic                  | full                           | 10              |
| 40 | 2026-10-17 | 5       | Classic                  | no                             | 10              |

Game 37 ends in a three-way tie for first place: handy to test the podium.

## Using the demo database

The app must be installed on a phone or an emulator (`./gradlew :app:installDebug`).

1. **Copy the file to the device.**
   - With adb:

     ```bash
     adb push demo/skullking-demo.json /sdcard/Download/skullking-demo.json
     ```

   - or, without adb: download / transfer it to the phone (USB cable, Drive, email…) so that it is reachable from
     the Android file picker.
2. In the app: **Home → History → Import**.
3. Pick `skullking-demo.json` in the file picker.
4. A dialog announces "40 new game(s), 12 new player(s)": tap **Import** (the whole file is always imported).

The data appears right away: history, statistics (filterable by mode), profiles in **Home → Players**, and detailed
scoreboards (tap a cell to see the calculation).

### Good to know

- The import **merges** by identifier: importing again creates no duplicates ("0 new game(s)"), and games already
  present are skipped. It can therefore be done on a database that already contains your own games.
- The demo players are ordinary profiles: you can rename them or **archive** them (Home → Players) so that they no
  longer get in the way when creating games.
- To start from scratch: delete the games one by one in the history, or clear the app data
  (`adb shell pm clear com.skullking.score`, or Android Settings → Apps → Skull King Score → Storage).

## Regenerating the demo database

The database is produced by a deterministic generator (fixed seeds, same results on every run) that uses the app's
real calculations: `app/src/test/java/com/skullking/score/demo/DemoData.kt`. To change it (other players, other
games…), edit that file then run:

```bash
# Linux / macOS / Git Bash
DEMO_OUT=../demo/skullking-demo.json ./gradlew :app:testDebugUnitTest --tests "*DemoDataTest*"
```

```powershell
# PowerShell
$env:DEMO_OUT = "../demo/skullking-demo.json"
.\gradlew.bat :app:testDebugUnitTest --tests "*DemoDataTest*"
Remove-Item Env:DEMO_OUT
```

`DemoDataTest` also checks, on every `./gradlew test`, that the committed file matches the generator (it fails if it
is stale), that all rounds are consistent (no warning, matching totals) and that the file can be read by the app's
import.
