# FranchiseGuide Suite 0.35.0

Base réellement utilisée : **Suite V0.34 fournie**, SHA-256 du ZIP `6dfe05c6c0c01b63c64f5793b811c0f07bfbb50cea3c4bcba14b9c5d78268a28`.

V0.35 poursuit la parité SeriesGuide avec trois chantiers vérifiables : **check-ins Trakt récupérables**, écran **Aujourd’hui** et rappel de **sauvegarde externe vérifiée**. Elle renforce également l’effacement local Web/PC. Les séries, films, jeux, livres, franchises, imports, listes, statistiques, réussites et fonctions V0.34 restent présents.

## Check-ins Trakt sans double visionnage silencieux

Un film ou épisode ajouté et possédant un identifiant TMDb peut démarrer un check-in. La session est écrite localement avant la requête, liée au compte et à l’application Trakt, et récupérable après une réponse réseau incertaine. Démarrer ne modifie jamais l’historique local.

Avant la durée estimée — ou si elle est inconnue — **Terminer** écrit explicitement l’historique Trakt, attend un accusé exploitable, met ensuite à jour le suivi local, puis nettoie le check-in distant. Après la durée estimée, Trakt peut déjà avoir transformé le check-in en visionnage : FranchiseGuide lit alors l’historique récent et **n’envoie pas un second POST d’historique**. Si le visionnage n’est pas encore visible, la session reste incertaine et vérifiable, sans modification locale.

Une réponse perdue ne devient pas un succès supposé. L’utilisateur peut vérifier l’historique, annuler le check-in, reprendre le nettoyage distant ou exporter/oublier seulement sa référence locale. Un autre compte ou une autre application Trakt ne peut pas reprendre cette session.

Cette conception n’est pas l’automatisation exacte de SeriesGuide : aucun travail n’est planifié lorsque Web/PC est fermé. Ouvrir **Aujourd’hui** ou relancer une synchronisation/vérification reste nécessaire. Android possède les règles, le panneau et les sources de widget, mais aucun build SDK/appareil n’est fourni.

## Aujourd’hui

Le nouvel écran rassemble : check-in actif, sauvegarde externe, opérations Trakt incertaines/suspendues, sorties ou épisodes datés aujourd’hui et prochains éléments prêts. Il est calculé depuis les données locales ; il ne fabrique ni disponibilité ni date manquante. Les fiches restent accessibles explicitement.

## Sauvegarde externe

Après un export JSON ou protégé, l’application enregistre **fichier proposé**, jamais **fichier vérifié**. L’utilisateur confirme seulement après avoir constaté que le fichier existe dans l’emplacement voulu. Le rappel réapparaît après quatorze jours et peut être reporté sept jours.

Cette confirmation est déclarative : le navigateur et le code Android ne prouvent pas la présence durable du fichier. Il ne s’agit pas encore de l’Auto Backup hebdomadaire de SeriesGuide ou d’une copie Drive automatique. Le format portable reste V11 et le conteneur protégé reste V1.

## Effacement local Web/PC

L’effacement refuse de commencer avec un check-in actif ou une écriture distante. Il retire les clés FranchiseGuide, caches, copies IndexedDB et service worker de l’origine, révoque d’abord la session Steam locale lorsqu’elle existe et vérifie les résidus. Un stockage inaccessible n’interrompt plus prématurément les autres nettoyages : l’application les poursuit puis annonce honnêtement un effacement incomplet à reprendre après rechargement. Aucun compte ou historique distant n’est supprimé.

## Démarrer

- **PC** : Java 17+, `desktop/FranchiseGuide-PC.bat` ou `bash desktop/FranchiseGuide-PC.sh`, puis navigateur sur `127.0.0.1:47631`.
- **Web personnel** : Node 22+, `node server/start.cjs`, puis `127.0.0.1:47632`.
- **Android** : sources uniquement ; pas d’APK/AAB ni de build SDK/Compose/appareil validé.

Les catalogues publics TVmaze, Apple/iTunes, FreeToGame et Open Library restent accessibles sans clé utilisateur dans leur périmètre. Les fonctions TMDb, RAWG, Trakt, Steam et RetroAchievements nécessitent les accès autorisés correspondants. Aucun secret d’éditeur n’est préactivé dans les archives.

Exporter V0.34 avant migration, fermer ses anciens onglets/lanceurs et ne pas faire modifier les mêmes données par deux versions. Lire **START_HERE.md**, `docs/CHECKINS_AUJOURDHUI_ET_SAUVEGARDES.md`, `docs/QA_REPORT.md` et `docs/FEATURE_MATRIX.md`.

FranchiseGuide reste une version de développement : aucune certification commerciale, PWA installée, compte distant réel ou absence totale de bugs n’est revendiquée.
