# V0.36.0 — Import GitHub, reprise fiable et compilation Android

26 octobre 2026. Base exacte : archive Suite V0.35 récupérable, documentée dans `docs/INTEGRATION_GITHUB.md`.

- Regroupement des sources Android, Web/PWA, serveur local et lanceur PC, avec licences et documentation historique.
- Check-ins Trakt : accusé pour un seul visionnage, intention persistée avant écriture, vérification après résultat incertain, gardes de changement de compte et reprise du nettoyage.
- Passerelles Node/Java : masquage des secrets dans le JSON décodé, garde contre en-têtes de sécurité dupliqués, délais réseau corrigés, validation Steam UTF-8/JSON avant cache.
- Correction de trois écrans Compose empêchant la compilation Android et réglage de la mémoire Gradle ; APK de développement 0.36.0/code 36 assemblée avec le SDK 36.
- Rétablissement des suites DOM, nouvelle recette sur une véritable origine Chrome, synchronisation automatique des copies Web et JAR PC reconstruit.
- CI GitHub Actions Web/PC, navigateur et Android ; registre des travaux restants en JSON et classeur Excel détaillé.
- Résultats effectivement exécutés et limites dans `docs/QA_REPORT.md`. Les fonctions cloud, les comptes réels et la distribution sur appareils restent des chantiers distincts.

---

# V0.35.0 — Check-ins Trakt, Aujourd’hui et sauvegardes externes

- Session de check-in film/épisode persistante, liée au compte et à l’application.
- Historique local uniquement après accusé ou vérification distante.
- Après la durée estimée, vérification de l’auto-historique Trakt sans second POST susceptible de créer une relecture.
- États incertains, reprise, annulation, nettoyage et export de récupération.
- Écran Aujourd’hui et panneau/widget Android source.
- Distinction fichier proposé / sauvegarde externe vérifiée, rappel14j et report7j.
- Effacement local Web/PC poursuivant les autres nettoyages même si un stockage est inaccessible, puis signalement honnête des résidus.
- Régressions SeriesGuide/franchises/réglages/sauvegardes rejouées ; limites dans QA_REPORT.md.

---

# Changelog

## V0.33.0 — Découverte réellement paginée et reprise sûre

- TVmaze : pages index réellement distinctes au lieu de relire page zéro ; 404 non initial traité comme fin d'index.
- Sources publiques : FreeToGame index découpé en pages locales, Open Library / TMDb / RAWG paramètres de pages transmis.
- UI Découvrir : erreurs par page, relancer la même page, conservation des résultats chargés, filtres/tri accent-insensible, 60 cartes initiales, bornes 100 pages/5 000 références.
- Apple/iTunes : recherche de films par titre, **aucun classement populaire fictif**.
- Ajout catalogue : possibilité de réajouter volontairement un titre retiré sans perdre ses champs personnels ; refus si retrait pendant un ajout en cours.
- Sources Android : modèle Kotlin DiscoveryRules + pagination TVmaze/RAWG/Open Library/repository, UI native adaptée aux pages par type.
- Tests Node, Chromium et Kotlin nouveaux, plus régressions, JAR PC reconstruit et archives vérifiées.
- Le portable V11, le `.fgbackup` V1 et les identités des fiches restent inchangés.
- **Base vérifiée : ZIP V0.31.** Le rapport de livraison V0.32 n'avait pas d'archive accessible dans cette session ; cette passe reconstitue son chantier de découverte sur les sources disponibles.

---

# V0.31.0 — réglages fiables et reprise

- Trois défauts V0.30 reproduits : nouveaux accès persistés malgré un refus principal, déconnexion anticipée, préférences invalides.
- Validation, résumé, annulation, expiration et préparation des valeurs publiques par défaut.
- Stockage d'accès préparé puis sélectionné avec la bibliothèque ; liaison Trakt à l'application active.
- Réactions aux modifications/effacements externes ; vue périmée non modifiable.
- Sources Android adaptées, sans build SDK/Compose ni APK.
- PortableV11 et `.fgbackup`V1 inchangés. Preuves et limites dans le rapport QA courant.

