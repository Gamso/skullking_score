# Spécification V2 – Application Android de comptage Skull King

Complète la spécification V1 (`Spécification – Application Android de comptage Skull King.md`, précisions V1.1 et V1.2
comprises). Ce document décrit, jalon par jalon, ce que la V2 doit faire ; l'ordre de réalisation et les tailles sont
dans `ROADMAP.md`. Les identifiants (1.1, 2.3…) sont ceux de la roadmap.

## Principes (rappel, inchangés)

- Hors périmètre, définitivement : jeu en ligne et synchronisation multi-appareils. L'app reste sur un seul téléphone,
  hors ligne, sans compte ni collecte de données.
- Le score n'est jamais saisi : il est recalculé à partir des saisies (`core-scoring`) puis mis en cache.
- Toute règle ou variante nouvelle passe d'abord par `core-scoring`, avec ses tests.
- Textes en français et en anglais dans le même changement ; la langue et le thème suivent Android.
- Aucune publication avant la fin de la V2 : le schéma Room (version 1) et le format de sauvegarde (version 1)
  peuvent encore changer sans migration jusque-là.

## Glossaire

| Terme | Sens |
| --- | --- |
| Partie terminée | Partie au statut « terminée » ; seules ces parties alimentent les statistiques |
| Manche validée | Manche dont les scores sont calculés (`SCORED`) |
| Pli saisi | Pli enregistré en direct (gagnant, carte gagnante, pirate, cartes gagnées) |
| Mode | Classique, Rascal (« Coup direct »), Rascal + Boulet de canon |

---

## Jalon 1 : partage et lisibilité

### 1.1 Partage du tableau final en image (F14)

**But** : envoyer dans une conversation une image propre du résultat d'une partie.

**Où** : écran de fin de partie (podium) et tableau des scores d'une partie terminée, via une action « Partager »
dans la barre supérieure.

**Parcours**

1. L'utilisateur touche « Partager ». Un écran d'aperçu s'ouvre avec l'image générée.
2. Il choisit le contenu par une série de pastilles à bascule : **Podium** (classement), **Tableau des scores**
   (manche par manche), **Statistiques de la partie** (meilleure manche, bonus marqués). Par défaut : Podium + Tableau.
3. « Envoyer » ouvre la feuille de partage Android avec un PNG ; « Enregistrer » écrit le PNG dans
   `Pictures/SkullKing` (MediaStore, sans permission de stockage sur Android 10 et plus).

**Contenu de l'image**

- En-tête : nom de l'appli, date de la partie, mode de calcul, extension utilisée (oui / non).
- Podium : rangs (les ex æquo partagent leur rang), médailles pour les 3 premiers, initiale colorée du joueur, nom,
  total.
- Tableau : une ligne par manche (numéro, nombre de cartes), une colonne par joueur (points de la manche, cumul),
  ligne de total, alternance de fond une ligne sur deux, ⚠ et couleur des manches ratées comme à l'écran.
- Pied : « Skull King Score » (sans lien ni publicité).

**Règles**

- L'image est dessinée **hors écran** à une largeur fixe de 1080 px (hauteur adaptée au nombre de manches), indépendante
  de la taille ou de l'orientation de l'écran. Au-delà de 9 joueurs, les colonnes se resserrent ; la largeur reste 1080.
- Thème **clair** forcé pour l'image (lisible quel que soit le thème de l'appareil).
- Textes dans la langue de l'appareil.
- Génération sur un thread d'arrière-plan avec indicateur de progression ; aucune donnée ne quitte l'appareil, sauf
  ce que l'utilisateur envoie lui-même.

**Critères d'acceptation**: image identique pour une même partie ; 3 à 9 joueurs et 10 à 14 manches rendus sans
troncature ; partage et enregistrement fonctionnels ; test d'instantané (golden) sur une partie de démo.

### 1.2 Partage depuis l'historique

