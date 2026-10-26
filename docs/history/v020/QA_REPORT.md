# Rapport QA — FranchiseGuide 0.20.0

Date de contrôle : **6 octobre 2026**. Base réellement utilisée : Suite V0.19 RAR, SHA-256 `6ef63432d3b30203e3eb92c73fe8c8369e1eea0522f8a9d0a1bef32207f969a1`.

La campagne disponible a terminé **41 commandes réussies, aucun échec**, avec **trois exclusions déclarées**. Le détail des commandes, codes de retour et durées est dans `tests/results/v020/available-qa.json` ; les journaux individuels et `SUMMARY.json` sont conservés à côté. La recette complète `tools/run_checks.sh` existe, mais ne doit pas être confondue avec les tests effectivement réalisables sur cet hôte. Aucun ancien résultat de suite absente n’a été recyclé.

## Résultats exécutés

| Groupe | Résultat | Portée réelle |
|---|---:|---|
| JavaScript / Node | **897 tests réussis** | Dont82 nouveaux tests V0.20 ; zéro échec/annulation/test ignoré dans cette sélection |
| Chromium | **725 assertions réussies** | Dont68 nouveaux scénarios V0.20 ; vrais scripts, DOM et CSS, avec réponses/API/images/stockage/horloge contrôlés |
| Kotlin JVM | **496 assertions +1 scénario franchises réussis** | Dont72 nouveaux tests de progression/bilan ; une assertion de frontière du schéma ajoutée ; pas d’Android SDK |
| Parité JS/Kotlin | **4 comparaisons réussies** | Compteurs de bilan, franchises/ordre, compteurs d’import, fichier calendrier Unicode |
| Programme PC compilé | **75 contrôles HTTP +9 démarrage réussis** | Vrai JAR bytecode Java17, ressources embarquées et contraintes du serveur local |
| Passerelles Java | **265 assertions réussies** | SteamBridge28, SteamAuth53, RetroBridge184 ; fournisseurs simulés |
| Structure | **80 contrôles réussis** | Scripts, assets, XML, versions, nouveaux branchements et schéma |
| Bootstrap | **8 contrôles réussis** | Rejet de distribution corrompue ; une partie PowerShell est un contrôle statique |
| Native non-UI | **Compilation sur signatures réussie** | Modèles/clients/import/repository ; ne remplace pas JSON/SharedPreferences Android ni un build Compose |

Les chiffres désignent des assertions/scénarios, pas une mesure de parité avec SeriesGuide ni une garantie de qualité commerciale. Les répétitions d’un même test après correction ou conditionnement ne sont pas additionnées une seconde fois.

### Détail Chromium

Harnais historique64 ; V0.8 45 ; V0.9 39 ; V0.10 70 ; V0.11 64 ; V0.12 54 ; V0.15 63 ; V0.16 62 ; V0.17 72 ; V0.18 64 ; V0.19 60 ; **V0.20 68**. Total725. Les assertions historiques disponibles sont donc rejouées, pas seulement les nouveaux écrans.

### Détail Kotlin

TrackingRules42, TrackingV08 22, IntegrationRules15, ReleaseRules48, GamingRules52, SteamMetadata20, ActivityRules53, LibraryRules56, ImportReviewRules60, FranchiseRules56, InsightsProgressRules72. Total496, plus le scénario CoreLogicTest. Les codecs ont une vérification de compilation séparée, pas une exécution contre le vrai parseur Android.

## Ce que les nouveaux tests vérifient

### Parcours courant et historique

Réduire un avancement, mettre en pause, abandonner et recommencer conservent les fins déjà enregistrées et leurs dates. Une nouvelle fin ajoute exactement une date ; resauvegarder le même état terminé n’en ajoute aucune. L’effacement explicite reste distinct. Le total inconnu vaut0, pas un total inventé ; un avancement supérieur au total connu est refusé. Les champs personnels d’édition, plateforme et minutes jouées survivent à l’actualisation. Une fusion refuse les parcours explicites contradictoires au lieu de choisir un maximum silencieux.

### Bilan et historique

La période filtre les événements datés connus et non la bibliothèque entière. Les compteurs sans dates, les dates futures et les durées absentes/0 sont signalés au lieu d’être remplacés par des valeurs fictives. Une date agrégée de série ne crée pas un événement supplémentaire. Les pages courantes ne sont pas multipliées par les anciennes lectures ; pourcentages/chapitres ne sont pas additionnés aux pages. Les minutes Steam restent par profil et par App ID, avec doublons signalés. Les succès normal/Hardcore, cases personnelles et platines explicitement renseignés ne se remplacent pas mutuellement.

Le bilan numérique JSON ne contient pas les titres, textes privés, identifiants ou libellés de profils. Il ne s’agit pas pour autant d’une certification d’anonymisation. L’historique détaillé contient titres et dates ; son CSV réel est contrôlé en octets (UTF-8/BOM, délimiteurs, échappement de formules). La recherche porte sur toute la sélection, avec100 événements au premier rendu puis100 supplémentaires. Au-delà de10000 événements à exporter, une réduction des filtres est demandée, pas une troncature implicite. Aucun tableur externe n’a été utilisé pour déclarer une interopérabilité réelle.