---

# V0.30.0 — Sauvegardes protégées et reprise

- Reconstruction à partir du ZIP V0.29 fourni ; aucun état V0.30 interrompu supposé terminé.
- Export facultatif .fgbackup, AES-256-GCM et PBKDF2-HMAC-SHA256 à 600000 itérations via primitives natives.
- En-tête authentifié, tailles/coût bornés, phrases Unicode exactes, refus des fichiers altérés et du plaintext invalide.
- Déchiffrement → validations portables → aperçu → confirmation existante ; aucune application automatique.
- Gardes d'annulation, d'état, d'expiration et de raccourcis ; aucune phrase stockée volontairement.
- Sources Android du noyau cryptographique et du panneau dédiés ; pas de SDK/Compose testé.
- Tests de la nouvelle implémentation : correction d'une copie Buffer partagée et d'une course à la fermeture.
- Portable V11 maintenu. Bibliothèque locale, credentials et snapshots non chiffrés par cette option.

---

# V0.29.0 — recherche fiable et conservation des données

- Pagination indépendante des catalogues, reprise seulement des sources échouées, arrêt des sources épuisées ; messages source et erreurs de contrat explicites.
- Annulation des lectures actives/en attente et des temporisations Retry-After, gardes de génération de recherche.
- Recherche locale accent-insensible par60 lignes ; référence retirée retrouvable pour réajout.
- Fusion des dates avant ajustement des compteurs ; scores/notes privés préservés ; contradictions d’identités refusées au réimport.
- Audit de doublons publics plus complet et borné, normalisation Open Library et caractères non latins.
- Isolation des requêtes Trakt par génération de compte, avant renouvellement/réessai et écriture de la file.
- Règles et clients Android non-UI alignés sur les validations retenues, sans build SDK/Compose.
- Reprise depuis le ZIP V0.28 fourni ; nouvelle livraison directe, envoi Drive non disponible dans cette session.

---

# 0.28.0 — ISBN et édition retenue

- Recherche ISBN publique explicite et bornée, édition vérifiée par identité, œuvre et équivalence10/13.
- Snapshot personnel d’édition, application facultative du total, retrait de référence sans perte d’historique.
- PortableV11, normalisation du snapshot, garde des conflits et conservation à l’actualisation.
- Correction du validateur ISBN13 acceptant un EAN ordinaire ; titre de l’œuvre récupéré à l’actualisation ; dates de relevé par page conservées.
- Règles Kotlin, codec et panneau Android pour une œuvre existante. Aucun APK/SDK/Compose testé.
- Nouvelles preuves unitaires, JVM, parité et navigateur à fixtures. Rapport actuel dans docs/QA_REPORT.md.

---

# V0.27.0 — éditions de livres sans clé et total personnel sécurisé

- Consultation des éditions Open Library depuis une fiche livre, sans clé : pagination explicite30/300max, couvertures disponibles, ISBN contrôlés, recherche dans les pages reçues.
- Confirmation ciblée pour appliquer uniquement le total de pages ; copie préalable, conservation du parcours/historique/notes et rejet des changements concurrents.
- Nouveaux résultats de recherche livres avec total inconnu, plus de médiane imposée au suivi.
- Identifiants d’œuvre courts/canoniques normalisés ; contrôle de l’identité retournée et chemins de lecture bornés.
- Pacing public Open Library, file limitée, corps2Mio, UTF‑8 strict, délai429 conservé et annulation des réponses tardives.
- Modèles, client et panneau Android source ; pas d’APK ni de build SDK/Compose annoncé.
- Comparaison JS/Kotlin et tests de parcours dédiés. Archives et guides déposés dans le dossier Drive demandé, sans nouvelle synchro Drive dans l’application.

---

# V0.26.0 — personnes et épisodes TVmaze sans clés

