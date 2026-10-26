# Correctifs confirmés de la reprise V0.29

| ID | Défaut | Correction | Preuve |
|---|---|---|---|
| FG29-01 | Page partagée entre catalogues, page sautée/relue après échec partiel | Curseurs par source, reprise source/page identiques, arrêts des sources épuisées | reliability-v029.test.cjs et browser_v029.py |
| FG29-02 | Réponse sans tableau annoncée vide avec succès | Contrat strict + message fournisseur | before-after-reproductions.json, cas2 |
| FG29-03 | Une nouvelle date importée perdue avant ajustement du compteur | Union de dates avant calcul du compteur | before-after-reproductions.json, cas1 ; JVM ReliabilityRulesTest |
| FG29-04 | Identités publiques contradictoires autorisées à fusionner | Contrôles TVmaze/Apple/FreeToGame et normalisation OL | before-after-reproductions.json, cas3 |
| FG29-05 | Notes et dates d’épisodes mises en danger au réimport par numéro | Identité compatible requise et priorité locale | VM du vrai importeur + régressions import |
| FG29-06 | Ancienne réponse Trakt401/refresh/pagination utilisable après déconnexion | Contrôles de génération aux frontières réseau/stockage | Scénarios browser_v029 ; réponse tardive et deux comptes |
| FG29-07 | Tâche annulée attend la file ou sa temporisation avant rejet | Annulation pendant attente et libération de slot | Tests réseau contrôlés |
| FG29-08 | Titres non latins rapprochés vers une même chaîne vide et analyse non bornée | Normalisation Unicode, limite et avertissement partiel | Tests de règles/DOM |

Les tests de la première campagne ont aussi révélé un ancien montage VM ne chargeant pas le nouveau module FGReliability. Le test a été raccordé au même module que l’application, sans assouplir les attentes. La première exécution en erreur est conservée sous tests/results/v029/initial-attempts/. Le correctif d’un sélecteur dans le nouveau scénario DOM n’a pas changé les règles de l’application. La campagne finale est distincte.

---

## Registre historique conservé

# V0.28 — défauts confirmés et protections de la nouvelle référence

| Cas | Correction | Preuve |
|---|---|---|
| V0.27 accepte un EAN13 produit sans préfixe ISBN (`4006381333931`) | ISBN13 limité aux préfixes978/979 avec contrôle de clé ; ISBN10 toujours accepté | Exécution du validateur du ZIP fourni vs actuel, confirmed-base-defect.json ; Node/JVM/DOM |
| Enrichissement Open Library Web ignore le titre de l’œuvre reçue | Valider le titre non vide et actualiser la métadonnée d’œuvre, sans écraser la référence personnelle | Assertion du parcours d’ajout ISBN ; actualisation et notes préservées |
| Chargement d’une nouvelle page rajeunit la date présentée pour une édition reçue auparavant | Date de consultation conservée par entrée et utilisée lors du choix | Scénario Chromium deux pages ; horodatage enregistré comparé |
| Nouvelle référence d’édition risquant de modifier une autre fiche au même titre | Référence d’œuvre/édition/ISBN obligatoire, ambiguïté locale refusée | Node, JVM et Chromium |
| Référence ou total modifié après préparation | Refus de confirmation obsolète, copie de sécurité, rollback sur erreur d’écriture | Plans purs et scénarios de stockage |
| Référence perdue à l’actualisation ou lors du transfert Android | Transport V11, codecs validés et préservation des aliases/champs personnels | Node/JVM/typecheck ; true org.json encore non exécuté |

Les essais ne prouvent pas l’attribution réelle d’un ISBN, le réseau Open Library réel, la persistance installée ni un APK. Le rapport QA distingue les résultats des campagnes historiques conservées ci-dessous.

---

# Défauts corrigés dans V0.27

| Cas | Correction | Preuve |
|---|---|---|
| `number_of_pages_median` utilisé comme total personnel d’un livre nouvellement ajouté | Total initial0/inconnu ; choix explicite des pages d’une édition | tests/book-editions-v027.test.cjs, tests/browser_v027.py, recherche native source |
| Identifiant court `OL…W` concaténé à l’hôte OpenLibrary ou non reconnu équivalent au chemin | Clé canonique `/works/…` et alias sûr | tests JS + JVM ; parcours d’ajout DOM |
| Détail d’œuvre retourné pour une identité différente | Rejet avant commit | validation client + tests contrôlés |
| Couverture négative avant une couverture valide | Première référence numérique positive sélectionnée | tests normalisation |

Les premières vérifications de la passe ont également détecté un appel JSONArray.opt absent des signatures de test utilisées ; la lecture des indices bornés utilise get. Ce n’était pas un APK compilé. Le contrôle structurel du nouveau plan a été corrigé pour lire le nom réel du champ `plan.pages` ; les tests de comportement demeurent indépendants.

---

# Registre V0.26 — défauts confirmés et limitations

## V026-01 — Historique transféré entre deux IDs TVmaze occupant le même numéro
**Reproduit sur la fonction commitCatalogue V0.25.** Un ancien `tvmaze-episode-100` vu, noté et commenté à S01E01 était repris pour une nouvelle entrée `tvmaze-episode-200` à S01E01. Le commit utilisait le rapprochement par emplacement après échec du match d’ID. Le nouvel épisode pouvait donc reprendre l’histoire du précédent et le nouvel identifiant n’était pas enregistré correctement.