### Entretien catalogue Web/PC

Un lot manuel traite8 fiches au maximum, une passe automatique3. Les réponses sont préparées avant leur application. Les données personnelles modifiées pendant l’attente restent prioritaires. Les épisodes protégés par du suivi/texte privé ne sont pas supprimés silencieusement. Une fiche en échec entre en attente sans empêcher de lire les suivantes. Un délai429 concerne le même fournisseur, pas les autres. Une nouvelle tentative manuelle n’ignore pas le délai. Les résultats d’avant une restauration/remplacement sont rejetés.

L’annulation arrête le lot entier et n’est pas déclarée comme panne fournisseur. Un véritable dépassement de délai reste une erreur avec reprise différée. Une image absente sur une fiche complète ne suffit pas à provoquer une boucle de relecture. Les fiches locales/Steam/Retro sans identifiant catalogue compatible ne sont pas recherchées par ressemblance de titre pour être automatiquement modifiées.

### Régressions et affichage

Une première version du nouveau bilan avait retiré l’affichage des genres ; le test existant l’a signalé. Les répartitions genres/franchises ont été rétablies avec clés sûres, ainsi que les compteurs favoris/collections et séquences de jours. Une annulation ne coupait pas encore tout le nouveau lot catalogue : la garde a été corrigée et le scénario correspondant repasse.

Les scénarios d’heure d’hiver utilisent une horloge cohérente pendant l’assertion, sans relâcher le rejet des événements futurs. Le test d’actualisation de langue met explicitement fin au nouveau délai avant de simuler une reprise : cliquer immédiatement ne doit plus contourner ce délai. Les frontières de versions futures ont été déplacées deV9 àV10, puisqueV9 est désormais valide.

Les vues progression, bilan et entretien ont été testées à **390,768 et1360 pixels**, sans débordement horizontal dans ces cas. Les captures contrôlées V0.20 sont dans `tests/results/v020/fixture-*.png`. Elles utilisent des titres et données de validation. Aucune erreur JavaScript non interceptée dans les nouveaux scénarios.

Un cas synthétique de **1000 séries /50000 épisodes** vérifie les calculs du bilan et le non-mélange des compteurs. C’est une mesure sur cette machine et des données de test, pas une garantie de latence mobile ni une validation de la capacité de localStorage pour cette volumétrie.

## Non exécuté sur cet hôte

1. `tests/retro-orchestration-v014.test.cjs` : jsdom absent.
2. `tests/web-dom-v014.test.cjs` : jsdom et fake-indexeddb absents.
3. `GamingCodecRetroTest` et `MediaIdentityCodecTest` contre le véritable org.json : `FG_JSON_JAR` absent. Deux tests de codecs regroupés dans une exclusion de commande.

Les tentatives DNS vers les registres de dépendances ont échoué. Aucun résultat antérieur de ces suites n’est compté. Les signatures de test ne sont pas un substitut revendiqué au parseur JSON réel. La compilation Android SDK complète, Compose, lint, signature APK/AAB, émulateur et appareils restent non exécutés.

## Origine Web, services et distribution

Le serveur personnel a répondu HTTP200 à une requête locale. `agent-browser` n’est pas installé ; son lancement a été tenté. Chromium via Playwright a refusé la navigation vers `http://127.0.0.1:47720/` avec **ERR_BLOCKED_BY_ADMINISTRATOR**. Preuve : `origin-attempt.json`. Aucun contournement ; les suites DOM ne sont pas présentées comme persistance réelle, installation PWA, horsligne, concurrence Web Locks entre deux profils ou cycle OAuth réel.

Aucune clé personnelle ou compte réel Steam, Trakt, RetroAchievements, TMDb ou RAWG utilisé. Aucune publication d’avis, modification de calendrier externe, compte ou serveur public effectuée. Le JAR/serveur est réellement exécuté sous Linux ; la fenêtre graphique et l’ouverture du navigateur sur Windows/macOS restent à vérifier. Aucun EXE natif ni runtime Java empaqueté.

## Conditionnement

La distribution contient le Web canonique et ses miroirs exacts. Le JAR est recompilé après la mise à jour des ressources ; son test HTTP est relancé sans doubler le compteur de75. Les quatre archives (SuiteRAR, PCRAR, WebRAR, SuiteZIP) sont extraites avec un lecteur indépendant et comparées aux dossiers sources, octets/chemins/empreintes/permissions exécutables compris. Leurs manifestes internes sont vérifiés.

Le JAR extrait du PCRAR exécute aussi `--probe`. Le serveur Web extrait reçoit une campagne HTTP distincte. Les nombres réels de fichiers, tailles, SHA-256 et résultats de ces dernières étapes sont dans le rapport de conditionnement séparé `FranchiseGuide_V0_20_VERIFICATION_ARCHIVES.json`. Les archives ne sont déclarées vérifiées qu’après réussite de ces commandes.