- La carte dépliée d'une partie **terminée** reçoit une action « Partager » à côté de « Ouvrir ».
- Elle ouvre directement l'aperçu du 1.1 pour cette partie, sans passer par l'écran de la partie.
- Une partie **en cours** ne propose pas le partage (le tableau n'est pas final).

### 1.3 Tablette et paysage

**But** : exploiter les grands écrans et l'orientation paysage sans refaire les écrans.

**Principe** : adaptation par classes de taille de fenêtre (`WindowSizeClass`) : compacte (téléphone portrait),
moyenne (téléphone paysage, petite tablette), étendue (tablette).

| Écran | Compacte | Moyenne / étendue |
| --- | --- | --- |
| Accueil | Colonne unique | Deux colonnes : actions à gauche, dernière partie / reprise à droite |
| Nouvelle partie | Colonne unique | Deux colonnes : joueurs (pastilles + ordre de table) à gauche, mode et extension à droite |
| Tableau des scores | Total fixe sous l'en-tête | Idem, colonnes élargies ; détail d'une manche dans un panneau latéral à droite plutôt qu'en boîte de dialogue |
| Jeu / saisie des plis | Colonne unique | Plis saisis à gauche, événements en direct et progression à droite |
| Historique | Liste de cartes | Liste à gauche, détail de la partie sélectionnée à droite (liste-détail) |
| Statistiques | Une colonne | Deux colonnes : filtres et classement à gauche, graphiques à droite |

**Règles**

- Rotation sans perte de saisie : tout état en cours (pli en saisie, sélection de joueurs, filtres) survit au
  changement d'orientation et de taille.
- Aucune orientation n'est verrouillée.
- Les boîtes de dialogue (saisie d'un pli, détail d'une manche) restent des boîtes de dialogue en classe compacte, et
  sont plafonnées en largeur (560 dp) sur grand écran.

**Critères d'acceptation**: toutes les fonctions V1 utilisables sur un émulateur tablette 10" et en paysage
téléphone ; pas de texte tronqué ni de zone vide excessive ; captures de référence pour chaque écran.

---

## Jalon 2 : statistiques plus riches

Toutes les statistiques portent sur les **parties terminées**, par **mode** (le filtre de mode existe déjà et reste
obligatoire : les scores de deux modes ne se comparent pas). Les parties importées comptent comme les autres.

### 2.1 Courbes d'évolution

- **Dans une partie** (tableau des scores et écran de fin, onglet « Courbe ») : score cumulé manche par manche, une
  ligne par joueur, légende cliquable pour masquer / montrer un joueur, point touché = détail de la manche.
- **Dans le temps** (fiche joueur et statistiques) : score moyen par partie, glissant sur les 5 dernières parties,
  avec les parties en points. Au moins 3 parties terminées sont nécessaires, sinon un message l'explique.
- Les graphiques sont dessinés en Compose (Canvas), sans bibliothèque externe, avec une description TalkBack
  résumant la tendance (« Alice : de −10 à 80 en 10 manches »).

### 2.2 Comparaison entre joueurs

- Écran « Face-à-face » : on choisit deux joueurs.
- Indicateurs, sur les parties terminées où ils étaient tous deux présents :
  parties jouées ensemble ; victoires de chacun (le mieux classé de la table gagne le duel, ex æquo exclus) ;
  écart moyen de score ; meilleure et pire manche de chacun ; plus gros écart sur une partie.
- Moins de 2 parties communes : message « Pas assez de parties jouées ensemble ».

### 2.3 Statistiques de plis

Calculées à partir des plis saisis ; une partie dont des plis sont absents compte dans les autres statistiques mais
pas dans celles-ci. Un compteur « plis saisis sur X » rend ce point visible.

- **Plis gagnés avec** (existant) : par carte et par pirate, en nombre et en pourcentage.
- **Réussite par taille de manche** : taux d'annonces exactes pour les manches de 1–3 cartes, 4–6, 7–10, 11 et plus.
- **Annonces à 0** : tentées, réussies, taux, plus longue série réussie.
- **Écart moyen annonce / réalisé** : biais (tend à sur-annoncer ou sous-annoncer), en plis par manche.
- **Pirates** : plis gagnés par pirate, réussite du pari de Rascal, usages et réussite de l'ajustement de Harry.

### 2.4 Records et faits marquants

Fiche joueur et écran de statistiques, avec la partie d'origine accessible d'un toucher :

- meilleure et pire manche (points) ;
- plus longue série d'annonces réussies, plus longue série de victoires ;
- plus gros bonus en une manche ;
- meilleur et pire total de partie ;

### 2.5 Filtres de statistiques

Ajoutés au filtre de mode, tous cumulables, mémorisés tant que l'écran est ouvert :

- **Nombre de joueurs** : 2–3, 4–5, 6 et plus.
- **Extension** : avec, sans, toutes.
- **Joueurs présents** : n'inclure que les parties où tous les joueurs choisis étaient à la table.

Le nombre de parties retenues est affiché en permanence (« 7 parties ») ; zéro partie affiche un état vide explicite.

---

## Jalon 3 : confort de jeu

### 3.1 Annuler / rétablir

**But** : corriger une erreur de saisie sans parcourir plusieurs écrans.

- Action « Annuler » (et « Rétablir ») dans la barre supérieure de l'écran de jeu et d'annonces, avec message
  « Annulé : pli 3 » dans une barre d'état temporaire.
- Portée : annonces d'une manche (par joueur), pli saisi, événement en direct (Butin, Casier de Davy Jones), validation
  de manche (équivaut à « Rouvrir la dernière manche »).
- Pile d'annulation **par partie**, conservée jusqu'à la fermeture de l'écran de partie (pas après redémarrage de
  l'appli), de 30 actions au plus.
- Toute nouvelle saisie vide la pile « Rétablir ».
- Annuler ne modifie jamais une partie terminée.

### 3.2 Écran toujours allumé

- Réglage **« Garder l'écran allumé pendant une partie »**, activé par défaut, dans l'écran Réglages (qui n'existe pas
  encore : voir Réglages ci-dessous).
- Appliqué uniquement sur les écrans de partie en cours (annonces, jeu, résultats, tableau d'une partie en cours).

### 3.3 Tirage du donneur et ordre de table

- À la création d'une partie : bouton **« Tirer le donneur »** (choix aléatoire, mis en évidence) ; l'ordre de la liste
  est celui de la table, il n'est jamais mélangé.
- Le donneur de chaque manche est affiché sous le titre (« Donneur : Camille ») et avance d'un cran à chaque manche
  dans l'ordre de table. Le premier à annoncer est le joueur à gauche du donneur (suivant dans l'ordre).
- Le donneur est une information d'affichage : il n'intervient pas dans le calcul des scores.
- Modifiable : appui long sur le nom du donneur pour en désigner un autre (utile si la table se trompe de sens).

### 3.4 Rejouer

- Action **« Rejouer avec les mêmes joueurs »** sur le podium et dans la carte dépliée d'une partie terminée.
- Ouvre l'écran « Nouvelle partie » **prérempli** : mêmes joueurs, mode, extension, cartes choisies et option du
  Casier de Davy Jones. L'ordre de table est décalé d'un cran (le premier joueur passe en dernier) ; l'utilisateur
  peut tout modifier avant de commencer.

### 3.5 Autres variantes de comptage

- Cadre : une variante est un `RuleSet` supplémentaire de `core-scoring`, ajouté **sur demande** avec sa source
  (règle officielle ou fournie par l'utilisateur).
- Pour chaque variante : tableau des points dans la spec, tests unitaires par ligne de tableau, libellé et description
  FR / EN, ajout au choix de mode (« Nouvelle partie »), au filtre de statistiques et à l'export / import.
- Une partie conserve son mode pour toujours ; il n'est pas possible de la convertir.
- Aucune variante n'est ajoutée sans règle écrite : `core-scoring` ne contient que des règles vérifiées.

### Réglages (nouvel écran, accessible depuis l'accueil)

Écran minimal introduit par 3.2 et 4.1 : « Garder l'écran allumé », « Sauvegarde automatique » (4.1), et une section
« À propos » (version, licence). La langue et le thème continuent de suivre Android : aucun réglage de langue ou de
thème dans l'appli.

---

## Jalon 4 : données et robustesse

### 4.1 Sauvegarde automatique

- Réglage « Sauvegarde automatique » : désactivée par défaut. À l'activation, l'utilisateur choisit un **dossier** via
  le sélecteur de dossier Android (accès persistant au dossier uniquement, aucune permission de stockage large).
- Fréquence : après chaque partie terminée, et au plus une fois par jour pour une partie en cours.
- Fichier : `skullking-AAAA-MM-JJ.json` au format de sauvegarde en vigueur ; conservation des **10** dernières
  sauvegardes (les plus anciennes sont supprimées).
- Exécution par `WorkManager`, sans réseau, sans exigence de charge ; échec signalé par une notification discrète et
  par une ligne d'état dans les Réglages (date de la dernière réussite).
- Restauration : « Restaurer depuis la sauvegarde » dans les Réglages → même import que l'historique (aperçu, fusion
  sans doublon).
- Si le dossier n'est plus accessible, le réglage se désactive et l'explique.

### 4.2 Import enrichi

L'aperçu avant import (aujourd'hui : nombre de parties nouvelles, déjà présentes, joueurs nouveaux) devient :

