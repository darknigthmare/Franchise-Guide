# Fonctions et limites — FranchiseGuide 0.20.0

Base : **Suite V0.19 RAR disponible**, conservée puis modifiée. Web/PC partagent les fichiers canoniques. Dans cette matrice, « Android » signifie sources : aucun APK/AAB, SDK/Compose ou appareil validé. Les tests JVM et la compilation de signatures non-UI ont une portée distincte.

| Fonction V0.20 | Web / PC | Android source |
|---|---|---|
| Parcours courant jeu/livre | Éditeur strict : état, unité, total inconnu0, édition/plateforme/minutes déclarées | Règles JVM, panneau et repository intégrés |
| Diminuer, pause, abandon, recommencer | Conserve fins et dates ; nouvelle fin idempotente ; effacement séparé | Même règle pure testée, UI non exécutée au SDK |
| Onglets pause/abandon/actif | Selon le parcours actuel, pas le drapeau de fin historique | Sources modifiées, pas de rendu appareil |
| Fusions de parcours contradictoires | Refus explicite, données personnelles conservées | Préservation explicite à la fusion catalogue ; pas déclaration d’une UI de fusion totalement identique |
| Compteurs bibliothèque/événements/relevés séparés | Moteur partagé de bilan ; événements valides seulement | Moteur JVM et panneaux natifs |
| Filtres de bilan | Périodes/préférences, catégorie, masqués, favoris, spéciaux ; date ne réduit pas le compteur de bibliothèque | Catégories et filtres sources, périodes prédéfinies ; pas toute la parité du Web |
| Temps vidéo | Films/épisodes avec durée connue positive ; inconnus signalés ; estimation actuelle et avec revisionnages | Règles correspondantes JVM |
| Pages courantes | Page/pages seulement ; pas de cumul de chapitres/% ni multiplication historique | Même calcul pur |
| Temps Steam | Par profil/App ID, doublons signalés et maximum connu retenu une fois | Données portables, règles et affichage source ; pas connexion Steam native |
| Succès | Normal, Hardcore, inconnus, cases personnelles et platines explicites distincts | Relevés et calculs sources ; pas API gaming native |
| Genres/franchises/favoris/collections | Répartitions et compteurs rétablis, clés de libellés sûres | Répartitions source, pas tous les détails d’interface Web |
| Séquences de jours connus | Jours réellement datés ; jours absents non comblés | Calculs natifs existants ; pas de validation d’affichage SDK |
| Historique filtré/recherché | Recherche dans tout le résultat ;100 lignes initiales ; période/catégorie | Source date/recherche et rendu borné |
| Export du bilan numérique JSON | Sans titres, notes, noms/identifiants de profils ; pas une certification d’anonymisation | Non ajouté dans cette passe |
| Export historique JSON/CSV | Titres/dates connus ; CSV UTF8/BOM et échappement ; limite10000 explicite | Non ajouté dans cette passe |
| Entretien catalogue | Centre60 lignes initiales ; incomplet/périmé/délai/clé/source non supportée | Pas de nouveau centre natif équivalent |
| Lots catalogue et reprise | Manuel8, automatique3 ; délai par fiche/fournisseur ; annulation et réponse obsolète gardées | Correction des critères de rafraîchissement et protection des notes ; pas de parité du délai persistant |
| Affiche catalogue absente | Pas de boucle d’actualisation d’une fiche autrement complète ; clés et réseau toujours requis | Critère de réparation corrigé dans les sources |
| Données de progression portables | Nouveau schémaV9, lecture V1–V8 ; champs personnels conservés | Codec de signatures compilé ; org.json réel non exécuté |
| Langues catalogue et préférences existantes | Conservées, nouvelle reprise catalogue respecte les délais | Sources existantes conservées |

## Fonctions antérieures conservées et retestées lorsqu’exécutables

Univers personnels/alias/classement manuel/Aucune/ordre/suggestions deV0.19 ; aperçu préparé d’import et restauration V0.18 ; organisation groupée/annulation et reprise des titres V0.17 ; agenda quatre médias, export calendrier et dates passées V0.16 ; centre de synchronisation gaming, Trakt, Steam OpenID et RetroAchievements personnels ; fiches riches, casting/personnes/filmographies, import SeriesGuide, manuels, récupération et diagnostic. Les résultats antérieurs manquants ne sont pas réutilisés comme nouveaux résultats.

## Limites transversales

Le stockage principal Web reste localStorage, synchrone et soumis à quota. IndexedDB sert aux copies de récupération existantes, pas à une migration générale de toutes les données. Le plafonnement des lignes affichées ne garantit pas les performances de tous appareils ni le stockage d’une bibliothèque synthétique de50000 épisodes.

Les temps de jeu Steam sont des relevés par profil. Ils ne prouvent ni la propriété du compte ni la durée d’une campagne terminée. La connexion officielle Steam reste distincte d’une référence de profil manuel. RetroAchievements lit un profil public, sans authentification de propriété ; les champs normal/Hardcore n’équivalent pas à un trophée PlayStation. PSN/PSNProfiles, Xbox, Epic et GOG restent des références/imports, pas de nouveaux connecteurs automatiques.

Les nouvelles plages de relecture catalogue sont des politiques de FranchiseGuide : pas des quotas ou SLA fournis par les catalogues. Une image ou durée absente ne devient pas disponible sans donnée réelle. Les fiches manuelles/Steam/Retro sans correspondance catalogue ne sont pas enrichies en devinant une identité depuis le titre.

Les univers ne constituent pas un catalogue exhaustif ni une chronologie officielle. Le bilan ne mesure pas le pourcentage d’achèvement d’une franchise entière inconnue. L’export CSV est un fichier détaillé de lecture, pas une sauvegarde complète ni une intégration distante automatisée.

**Restent ouverts :** deux suites jsdom/fake-indexeddb, deux tests de codecs org.json réel, build Android SDK/Compose/signature/appareil, comptes et lectures fournisseurs réels, PWA persistante installée/horsligne, vraie concurrence multi-onglets, calendriers/tableurs externes, fenêtres PC Windows/macOS, parité sociale/réglages exhaustive SeriesGuide, gaming natif Android, nouveaux connecteurs, cloud général, backend d’une offre publique et dossier commercial. Aucun résultat de cette version ne ferme à lui seul ces points.