**Correction :** absence de fallback numéro pour le fournisseur public ; mêmeID exact uniquement. Conservation de l’ancien épisode suivi comme orphelin côté Web, nouveau record sans observations héritées. Source native branchée sur le même critère. Preuves : tests/tvmaze-detail-v026.test.cjs (véritable fonction Web exécutée en VM), PublicTvDetailRulesTest.kt (identités pures) et comparaison interplateformes. Pas de test de base de données Android réelle.

## V026-02 — Détails et profils publics renvoyés vers le mauvais parcours fournisseur
Les fiches TVmaze de V0.25 étaient ajoutables sans clé, mais les parcours de détail restaient essentiellement TMDb et le casting public ouvrait le site externe. V0.26 fournit le routage interne explicite et conserve le lien officiel de secours. Un même numéro n’est jamais supposé être une personne/épisode de l’autre fournisseur. Tests navigateur avec clefs vides, retour, ajout de série, texte privé et suivi inchangés.

## V026-03 — Validation trop permissive des dates publiques et ports d’images
Le parseur V0.25 acceptait n’importe quel suffixe après T pour une date. V0.26 accepte uniquement un timestamp ISO exploitable, heure et jour valides ; tests Apple publics hérités conservés. Une image publique avec un port non autorisé est rejetée. Tests purs et clients sources.

## V026-04 — Nouveau champ de filtre de personne trop petit sur mobile
Observation visuelle dans la première capture V0.26 : le champ héritait du style input par défaut. Correction locale44px, capture et assertion de taille à390/768/1360. Les captures utilisent une image unie de fixture, pas un portrait réel.

## Non fermé par cette livraison
Les tests n’affirment ni disponibilité réelle TVmaze ni traduction garantie, ni mapping Trakt des épisodes publics. Android SDK/Compose et compteurs en stockage Android réel, PWA installée, persistance entre vrais onglets, authentifications réelles et audits commerciaux restent ouverts. Les nouvelles consultations ne remplacent pas une récupération persistante complète du catalogue.

---

# Registre des défauts — V0.24

| Défaut confirmé / risque ciblé | Correction | Preuve actuelle |
|---|---|---|
| V0.23 : une réponse mémorisée sous une politique de taille large peut satisfaire une requête ultérieure avec plafond plus bas | Clé inclut taille, délai, credentials, mode, redirection, cache et politique de retry ; no-store / reload / no-cache non mémorisés | Reproduction réelle des deux fichiers réseau : ancien code accepte au-delà du nouveau plafond avec1 requête ; nouveau code refuse après lecture bornée. `confirmed-baseline-defects.json` et tests Node |
| Source Compose : action disponibilité du bloc `sp` utilise `season` qui n’est pas défini à cette portée | Utilise `sp.season` | Comparaison source ZIP V0.23, contrôle structurel ; **pas** de prétendue compilation Compose |
| Édition personnelle d’épisode susceptible d’écraser un changement concurrent | Plan ciblé rating/userNote, identité, contexte, délai10min ; applique aux autres données courantes sans les remplacer | RèglesNode/JVM et scénarios DOM |
| Mauvais épisode renvoyé, identité ou crédits contradictoires | Rejeter avant remplacement du relevé temporaire | Node/JVM et réponses simuléesDOM |
| Réponse tardive après abandon/clé/langue/route/époque modifiée | Invalidation par génération et comparaison de contexte ; relevé/brouillon conservés | ScénariosChromium à fixtures |
| Distribution d’un épisode non vu révélée par nom/rôle ou titre | Masquer contenu et recherche tant que la révélation explicite n’a pas eu lieu | Node/DOM et source Compose |
| Note vide, score catalogue0 et durée0 confondus avec une valeur manquante | Nullable explicite, zéro validé et score personnel séparé | Node/JVM et écransDOM |

La première tentative du nouveau test navigateur ciblait tous les h2 du document, y compris ceux des dialogues masqués ; le sélecteur a été restreint au contenu principal. Cet échec de test n’est pas présenté comme un défaut de l’application et sa trace est conservée séparément. Les chiffres finaux viennent uniquement de la campagne disponible terminée.

Aucune preuve d’absence de tous les bugs, de fonctionnement Android SDK, de connexion fournisseur réelle ou de droits commerciaux n’est déduite de ces contrôles.

---

# Corrections V0.23 — disponibilités pays et saisons

| Défaut/risque | Correction | Preuve et limite |
|---|---|---|
| Ancien relevé de noms réétiqueté avec la région des réglages au rendu | Pays/date inconnus explicités ; nouveau panneau garde son pays de lecture | Scénario FR→US sur ancien relevé de saison, nouveau résultat isolé |
| Abonnement/location/achat aplatis en une liste de services | Nouveau modèle par catégorie et identifiant | Règles Node/JVM + cinq catégories DOM |
| Mauvaise identité ou payload partiel remplace un relevé | Validation atomique avant publication | Corps malformés, pays absent/présent vide, ID contradictoire et limites testés |
| Retour réseau après changement de contexte | Sérialisation des requêtes et gardes de pays/portée/clé/epoch/navigation | Requêtes volontairement retenues puis libérées dans scénarios DOM |
| Échec actualisation présenté comme fraîcheur | Dernière observation complète gardée avec son horodatage initial | Cache5min, force, erreur503 et429 testés |
| Nouveau transport pouvait consommer un corps illimité | Limites explicites aux appels du panneau, décodage UTF8 strict | ContentLength, flux réel octets, UTF8 multioctet/invalides et annulation lecteur |
| Faux lien de lecture/profil prix inventé depuis données faibles | Sortie TMDb fournie exacte, hôte/type/ID/pays contrôlés ; texte vide explicite | Liens externes, credentials, fragments, mauvais média rejetés |
| Sélections mises en cache exportées comme données métier | Cache runtime uniquement, effacé sur remplacement/effacement | Comparaison état sauvegardé et JSON avant/après lectures |
| Première assertion navigateur comparait l’heure de création de deux exports | Comparaison de l’état et du stockage inchangés, pas du timestamp généré à l’export | Correction d’oracle de test, pas de changement métier caché ; trace ancienne séparée |