- liste des parties à importer (date, mode, joueurs), avec case à cocher par partie ;
- **joueurs homonymes** : pour chaque joueur importé dont le nom existe déjà avec un autre identifiant, choix entre
  « Fusionner avec le profil existant » (par défaut) et « Créer un nouveau profil » ;
- avertissement si le fichier vient d'une version plus récente de l'appli (import refusé, message explicite) ou d'une
  version plus ancienne (importé normalement) ;
- résumé final : parties ajoutées, ignorées, profils fusionnés / créés.
- L'import reste **idempotent** : réimporter le même fichier n'ajoute rien.

### 4.3 Tests instrumentés

Exécutés en CI sur un émulateur (nouveau job, désactivable) :

| Parcours | Vérifie |
| --- | --- |
| Partie complète, 4 joueurs, 10 manches | Création, annonces, saisie des plis, validation, tableau, podium, historique |
| Correction d'une manche passée | Rouvrir, modifier, totaux et classement recalculés |
| Reprise après redémarrage | Partie en cours rouverte dans l'état exact après arrêt du processus |
| Import de la base de démo | Aperçu, import, historique et statistiques remplis, second import sans doublon |
| Partage d'image (1.1) | Le fichier est généré, non vide, aux dimensions attendues |
| Orientation (1.3) | Rotation au milieu d'un pli : aucune saisie perdue |