- Profil interne TVmaze depuis le casting ou les invités : portrait, informations disponibles et séries créditées, rôles regroupés, recherche et ajout explicite complet.
- Détails d’épisode TVmaze à la demande, invités et équipe invitée, cache de consultation sans écriture de suivi, source et langue explicites.
- Rejet des réponses incohérentes/partielles/tardives, limitation de taille, délai429 partagé côté Web et nouvelles routes GET fixes Node/Java.
- Correction confirmée de la fusion par numéro d’épisodes publics aux IDs différents ; historique conservé sur son identité d’origine.
- Validation stricte des suffixes de dates publiques et des ports d’images ; filtre du nouveau profil à hauteur tactile44px.
- Raccordements Android source, modèles/règles JVM et client public ; aucun APK/SDKUI revendiqué.
- Portable V10 inchangé ; tous accès privés, droits commerciaux et comptes réels restent à valider/configurer.

---

# V0.25.0 — services automatiques et catalogues sans clé utilisateur

- TVmaze, Apple/iTunes (métadonnées films limitées) et FreeToGame ajoutés aux recherches/fiches ; Open Library public conservé.
- Séries TVmaze chargées avec saisons/épisodes/cast validés et IDs propres au fournisseur ; score, personnes et sources distingués de TMDb.
- Options de clés personnelles regroupées dans un panneau avancé fermé ; état des services automatiques visible.
- Passerelles Node et Java bornées, same-origin/loopback, allowlists strictes ; détection de capacités sans divulgation d’accès.
- Injection serveur des propres accès éditeur TMDb/RAWG/Trakt, flux appareil Trakt sans saisie utilisateur de credentials si l’éditeur l’a activé.
- Pas de cache serveur caché, pas de retry POST automatique, secrets applicatifs supprimés des réponses, contraintes 429 et corps/timeout.
- Source d’une fiche existante conservée ; identité publique non transformée en ID TMDb et suivi personnel préservé.
- Clients publics intégrés aux sources Android et réglages avancés masqués ; pas d’APK/build SDK et pas de nouveau transport éditeur natif.
- Format portable V10 inchangé, aucun secret ou vraie clé dans la livraison.

---

# V0.24.0 — fiche épisode, notes gardées et distribution propre

- Nouvelle ficheWeb/PC depuis les saisons et le gestionnaire, données locales sans lecture implicite et consultation explicite des détails/crédits TMDb de l’épisode.
- Groupes distribution/invités/équipe, rôles dédupliqués, recherche complète, rendu borné, source correspondant à l’épisode.
- Note personnellenullable et texte privé gardés par identité, valeurs ciblées, contexte compte et validité 10 min ; histoire/collection préservées.
- Spoilers masqués aussi dans la recherche de personnes ; révélation seulement pour l’épisode courant.
- Retour, voisins numérotés, fiche personne, disponibilités de saison et mécanismes de suivi existants raccordés ; brouillons confirmés avant abandon.
- GET borné2 Mio, annulation/invalidation,5 min de cache mémoire sans fausse fraîcheur ;réponses invalides rejetées et délai 429 respecté.
- Cache générique : politique de lecture prise en compte dans la clé ; modes no-store / reload / no-cache non mémorisés.
- Source Android : règles/client/panneau, sauvegarde de champs ciblés et raccords. Référence de saison hors portée corrigée (`sp.season`). Pas de build SDK/Compose ni APK.
- Schéma portable V10 inchangé ; aucun nouveau connecteur gaming ou cloud.
- Nouveaux tests Node, Kotlin JVM, comparaison JS/Kotlin et scénarios Chromium ; exclusions explicites dans le rapport courant.

---

# V0.23.0 — Où regarder, contexte pays et protection des relevés

