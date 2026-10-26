# Rapport QA — FranchiseGuide V0.35.0

## Base et résultat

Base réellement utilisée : **ZIP V0.34 fourni**, SHA-256 `6dfe05c6c0c01b63c64f5793b811c0f07bfbb50cea3c4bcba14b9c5d78268a28`.

La campagne cumulative propre `python3 tools/run_v035_checks.py` termine avec **81 commandes réussies, 0 échec et 3 groupes explicitement non exécutés**. Le runner enregistre chaque commande, durée, statut et journal dans `tests/results/v035/available-qa.json`. Les premières campagnes interrompues ou échouées sont archivées séparément et ne sont pas additionnées.

| Groupe exécuté | Résultat actuel | Portée |
|---|---:|---|
| JavaScript / Node | **1498 tests réussis**, dont 33 nouveaux | Règles, stockage, réseau et modules disponibles |
| Chromium | **1406 assertions**, dont 29 nouvelles V0.35 | Scripts/DOM/CSS réels ; API, images, temps et stockage contrôlés |
| Kotlin/JVM | **987 assertions + 1 scénario franchises**, dont 34 nouvelles | Règles pures ; pas Android SDK ou appareil |
| Parités de domaine JavaScript/Kotlin | **13 programmes** | Check-in, scanner, imports, épisodes, listes, franchises et autres domaines |
| Fichiers protégés | **2 directions** | JVM→JavaScript et JavaScript→JVM avec primitives réelles |
| Programme PC compilé | **109 contrôles HTTP + 9 de démarrage** | Vrai JAR compatible bytecode Java17, exécuté sous Java de l’environnement |
| Passerelles Java | **333 assertions** | Serveurs HTTP locaux réels, réponses et accès fictifs |
| Structure / bootstrap | **216 + 8 contrôles** | Branchement, ressources, versions et téléchargement ; pas des parcours utilisateur |
| Code Kotlin non graphique | **Compilation de signatures réussie** | Stubs Android/Compose/JSON ; pas framework, stockage ou parser réels |

## Check-ins Trakt V0.35

Les nouveaux tests couvrent le démarrage films/épisodes, la persistance avant envoi, la liaison au compte et à l’application, l’annulation, le nettoyage, la réponse perdue et la reprise. Aucun démarrage de check-in ne crée de lecture locale.

Avant la durée estimée — ou sans durée connue — terminer envoie l’historique, attend un accusé exploitable, applique ensuite la date locale puis nettoie le check-in. Après la durée estimée, FranchiseGuide consulte d’abord l’historique récent Trakt : une correspondance exacte applique sa date et **aucun second POST d’historique n’est envoyé**. Si aucune correspondance n’est visible ou si la lecture réseau échoue, l’état reste incertain et récupérable, sans suivi local.

Les tests vérifient aussi : ancien historique antérieur au démarrage ignoré, autre compte ou application refusés, changement d’identité fournisseur bloqué, nettoyage non confondu avec l’application du visionnage et oubli local sans mutation de l’historique.

## Aujourd’hui et sauvegardes

Le tableau Aujourd’hui regroupe le check-in, les opérations distantes incertaines/suspendues, la sauvegarde externe, l’agenda local du jour et les prochains éléments prêts. Il reste borné et n’invente ni date ni disponibilité.

L’export note seulement qu’un fichier a été **proposé**. Une confirmation séparée indique que l’utilisateur déclare l’avoir vérifié. Le rappel est dû après quatorze jours et reportable sept jours. Les règles distinguent proposition, vérification, rappel et report ; aucun test ne prétend inspecter le disque ou un Drive externe.

## Effacement local

Le parcours Web/PC refuse de commencer avec un check-in ou une écriture distante non résolus. Il tente la révocation de la session Steam locale, supprime les clés FranchiseGuide, caches, IndexedDB et service worker de l’origine, puis vérifie les résidus. Si `sessionStorage` ou une autre étape est inaccessible, les suppressions suivantes continuent et le résultat reste incomplet plutôt que faussement réussi. Aucun compte, historique ou fichier distant n’est effacé.

## Régressions héritées et corrections de campagne

Les suites V0.08 à V0.34 sélectionnées ont été rejouées. Les régressions initiales ont été corrigées puis toute la campagne a été relancée depuis zéro :

- le nettoyage local ne s’arrête plus au premier stockage inaccessible ;
- l’ancien scénario franchise V0.19 utilise le scanner multi-catégories actuel tout en conservant ses garanties de page échouée, reprise, résultat temporaire et réponse tardive ;
- les assertions asynchrones de sauvegarde/recherche/export attendent désormais leur vrai point de stabilisation ;
- un check-in arrivé à sa durée ne crée plus un second visionnage après l’auto-validation Trakt.

Les traces des essais précédents sont dans `first-attempt/` et `interrupted-concurrent-run/`. Elles ne comptent pas dans le total final.

## Limites et groupes non exécutés

- `retro-orchestration-v014.test.cjs` : `jsdom` absent.
- `web-dom-v014.test.cjs` : `jsdom` et `fake-indexeddb` absents.
- Deux programmes de codecs Kotlin nécessitant le véritable `org.json` : `FG_JSON_JAR` absent. Les stubs de compilation ne remplacent pas l’exécution d’un parser.
- Aucun compte Trakt/Steam/RetroAchievements ou clé catalogue réel utilisé. Les réponses distantes sont simulées.
- Aucun Android SDK/Compose, APK/AAB, émulateur/appareil, SharedPreferences, WorkManager, notification ou widget réel exécuté.
- Le serveur local a répondu HTTP 200, mais Chromium a refusé la navigation avec `ERR_BLOCKED_BY_ADMINISTRATOR`; `agent-browser` n’est pas installé. Voir `real-origin-attempt.json`.
- Les scénarios Chromium contrôlent le DOM et les règles, pas la persistance après crash, l’installation/offline PWA ou les courses de deux vraies fenêtres.
- Web/PC ne programme aucune fin de check-in application fermée. Android contient les règles, panneaux et sources de widget, mais pas de tâche de fond/appareil qualifié.
- La confirmation de sauvegarde reste déclarative ; aucun contrôle de présence durable du fichier n’est effectué.
- Le JAR est compilé avec `--release 17` et exécuté ici sous le Java installé. La fenêtre graphique Windows/macOS et un EXE natif ne sont pas testés.

## Conditionnement

Après les textes finaux, le moteur Web canonique est recopié vers `web-preview` et `desktop/resources/web`, le JAR est reconstruit, puis les contrôles HTTP/démarrage sont rejoués. Chaque distribution reçoit un `MANIFEST.sha256`.

Les quatre archives finales sont extraites avec le lecteur indépendant de la Suite et comparées aux dossiers préparés — chemins, octets et permissions exécutables. Les manifestes internes sont relus. Les JAR extraits repassent `--probe` et leurs ressources Web sont comparées au moteur canonique. Le serveur Web extrait est exercé par de vraies requêtes HTTP sans accès fournisseur.

Le rapport extérieur `FranchiseGuide_V0_35_VERIFICATION_ARCHIVES.json` contient les résultats, tailles et empreintes. Cette vérification d’intégrité ne transforme pas les limites ci-dessus en validation réelle des comptes, appareils ou navigateurs persistants.
