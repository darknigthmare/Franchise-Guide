# Fonctions et limites — FranchiseGuide 0.23.0

**Web et PC utilisent le même moteur. Android intégré en source n’est ni un APK ni une validation SDK/Compose.** Les tests sont des scénarios, pas un pourcentage de reproduction SeriesGuide.

## Livré V0.23 — disponibilités

| Fonction | Web / PC | Android source |
|---|---|---|
| Où regarder par film/série/saison | Fiche, saison et raccourci À reprendre, retour d’origine testés | Écran Compose branché depuis fiche et saison ; UI non construite au SDK |
| Pays disponibles et portée | Lectures GET à la demande ; pays sélectionné ne change pas les réglages | Même règle/source client ; connexion réelle non exercée |
| Catégories d’accès | Abonnement, gratuit, publicité, location, achat séparés ; recherche complète | Règles JVM, filtres source |
| Ancien relevé de noms | Pays et date explicitement inconnus, jamais empruntés au pays courant | Libellés source corrigés |
| Vide/inconnu/erreur | Distincts ; jamais une fausse indisponibilité mondiale | Même modèle de résultats et texte source |
| Cache et reprise | Mémoire20,5min sans nouvelle date, dernier relevé après erreur | Cache dans écran20, pas persistance/appareil testée |
| Annulation et résultat tardif | Changement pays/saison/clé/écran/bibliothèque gardé, tests DOM | Job/identité/génération gardés au retour ; interruption du socket non garantie |
| Quota429 | Délai commun aux lectures de ce panneau, manuel inclus | Délai en mémoire du client source ; pas de compte réel |
| Réponse bornée |1Mio pays,2Mio offres ; octets/UTF8/structures contrôlés | Limites transport/code source ; parser Android réel non exécuté |
| Source et logos | JustWatch via TMDb, lien TMDb fourni/sanitized, pas prix/deep-link inventé | Mêmes règles JVM ; Coil UI pas testée sur appareil |
| Export et suivi | Aucun nouveau champ portable ; aucune mutation des comptes/historiques | Modèles de lecture hors repository, pas nouveau schéma |

Les fonctions V0.22 et antérieures ci-dessous restent conservées ; les suites exécutables sont rejouées. Les exclusions ne sont pas remplacées par de vieux résultats.

---

## Héritage de la V0.22

**Web et PC partagent le moteur canonique. Android « source intégré » n’est ni un APK ni une validation SDK/Compose.** Les assertions sont des contrôles précis, pas un pourcentage de parité SeriesGuide.

## Livré V0.22

| Fonction | Web / PC | Android source |
|---|---|---|
| Gestionnaire d’épisodes par série | Sélection inter-filtres/saisons, recherche anti-spoiler, états/dates, retour fiche testés | Gestionnaire Compose intégré ; pas SDK/appareil |
| Rendu et sélection | 60 lignes puis suite ; sélection de tous les résultats max10k ; aperçu40 puis suite | LazyColumn, sélection max10k, aperçu40 ; performances appareil non testées |
| Actions vu/nonvu/ignoré/collection | Préparation sans mutation, confirmation, copie de sécurité, refus de plan obsolète | Règles pures JVM, repository verrouillé avec rollback |
| Historique protégé par Ignorer | Historique préservé via bouton individuel, saison et lot | Règle JVM et boutons source |
| Visionnage groupé conservateur | Aucun revisionnage implicite, ignorés protégés, futurs/inconnus exclus par défaut | Mêmes règles, parité des compteurs sur quatre actions |
| Effacement d’historique | Compteurs/dates annoncés, notes/score/collection conservés ; retrait Trakt possible après confirmation | Source/dialogues ; stockage et fournisseur réel non exécutés |
| État modifié après aperçu | Tout le lot refusé si champ ciblé ou identité change ; champs étrangers conservés | Même règle immuable ; courants concurrents Activity/Worker non validés sur appareil |
| Refus quota/backup | Scénarios DOM avec stockage contrôlé, état sauvegardé et brouillon exportable | Signatures compilées, pas injection de panne SharedPreferences réelle |
| Trakt | Nouveau suivi seulement ; statut ignoré local ; aucune requête directe dans l’aperçu | Création de changements par les règles existantes ; pas de compte réel |
| Schéma portable | V10 inchangé ; filtre/sélection/plan non exportés | Champs existants de suivi, aucun nouveau schéma |