Aucune garantie d’absence totale de bugs. Le premier défaut est confirmé dans la V0.22 fournie ; les autres sont des protections et nouveaux cas de la fonctionnalité, pas tous des bugs préexistants annoncés comme exploités.

---

# Corrections V0.22 — gestion sûre des épisodes

| Défaut / risque | Correction | Vérification et limite |
|---|---|---|
| Ignorer un épisode vu remplaçait watched/plays/dates | Ignorer protège toute trace d’historique ; réactiver ne crée pas de lecture | Nouvelles règles Node/JVM ; scénario du bouton individuel et du lot |
| Actions de saison mutaient toutes les lignes sans aperçu détaillé | Préparation partagée, protection des ignorés/futurs/inconnus, aperçu, confirmation et backup | Scénario de bouton de saison et scénarios du gestionnaire |
| Bouton individuel nonvu effaçait en un clic | Aperçu Web ; confirmation et snapshot source Android | DOM Web exécuté ; natif UI non SDK-validé |
| Une action différée pouvait écraser un suivi plus récent | Gardes par champ visé et identité, validation de toutes les lignes puis commit | Refus d’un champ changé ; nouvelle note/lecture conservée pour une opération de collection |
| Lot préparé avant changement de compte/bibliothèque | Vérification génération/session et blocage pendant sync | Tests DOM ; verrou repository côté source |
| Action de saison native sauvait potentiellement morceau par morceau | Plan unique, verrou et rollback mémoire | Règles JVM, compilation repository sur signatures ; pas stockage appareil |
| Copie de sécurité ou écriture refusée | Refus sans faux succès ; rollback, brouillon de récupération Web conservé | Pannes de stockage et backup contrôlés |
| Nouveau gestionnaire prenait le retour haut comme retour à la racine | Identité de la série portée dans returnTo | Deux chemins de retour testés |
| Première retouche du branchement saison a laissé un ancien bloc, syntaxe invalide pendant le développement | Bloc remplacé complètement et contrôlé avant reprise de la campagne | Ancienne erreur conservée comme trace de développement, résultat final distinct |
| Champ de recherche non aligné avec le thème tactile | Dimensions, couleur et bordure partagées ; consentement de dates masqué hors action vu | Inspection visuelle et captures390/768/1360 |

Les anciens tests affirmant que « ignoré gagne sur vu » ont été remplacés par une attente de conservation de l’historique. C’est un changement explicite de règle, pas une exclusion de leurs assertions. Le suivi ignoré n’est pas une suppression Trakt. Pas de garantie d’absence de tous les défauts.

---

# Corrections constatées V0.21

| Défaut / risque | Correction | Vérification |
|---|---|---|
| Ancienne liste Web rendue entièrement | 60 références puis suite, recherche/filtre sur toutes les œuvres | Nouveau scénario175membres, présence hors première page |
| Suppression de liste sans copie préalable | Copie obligatoire et aperçu confirmé ; œuvres conservées | Refus de sauvegarde + succès sans perte d’historique |
| Modification de membres depuis un écran périmé | Préparation pure puis revalidation liste/membres, refus global | Conflits définition/membres/title retiré ; notes étrangères préservées |
| Création/édition native sans rollback d’échec | Transformation préparée sous verrou et restauration des listes/media | Règles JVM + signature de repository ; stockage téléphone non validé |
| Nom/description anciens dépassant les limites éditoriales | Lecture conservatrice sans troncature ; bornes120/3000 uniquement lors d’édition explicite | Tests JS et JVM |
| Série faussement à jour avec numéros inconnus/dupliqués | État chargé exige catalogue complet, positions connues/distinctes et quota attendu | Tests JS conservateurs |
| Hook de retour liste écrasait le retour acteur→série (introduit pendant la passe) | Réemploi de returnTo hors titre ouvert depuis une liste | Régression browser_v08 échouée puis corrigée, nouveau cas V0.21 |
| Champ de recherche de liste trop petit/natif | Hauteur44px et thème cohérents, largeur bornée | Captures390/768/1360 et absence de débordement |

La défaillance d’intégration détectée pendant le développement est conservée dans `tests/results/v021/development-back-navigation-failure.log`. Elle n’est pas comptée comme une réussite : le parcours corrigé est rejoué dans la campagne finale. Ces corrections ne garantissent pas l’absence de tous les bugs.

---

# V0.20 — corrections et défauts détectés pendant la passe