- Nouveau panneau film/série/saison : catégories de monétisation distinctes, sélection pays, filtre de services et sortie TMDb fournie ; attribution JustWatch.
- Relevés temporaires20/5min, date de consultation non réinventée, rétention après erreur, refus de réponse contradictoire et annulations/contextes obsolètes.
- Réponses réseau bornées et UTF8 strict pour les nouvelles lectures ;429 respecté par le panneau.
- Anciennes disponibilités ne portent plus la région courante sans preuve de leur pays/date.
- Code Android règles/client/navigations/écran source ajouté ; pas de build SDK/Compose.
- Schéma portable V10 conservé, pas de données de disponibilité/session ajoutées aux exports ni aux comptes.

---

# 0.22.0 — Sélection et prévisualisation des actions d’épisodes

- Nouveau gestionnaire Web/PC et source Android : filtre saison/état/date/recherche, sélection retenue, limite10k et rendu borné.
- Vue préalable explicite pour vu, effacement, ignorer, réactiver, collection ; confirmation, snapshot, validité10minutes et gardes contre les changements ciblés.
- Ignorer ne détruit plus l’historique existant ; vu groupé ne rejoue pas les vus ni les ignorés ; dates futures/inconnues exclues sans opt-in.
- Anciens boutons de saison raccordés au même aperçu ; effacement individuel confirmé.
- Mutations natives sous garde/verrou et rollback. Sources seulement, aucun APK.
- PortableV10 inchangé, sélections et plans non exportés. Nouvelles suites Node/JVM/Chromium et parité de compteurs.

---

# V0.21.0 — listes personnelles, ordre et sélections protégées

- Gestionnaire de listes Web/PC borné, noms/descriptions, listes vides, comptage des œuvres ajoutées, filtres et recherche complète.
- Sélection multi-membres avec aperçu, copie préalable, confirmation et garde contre concurrence ; notes et données étrangères au changement conservées.
- Duplication de références sans copies d’œuvres ; suppression de liste sans suppression d’historique ni écriture distante.
- Ordre personnel persistant, positions dans la liste entière, tris automatiques alternatifs ; rapport filtré JSON/CSV borné10k.
- Modèle/codec portable V10, anciennes versions1–9 lisibles ; gestionnaire/dialogues/repository/règles Kotlin source.
- Retour acteur→série rétabli après une régression d’intégration ; champs de recherche tactiles alignés ; définitions anciennes longues lisibles.
- Rapport de QA disponible et exclusions explicites, pas de SDK/Compose ni compte réel validé.

---

# V0.20.0 — parcours, statistiques et entretien des catalogues

- Progression de jeux/livres indépendante des fins historiques ; états, reprise, édition/plateforme et minutes personnelles.
- Statistiques actuelles/datées/provider distinctes ; filtres de période et visibilité, historique recherché/paginé, bilan JSON et CSV détaillé contrôlés.
- Temps Steam par profil/App ID, données inconnues signalées, normal/Hardcore et cases personnelles séparés.
- Centre catalogue Web/PC : lots8/passe automatique3, ancienneté, erreurs avec délai5–60minutes,429 fournisseur, cancellation explicite et garde des réponses obsolètes.
- Conservation du parcours pendant actualisation et des notes privées lors d’entretien ; fusion refusée pour données de parcours contradictoires.
- Codec portable V9 et sources Android de règles/écrans ; aucune compilation SDK/Compose ni nouvelle auth native.
- Régressions rejouées et comparaisons JS/Kotlin ; voir QA_REPORT.md pour comptes exacts, périmètre contrôlé et exclusions.

---

# V0.19.0 — univers personnels, priorité manuelle et suggestions connues

- Gestionnaire des franchises quatre médias : création/renommage/suppression locale, univers personnels vides et références importées récupérables.
- 32 définitions intégrées communes Web/native ; classement principal, non canon exhaustif.
- Aucune manuelle conservée, réactivation explicite de la détection, pas de réécriture de libellé personnel par une collection TMDb.
- Ordre personnel, tris et filtres, candidats catalogue paginés à retenir individuellement ; aucune insertion implicite par simple ouverture de la recherche.
- Sauvegarde réelle des nouvelles franchises dans le repository Android, source de gestionnaire/confirmations et règles JVM.
- Noms Unicode/emoji différents, descriptions multilignes, titre long qui ne chasse plus les boutons de la barre supérieure.
- Nouvelle suite de règles et DOM ; ancien scénario de création au prompt adapté au nouveau formulaire, en conservant les contrôles de noms doublons/nonlatins.

