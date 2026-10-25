# Spécification – Application Android de comptage Skull King

Oct 25, 2026 · @Dimitri

## Présentation

L'application remplace le carnet de score papier de Skull King : on saisit les annonces et les plis de chaque joueur, l'app calcule les points, bonus compris, selon le mode choisi. Elle couvre le jeu de base (édition Grandpa Beck's avec Baleine Blanche, Kraken, Butins, Tigresse) et l'extension 2026 (Second « First Mate Con », Mary Thorne, 7/8/0-14, Wild 15, Raie tachetée, Casier de Davy Jones, Supplice de la planche, Dernière salve).

| Élément | Choix |
| --- | --- |
| Plateforme | Android 8.0+ (API 26), smartphone d'abord, tablette en paysage ensuite |
| Joueurs | 2 à 9 avec l'extension (2 à 8 sans) |
| Usage | Un seul téléphone posé sur la table, tenu par le « scribe » |
| Connexion | Aucune : 100 % hors ligne, données locales |
| Langue | Français d'abord, anglais ensuite |

Hors périmètre, définitivement : jeu en ligne et synchronisation multi-appareils ; l'app reste sur un seul téléphone. Hors V1 : reconnaissance des cartes par photo. L'app ne vérifie pas qui a gagné chaque pli : elle fait confiance à la saisie et signale seulement les incohérences.

## Règles du jeu de base

Une partie compte par défaut 10 manches de 1 à 10 cartes, mais rien n'est figé : avant chaque manche, l'app propose le nombre de cartes (manche précédente + 1) et le scribe peut le modifier. On peut aussi ajouter des manches à la volée, par exemple une ou deux manches de plus à 10 cartes quand il reste 10 minutes. Par manche et par joueur, l'app enregistre l'annonce, les plis remportés et les cartes capturées ; les Butins (avec les deux joueurs liés) et les captures du Casier de Davy Jones se saisissent en direct, pendant la manche.

### Hiérarchie des cartes (rappel pour l'aide intégrée)

1. Fuite (drapeau blanc) : perd toujours, sauf si tout le pli n'est que fuites (la première jouée gagne).
2. Cartes de couleur jaune, violet, vert (1 à 14) : la couleur demandée l'emporte sur les autres couleurs.
3. Atout noir (1 à 14) : bat toutes les couleurs.
4. Sirènes : battent couleurs et atouts, perdent contre les pirates.
5. Pirates : battent les sirènes, perdent contre le Skull King.
6. Skull King : bat les pirates, mais perd contre une sirène.

Cartes spéciales du jeu de base : la **Tigresse** se joue au choix comme pirate ou comme fuite ; le **Kraken** détruit le pli (personne ne le gagne) et le pli suivant est entamé par le joueur qui l'aurait gagné sans le Kraken (règle de la FAQ officielle) ; la **Baleine Blanche** annule les cartes spéciales et fait gagner la plus haute carte numérotée ; les **Butins** se jouent comme une fuite et lient deux joueurs pour un bonus. Pouvoirs des pirates : Rosie la Douce (choisit qui entame), Will le Bandit (pioche 2, défausse 2), Rascal le Flambeur (pari de 0, 10 ou 20 points), Juanita Jade (regarde les cartes non distribuées), Harry le Géant (modifie son annonce de ±1 après le dernier pli). Harry le Géant est le seul pirate dont le pouvoir s'utilise aussi au dernier pli (il doit gagner le pli pour l'activer) ; l'aide intégrée présente les pouvoirs sur une ligne par pirate.

### Points (mode classique)

| Situation | Points |
| --- | --- |
| Annonce ≥ 1 réussie | +20 par pli annoncé |
| Annonce ≥ 1 ratée | −10 par pli d'écart |
| Annonce 0 réussie | +10 × nombre de cartes de la manche |
| Annonce 0 ratée | −10 × nombre de cartes de la manche |

| Bonus (seulement si l'annonce est réussie) | Points |
| --- | --- |
| 14 de couleur (jaune, violet, vert) capturé | +10 chacun |
| 14 noir capturé | +20 |
| Sirène capturée par un pirate | +20 chacune |
| Pirate capturé par le Skull King | +30 chacun |
| Skull King capturé par une sirène | +40 |
| Butin : le joueur du Butin et le gagnant du pli réussissent tous deux | +20 chacun |
| Pari de Rascal réussi / raté | +mise / −mise (0, 10 ou 20) |

Points importants pour le calcul : le Kraken et une Tigresse jouée en fuite ne rapportent rien ; Harry le Géant change l'annonce finale, donc l'app doit permettre de corriger l'annonce après la manche. Source : [règles officielles Skull King](https://www.grandpabecksgames.com/pages/skull-king).

## Extension 2026 et le Second (First Mate Con)

L'extension ajoute 4 nouveaux bonus ou malus au calcul et une règle à retenir : comme dans le jeu de base, tout bonus ou malus n'est compté que si le joueur réussit son annonce. Elle permet aussi de jouer jusqu'à 9 joueurs sur les 10 manches ; au-delà, on rejoue la plus grande manche possible pour atteindre 10 manches.

### Le Second, First Mate Con

Con bat tous les pirates, mais perd contre les sirènes et le Skull King. Quand il gagne un pli, son joueur peut utiliser les pouvoirs de tous les pirates qu'il a capturés. Il ne rapporte **aucun** bonus pour les pirates capturés. En revanche, le Skull King ou une sirène qui capture Con rapporte **+30**. Si un pirate, une sirène et Con sont dans le même pli, la sirène gagne, quel que soit l'ordre.

Ordre complet pour l'aide intégrée : Fuite < couleurs < atout noir < sirène < pirates < Con < Skull King < sirène (la sirène bat Con et le Skull King).

### Effet de chaque carte sur le score

| Carte | Effet en jeu | Impact sur le calcul |
| --- | --- | --- |
| 7 (×4, un par couleur) | Carte numérotée normale ; à égalité, la première jouée gagne | −5 par 7 capturé, si annonce réussie |
| 8 (×4) | Idem | +5 par 8 capturé, si annonce réussie |
| 0/14 (×4) | Annoncée 0 ou 14 au moment de la jouer | Aucun bonus, même jouée en 14 ; à ne pas compter comme un 14 |
| Wild 15 (singe) | Prend la couleur demandée (jamais le noir) avec la valeur 15 | Aucun bonus |
| Mary Thorne | Pirate ; si elle gagne, une carte tirée au hasard dans une main doit être jouée au pli suivant | Compte comme un pirate : +30 si capturée par le Skull King, +20 par sirène qu'elle capture |
| First Mate Con | Voir ci-dessus | +30 au Skull King ou à la sirène qui le capture ; 0 pour les pirates qu'il capture |
| Casier de Davy Jones | Ne gagne pas ; détruit tous les monstres marins du pli | +20 par monstre marin détruit (Kraken, Baleine Blanche, Raie tachetée) pour le joueur du Casier |
| Supplice de la planche | Ne gagne pas ; retire un pirate du pli | Le pirate retiré ne rapporte aucun bonus au Skull King |
| Raie tachetée | Monstre marin : la plus basse carte gagne | Aucun bonus |
| Dernière salve | Ne gagne pas ; son joueur joue une 2e carte après tous, puis saute le dernier pli | Aucun bonus, mais le total des plis de la manche peut changer |

Point ouvert : la règle ne précise pas si le +20 du Casier de Davy Jones est soumis à la réussite de l'annonce. Par cohérence avec « les bonus ne comptent qu'avec l'annonce réussie », l'app l'applique sous cette condition par défaut, avec une option pour le changer.

Conséquences pour la saisie : un pli peut être défaussé (Kraken, ou pli fait uniquement de cartes spéciales non gagnantes), donc la somme des plis peut être inférieure au nombre de cartes ; à 2 joueurs, le Wild 15, le Supplice de la planche et les 0/14 sont retirés. Sources : [livret de règles de l'extension](https://cdn.shopify.com/s/files/1/0565/3230/4053/files/Skull_King_Expansion_Rulebook_Web.pdf?v=1753214318), [page officielle de l'extension et FAQ](https://www.grandpabecksgames.com/pages/skull-king-expansion).

## Modes de calcul

L'app propose les 3 modes officiels, choisis à la création de la partie et figés ensuite.

| Mode | Principe | Annonce exacte | Écart de 1 | Écart de 2+ |
| --- | --- | --- | --- | --- |
| Skull King classique | Points selon l'annonce | 20 × annonce (0 : 10 × N) + bonus | −10 par pli d'écart (0 : −10 × N) | idem |
| Rascal (« Coup direct ») | Même potentiel pour tous : 10 × N | 10 × N + 100 % des bonus | 5 × N + 50 % des bonus | 0 |
| Rascal + Boulet de canon | Chaque joueur peut miser « tout ou rien » à l'annonce | 15 × N + bonus | 0 | 0 |

N = nombre de cartes distribuées dans la manche. En mode Rascal, le Butin et le pari de Rascal le Flambeur exigent toujours une annonce exacte, même en cas d'écart de 1. Boulet de canon (poing fermé) : annonce exacte = 15 × N + 100 % des bonus ; annonce ratée = 0 point, sans bonus ; il s'oppose à la Chevrotine (main ouverte), qui applique le calcul Rascal standard. Valeur 15 × N confirmée sur la règle papier. Source : [règle « The Rascal's Scoring »](https://cdn.shopify.com/s/files/1/0565/3230/4053/files/Skull_King_Rascal_Scoring.pdf?v=1784153921).

### Formule du mode classique

```latex
\text{score} = \begin{cases} 20\,a + B & a \ge 1,\ p = a \\ -10\,|p - a| & a \ge 1,\ p \ne a \\ 10\,N + B & a = 0,\ p = 0 \\ -10\,N & a = 0,\ p > 0 \end{cases}
```

a = annonce finale (après Harry le Géant), p = plis remportés, B = somme des bonus et malus de la manche (ci-dessous).

### Calcul des bonus B

```latex
B = 10\,n_{14c} + 20\,n_{14n} + 20\,n_{sir} + 30\,n_{pir} + 40\,n_{SK} + 30\,n_{Con} + 20\,n_{but} + 20\,n_{DJ} + 5\,n_{8} - 5\,n_{7} \pm m_{R}
```

Avec : 14 de couleur, 14 noir, sirènes capturées par un pirate (Mary Thorne comprise, Con exclu), pirates capturés par le Skull King (hors pirates retirés par la planche), Skull King capturé par une sirène, Con capturé par le Skull King ou une sirène, Butins validés, monstres détruits par le Casier de Davy Jones, 8 et 7 de l'extension capturés, mise de Rascal (+ si annonce exacte, − sinon). Le pari de Rascal est le seul terme appliqué même quand l'annonce est ratée (en négatif).

### Pseudo-code de référence

```kotlin
fun scoreRound(mode: ScoringMode, n: Int, r: PlayerRound): Int {
    val a = r.finalBid; val p = r.tricks; val diff = abs(p - a)
    val b = bonusTotal(r)            // B sans le pari de Rascal
    val rascal = if (diff == 0) r.rascalWager else -r.rascalWager
    val base = when (mode) {
        CLASSIC -> when {
            a == 0 && p == 0 -> 10 * n + b
            a == 0           -> -10 * n
            diff == 0        -> 20 * a + b
            else             -> -10 * diff
        }
        RASCAL -> when (diff) {
            0    -> 10 * n + b
            1    -> 5 * n + b.halfExcludingLoot()   // butins : exacts seulement
            else -> 0
        }
        CANNONBALL -> if (!r.cannonball) scoreRound(RASCAL, n, r) - rascal
                      else if (diff == 0) 15 * n + b else 0
    }
    return base + rascal
}
```

### Exemple de vérification (manche 5, mode classique)

| Joueur | Annonce | Plis | Captures | Score |
| --- | --- | --- | --- | --- |
| Léa | 2 | 2 | Con avec le Skull King, un 8 | 40 + 30 + 5 = 75 |
| Tom | 0 | 0 | Aucune | +50 |
| Inès | 3 | 2 | Un 14 noir | −10 (bonus perdu) |
| Noé | 1 | 1 | Un 7 | 20 − 5 = 15 |

Ce jeu d'exemples sert aussi de cas de test unitaire.

## Fonctionnalités et parcours utilisateur

Le parcours tient en une boucle par manche : annonces, puis résultats, puis tableau des scores ; la saisie d'une manche doit prendre moins de 30 secondes pour 5 joueurs.

| ID | Fonctionnalité | Priorité | Détail |
| --- | --- | --- | --- |
| F1 | Création de partie | V1 | Joueurs (2 à 9), ordre autour de la table, mode de calcul, cartes de l'extension activées carte par carte |
| F2 | Plan de manches flexible | V1 | Nombre de cartes proposé (+1 par manche) et modifiable avant chaque manche ; bouton « Manche supplémentaire » après la dernière manche prévue, nombre de cartes au choix (10 par défaut) |
| F3 | Saisie des annonces | V1 | Un sélecteur 0…N par joueur, saisi par le scribe |
| F4 | Événements en direct | V1 | Pendant la manche : Butin (joueur du Butin + gagnant du pli), Casier de Davy Jones (joueur + créatures détruites : Kraken, Baleine Blanche, Raie tachetée), journal de la manche avec annulation |
| F5 | Saisie des résultats | V1 | Plis + compteurs de bonus ; Butins et Davy Jones pré-remplis depuis le direct, modifiables ; Boulet de canon et mise de Rascal si activés |
| F6 | Calcul et tableau des scores | V1 | Score de la manche, cumul, classement, détail du calcul au toucher |
| F7 | Contrôles de cohérence | V1 | Alertes non bloquantes (voir Cas limites) |
| F8 | Correction | V1 | Modifier une manche passée, annuler la dernière action, correction d'annonce (Harry le Géant) |
| F9 | Profils joueurs | V1 | Profil persistant (nom ; la pastille affiche la première lettre du nom sur une couleur tirée au hasard) auquel toutes les parties sont rattachées |
| F10 | Historique et reprise | V1 | Partie sauvegardée à chaque saisie, reprise après fermeture, historique complet conservé sans limite de durée |
| F11 | Statistiques par joueur | V1 | Sur l'ensemble des parties terminées, par mode de calcul : parties, victoires, score moyen et record, taux d'annonces réussies, réussite des annonces à 0, bonus marqués, évolution mois par mois |
| F12 | Export et sauvegarde | V1 | Export CSV et JSON via le partage Android ; import du JSON pour restaurer l'historique sur un nouveau téléphone |
| F13 | Aide intégrée | V1 | Hiérarchie des cartes, bonus, résumé de l'extension, FAQ officielle (dont la règle du Kraken) |
| F14 | Partage du tableau final en image | V2 | Image prête à envoyer dans une conversation |

### Parcours principal

1. Accueil : « Nouvelle partie », « Reprendre » ou « Statistiques ».
2. Configuration : choix des joueurs, du mode et des cartes de l'extension.
3. Manche N, préparation : nombre de cartes proposé, modifiable d'un toucher.
4. Manche N, annonces : chacun donne son annonce, validation.
5. Manche N, jeu à la table : rappel des annonces et boutons « Butin » et « Davy Jones » pour noter les événements au moment où ils arrivent.
6. Manche N, résultats : plis et bonus (Butins et Davy Jones déjà remplis), validation ; l'app affiche les points gagnés.
7. Tableau des scores, puis retour à l'étape 3 pour la manche suivante.
8. Après la dernière manche prévue : « Manche supplémentaire » (retour à l'étape 3, 10 cartes par défaut) ou « Terminer » : podium, historique et statistiques mis à jour.

Le scribe peut ouvrir le tableau des scores et l'aide à tout moment, et toucher n'importe quelle case du tableau pour corriger une manche.

## Wireframes

Quatre écrans portent toute la partie ; chacun a un seul bouton d'action principal en bas, à portée de pouce. Les données affichées reprennent l'exemple de la manche 5 de la section Modes de calcul.

&#91;embedded content: Wireframes · configuration et annonces\]

La configuration n'affiche les cartes de l'extension que si l'interrupteur est activé ; une carte désactivée fait disparaître son compteur de bonus plus loin. L'écran d'annonces garde le score cumulé sous chaque nom pour aider à décider.

&#91;embedded content: Wireframes · résultats et tableau des scores\]

Sur l'écran de résultats, un seul joueur est déplié à la fois ; les autres montrent leur score en une ligne, en rouge si l'annonce est ratée. Le tableau des scores ouvre le détail du calcul au toucher d'une case et permet d'y corriger la manche.

&#91;embedded content: Wireframes · saisie en direct et manche supplémentaire\]

Pendant la manche, l'écran « en jeu » garde les annonces en rappel et offre deux gros boutons : un Butin se note en deux touchers (qui l'a joué, qui a gagné le pli), un Casier de Davy Jones en deux aussi (qui l'a joué, quelles créatures). Après la dernière manche prévue, l'app propose une manche supplémentaire avec 10 cartes par défaut, modifiables ; le même sélecteur ✎ existe au début de chaque manche.

Écrans secondaires, non dessinés : accueil (Nouvelle partie, Reprendre, Historique), fin de partie (podium et partage), aide (hiérarchie des cartes, tableau des bonus, résumé de l'extension), réglages (thème, langue).

## Modèle de données et architecture

Stack recommandée : Kotlin, Jetpack Compose (Material 3), architecture MVVM avec un module de calcul pur, Room pour la persistance, Hilt pour l'injection. Le calcul des scores vit dans un module Kotlin sans dépendance Android, donc testable en JUnit et réutilisable (Kotlin Multiplatform plus tard).

### Entités

| Entité | Champs principaux |
| --- | --- |
| Player | id, uuid, nom, couleur (tirée au hasard à la création), créé le, archivé (masqué sans perdre ses statistiques) |
| Game | id, début, fin, mode (CLASSIC, RASCAL, CANNONBALL), expansionCards (cartes actives), statut (EN\_COURS, TERMINÉE) |
| GamePlayer | gameId, playerId, siège (ordre de table) |
| Round | gameId, numéro, cardsDealt (proposé = précédente + 1, modifiable), isExtra (manche supplémentaire), discardedTricks (plis défaussés) |
| PlayerRound | roundId, playerId, bid, finalBid (après Harry), tricks, cannonball, rascalWager (0/10/20), bonus (objet ci-dessous), score calculé |
| Bonus | colored14, black14, mermaidsByPirate, piratesBySkullKing, skullKingByMermaid, conCaptured, eights, sevens |
| LootLink | id, roundId, lootPlayerId (a joué le Butin), trickWinnerId (a gagné le pli), saisi à |
| DavyJonesCapture | id, roundId, playerId, creatures (ensemble de KRAKEN, BALEINE\_BLANCHE, RAIE\_TACHETÉE), saisi à |

Il n'y a plus de plan de manches figé : la partie est simplement la liste de ses manches, et une manche supplémentaire est une ligne Round de plus. Les +20 des Butins et du Casier de Davy Jones ne sont pas saisis comme compteurs : ils sont dérivés de LootLink et DavyJonesCapture au moment du calcul.

Le score n'est jamais saisi : il est recalculé à partir des saisies, puis mis en cache dans PlayerRound pour l'affichage. Changer une valeur passée relance le calcul de toute la partie.

### Historique, statistiques et export

L'historique est conservé sans limite dans la base Room locale et rattaché aux profils joueurs, ce qui permet des statistiques sur plusieurs mois. Les statistiques sont calculées à la demande à partir de PlayerRound et Game, sur toutes les parties terminées et filtrées par mode de calcul (aucun filtre de période), car un score Rascal ne se compare pas à un score classique.

| Statistique par joueur | Calcul |
| --- | --- |
| Parties jouées, victoires, taux de victoire | Parties terminées où le joueur figure ; victoire = meilleur total |
| Score moyen, record, pire score | Sur les totaux de fin de partie |
| Annonces réussies | Manches où finalBid = tricks, divisé par manches jouées |
| Réussite des annonces à 0 | Même calcul, limité aux annonces à 0 |
| Bonus marqués | Somme des bonus validés, détaillée par type (Con, Skull King, Butins, Davy Jones…) |
| Évolution | Score moyen et taux de réussite mois par mois, en courbe |
| Face-à-face | Victoires de A contre B dans les parties communes |
| Plis gagnés avec | Par carte (sirène, Skull King, Second, pirate, autre) et par pirate |

Export via le partage Android (fichier, mail, Drive, au choix de l'utilisateur) :

- **CSV**, une ligne par joueur et par manche : date, id de partie, mode, numéro de manche, manche supplémentaire, cartes, joueur, annonce, plis, détail des bonus, Butins, captures Davy Jones, score de la manche, cumul. Lisible directement dans un tableur.
- **JSON** complet (joueurs, parties, manches, événements), avec un numéro de version de format ; l'import de ce fichier restaure tout l'historique sur un nouveau téléphone, sans aucune synchronisation.
- Export filtrable par période et par joueur.

### Modules

- `core-scoring` : règles, modes, `scoreRound()`, validation ; 100 % Kotlin pur.
- `data` : Room (DAO, migrations), dépôts.
- `feature-setup`, `feature-round`, `feature-scoreboard`, `feature-history`, `feature-help` : écrans Compose + ViewModels.
- `app` : navigation (Navigation Compose), thème, injection.

Les valeurs de points sont dans une table `RuleSet` (classique, Rascal) plutôt qu'en dur, pour suivre un éventuel erratum sans réécrire le calcul.

## Cas limites, tests et roadmap

Toutes les alertes sont non bloquantes : la table a toujours raison, l'app prévient puis laisse valider.

| Cas | Comportement attendu |
| --- | --- |
| Somme des plis ≠ N | Alerte si > N ; si < N, demande le nombre de plis défaussés (Kraken, pli de cartes spéciales seules) |
| Nombre de cartes modifié | Accepté de 1 à 10 ; alerte si cartes × joueurs dépasse le paquet en jeu |
| Manche supplémentaire | Numérotée 11, 12… et marquée « supplémentaire » dans l'historique ; comptée dans le total et les statistiques |
| Plus de 3 « 14 de couleur », 1 « 14 noir », 1 Skull King, 1 Con capturés au total | Alerte : la carte n'existe qu'en un exemplaire (les 0/14 ne comptent pas) |
| Plus de 4 « 7 » ou « 8 » de l'extension | Alerte |
| Plus de 2 sirènes capturées | Alerte |
| Plus de pirates capturés que de pirates en jeu (5 + Mary Thorne, + Tigresse) | Alerte |
| Plus de Butins saisis que de cartes Butin dans le paquet | Alerte |
| Butin : un seul des deux joueurs liés réussit son annonce | Aucun des deux ne marque le +20 |
| Butin ou Davy Jones saisi en direct, puis corrigé aux résultats | La correction remplace l'événement ; le journal garde la trace |
| Davy Jones : une même créature saisie deux fois dans la manche | Alerte (1 Kraken, 1 Baleine Blanche, 1 Raie tachetée) |
| Davy Jones : créature d'une carte non activée | Non proposée dans la fiche |
| Bonus saisi sur une annonce ratée | Saisie conservée mais grisée, comptée 0, détail visible |
| Plus de 9 joueurs | Nombre de cartes proposé plafonné, le scribe ajuste |
| 2 joueurs avec l'extension | Wild 15, Supplice de la planche et 0/14 désactivés automatiquement |
| Carte d'extension non activée | Son compteur de bonus n'apparaît pas |
| App tuée en cours de manche | Saisie et événements en direct restaurés tels quels au redémarrage |
| Import d'un JSON sur un téléphone qui a déjà un historique | Fusion par identifiant de partie, sans doublon, après confirmation |

### Tests

- Tests unitaires de `core-scoring` : chaque ligne des tableaux de points, l'exemple de la section Modes de calcul, les écarts 0, 1, 2 en Rascal, le Boulet de canon.
- Tests de propriétés : le score ne dépend pas de l'ordre de saisie ; recalculer une partie donne le même total.
- Tests UI Compose : partie complète de 10 manches à 4 joueurs, correction d'une manche passée, reprise après redémarrage.
- Test terrain : 3 vraies parties avec carnet papier en parallèle, écart attendu 0 point.

### Roadmap

| Version | Contenu |
| --- | --- |
| V1 (MVP) | F1 à F13 : manches flexibles, événements en direct, modes classique et Rascal, extension complète, historique et statistiques par joueur, export CSV et JSON, français |
| V1.1 | Boulet de canon, anglais, thème sombre soigné (livrée, voir précisions ci-dessous) |
| V1.2 | Saisie des plis en direct, « gagné avec » facultatif et statistiques associées (voir précisions ci-dessous) |
| V2 | Partage du tableau en image, tablette en paysage, graphiques de statistiques plus riches ; détail, jalons et ordre proposé dans `ROADMAP.md` à la racine du dépôt. Aucune publication avant la fin de la V2 |

### Précisions V1.1

- **Boulet de canon** : proposé comme troisième mode à la création de la partie. À l'écran des résultats, un interrupteur par joueur indique la mise « tout ou rien » ; un joueur sans mise est compté comme en Rascal.  La valeur 15 × N est confirmée sur la règle papier.
- **Anglais** : l'interface est traduite en anglais. La langue est toujours celle d'Android (aucun réglage propre à l'application) : français ou anglais, et anglais pour toute autre langue.
- **Thème sombre** : palette dédiée (fond marine, surfaces étagées, accents turquoise et or) ; le choix clair / sombre / système des Réglages s'applique aussi aux barres système.
- **Aide** : chaque carte est accompagnée d'un pictogramme ; le dialogue de saisie du Casier de Davy Jones affiche aussi un pictogramme par créature marine.

### Précisions V1.2 : saisie des plis en direct

- **Écran « en jeu »** : un suivi « Pli n sur N » remplace l'attente passive. Après chaque pli, le scribe touche « Saisir le pli n », indique qui l'a gagné (ou « pli détruit » en cas de Kraken) et les cartes capturées par le gagnant (14, sirène, pirate, Skull King, Second, 7 / 8 selon les cartes en jeu). Les plis saisis sont listés, corrigeables un à un, et le dernier peut être annulé.
- **Validation directe** : dès qu'un pli est saisi, le bouton du bas devient « Valider la manche » (l'étape « Saisir les résultats » n'est plus nécessaire). Une alerte de cohérence éventuelle s'affiche avant validation (« Valider quand même » / « Corriger »). Les réglages liés aux pirates se font dans la fenêtre de saisie du pli : le **pari de Rascal le Flambeur** (0, 10 ou 20, pour le gagnant du pli) et l'**annonce ajustée par Harry le Géant** (±1, pour le gagnant du pli), saisissable à n'importe quel pli, dernier compris. Le **Boulet de canon** se choisit avec l'annonce, à l'écran des annonces. L'écran des résultats reste disponible pour corriger une manche, et pour une saisie manuelle quand aucun pli n'a été saisi.
- **Pré-remplissage** : les plis remportés par chaque joueur, les cartes capturées et les plis détruits sont recopiés automatiquement dans la manche. L'écran des résultats affiche ces valeurs, toujours modifiables, et le score se met à jour en direct sur l'écran de jeu (« Annonce 1 · plis 0 »).
- **Butin et Casier de Davy Jones** : ils restent saisis en direct, rattachés au pli en cours (« Pli 3 · Butin joué par… »). Le gagnant d'un Butin n'est plus demandé : il est renseigné à la saisie du pli. Un Butin joué dans un pli détruit ne rapporte rien.
- **Données** : nouvelle entité `Trick` (manche, numéro, gagnant ou `null`, cartes gagnées, pari de Rascal, ajustement de l'annonce par Harry, carte et pirate gagnants facultatifs), numéro de pli sur `LootLink` et `DavyJonesCapture`. Le schéma Room reste en version 1 (l'application n'est pas encore publiée, aucune migration n'est nécessaire) ; l'export JSON inclut les plis (champs facultatifs, format 1).
- **Contrôles** : l'application ne vérifie toujours pas la validité des plis saisis ; les alertes de cohérence de l'écran des résultats s'appliquent aux totaux.
- **Fenêtre de saisie du pli, version dense** : libellés courts (« 14 de couleur », « Sirène (par un pirate) »… sans le mot « capturé », puisqu'il s'agit toujours de cartes gagnées), compteurs compacts, et réglages affichés sur une seule ligne. « Pli détruit » n'est proposé que tant qu'aucun gagnant n'est sélectionné ; un second toucher sur le gagnant le désélectionne. Aucune description n'accompagne le réglage de Harry le Géant.
- **« Gagné avec » (obligatoire)** : une fois le gagnant choisi, on indique la carte avec laquelle le pli a été gagné : Sirène, Skull King, Second (si l'extension est activée), Pirate ou Autre (carte de couleur, atout noir ou fuite, sans plus de détail). Toucher « Pirate » ouvre une liste déroulante pour choisir lequel (Rosie la Douce, Rascal le Flambeur, Will le Bandit, Juanita Jade, Harry le Géant, la Tigresse, Mary Thorne si l'extension est activée) ; le pli ne peut pas être validé sans ce choix. Les réglages propres à un pirate dépendent de celui qui gagne : le pari de Rascal n'apparaît que pour Rascal le Flambeur et l'ajustement de l'annonce que pour Harry le Géant. Ces réglages s'affichent juste sous « Gagné avec », avant les cartes gagnées. Ce choix n'influence pas le calcul du score.
- **Statistiques** : l'écran des statistiques d'un joueur détaille les plis gagnés avec chaque carte et, pour les pirates, avec chaque pirate, d'après les plis saisis en direct (parties terminées du filtre courant).
- **Aide** : l'aide n'est plus une longue page : elle est organisée en quatre onglets (Cartes, Points, Extension, FAQ) et comporte une barre de recherche. Dès qu'on saisit un texte, les onglets laissent place aux seules lignes correspondantes, groupées par rubrique, avec les occurrences surlignées ; la recherche ignore la casse et les accents, et ne tient pas compte des mises en forme (« 14 » trouve « 14 noir »).
- **Saisie manuelle des résultats** (sans saisie des plis, ou pour corriger une manche) : l'écran est organisé en onglets. Le premier, « Manche », regroupe les plis défaussés, les alertes de cohérence (rappelées par ⚠️ sur l'onglet quand on est sur la page d'un joueur) et les événements en direct (Butin, Casier de Davy Jones). Il est suivi d'un onglet par joueur (plis, annonce finale, cartes gagnées, pari de Rascal, Boulet de canon), ce qui évite de faire défiler une longue liste. « Valider la manche » affiche les alertes éventuelles avant de valider.
- **Statistiques sans filtre de période** : l'écran des statistiques n'a plus que le filtre par mode de calcul (Classique, Rascal, Rascal + Boulet de canon), qui reste indispensable puisque les scores de deux modes ne se comparent pas. Les statistiques portent sur toutes les parties terminées. L'export garde son filtre de période.
- **Historique condensé et recherche** : chaque partie est une carte repliée (date, mode, vainqueur·s) qui se déplie pour montrer le classement complet, avec « Ouvrir » et la suppression. En haut de l'écran, une recherche (joueur, date, mode ; insensible à la casse et aux accents) et des filtres par mode permettent de retrouver une partie dans un long historique.
- **Podium** : flèche de retour en haut à gauche (vers l'historique ou l'accueil selon l'origine) ; le bouton « Retour à l'accueil » est supprimé, il reste « Voir le tableau des scores ».
- **Tableau des scores** : la fonction « Recalculer en » est supprimée. Les totaux sont affichés juste sous l'en-tête des joueurs (fixes pendant le défilement des manches) et les colonnes se partagent toute la largeur quand il y a peu de joueurs.
- **Nouvelle partie** : les profils disponibles sont des pastilles compactes (un appui ajoute le joueur à la table) suivies de « Nouveau profil », avec un champ de recherche dès que la liste dépasse 8 profils ; la configuration (mode, extension) reste ainsi à portée sans long défilement.
- **Tableau des scores** : une manche sur deux est légèrement grisée pour faciliter la lecture des lignes.
- **V2 réalisée (hors publication)** : partage du tableau en image, annuler / rétablir, donneur, réglages (écran allumé, sauvegarde automatique), « Rejouer », courbes et statistiques enrichies, face-à-face, import avec aperçu, adaptation aux tablettes, accessibilité, tests instrumentés. Détail dans `spec/Spécification V2 – Application Android de comptage Skull King.md`.