| Défaut ou risque confirmé | Correction | Vérification / réserve |
|---|---|---|
| Réduire la progression jeu/livre pouvait retirer ses anciennes fins | Parcours explicite séparé des fins historiques ; abandon/pause/reprise non destructifs | Node, Chromium, JVM ; Android UI non exécutée |
| Un jeu terminé historiquement mais recommencé pouvait rester hors de l’onglet en cours | Statut courant prioritaire pour ces onglets | Tests Web et règles Kotlin |
| Statistiques mélangeant activités agrégées, dates inconnues et durées par défaut | Sélection/événements séparés, pas de lecture agrégée de série, inconnus signalés | Node/JVM/parité de compteurs et DOM |
| Pages courantes et chapitres/% pouvaient être agrégés sans unité cohérente | Pages/page seulement ; parcours courant non multiplié | Règles et UI testées |
| Risque d’addition des temps Steam de plusieurs profils ou d’un même App ID répété | Profil isolé ; doublons signalés ; max connu une seule fois par App ID | Fixtures Node/JVM et affichage Web |
| Réparation répétée d’affiches absentes et premières fiches défaillantes monopolisant le lot | Critère de complétude distinct, file éligible, backoff et sélection bornée | Node/DOM avec erreurs contrôlées |
| Réponse catalogue tardive pouvant correspondre à une bibliothèque remplacée | Garde de génération, source et identité avant commit | Scénario Chromium |
| Premières implémentations du nouveau lot continuaient après annulation | Drapeau d’annulation explicite pour couper tout le lot | Défaut détecté pendant cette passe, corrigé et rejoué |
| Première nouvelle vue statistiques retirait le graphique de genres existant | Répartition sûre Map, franchises, favoris/collections et séquences de jours conservés | Régression historique détectée puis relancée avec succès |
| Ancien raccourci durée RAWG assimilé à la progression personnelle | Estimation catalogue séparée ; champs saisis prioritaires | Domaine et actualisation à fixtures |
| Fusion de deux parcours explicitement incompatibles choisissait une valeur | Refus des conflits ; historique daté connu réuni séparément | Tests d’intégrité additionnels |
| Note privée sur épisode orphelin pouvait être mal protégée dans les sources Android | Protection et réparation sous garde/rollback sans écriture Trakt | Compilation non-UI/règles ; stockage appareil non exécuté |
| Rapports détaillés pouvaient être pris pour un export anonyme | Rapport numérique sans libellés distinct du CSV/JSON d’historique ; avertissement de confidentialité | Contrôles d’export et documentation |

Les dates/fuseaux des tests ont été ajustés pour garder une horloge cohérente lors de la vérification d’historique, sans autoriser des dates futures. Le scénario de reprise de langue avance explicitement le délai de nouvelle tentative. Les versions futures rejetées sont maintenantV10 puisqueV9 est valide. Les essais antérieurs échoués conservés dans `tests/results/v020/earlier-attempts/` ne sont pas les résultats finaux. Ils ne sont pas ajoutés au bilan de réussite.

**Réserves :** pas de SDK/Compose/appareil, de compte fournisseur réel ou d’origine PWA validée ; pas de garantie générale « sans bug ». Les écarts et exclusions sont détaillés dans QA_REPORT.md et FEATURE_MATRIX.md.

---

# V0.19 — défauts corrigés et portée de validation

| Défaut confirmé dans V0.18 / pendant QA | Correction | Vérification |
|---|---|---|
| addCustomFranchise Android changeait uniquement la liste mémoire | Passe par changeFranchise + save(false), garde synchronisation et restauration des listes en cas d’erreur | Compilation de signatures et règles JVM, pas de runtime Android |
| Aucune manuelle pouvait être ré-inférée par les alias | Priorité manuelle explicite, y compris id nul, dans les deux règles et l’ancien moteur | Node, JVM, UI Web |
| Identifiant de franchise présent sans définition pouvait disparaître des vues Web | Définitions récupérées et édition explicite | Node, JVM, DOM |
| Univers personnel vide inaccessible | Carte conservée | Node, JVM, DOM |
| Crossover ou édition locale classés/confondus par heuristique de titre | Groupe principal et identités locales/fournisseurs distinctes | Node et JVM |
| Enrichissement collection TMDb remplaçait le libellé manuel | Ne réaffecte pas les fiches manuelles et n’introduit pas de frères implicitement | DOM avec collection contrôlée, source native |
| Compteurs mélangeaient titres retirés et suggestions | Actifs masqués restent ajoutés ; retirés ne sont pas des candidats | Node et JVM |
| Long nom d’univers repoussait recherche/réglages hors écran390px | min-width0 et ellipsis au titre de barre, boutons stables | Test DOM initial échoué puis corrigé et rejoué |
| Nouvelle normalisation confondait deux noms emoji et supprimait les lettres nonlatines | Lettres Unicode et clé de nom de repli, sans fabriquer d’identifiant commun | Régression legacy adaptée au formulaire + Node/JVM |
| Nouvelle validation rejetait un retour à la ligne normal dans description | Description multiline bornée, autres contrôles dangereux refusés | Node/JVM |
| Dialogue de recherche fermé après ajout ne rafraîchissait pas immédiatement le compte | Rendu du groupe à la fermeture, sans écriture additionnelle | Implémentation et campagne DOM |

Les fichiers de la première campagne échouée et les résultats finaux sont conservés séparément. L’échec du vieux test basé sur window.prompt nécessitait une adaptation à la nouvelle interaction par formulaire ; le contrôle de doublons a été maintenu, pas supprimé. Cette adaptation a également révélé le cas réel des deux noms emoji, corrigé dans le produit.

La validation ne garantit ni parité complète avec SeriesGuide, ni absence de tous les bugs, ni qualité du canon d’une recherche fournisseur. Voir QA_REPORT.md pour les essais non exécutés.

---

# Passe V0.18 — défauts reproduits et invariants d’import