---

# V0.18.0 — imports et restaurations avec aperçu

- Centre Importer et vérifier : préparer sans modifier, compteurs avant/après, différences par ID local, recherche/filtres et50lignes initiales.
- Confirmation obligatoire, options de préférences publiques, secrets locaux préservés, rapport de compteurs exportable sans corps des notes.
- Copie préalable obligatoire et garde contre aperçu obsolète, Trakt actif, refus de stockage ; snapshots locaux et IndexedDB Web entrent dans le même parcours.
- Décodage UTF‑8 strict et budgets de fichiers, refus des drapeaux mal typés, durées0/connues et notes d’épisodes conservées.
- Format de date officiel ISO8601 complet + millisecondes d’époque ; refus jours/heures impossibles, jour UTC explicite.
- Préparation/panneau natifs ajoutés, notes privées dans codec épisode, lecture séquentielle sous budget, public_preferences_v18 enregistré avec état/file.
- Nouveaux tests de règles et DOM, parité des compteurs JS/Kotlin et régressions disponibles ; aucune validation SDK ou compte réel.

---

# V0.17.0 — reprise et organisation locale

- Vue À regarder / reprendre des quatre catégories, avec un épisode non vu/non ignoré par série, états prudents pour dates/numéros incomplets et catalogue vide.
- Durée connue films/épisodes, favoris/masqués/spéciaux et recherche anti-spoiler ; rendu progressif.
- Ancien raccourci Séries corrigé pour ne pas sauter un épisode antérieur inconnu/futur.
- Organisation groupée favoris/visibilité/listes locales, aperçu/copie de sécurité requis, une écriture, garde contre conflits et annulation ciblée.
- Sources Android équivalentes, règles JVM exécutées ; pas APK ni build UI SDK. Empreinte de notification source enrichie.
- Format portable V8 et connexions existantes inchangés ; filtres/sélections/undo de session non exportés.
- 49 tests Node, 56 assertions JVM et 72 scénarios Chromium ajoutés et inclus dans la campagne disponible.

---

# V0.16.0 — agenda et visionnages datés

- Agenda unifié épisodes/films/jeux/livres, recherche et filtres, périodes, compteurs des dates absentes et doublons, rendu60 lignes.
- Export ICS confirmé de toute la sélection ; journées entières sans horaire, UID stables, UTF8/CRLF/échappements, masque spoiler, max10 000 explicite ; aperçu invalidé si les données changent.
- Ajout de visionnage passé sur film/épisode sans déplacement des anciens ; heures locales inexistantes refusées et répétées explicitement choisies ; doublon/futur refusés.
- File Trakt construite à partir des nouvelles dates connues et non du seul lastWatchedAt agrégé. Plusieurs événements nouveaux, plusieurs opérations ; compteur seul ne réémet pas l'ancienne date.
- Accusé HTTP200 sans compteur exploitable : conservation de l'opération incertaine au lieu d'un faux acquittement.
- Note privée d'épisode préservée au refresh, durée manuelle0 préservée à la migration.
- Sources Android : dialogue de date, agenda et sélecteur d'export, rollback sur erreur de sauvegarde, notifications/PendingIntents à identité complète et préférences courantes. Aucun build SDK ni test appareil.
- Nouveau lot de tests Node, Chromium à fixtures et JVM, parité binaire ICS ; exclusions de dépendances documentées.
- PortableV8 et clés de stockage conservés ; Web0.16 / Android code16 / JAR Java17 reconstruit.

---

# V0.15.0 — centre de synchronisation et reprise sûre

