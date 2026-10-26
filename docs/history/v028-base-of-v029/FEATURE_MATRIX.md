# Fonctions et limites — FranchiseGuide V0.28

Les compteurs de tests actuels sont dans QA_REPORT.md. Les matrices historiques ensuite décrivent la continuité, pas des essais à additionner aux résultats actuels.

| Fonction nouvelle | Web / PC | Android source |
|---|---|---|
| Recherche ISBN10/13 sans clé | Panneau global depuis Livres/recherche, cinq œuvres candidates maximum | Recherche dans le panneau d’une œuvre existante, pas parcours global d’ajout |
| Vérifier l’édition proposée | ID, lien à l’œuvre et ISBN équivalent exigés | Règles JVM/client source |
| Retenir l’édition | Snapshot personnel validé et sauvegardé, pas nouvelle identité principale | Modèle, codec, repository et panneau source |
| Total de pages | Option explicite décochée, conserve progression/historique | Action de référence et action de pages séparées |
| Retirer la référence | Confirmation, conserve livre/total/historique | Source gardée |
| Détails de référence | Carte avec métadonnées, dates et lien source | Texte simplifié et action de retrait ; pas parité visuelle complète |
| Portable | V11, lecture V1–V10, snapshot normalisé à l’import/export | Codec source et règles ; parser org.json réel non exécuté |
| Actualisation/fusion | Snapshot conservé ; conflit d’édition bloque fusion | Préservation des alias/snapshot source ; pas tous écrans Web natifs |
| Scan caméra, plusieurs exemplaires indépendants | Non fournis | Non fournis |
| Réseau réel/PWA/compte fournisseur | Non validés ; fixtures et HTTP local séparés | Pas de SDK/Compose, APK ou appareil |
| Drive | Archives et guides, après envoi confirmé | Pas de synchronisation Drive intégrée à l’app |

Le contrôle ISBN ne certifie pas une attribution réelle. Une référence unique par œuvre n’est pas une propriété/droit d’accès, disponibilité d’emprunt ou catalogue complet. Les comptes Trakt ne synchronisent pas les livres. La parité complète avec SeriesGuide, les validations commerciales et les comptes réels restent ouverts.

---

## Périmètre historique conservé (les limitations V0.28 ci-dessus prévalent)

# Fonctions et limites — FranchiseGuide V0.27

Les preuves de cette version sont dans QA_REPORT.md. Les matrices anciennes ci-dessous documentent la continuité fonctionnelle, pas des essais ajoutés au bilan actuel.

| Fonction V0.27 | Web / PC | Android source |
|---|---|---|
| Livres sans clé, identités d’œuvre courtes et canoniques | Recherche corrigée, total personnel inconnu au premier ajout | Recherche et détail corrigés ; pas réseau Android réel |
| Consultation des éditions | Lecture explicite30/page,300maximum ; pas de collecte automatique | Client et panneau source, pagination identique |
| Titres/éditeurs/date/format/langues/ISBN | Champs validés ; ISBN au checksum, pas certification de l’ouvrage | Même données/règles JVM |
| Couvertures | Affichées si identifiant positif reçu, fixture visuelle testée | Pas d’image dans le nouveau panneau natif |
| Filtres | Recherche et langue exacte déclarée ; pages connues ; pages reçues seulement | Recherche et pages connues ; pas sélecteur de langue |
| Choix d’une édition | Consultation seulement, pas nouvelle identité d’édition persistée | Même portée |
| Application du total | Confirmation + copie de sécurité ; change uniquement le total | Règle JVM + repository gardé ; stockage/appareil non testé |
| Historique/progression/personnel | Conservés, pas de fin de lecture induite | Règles exécutées JVM ; UI non compilée SDK |
| Anciennes valeurs de total | Conservées, aucune migration automatique de la médiane | Conservées |
| API publique Open Library | Pacing1050ms, file8, borne2Mio, 429 et annulation ; fixtures | Pacing et bornes source ; annulation de résultat, pas garantie socket |
| Portable | V10 inchangé, total conservé ; consultations exclues | Codec existant pour progressTotal |
| Dépôt dans Google Drive | Livraison des archives et guides dans le dossier demandé | Ne crée pas de synchro cloud de bibliothèque |
| Statut | Version de développement, pas certification commerciale ou zéro-bug | Aucun APK/AAB/SDKCompose/appareil qualifié |

Pas de lecture de livres complets, prix, prêt, disponibilité d’emprunt, scanner ISBN, authentification Open Library ou nouveau connecteur gaming. Les quatre catégories et modules précédents sont conservés. Les accès TMDb/RAWG/Trakt privés demeurent non fournis.

---

# Historique des fonctions conservées

# Fonctions et limites — FranchiseGuide V0.26

La V0.26 étend le parcours sans clé de la V0.25. Les tableaux hérités ci-dessous décrivent les fonctions conservées ; seul QA_REPORT.md fait foi pour les commandes rejouées sur la livraison actuelle.