Les tests unitaires existants (`core-scoring`, domaine, démo) restent obligatoires et rapides.

### 4.4 Accessibilité

- **Taille de police** : toutes les vues restent utilisables à 200 % ; le tableau des scores défile plutôt que de
  tronquer.
- **Contrastes** : texte et icônes conformes WCAG AA en clair et en sombre (vérification des couleurs de joueur : texte
  de l'initiale sur fond coloré, scores négatifs en rouge sur les deux fonds).
- **TalkBack** : chaque icône porteuse de sens a une description ; une cellule du tableau est lue en phrase
  (« Alice, manche 3, moins 30 points, total 50 ») ; l'état des puces et des interrupteurs est annoncé.
- **Cibles tactiles** : 48 dp au minimum.
- **Couleur seule** : un score négatif n'est jamais signalé uniquement par la couleur (signe « − » toujours affiché).
- Audit automatisé (Android Lint, vérifications d'accessibilité des tests instrumentés) intégré à la CI.

---

## Jalon 0 : publication (en dernier)

Traité seulement quand tout le reste est livré :

- Export du schéma Room, versionné ; une migration testée (`MigrationTestHelper`) par changement de schéma ensuite.
- Le format de sauvegarde publié devient la référence : les versions suivantes doivent relire les précédentes.
- Fiche Play Store (captures, descriptions FR / EN), politique de confidentialité (aucune donnée collectée), test fermé
  avant la production.

## Modifications de données prévues

| Jalon | Changement de données |
| --- | --- |
| 1.x | Aucun (image générée à la demande) |
| 2.x | Aucun : calculs à partir de `PlayerRound`, `Round`, `Trick`, `Game` existants |
| 3.3 | Donneur initial de la partie (`Game.firstDealerIndex`, facultatif) ; le donneur d'une manche se déduit du numéro |
| 3.1 | Aucun (pile d'annulation en mémoire) |
| 4.1 | Dossier de sauvegarde et date de dernière réussite (préférences, hors base) |
| 4.2 | Aucun (la sauvegarde garde ses références par identifiant unique) |

Le schéma est encore modifiable librement : `Game.firstDealerIndex` s'ajoute sans migration (réinstallation des
appareils de développement), et le format de sauvegarde l'accepte comme champ facultatif.

## Récapitulatif des critères communs

- Ktlint, Detekt, Android Lint et tests unitaires au vert à chaque jalon.
- Chaînes FR / EN en parité ; aucune chaîne en dur dans le code.
- `spec/` et `README.md` mis à jour dans le même changement que le code.
- Jeu de démo (`demo/skullking-demo.json`) régénéré si le modèle de données change.

---

## État d'avancement et écarts avec la spécification ci-dessus

Tous les jalons 1 à 4 sont réalisés. Le jalon 0 (publication) reste à faire en dernier. Ce qui diffère de la
description initiale, parce que l'implémentation a montré une solution plus simple ou plus sûre :

| Point | Prévu | Réalisé |
| --- | --- | --- |
| 3.1 Annuler / rétablir | Annonces, plis, événements, validation | Annonces, mise « boulet de canon », saisie / correction / suppression d'un pli. Les événements en direct s'annulent par leur propre croix, et une manche validée se corrige par « Rouvrir la dernière manche ». La pile est vidée à la validation des annonces et de la manche. |
| 3.3 Donneur | Appui long pour changer de donneur | Un appui sur « Donneur : … » ouvre la liste des joueurs. À la création : « Tirer le donneur » (pas de mélange de l'ordre : la liste suit la table). Le champ `Game.firstDealerSeat` est facultatif dans la sauvegarde (0 par défaut). |
| 4.1 Sauvegarde automatique | `WorkManager` | Exécutée par l'application elle-même, sans dépendance supplémentaire : à la fin d'une partie, et au démarrage quand la dernière sauvegarde date de plus d'un jour (ou a échoué). Les 10 dernières sauvegardes sont conservées. |
| 1.3 Grands écrans | Deux colonnes pour la plupart des écrans | Accueil et jeu en deux colonnes dès 600 dp de large ; historique et statistiques en grille adaptative ; tableau des scores avec panneau de détail latéral dès 840 dp ; autres écrans (nouvelle partie, annonces, résultats, réglages…) centrés sur 720 dp au plus. |
| 2.1 Courbe d'une partie | Onglet « Courbe » avec légende et détail | Réalisé dans le tableau des scores (onglets Tableau / Courbe), pastilles pour masquer un joueur, appui sur une manche pour son détail. |
| 2.5 Filtres | Visibles en permanence | Le mode reste visible ; nombre de joueurs, extension et joueurs présents sont dans « Plus de filtres » (le nombre de filtres actifs est affiché). |
| 4.3 Tests instrumentés | Six parcours | Parcours d'accueil, ouverture des réglages, première manche jouée de la création au tableau des scores, conservation de la sélection de joueurs à la rotation. Job CI `instrumented-tests`, désactivable avec la variable de dépôt `SKIP_INSTRUMENTED_TESTS`. |
| 1.1 Partage | Test d'instantané | Rendu vérifié sur émulateur ; pas de test d'instantané automatisé. |
| Correctif | — | Le gagnant d'un pli et la carte / le pirate gagnants saisis en direct n'étaient pas relus depuis la base de données (statistiques « plis gagnés avec » vides, et fenêtre de correction d'un pli sans sa carte) : corrigé, avec un test. |
| Import (4.2) | Parties à cocher, fusion des homonymes | Le fichier est toujours importé en entier : plus de sélection de parties. Seuls les joueurs dont le nom existe déjà se choisissent (fusion par défaut, ou nouveau profil). |
| Donneur (3.3) | Premier joueur de la liste, tirage au sort | L'ordre de la liste est l'ordre de table ; le donneur de la première manche se désigne d'un appui sur 🃏 dans la liste (ou « Tirer le donneur »). Il est rappelé en gros sur la page du nombre de cartes de chaque manche, où un appui permet de le changer. |
| « Rejouer » (3.4) | Podium et historique | Podium seulement. La table reste dans le même ordre et le donneur passe à la personne suivante. |
| Partage (1.1) | Podium, tableau, faits marquants | Ajout d'un contenu « Courbe de score » (score cumulé manche par manche, une couleur par joueur, légende). |
| Base de démo | 10 parties | 40 parties, 12 joueurs, janvier 2025 à octobre 2026 (voir `demo/README.md`). |
| Statistiques (2.1, 2.3) | Cartes dépliables d'un toucher, deux courbes pâle et pleine, écart moyen chiffré | Une flèche en haut à droite de chaque carte indique qu'elle se déplie ; courbe de tendance : score de chaque partie en trait plein, moyenne glissante en pointillés, dans la couleur de l'application (et non celle du joueur) ; « Tendance d'annonce » exprimée en mots (équilibrée sous 0,25 pli d'écart par manche, sinon sous- ou sur-annonce avec l'écart en plis par manche) et accompagnée d'une explication. |
| Statistiques (2.5) | Filtres sur plusieurs lignes | Écran condensé : modes sur une seule ligne (libellés courts), recherche d'un joueur par son nom (sans casse ni accents), puis « Plus de filtres », nombre de parties et « Face-à-face » sur une même ligne. |
| Partage des statistiques d'un joueur | — | Icône de partage sur la carte d'un joueur : image (1080 px, thème clair) avec ses chiffres clés, ses records, la courbe d'évolution (score en trait plein, moyenne en pointillés) et la réussite par taille de manche, selon les filtres en cours. |