| ID | Constat / correction | Preuve et périmètre |
|---|---|---|
| FG-018-01 | L’ancien sélecteur Web décodait les fichiers sans UTF‑8 fatal. Octets invalides désormais refusés avant application. | Production File/arrayBuffer/TextDecoder dans Chromium contrôlé ; pas de fichiers réels utilisateur publiés |
| FG-018-02 | Import SeriesGuide pouvait remplacer une durée d’épisode existante par la durée de série/par défaut. Conservation du champ connu, y compris0. | Tests Node de stage et réimport DOM |
| FG-018-03 | L’ancien codec Android ne transportait pas le texte privé d’un épisode Web. Ajout du champ au modèle/codec/merge. | Sources + compilation signatures, règles de copie JVM ; parser Android réel non testé |
| FG-018-04 | Un remplacement pouvait être déclenché sans comparaison de l’impact ou avec un aperçu devenu obsolète. Préparation isolée, empreinte des données/réglages/révision et confirmation. | Node/JVM/DOM et snapshot+quota injectés |
| FG-018-05 | Préférences publiques importées pouvaient rester seulement en mémoire face au chargement depuis les credentials. Enregistrement dans le commit de l’état et relecture. | Inspection et compilation repository ; panne/restart réel Android non testé |
| FG-018-06 | Normalisation permissive d’une date impossible par Date JavaScript. Validation du jour/heure ; acceptation explicite de l’ISO8601 complet du format officiel. | Tests leap-day, jour impossible, décalage et fixture d’import officiel-forme |
| FG-018-07 | Un vieux état watched vrai court-circuitait la validation du drapeau entrant. Tous les drapeaux pris en charge validés avant fusion. | Nouveau test avait échoué, correction du parser puis répétition réussie |
| FG-018-08 | Le nouveau compteur de dates excluait aussi par erreur les notes privées des séries. Les notes sont comptées séparément de l’historique agrégé. | Test Node dédié + fixture de parité JS/Kotlin |
| FG-018-09 | Une simple vérification cumulative après avoir lu tous les fichiers serait trop tardive. Budget restant appliqué séquentiellement aux streams natifs ; fermeture et octets BOM testés. | Règles JVM avec streams contrôlés, pas stockage Android réel |
| FG-018-10 | L’écran À propos affichait une version ancienne et présentait RetroAchievements comme non connecté malgré le code livré. Texte/version alignés avec le périmètre existant. | Source Web et ressources/JAR recompilés |

La première attente du test IndexedDB cherchait un nom de fichier dans un `<details>` fermé. Le test a été corrigé pour ouvrir le détail, sans modifier l’application afin d’afficher plus de données privées. Un contrôle de date provisoire trop restrictif refusait l’heure ISO officielle ; il a été corrigé puis couvert par le test de régression et la documentation officielle. Ces étapes ne sont pas comptées comme des succès de la version finale tant que les suites correspondantes n’ont pas été rejouées.

---

# Passe V0.17 — défauts constatés et protections vérifiées

| ID | Type | Constat / correction | Preuve |
|---|---|---|---|
| FG-017-01 | Défaut fonctionnel | L’ancien raccourci Séries filtrait les dates avant l’ordre : un épisode ultérieur diffusé pouvait être marqué malgré un précédent inconnu/futur. Réutilisation des règles next-up, refus du saut. | library-v017.test.cjs, LibraryRulesTest.kt, browser_v017.py |
| FG-017-02 | Défaut source Android | Une série sans épisodes chargés pouvait être présentée comme À jour, et le titre du prochain épisode apparaître malgré l’anti-spoiler. Sous-titre dérivé du même modèle prudent et masqué. | Règles JVM ; UI source, pas SDK |
| FG-017-03 | Défaut de mise en page | Champ de recherche nouvel écran limité par une largeur héritée. CSS ciblé avec champs44px, captures responsives rejouées. | Captures390/768/1360 dans results/v017 |
| FG-017-04 | Protection nouvelle | Une action groupée ou son annulation ne doit pas écraser une valeur visée changée, une autre note ou une autre appartenance de liste. Plans à préconditions champ par champ, aucune mutation partielle. | Node/JVM/Chromium, conflits et snapshots |
| FG-017-05 | Protection nouvelle | Ne pas afficher un succès d’action groupée quand snapshot ou écriture refuse. Copie requise, rollback/draft existants et annulation non enregistrée en échec. | Tests DOM avec panne simulée |
| FG-017-06 | Correction source Android | L’empreinte de notification n’incluait pas toutes les valeurs locales modifiées par l’organisation. Ajout added/hidden/title au déclencheur de replanification. | Inspection source ; pas de validation notification OS |

Ces preuves concernent des défauts lus dans le code/reproduits en test et des invariants préventifs, pas des incidents utilisateur inventés. Une réussite des tests ciblés n’est pas une preuve d’absence totale de défauts.

---

# Corrections ajoutées en V0.16

| Référence | Défaut constaté dans le code réel | Correction | Preuve et limite |
|---|---|---|---|
| FG16-HISTORY-DATE | Une augmentation de compteur envoyait uniquement la dernière date agrégée : une nouvelle lecture plus ancienne pouvait envoyer la mauvaise date | Diff des événements datés connus, un envoi par date ajoutée, latest=max ; doublons à la même seconde rejetés | Tests Node/JVM et vrais formulaires DOM avec POST simulés ; pas de compte réel |
| FG16-ACK | HTTP200 avec `{}` pouvait retirer l'action de la file | Exige compteur numérique non négatif correspondant au type ; sans confirmation, conserve l'envoi historique incertain | Cas Node/Chromium ; Kotlin logique pure + client sur signatures, pas JSON Android réel |
| FG16-EPISODE-NOTE | Une actualisation de métadonnées perdait `userNote` d'un épisode encore présent | Champ privé local recopié lors du commit catalogue | Régressions et contrôle de source |
| FG16-RUNTIME-ZERO | `runtimeMinutes || 45` réécrivait0 en45 lors d'une migration Web | Valeur nulle seulement remplacée, 0 conservé | Test DOM de migration |
| FG16-NOTIFY-ID | Des épisodes dont les chaînes ont le même hash pouvaient partager le PendingIntent et la notification | Action à identifiant encodé complet, tag d'épisode ; annulation des anciennes requêtes hash-only | Fixture JVM Aa/BB + source ; AlarmManager/notifications non testés sur OS |
| FG16-NOTIFY-STALE | Un titre/texte d'alarme pouvait rester ancien après changement des réglages ou de la bibliothèque | Relecture des états, titre et préférence anti-spoiler à réception ; nouvelles clés de replanification | Source uniquement, pas preuve d'affichage natif |
| FG16-FORM-WIDTH | Les nouveaux champs de date héritaient d'une largeur maximum trop courte sur mobile | Champs de largeur disponible, une colonne sur petit écran, cibles44px | Captures Chromium390/768/1360 avec CSS réel |