| Fonction V0.26 | Web / PC | Android source |
|---|---|---|
| Épisode TVmaze sans clé | Lecture explicite détails/show/invités/équipe invitée, réponse validée par identité | Client public borné et règles JVM ; pas d’exécution réseau Android |
| Fiche personne TVmaze interne | Profil et séries créditées, rôles réunis, recherche/tri40+40, ajout explicite complet | Dialog source, recherche titre/rôle et LazyColumn ; pas build Compose |
| Langue des métadonnées | Celle fournie par TVmaze, pas traduction française automatique | Même limitation |
| Distribution par épisode | Invités + équipe invitée seulement ; pas casting principal supposé | Idem source |
| Anti-spoiler, brouillon, retour | Scénarios DOM à fixtures, sans comptes réels | Règles/panneaux source, validation téléphone manquante |
| Cache de détails | Épisodes20 / personnes24,5min en mémoire, pas de sauvegarde portable | Mémoire de panneau ; pas parité LRU inter-écrans revendiquée |
| Fusions d’épisodes TVmaze | MêmeID exact uniquement ; numéro identique ≠ même épisode | Même règle d’identité branchée dans upsert |
| Lien Où regarder sur identité publique seule | Non proposé comme lien TMDb supposé | Non proposé |
| Synchronisation Trakt d’IDs publics | Aucune correspondance TMDb inventée ; notes/suivi locaux conservés | Même limitation |
| Accès TMDb/RAWG/Trakt éditeur | Détection existante ; vrais credentials toujours non fournis | Pas de nouvelle passerelle éditeur native |
| Films et jeux sans clé | Limites Apple/iTunes et FreeToGame inchangées | Clients existants, pas catalogue exhaustif |
| Commercialisation / complétude | Toujours version de développement, validation de droits/compte/origine/appareil ouverte | Aucun APK/AAB/SDKUI validé |

Les consultations sont en mémoire et exclues du portable V10. Les données personnelles enregistrées et identités fournisseur sont conservées. Pas de nouveau connecteur gaming ou cloud général.

---

# Périmètre hérité de V0.25

## Nouveauté ciblée : ne pas demander des clés à l’utilisateur ordinaire

| Parcours | Web / PC | Android source |
|---|---|---|
| Séries sans clé | TVmaze : recherche et ajout complet selon données reçues, saisons/épisodes/cast, attribution | Client public intégré ; typecheck hors SDK, pas réseau/appareil réel |
| Films sans clé | Recherche Apple + fiche limitée et lien ; sans artwork/previews Apple | Même type de client public intégré ; pas écran réel testé |
| Jeux sans clé | FreeToGame free-to-play seulement ; données/images disponibles | Source publique et recherche/fiche intégrées |
| Livres sans clé | Open Library existant, découverte humaine faible volume | Client existant conservé |
| Changer de fournisseur sur fiche ancienne | Aucune conversion silencieuse ; identités et suivi conservés | Garde de source au commit ajoutée |
| Avancés API masqués | Panneau fermé par défaut, état des sources lisible | Paramètres techniques regroupés dans une section explicite |
| TMDb/RAWG gérés par éditeur | Détectés automatiquement si propres accès dans l’environnement du serveur ; non fournis | Nouvelle passerelle native pas intégrée ; accès direct/personnel existant demeure avancé |
| Trakt sans saisie Client ID/Secret utilisateur | Flux appareil via passerelle lorsque l’application éditeur est activée ; testé à fixtures | Source directe existante ; pas de nouvelle authentification native gérée côté serveur |
| Steam/Retro | Connexions et services existants conservés ; accès serveur toujours requis | Transport/connecteurs natifs encore partiels comme avant |
| TVmaze → Trakt | Pas de rapprochement automatique complet des IDs d’épisodes ; suivi public local | Même limitation : aucun TMDb ID inventé |
| Détails riches TMDb / Où regarder / filmographie | Requiert identité TMDb et accès éditeur ou override personnel ; pas remplacé par une fiche Apple | Sources antérieures conservées ; pas build SDK |
| Clés/compte fournis dans l’archive | **Aucun** : publics utilisables sans clé, services privés non préactivés | **Aucun** |
| Déploiement partagé/public | Non livré ; serveurs restreints à l’usage loopback | Non livré |

Les sources publiques sont un secours utilisable sans configuration API, pas une reproduction exhaustive de tous les catalogues payants/avec application enregistrée. Le format portable reste V10. Aucune nouvelle connexion gaming, cloud général, APK/AAB ou certification commerciale. Preuves actuelles dans QA_REPORT.md ; les preuves héritées ne sont pas comptées si leurs commandes sont indisponibles.

---

# Fonctions et limites héritées — V0.24 conservée

**Web et PC embarquent le même moteur. Android source n’est ni un APK ni une exécution sur appareil.** Les lignes ci-dessous décrivent l’implémentation et la preuve disponible, pas une certification commerciale ou une reproduction exhaustive de SeriesGuide.

## Nouveau : fiche épisode

