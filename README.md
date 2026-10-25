# Skull King Score

Application Android de comptage pour le jeu de cartes **Skull King** : on saisit les annonces et les plis de chaque
joueur, l'application calcule les points (bonus compris) selon le mode choisi et remplace le carnet de score papier.

- 100 % hors ligne, un seul téléphone posé sur la table, tenu par le « scribe »
- Jeu de base (Baleine Blanche, Kraken, Butins, Tigresse) et **extension 2026** (Second, Mary Thorne, 7/8/0-14,
  Wild 15, Raie tachetée, Casier de Davy Jones, Supplice de la planche, Dernière salve)
- 2 à 8 joueurs (9 avec l'extension), manches flexibles (nombre de cartes modifiable, manches supplémentaires)
- Modes de calcul : classique, Rascal et Rascal + Boulet de canon (valeur 15 × N à confirmer sur la règle papier)
- Saisie des plis en direct (gagnant et cartes capturées), pré-remplissage des résultats
- Événements en direct (Butin, Casier de Davy Jones) rattachés au pli, journal annulable, alertes de cohérence non bloquantes
- Français et anglais (langue d’Android), thème clair / sombre
- Profils de joueurs, historique, statistiques, export CSV / JSON et import de sauvegarde, aide intégrée

La spécification complète est dans [`spec/`](spec).

## Architecture

| Module         | Rôle                                                                                         |
| -------------- | -------------------------------------------------------------------------------------------- |
| `core-scoring` | Règles, modes, `RoundScorer`, validation. Kotlin pur, sans dépendance Android, testé en JUnit |
| `app`          | Application Compose, organisée en couches `domain` / `data` / `ui`                           |

Dans `app` :

- `domain` : modèles, interfaces de dépôts, cas d'usage, calcul de partie, statistiques, export CSV
- `data` : Room 3 (entités, DAO), dépôts, sauvegarde JSON, DataStore
- `ui` : écrans Jetpack Compose (Material 3) + ViewModels, navigation Compose
- `di` : modules Hilt

Le score n'est jamais saisi : il est recalculé à partir des saisies (annonce, plis, cartes capturées, événements),
puis mis en cache pour l'affichage. Corriger une manche passée relance le calcul de toute la partie.

Stack : Kotlin 2.4, Jetpack Compose, Hilt, Room 3, kotlinx.serialization, DataStore. `minSdk` 26.

## Construire

Prérequis : JDK 17 ou plus (le JBR d'Android Studio convient) et le SDK Android (`compileSdk` 37).

```bash
./gradlew :app:assembleDebug      # APK debug : app/build/outputs/apk/debug/
./gradlew :app:installDebug       # installation sur un appareil ou un émulateur
```

## Qualité

```bash
./gradlew ktlintCheck             # style (Ktlint) ; ktlintFormat corrige automatiquement
./gradlew detekt                  # analyse statique (Detekt), config dans config/detekt/detekt.yml
./gradlew :app:lintDebug          # Android Lint
./gradlew :core-scoring:test :app:testDebugUnitTest

# Tests instrumentés (émulateur ou téléphone connecté)
./gradlew :app:connectedDebugAndroidTest
```

Les règles de style sont dans [`.editorconfig`](.editorconfig). Detekt 2 est utilisé en version alpha, seule
version compatible avec Kotlin 2.4 à ce jour.

## Données de démonstration

[`demo/skullking-demo.json`](demo/README.md) contient 10 joueurs et 10 parties jouées (de 3 à 8 joueurs, avec ou
sans extension, dans les trois modes). Il s'importe depuis **Historique → Importer** ; la procédure et la manière de
le régénérer sont décrites dans [`demo/README.md`](demo/README.md).

## Intégration continue

| Workflow                                            | Déclencheur       | Contenu                                                                |
| --------------------------------------------------- | ----------------- | ---------------------------------------------------------------------- |
| [`ci.yml`](.github/workflows/ci.yml)               | push `main`, PR   | Ktlint, Detekt, Android Lint, tests unitaires, tests instrumentés (émulateur), puis APK debug en artefact |
| [`release.yml`](.github/workflows/release.yml)     | tag `v*`          | Vérifications, APK + AAB de release, publication d'une release GitHub  |

## Publier une release

1. Créer un keystore de signature (à conserver précieusement, hors du dépôt) :

   ```bash
   keytool -genkeypair -v -keystore release.jks -alias skullking -keyalg RSA -keysize 4096 -validity 10000
   base64 -w0 release.jks   # valeur du secret SIGNING_KEYSTORE_BASE64
   ```

2. Dans *Settings → Secrets and variables → Actions*, ajouter :
   `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
3. Pousser un tag : `git tag v1.0.0 && git push origin v1.0.0`.

La version (`versionName`) et le `versionCode` (`major × 10000 + minor × 100 + patch`) sont déduits du tag.
Sans les secrets de signature, le workflow produit tout de même un APK **non signé** (suffixe `-unsigned`).
Pour signer en local : exporter `SIGNING_KEYSTORE_FILE`, `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS` et
`SIGNING_KEY_PASSWORD`, puis lancer `./gradlew :app:assembleRelease`.

## Feuille de route

La suite (V2) est détaillée dans [`ROADMAP.md`](ROADMAP.md).

## Licence

[MIT](LICENSE)