La première exécution du nouveau test d'export JSON incluait volontairement un épisode orphelin, destiné au test d'agenda. Le parseur a correctement refusé ce JSON. La fixture a été corrigée avant le test de round-trip, sans assouplir le validateur. Ce n'était pas un défaut corrigé de l'application. Les fichiers finaux reflètent la campagne disponible relancée.

---

# Correctifs V0.15 — 6 octobre 2026

Défauts identifiés dans le code livré et reproduits dans des tests contrôlés, pas dans les comptes personnels de l'utilisateur. P1 : risque de données/identité ; P2 : fonctionnement/reprise/interface. La correction Web/PC n'est pas un correctif natif Android par défaut.

| ID | Priorité | Défaut ou risque | Correction / preuve actuelle |
|---|---|---|---|
| FG-015-01 | P2 | Délais gaming en mémoire perdus au rechargement ; nouveau clic trop précoce | Politique persistée et garde commune manuelle/automatique ; Node + DOM V0.15 |
| FG-015-02 | P2 | HTTP429 d'une liste ne protège pas les autres listes du même fournisseur | Échéance par Steam/RetroAchievements appliquée à tous les profils ; dates HTTP et secondes ; Node/DOM |
| FG-015-03 | P2 | Une grosse campagne exige des relances opaques sans compteurs durables | Lot explicite de toutes les listes retenues, checkpoint et passages de huit ; DOM18 listes, pause/rechargement/reprise |
| FG-015-04 | P1 | Réponse de session Steam ancienne réaffiche une identité après logout | Génération de session et garde pendant déconnexion ; deux sondes hors ordre et logout testés |
| FG-015-05 | P1 | Refus de sessionStorage après révocation empêche la suspension locale | Nettoyage protégé et arrêt des automatismes enregistré ; scénario DOM spécifique |
| FG-015-06 | P2 | Timeout de lecture confondu avec annulation volontaire, sans statut de reprise | Erreur temporisée distincte de l'annulation, conservation du relevé ; DOM timeout/abort/retour tardif |
| FG-015-07 | P1 prévention | Champs internes d'une importation pourraient imposer révision/file de contrôles | Retrait des champs racine privés ; export portable sans `_gamingControl` ; Node/DOM |
| FG-015-08 | P2 | Bandeau stockage inséré comme élément de grille PC, interface comprimée | Placement dans `#appShell`, vérification quota + captures 390/768/1360 |
| FG-015-09 | P2 | Relancer un lot après reload peut envoyer avant décision utilisateur | Checkpoint conservé mais lot manuel non repris automatiquement ; DOM |
| FG-015-10 | P2 | État visuel confirmé malgré refus de persistance d'une pause | Écriture atomique, rollback et brouillon conservé, pas de fausse confirmation ; DOMquota |

Preuves : `tests/sync-policy-v015.test.cjs`, `tests/browser_v015.py`, `tests/results/v015/available-qa.json` et journaux associés. Les tests jsdom/fake-indexeddb antérieurs n'ont pas pu être rejoués ici et sont signalés dans QA_REPORT.md.

---

# Passe V0.14 — défauts reproduits et protections ajoutées

| ID | Sujet | Résultat / preuve |
|---|---|---|
| FG-014-01 | Fusions perdant des historiques, notes ou informations Steam d’un second compte | Champs compatibles et événements distincts préservés ; régressions d’intégrité. |
| FG-014-02 | Identifiants contradictoires ou alias fournisseur absents du rapprochement | Contrôle des identités, réutilisation des alias exacts ; collisions explicitement bloquées. |
| FG-014-03 | Épisode suivi orphelin effacé ou vu/ignoré mal fusionné | Suivi conservé, éléments non identifiables gardés distincts ; tests d’intégrité. |
| FG-014-04 | Collision de listes de succès ou unités de progression incompatibles | Fusion refusée avec motif ; aucune décision silencieuse. |
| FG-014-05 | Requête IndexedDB réussie avant annulation de transaction | Réussite seulement après commit ; insertion et rotation atomiques, erreurs propagées. |
| FG-014-06 | Écriture/effacement concurrents ou copie modifiée dans une file | Opérations sérialisées et instantané copié à la mise en attente ; fake-indexeddb. |
| FG-014-07 | Réparation de l’état complet suivie d’une remigration pouvant perdre l’historique préservé | État réparé appliqué directement ; régression ciblée. |
| FG-014-08 | Échec d’effacement IndexedDB absorbé, message d’effacement complet trompeur | Erreur conservée et action signalée comme incomplète ; régression du flux d’effacement. |
| FG-014-09 | Import/lecture RetroAchievements partiel ou réponse distante obsolète | Validation avant commit, annulation, pages cohérentes et édition locale conservée ; Node/DOM/HTTP contrôlés. |
| FG-014-10 | Alias ou métadonnées gaming perdus dans le transport Android | Codecs réels JSON JVM et compilation non graphique ; runtime Android restant à valider. |