| Fonction | Web / PC | Android source |
|---|---|---|
| Entrée depuis la saison / gestionnaire | Épisode individuel, liens distincts des sélections, retours testés | Liens source raccordés à une nouvelle route |
| Données locales sans clé | Suivi, métadonnées enregistrées et champs personnels sans requête d’ouverture | Données locales présentées dans le panneau source |
| Détails TMDb propres à l’épisode | Lecture explicite liée série/saison/numéro/ID, langue de la fiche | ClientGET et validation JVM ; pas runtime Android réel |
| Distribution / invités / équipe | Groupes distincts, rôles réunis dans le groupe, recherche complète et60 références par affichage | Groupes, recherche simple et LazyColumn ; pas parité parfaite des accents |
| Anti-spoiler | Titre/image/synopsis/personnes masqués, révélation limitée, recherche protégée | Logique source et règles ; UI non exécutée |
| Ma note /10 et texte privé | Formulaire nullable, note séparée du score TMDb, limite 2 000, garde 10 min/champs/identité | Règles testées JVM et repository gardé ; UI non compilée SDK |
| Historique préservé | Édition ne touche ni lectures ni collection ; événements externes aux champs conservés | Même règle pure, sauvegarde source |
| Brouillon / fermeture | Confirmations sur retour/voisins/navigation ; refus de stockage avec copie exportable testé | Confirmation source et callback de retour ; cycle Android non vérifié |
| Dates de l’épisode | Dates connues affichées par 30 et compteur sans date ; ajout daté réutilisé | Compteurs des dates connues et bouton daté ; pas liste paginée identique |
| Actions vu/non vu/collection | Réutilise les aperçus protégés existants | Gestionnaire existant et ajout daté, pas tous boutons inline identiques |
| Navigation précédente/suivante | Saison/numéro connus, réglage des spéciaux, ambiguïtés bloquées | Même règle pure JVM, callback source |
| Personne et Où regarder | Retour personne et disponibilité de la saison testés à fixtures | Écrans source raccordés |
| Cache de consultation | Mémoire20 relevés / 5 min, sans fausse nouvelle date ; refus 429 respecté | Cache local au panneau et garde 429 du client ; pas persistance du cache |
| Échecs et réponses tardives | Annulation, mauvais ID, clé/langue/route/époque, payload incomplet,2 Mio,HTTP 429 testés | Modèles/client et générations source, signatures compilées ; pas vrai transport Android |
| Sortie JSON | Note/textes personnels existants conservés ; données temporaires absentes | Codec existant, pas changement de schéma |

## Corrections de cette passe

- Cache réseau : politique de taille/timeout/cache/credentials intégrée aux clés ; no-store / reload / no-cache pas mémorisés. Reproduction de l’ancien défaut et test corrigé dans la campagne Node.
- Source Android : disponibilité de saison corrigée de `season` hors portée à `sp.season`. Contrôle source et assertion structurelle, pas prétendue compilation Compose.

## Acquis conservés

Quatre médias, accès publics ou fournisseur selon configuration ; affiches/fiches/saisons/épisodes ; langues par fiche ; fiches personnes/filmographie ; saisie manuelle ; progression des jeux/livres distincte des fins passées ; univers/alias/suggestions grisées/ordre ; listes personnelles/membres/ordre/exports ; reprise et organisation groupée ; agendaICS et visionnages datés ; statistiques et historique ; entretien catalogue ; aperçu imports/restaurations ; sélection épisodes protégée ; Où regarder par pays/saison ; journal Trakt ; Steam OpenID et RetroAchievements public sur PC/Web avec centre de synchronisation.

Les régressions de ces routes sont rejouées lorsqu’exécutables. Les suites impossibles à charger ne sont pas remplacées par des chiffres de versions précédentes.

## Encore ouvert

- Android SDK/Compose : aucune construction, signature, installation téléphone, notification/widget ou comparaison de rendu appareil ; plusieurs écrans et connecteurs Steam/Retro natifs restent partiels.
- Origine navigateur : navigation locale bloquée dans l’environnement, agent-browser absent. Pas de validation PWA installée/hors ligne, persistance entre vraies fenêtres, WebLocks ou cookies réels.
- Comptes/fournisseurs : aucun compte TMDb/Trakt/Steam/Retro réel utilisé. Les passerelles HTTP utilisent des réponses contrôlées ; les photos de QA sont fictives.
- Stockage : localStorage synchrone/quota conservé ; IndexedDB sert aux copies de récupération, pas à une migration générale. Copies locales et brouillons ne remplacent pas un export externe.
- Connecteurs : pas de nouveau PSN/PSNProfiles/Xbox/Epic/GOG. Pas de cloud général livres/jeux/franchises ni backend public multiutilisateur ni déploiement.
- Distribution : PC=JAR Java 17 avec navigateur, pas EXE. Fenêtre Windows/macOS non exécutée. Droits et attributions des services doivent être vérifiés pour une offre commerciale réelle.
- QA : deux suites jsdom/fake-indexeddb et deux codecs Kotlin contre véritable org.json non exécutés faute de dépendances. Aucune affirmation d’absence de tout bug.