- Centre Web/PC pour listes prêtes, erreurs, pauses, délais et connexions manquantes ; filtres et affichage borné.
- Lots de lecture complets en premier plan, séquentiels par passages de8,30secondes entre passages, checkpoint durable et reprise manuelle après rechargement.
- Pauses individuelles/globales, Retry-After numérique/dateHTTP, délai par fournisseur sur429, erreurs de délai dépassé enregistrées.
- Réponse de session Steam ancienne ou post-déconnexion ignorée ; sessionStorage refusé n’interrompt plus la suspension des automatismes après révocation.
- Contrôles runtime exclus de l’export et supprimés d’un import externe ; écriture commune aux données principales.
- Bandeau de stockage déplacé dans le shell pour ne pas casser les colonnes PC.
-59 tests de politique réseau et63 scénarios Chromium nouveaux ; régressions disponibles relancées, exclusions de dépendances explicitement documentées.
- Recette CI préparée : installation des dépendances QA avant les tests DOM. Aucun déploiement/CI exécuté ni APK annoncé.

---

# FranchiseGuide V0.14.0 — RetroAchievements et intégrité

Édition du 6 octobre 2026, depuis la Suite V0.13 récupérée.

- Profil public RetroAchievements résolu par ULID stable, import paginé des jeux avec progression et lecture de succès par lots de huit ; reprise manuelle ou automatique facultative.
- États et dates normal/Hardcore indépendants ; aucune possession, fin de campagne ou médaille platine déduite.
- Passerelles personnelles Node 22 et Java 17 en lecture seule, clé serveur, limites et erreurs explicites.
- Imports incomplets, contradictoires ou obsolètes refusés ; notes, guides, épingles et checklists préservés pendant les lectures.
- Fusions contrôlées conservant les historiques, informations Steam multi-comptes et alias fournisseurs ; refus des conflits de données et de listes.
- IndexedDB : rotation atomique de cinq copies, ordre des écritures/effacements, erreurs de transaction et d’effacement correctement prises en compte.
- Sources Android : profils et identités portables, normal/Hardcore, codecs testés avec vrai JSON ; version 0.14.0 / code 14.
- JAR PC recompilé, Web harmonisé, tests actuels et documentation de portée fournis.

Les comptes réels, appareils Android, UI PC et PWA installée restent à valider. Aucun APK/AAB, EXE natif ni service public n’est livré. Le schéma portable reste V8 ; sauvegarder avant mise à jour et éviter un réenregistrement par une ancienne version qui ignorerait les nouveaux champs.

---

# FranchiseGuide V0.13.0 — intégrité et récupération locale

Édition du 5 octobre 2026, depuis la Suite V0.12 livrée.

- Nouveau centre Intégrité des données Web/PC.
- Détection des IDs médias/épisodes/listes en double, épisodes orphelins, références de listes cassées, relevés gaming orphelins et doublons candidats.
- Réparations sûres sans perte de suivi ; les épisodes orphelins suivis restent signalés au lieu d'être effacés.
- Fusion manuelle contrôlée des doublons sur Web/PC, avec blocage en cas d'identifiants fournisseurs contradictoires.
- Re-liaison des relevés trophées/succès lors d'une fusion de jeu compatible.
- Copie de récupération IndexedDB lors d'un échec du stockage principal ; gestion manuelle depuis Données & hors ligne.
- Migration de la clé de stockage Web V7 vers V8, avec compatibilité de lecture.
- Sources Android : rapport d'intégrité et réparation des références sûres.
- Libellés/versioning Android/Web/PC remis en cohérence avec le schéma portable V8.
- Tests unitaires dédiés au moteur d'intégrité et scénarios Chromium de migration/réparation.

Limites : IndexedDB n'est pas encore le stockage principal ; son fonctionnement sur une origine réelle reste à valider dans une matrice de navigateurs. Aucun APK ni compte réel Steam/Trakt n'a été utilisé.

---
# FranchiseGuide V0.12.0 — connexion Steam et import automatique