Les défauts ci-dessus proviennent du code et des scénarios de régression, sans incident utilisateur inventé. Les passerelles RetroAchievements constituent également une nouvelle fonction. Le détail des preuves et limites actuelles est dans `QA_REPORT.md`. Les passages suivants restent l’historique des versions antérieures.

---

# Passe V0.13 — intégrité, récupération et cohérence de stockage

| Sujet | Correction | Preuve |
|---|---|---|
| Clé Web restée `web-v7` alors que le format portable est V8 | Nouvelle clé `web-v8`, lecture/migration de V7 conservée | Chromium base |
| Échec `localStorage` sans voie durable indépendante | Copie best-effort IndexedDB, jusqu’à 5 instantanés | Tests module + branchement ; vraie origine encore à valider |
| Imports pouvant laisser références de listes/épisodes orphelins | Audit + réparation sûre, sans effacer un épisode suivi | Tests intégrité + DOM |
| Doublons possibles après imports multi-fournisseurs | Candidats signalés, fusion manuelle uniquement sans conflit d’identifiants | Tests intégrité |
| Relevé gaming orphelin | Signalé par audit ; pas supprimé automatiquement | Tests intégrité |
| Version Android et libellés backup obsolètes | Alignés sur 0.13.0 / schéma V8 | Validation structurelle |

**Aucune réparation automatique n’est autorisée à supprimer un état utilisateur suivi.** La récupération IndexedDB reste une protection additionnelle, pas le stockage primaire.

---

# Passe V0.12 — défauts corrigés et protections nouvelles

| Sujet | Correction | Preuve |
|---|---|---|
| Lots manuels relisant toujours les premières listes | Priorité aux listes sans lecture / les plus anciennes | browser_v012.py, bibliothèque de 20 jeux |
| Ancienne détection négative de passerelle retardant un nouvel essai manuel | Revérification de la passerelle lors d’une reprise manuelle si absente | Régression browser_v011.py |
| Effacement navigateur laissant une session serveur utilisable | Révocation préalable, blocage si serveur indisponible | browser_v012.py + tests serveur de révocation |
| Actualisation d’une fiche Steam annonçant un succès sans appel au fournisseur | Redirection vers le parcours d’import Steam | Contrôle de code ; parcours import couvert séparément |
| Recherche répétée des liens en balayant les listes | Index des liaisons, pas de comparaison de titres | steam-library-v012.test.cjs, 2 100 jeux |
| Metadata Steam perdue lors d’une fusion Android avec une fiche non-Steam | Conservation de l’ancien champ lorsque la source n’en fournit pas | Type-check source non UI ; runtime Android restant à vérifier |
| Erreur de retour OpenID brute et peu lisible | Page HTML locale avec lien de retour, sans paramètres réfléchis | Tests HTTP Node/Java et JAR réel |

Ne pas lire ce registre comme une certification « sans bug ». Les comptes réels, les appareils Android et le public hébergé restent non validés.

---
## Registre hérité

# Registre des défauts — passe0.10

Sévérité : P1 = risque important de données/sécurité ; P2 = fonctionnement/UX ; P3 = finition. « Corrigé source » ne signifie pas « validé au SDK ». Les symptômes ci-dessous viennent de l’inspection du code et des tests indiqués, pas d’incidents utilisateur inventés.

| ID | Sévérité | Défaut / conséquence | Correction | Preuve |
|---|---|---|---|---|
| FG-010-01 | P1 | Chargement Android futur/illisible pouvait aboutir à un état vide enregistré | Original préservé, récupération seule, garde des tâches/save/export | Contrat source Node et typecheck non-UI ; runtime Android encore requis |
| FG-010-02 | P1 | Entrées natives malformées pouvaient être ignorées au décodage | Validation des objets racine/id uniques/type de média et limites strictes | Règles JVM + contrats source ; parseur Android réel non exécuté |
| FG-010-03 | P1 | Références de listes/franchises importées interpolées en attribut HTML | Échappement de la sortie | Cas DOM d’injection inerte |
| FG-010-04 | P1 | Masquage des journaux pouvait conserver une partie d’un Bearer | Traitement du jeton complet avant les autres clés | Tests Node |
| FG-010-05 | P1 | Publication d’avis sans journal fiable ou ACK ambigu risquait répétition/perte de brouillon | Brouillon incertain avant POST, refus quota, ACK vérifié, reprise explicite | DOM panne/quota/double-submit/réouverture ; source Android |
| FG-010-06 | P2 | Validation avis à cinq caractères, différente des cinq mots Trakt | Règle commune cinq mots ; distinction revue longue200mots | Node/JVM/DOM |
| FG-010-07 | P2 | Pagination/actualisation avis remplaçait le contexte trop tôt | Pages distinctes, reprise même page, liste conservée sur erreur | DOM pagination/503 |
| FG-010-08 | P2 | Genres __proto__/constructor pouvaient perturber les statistiques | Dictionnaires sans prototype | DOM statistiques |
| FG-010-09 | P2 | Identifiants dérivés du nom pouvaient se confondre (emoji/slug), doublons non détectés | UUID et normalisation des noms | DOM |
| FG-010-10 | P2 | Structures de préférences/dates invalides atteignaient le rendu | Validation des types, dates calendaires et plages de timestamp | Node + DOM lecture seule |
| FG-010-11 | P2 | Lignes rolebutton contenant d’autres boutons | Titre avec bouton natif et parent group | DOM Entrée et contrat de structure |
| FG-010-12 | P2 | Absence d’une langue catalogue par titre | Exception optionnelle avec actualisation complète atomique | Sept assertions DOM ajoutées, règles JVM et Node |
| FG-010-13 | P1 prévention | Exécution du bootstrap après téléchargement non vérifié | SHA256 attendu avant extraction | Shell réel avec faux téléchargement refusé, PowerShell statique |