## Listes V0.21 conservées

| Fonction | Web / PC | Android source |
|---|---|---|
| Listes vides, création, description, renommage | Gestionnaire et dialogues, erreurs en ligne, recherche globale, 60 résultats initiaux | Gestionnaire/recherche/édition, LazyColumn ; pas de validation appareil |
| Membres des quatre types | Œuvres ajoutées, masqués compris ; suggestions et retirés exclus | Même règle pure JVM |
| Sélection multi-membres | Recherche toutes données, sélection retenue entre filtres, aperçu puis confirmation | Dialogue avec aperçu et règles de sélection |
| Garde contre changement concurrent | Définition/membres et génération de bibliothèque Web vérifiés ; pas de partie appliquée | Définitions/membres et verrou repository ; courses Android réelles non testées |
| Conservation d’autres changements | Notes/visionnages/appartenance à d’autres listes conservés | Règles immuables JVM exécutées |
| Duplication | Nouvelle liste vers mêmes références ; pas de titres doublés | Règles et UI source |
| Suppression | Copie préalable obligatoire, définition/appartenances retirées, œuvres conservées | Transaction et rollback source |
| Ordre personnel | Identifiants persistants, position dans la liste entière, tris alternatifs sans perte d’ordre | Modèle/codec/règles/dialogue source |
| Filtres détaillés | Catégorie, masqués/visibles, favoris, parcours courant, état de série chargé conservateur | Catégorie, texte et masqués ; pas de filtre état détaillé équivalent |
| Rendu borné | 60 titres puis suite ; gestionnaire60, aperçu Plus30 ; sélection intégrale limitée10k | LazyColumn ; performance appareil non mesurée |
| Rapport sélection | JSON/CSV complet filtré, aperçu de confidentialité, limite10k explicite | Non ajouté |
| Retour liste → titre → liste | Filtres et volume affiché retenus dans la session | Pas de parité de retour vérifiée sur appareil |
| Anciennes longues définitions | Lisibles sans troncature ; nouvelles éditions bornées | Règles JVM correspondantes |
| Schéma portable V10 | Lecture V1–V9, ordre personnel exporté ; sélections/filtres non exportés | Modèle/codec source compilé sur signatures, pas org.json appareil réel |

## Conservé des versions précédentes

Quatre médias, catalogues et images conditionnés aux accès, séries/saisons/épisodes complets, fiches personnelles et filmographies, manuels, langues de fiche, univers/alias/suggestions/ordre, imports avec aperçu, agenda/ICS et visionnages datés, reprise et organisation, statistiques/historique précis, entretien catalogue, Trakt et file de suivi, Steam OpenID, RetroAchievements public et centre de synchronisation gaming. Ces routes ont leurs régressions rejouées lorsqu’exécutables ; les exclusions du rapport ne sont pas remplacées par des résultats anciens.

## Limites qui ne sont pas fermées

- Android : pas de build SDK/Compose/signature/téléphone ; gaming Steam/Retro natif et certaines fonctions Trakt/exports restent partiels.
- Origine réelle : agent-browser absent, navigation locale Chromium bloquée par la politique de l’environnement. PWA installée, horsligne réel, persistance entre vraies fenêtres et Web Locks ne sont pas validés.
- Comptes : pas d’authentification ou lecture avec de vrais identifiants. Passerelles HTTP testées avec réponses amont contrôlées. Pas de nouveau connecteur PSN/PSNProfiles/Xbox/Epic/GOG.
- Stockage : localStorage synchrone/quota conservé ; IndexedDB concerne les copies de récupération, pas une migration complète. Les snapshots ne remplacent pas une copie externe.
- Services : pas de cloud général, pas de nouveaux comptes, pas de backend public multiutilisateur, pas de déploiement.
- Droits/distribution : pas de dossier commercial clos, pas d’EXE natif ni d’essais de fenêtre graphique Windows/macOS. Attributions/licences des sources et fournisseurs restent à appliquer selon le mode d’exploitation.
- QA : deux suites jsdom/fake-indexeddb et deux codecs Kotlin contre véritable org.json non exécutés faute de dépendances. Tableurs/calendriers externes non validés.

Cette version demeure de développement, sans affirmation de clone exhaustif ni de logiciel sans bug.