Édition du 6 octobre 2026, depuis l’archive Suite V0.11 livrée. Les corrections précédentes restent incluses.

- OpenID Steam dans les serveurs Node22 et Java17 : state aléatoire lié au navigateur, callback strict, signature vérifiée auprès de Steam, nonce anti-rejeu et session locale révocable.
- Bouton « Se connecter avec Steam » depuis Jeux et Trophées & comptes ; consentements d’import et d’actualisation visibles.
- Bibliothèque accessible récupérée par App ID : titres, icônes, temps de jeu par compte et liens vers la boutique. Aucune complétion de campagne déduite.
- Succès lus progressivement par lots de huit ; renouvellement automatique après six heures pendant l’utilisation, suspension et déconnexion.
- Sessions de sept jours, cookie HttpOnly/SameSite=Lax, empreintes de jetons côté serveur ; pas de mot de passe Steam ni de clé API dans le navigateur.
- Profil privé, réponse partielle, panne, changement de compte et réponse tardive : anciens relevés conservés.
- Reprises manuelles : listes les moins récemment lues d’abord. Relance après une ancienne détection de passerelle indisponible.
- Déduplication de liens indexée, sans comparaison par titre ; scénario de 2 100 jeux et limite explicite de 2 000 listes.
- Schéma portable 8 ; métadonnées Steam transportées dans les sources Android. Import ne réactive pas les lectures automatiques et ne prouve pas l’identité.
- Effacement local : révocation préalable de la session Steam du navigateur ; échec bloquant avant effacement.
- Erreur de callback : page de retour lisible, sans afficher la signature ou les paramètres reçus.
- Actualiser une fiche issue de Steam renvoie au parcours Steam au lieu d’annoncer une actualisation sans requête.

Limites : aucune connexion de compte réel validée ; aucun APK ; pas de connexion Steam native Android ; serveurs personnels loopback, pas d’hébergement public ni de nouveau connecteur PSN/Xbox/Epic/GOG/RetroAchievements.

---
## Historique conservé : V0.11

# Journal des changements

## 0.11.0 — 2026-10-05 — Trophées & succès

Base : archive Suite 0.10 livrée. Les acquis Séries/Films/Jeux/Livres, Trakt, imports, suivi, casting, statistiques et franchises sont conservés ; aucun effacement/recréation de bibliothèque à l’installation.

Ajouts : module Web/PC trophées par compte/plateforme/édition ; groupes, filtres, secrets, images, grades, points/rareté sourcée, dates, notes, épingles/checklist. Centre des profils publics, fiches jeu manuelles, imports JSON neutres et export de listes. Schéma portable 7 avec espace gaming distinct de Trakt. Transfert natif Kotlin/codec et panneau Compose de fiches jeux pour import/local.

Steam : passerelles Node22 et Java17 en lecture seule, clé serveur en environnement, association explicite AppID/SteamID64, validation de catalogue et suivi avant commit, rareté optionnelle, limites/timeout/cache, refus d’origine/hôte, aucune URL proxy arbitraire. Consentement d’auto-lecture six heures, première scène visible, erreur/backoff et annulation des lectures devenues obsolètes.

Corrections : anciens filtres après sélection/import de liste ; réponse catalogue tardive après remplacement/effacement ; conservation de la copie de travail en cas de quota lors d’une actualisation ; distinction unknown/locked/unlocked et dates cohérentes ; protection des notes personnelles sur réimport ; langue propre à la série conservée depuis les exports SeriesGuide sans écraser une préférence locale.

QA : campagnes Node, Chromium en DOM simulé, Kotlin/JVM, HTTP des serveurs local Java/Node et contrôles de build. Voir rapport pour résultats exacts. Aucun compte réel ni APK Android utilisé. L’essai navigateur de l’origine locale est bloqué par l’environnement. PSNProfiles reste un lien/import neutre, jamais un connecteur distant annoncé.

L’historique de développement et les écarts préexistants figurent dans FEATURE_MATRIX.md et les audits hérités conservés dans docs/.
