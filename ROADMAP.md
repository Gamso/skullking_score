# Roadmap V2

> **Avancement : les jalons 1 à 4 sont réalisés.** Reste le jalon 0 (publication), à traiter en dernier. Les écarts avec les
> descriptions ci-dessous sont listés à la fin de la spécification V2.

État de départ : V1.2 livrée (modes Classique / Rascal / Rascal + Boulet de canon, extension 2026, manches flexibles,
saisie des plis en direct, historique avec recherche, statistiques, export / import, français et anglais, thème
sombre). La V2 reprend le périmètre prévu dans la spécification (partage en image, tablette en paysage, statistiques
plus riches) et ajoute ce que l'usage a fait apparaître. Rien ici n'est engagé : c'est une proposition à ordonner.

Le détail fonctionnel de chaque ligne (parcours, règles, critères d'acceptation) est dans
`spec/Spécification V2 – Application Android de comptage Skull King.md`.

Hors périmètre, définitivement (inchangé) : jeu en ligne et synchronisation multi-appareils.

## Jalon 0 : publication (après la V2 uniquement)

**Aucune publication n'aura lieu tant que la V2 n'est pas terminée.** Il n'existe donc aucun appareil à migrer : le
schéma Room reste en version 1 sans migration, et le format de sauvegarde reste en version 1, modifiables librement
pendant toute la V2 (les appareils de développement se réinstallent). Ce jalon n'est à traiter qu'à la toute fin.

| # | Sujet | Détail |
| - | ----- | ------ |
| 0.1 | Figer le schéma | Au moment de la première publication : exporter le schéma Room (`room.schemaLocation`), le versionner, puis écrire une migration à chaque changement ultérieur (`MigrationTestHelper`). |
| 0.2 | Figer le format de sauvegarde | Même principe : la version du format publiée devient la référence, les suivantes devront relire les précédentes. |
| 0.3 | Publication | Fiche Play Store (captures, descriptions FR / EN), politique de confidentialité (aucune donnée collectée), test fermé. |

Déjà réglé : la valeur du **Boulet de canon** (15 × N + bonus si l'annonce est exacte, 0 sinon) est confirmée par la
règle papier et couverte par les tests de `core-scoring`.

## Jalon 1 : partage et lisibilité (F14)

| # | Fonction | Détail | Taille |
| - | -------- | ------ | ------ |
| 1.1 | Partage du tableau final en image | Rendu du podium et du tableau des scores en PNG, partage via la feuille Android, aperçu avant envoi. Image générée hors écran pour ne pas dépendre de la taille de l'écran. | M |
| 1.2 | Partage d'une partie depuis l'historique | Même image, accessible depuis la carte dépliée d'une partie terminée. | S |
| 1.3 | Mise en page tablette et paysage | Tableau des scores sur toute la largeur, saisie des plis et résultats en deux colonnes, navigation adaptée (`WindowSizeClass`). | L |

## Jalon 2 : statistiques plus riches

Les plis sont déjà saisis un par un (gagnant, carte, pirate, bonus) : les données existent, il reste à les montrer.

| # | Fonction | Détail | Taille |
| - | -------- | ------ | ------ |
| 2.1 | Courbes d'évolution | Score cumulé manche par manche pendant et après la partie ; évolution du score moyen par joueur dans le temps. | M |
| 2.2 | Comparaison entre joueurs | Face-à-face : victoires directes, écart moyen, parties jouées ensemble. | M |
| 2.3 | Statistiques de plis | Répartition des plis gagnés par carte et par pirate, taux de réussite par nombre de cartes de la manche, annonces à 0. | M |
| 2.4 | Records et faits marquants | Meilleure / pire manche, plus longue série d'annonces réussies, plus gros bonus. Affichés sur la fiche joueur. | S |
| 2.5 | Filtres de statistiques | Joueurs présents à la table, nombre de joueurs, avec ou sans extension (le filtre de mode existe déjà). | S |

## Jalon 3 : confort de jeu

| # | Fonction | Détail | Taille |
| - | -------- | ------ | ------ |
| 3.1 | Annuler / rétablir | Annulation de la dernière saisie (pli, annonce, résultat) sur toute la boucle de manche, au-delà de « Annuler le dernier pli ». | M |
| 3.2 | Écran toujours allumé | Option pendant une partie en cours (les parties durent longtemps). | S |
| 3.3 | Tirage du donneur / ordre de table | Choix aléatoire du premier joueur et rappel du donneur à chaque manche. | S |
| 3.4 | Parties rapides | « Rejouer avec les mêmes joueurs et les mêmes réglages » depuis l'historique ou le podium. | S |
| 3.5 | Autres variantes de comptage | À ajouter sur demande : le moteur `core-scoring` est isolé et testé, une variante revient à un `RuleSet` de plus. | M |

## Jalon 4 : données et robustesse

| # | Fonction | Détail | Taille |
| - | -------- | ------ | ------ |
| 4.1 | Sauvegarde automatique | Export JSON périodique vers un dossier choisi par l'utilisateur (accès au stockage via le sélecteur de fichiers), restauration guidée. | M |
| 4.2 | Import enrichi | Aperçu détaillé (parties, joueurs, conflits de noms) et fusion de joueurs homonymes. | M |
| 4.3 | Tests instrumentés | Parcours UI Compose prévus par la spec : partie complète à 4 joueurs, correction d'une manche passée, reprise après redémarrage ; exécutés en CI sur émulateur. | L |
| 4.4 | Accessibilité | Tailles de police élevées, contrastes des cartes et du tableau, descriptions TalkBack des icônes et des cellules de score. | M |

## Ordre proposé

1. **1.1 + 1.2** : le partage d'image est la fonction la plus demandée par la spec et la plus visible.
2. **2.1 + 2.3** : exploitent les plis déjà saisis, sans nouveau modèle de données.
3. **3.1, 3.2, 3.4** : petits gains de confort pour peu de travail.
4. **1.3** (tablette) puis le reste des jalons 2 à 4.
5. **Jalon 0** (publication) en dernier, quand la V2 est terminée.

Tailles : S = quelques heures, M = une à deux journées, L = plusieurs jours.

## Principes pour la V2

- Le score reste **toujours recalculé** à partir des saisies ; aucune nouvelle fonction ne le saisit à la main.
- Toute nouvelle règle ou variante passe par `core-scoring` avec ses tests avant d'apparaître dans l'interface.
- Textes en français **et** en anglais dans le même changement ; `spec/` mise à jour à chaque évolution.
- Les contrôles de la CI (Ktlint, Detekt, Android Lint, tests) doivent rester au vert à chaque jalon.
