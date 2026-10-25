# Base de démonstration

`skullking-demo.json` est une sauvegarde au format de l'application (export JSON, version 1) qui contient :

- **12 joueurs** : Alice, Bastien, Camille, Dylan, Emma, Farid, Gaëlle, Hugo, Inès, Jules, Karim, Lola ;
- **40 parties terminées** de janvier 2025 à octobre 2026, avec un nombre de joueurs variable (3 à 8), les trois modes
  de calcul, des tables avec ou sans extension, manche par manche et pli par pli (gagnant, carte et pirate gagnants,
  cartes gagnées, pari de Rascal, ajustement de Harry, Butins, Casier de Davy Jones) ;
- plus de 400 manches, soit environ 2 500 plis saisis : de quoi remplir l'historique (recherche et filtres), les
  courbes, les statistiques et le face-à-face.

Les trente premières parties (janvier 2025 à avril 2026) sont générées de façon déterministe. Les dix suivantes sont
décrites une à une :

| #  | Date       | Joueurs | Mode                 | Extension           | Manches |
| -- | ---------- | ------- | -------------------- | ------------------- | ------- |
| 31 | 2026-05-09 | 5       | Classique            | non                 | 10      |
| 32 | 2026-05-23 | 8       | Classique            | 7, 8, Second        | 10      |
| 33 | 2026-06-13 | 4       | Classique            | non                 | 10      |
| 34 | 2026-07-04 | 5       | Rascal               | non                 | 10      |
| 35 | 2026-07-25 | 3       | Classique            | non                 | 11 (1 en plus) |
| 36 | 2026-08-15 | 6       | Classique            | complète            | 10      |
| 37 | 2026-09-05 | 8       | Rascal + Boulet de canon | partielle (Davy Jones, Raie…) | 10 |
| 38 | 2026-09-19 | 5       | Classique            | Second, Davy Jones, Raie | 12 (2 en plus) |
| 39 | 2026-10-03 | 7       | Classique            | complète            | 10      |
| 40 | 2026-10-17 | 5       | Classique            | non                 | 10      |

La partie 37 se termine sur une égalité à trois pour la première place : pratique pour tester le podium.

## Utiliser la base de démo

L'application doit être installée sur un téléphone ou un émulateur (`./gradlew :app:installDebug`).

1. **Copier le fichier sur l'appareil.**
   - Avec adb :

     ```bash
     adb push demo/skullking-demo.json /sdcard/Download/skullking-demo.json
     ```

   - ou, sans adb : le télécharger / le transférer vers le téléphone (câble USB, Drive, mail…) pour qu'il soit
     accessible depuis le sélecteur de fichiers Android.
2. Dans l'application : **Accueil → Historique → Importer**.
3. Choisir `skullking-demo.json` dans le sélecteur de fichiers.
4. Une fenêtre annonce « 40 nouvelle(s) partie(s), 12 nouveau(x) joueur(s) » : toucher **Importer** (le fichier est toujours importé en entier).

Les données apparaissent aussitôt : historique, statistiques (filtrables par mode), profils dans
**Accueil → Joueurs**, et tableaux de scores détaillés (toucher une case pour voir le calcul).

### Bon à savoir

- L'import **fusionne** par identifiant : relancer l'import ne crée aucun doublon (« 0 nouvelle(s) partie(s) »),
  et les parties déjà présentes sont ignorées. Il peut donc se faire sur une base qui contient déjà vos parties.
- Les joueurs de démo sont des profils ordinaires : vous pouvez les renommer ou les **archiver** (Accueil →
  Joueurs) pour qu'ils ne gênent plus la création de parties.
- Pour repartir de zéro : supprimer les parties une à une dans l'historique, ou effacer les données de l'application
  (`adb shell pm clear com.skullking.score`, ou Réglages Android → Applications → Skull King Score → Stockage).

## Régénérer la base de démo

La base est produite par un générateur déterministe (graines fixes, mêmes résultats à chaque exécution) qui utilise
les vrais calculs de l'application : `app/src/test/java/com/skullking/score/demo/DemoData.kt`. Pour la modifier
(autres joueurs, autres parties…), éditer ce fichier puis lancer :

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

Le test `DemoDataTest` vérifie aussi, à chaque `./gradlew test`, que le fichier commité correspond bien au générateur
(il échoue s'il est périmé), que toutes les manches sont cohérentes (aucune alerte, totaux concordants) et que le
fichier est lisible par l'import de l'application.
