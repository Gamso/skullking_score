# Publishing on F-Droid

F-Droid builds the app itself from the source code of a tag and signs it with its own key. The store listing (title,
descriptions, icon, screenshots, changelogs) is read from [`fastlane/metadata/android`](../../fastlane/metadata/android).

## What is already in place

- No proprietary dependency (no Google Play Services, Firebase or analytics), MIT license
- `dependenciesInfo` disabled in `app/build.gradle.kts`: AGP would otherwise add a dependency block encrypted with a
  Google key, which F-Droid rejects
- An unsigned release build works without the signing secrets
- The default `versionCode` / `versionName` in `app/build.gradle.kts` match every release tag: F-Droid builds without
  the `-PversionName` / `-PversionCode` of the CI and reads them to detect new versions
- Fastlane metadata in English (fallback for every language), French, German, Spanish, Italian and Portuguese; the
  screenshots live in `en-US` only and are shown for every language

## On every release

1. Bump the default version in `app/build.gradle.kts` (already the habit), then tag `vX.Y.Z`
2. Add `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` for every language, 500 characters max, before the
   tag

F-Droid then picks up the new tag on its own (`AutoUpdateMode: Version`), usually within a few days.

## First submission

1. Release `v1.3.2` (the first tag that contains the F-Droid changes)
2. Fork [fdroiddata](https://gitlab.com/fdroid/fdroiddata) on GitLab, create a branch, and copy
   [`com.skullking.score.yml`](com.skullking.score.yml) to `metadata/com.skullking.score.yml`
3. Optional, with the [`fdroidserver`](https://f-droid.org/docs/Installing_the_Server_and_Repo_Tools/) tools:
   `fdroid readmeta`, `fdroid rewritemeta com.skullking.score`, `fdroid lint com.skullking.score`,
   `fdroid build -v -l com.skullking.score`
4. Open a merge request on fdroiddata with the "App inclusion" template, and answer the reviewers

The recipe deletes `gradle/gradle-daemon-jvm.properties` before the build: it would otherwise make Gradle download a
JetBrains JDK 25 from foojay, while the F-Droid build server provides its own JDK.