| FG-010-14 | P2 | Diagnostic PC pouvait attendre sans limite un service occupant le port et masquer l’erreur de version | Délais connexion/lecture, aucune redirection, message conservé | Neuf contrôles sur le vrai JAR avec service loopback contrôlé |

## Risques ouverts / non vérifiés

Android : UI Compose, parser/stockage réel, import volumineux et thread principal, rotation/changement de compte, travail de fond, notifications/widget. Web : localStorage synchrone et quotas, fenêtres réellement concurrentes, CSP/service worker sur origine HTTPS, plusieurs moteurs navigateur, acessibilité complète. Services : contrat API/OAuth réel, états de compte, pannes, limites et stabilité. Distribution : droits catalogue/images, inventaire transitoire des dépendances, signature, parcours d’installation et support.

Ce registre ne transforme pas ces risques en bugs déjà reproduits ; ils restent des prérequis à éprouver. Aucun certificat « zéro bug » n’est délivré.


## Passe V0.11

| ID | Sévérité | Défaut/risque | Traitement et preuve |
|---|---|---|---|
| FG-011-01 | P2 | Filtre d’une ancienne liste masque le contenu d’un nouvel import | Reproduit dans DOM, réinitialisation à sélection/import, scénario corrigé |
| FG-011-02 | P1 prévention | Relevé Steam incomplet/compte privé peut faire croire à une perte d’obtention | Validation complète et refus, ancien relevé conservé ; Node/DOM/JVM |
| FG-011-03 | P1 prévention | Réponse distante ancienne après import/remplacement/effacement | Génération/epoch/annulation ; réponses tardives ignorées dans DOM |
| FG-011-04 | P1 prévention | Quota après sync gaming écrase la copie de travail | Transaction et copie exportable conservée ; scénario DOM quota |
| FG-011-05 | P2 | Import SeriesGuide omet la langue catalogue propre à la série | Mapping langue et préférence locale conservés au réimport ; DOM + source Kotlin |
| FG-011-06 | P1 prévention | Confusion des profils/éditions et checklist avec vrais succès | Identités strictes, dates/états typés, aucun platine inféré ; Node/JVM/DOM |
| FG-011-07 | P1 prévention | Une passerelle peut exposer une clé ou devenir proxy libre | Clé environnement, endpoints fixes GET, limites/Host/Origin/timeout ; HTTP Java/Node simulant l’amont |

Les risques ouverts précédents restent ouverts. Aucune certification de sécurité publique ou de fonctionnement des comptes réels n’est déduite de ces contrôles.

## V0.25 — parcours de configuration et garde de données

1. Démarrage exigeant les clés de chaque utilisateur : sources publiques explicitement identifiées ajoutées ; accès éditeur détecté pour Web/PC. Tests public/managed config, ajout et autorisation à fixtures.
2. Confusion entre sources publiques et TMDb : IDs, scores, personnes et maintien du fournisseur d’une fiche ajoutée conservés ; aucun entier TVmaze réutilisé comme ID TMDb. Test d’actualisation préservant historique.
3. Override Trakt partiel : une paire personnelle incomplète n’est pas mélangée avec la paire éditeur ; chemin d’erreur explicite. Aucun accès privé fabriqué.
4. Découverte gérée : les marqueurs techniques de routage ne sont pas réenregistrés par le formulaire de filtres ; test de sauvegarde.
5. Cache proxy : pas de cache caché au serveur pouvant contredire l’actualisation explicite du frontend ; pas de retry POST. Tests Node/Java.

L’ancien test d’onboarding a initialement exigé « clé manquante » ; son nouveau contrat vérifie les sources publiques. Première exécution enregistrée, retest ciblé puis toute la campagne disponible relancée. Ce changement n’est pas une preuve que les vrais services privés sont activés.

## V0.30 — anomalies trouvées pendant la nouvelle implémentation

- **Copie binaire Node partagée** : le premier code utilisait `slice()` sur une entrée Uint8Array. Pour un `Buffer` Node, cela partage la mémoire ; le nettoyage final pouvait donc effacer le Buffer de l'appelant. L'interopérabilité a révélé l'erreur. Correction : copie explicite `new Uint8Array(value)`, avec test de non-mutation Buffer en plus des tableaux ordinaires. Ce n'était pas un défaut de V0.29, qui n'avait pas cet export.
- **Course à la fermeture du dialogue** : le listener de `close` pouvait s'exécuter après une réponse de chiffrement tardive. La première vérification `isConnected` ne suffisait pas. Correction : vérifier aussi `dialog.open`, annuler synchroniquement sur les boutons et sur `cancel`, puis ignorer le résultat. Scénarios annulé/export et Escape/déchiffrement rejoués. Première trace conservée dans `tests/results/v030/initial-cancel-race.log`.
- **Libellés d'export obsolètes hérités** : le menu Web annonçait V10 et le nom d'export Android V9 alors que le contenu portable était déjà V11. Les textes/noms concernés sont alignés, sans changer le schéma des données.
