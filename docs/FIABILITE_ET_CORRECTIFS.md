# Recherche, réimports et fiabilité — V0.29.0

## De la V0.28 à cette livraison

Cette passe a été reprise depuis le ZIP V0.28 fourni. Aucune archive V0.29 exploitable n’était présente au démarrage ; le dossier Google Drive V0.29 ne contenait qu’un guide d’une tentative antérieure. Les corrections suivantes sont présentes dans les sources de cette livraison et couvertes par des tests exécutés.

## Recherche catalogue : chaque source avance indépendamment

Le curseur est mémorisé en session par fournisseur, non globalement pour toute la recherche. Exemple : TMDb échoue en page1 tandis que RAWG et Open Library réussissent. Réessayer ne contacte que TMDb page1. Charger plus peut lire RAWG/Open Library page2 sans relancer TMDb ni perdre ses erreurs. Une réponse annoncée terminée ne fait plus l’objet de demandes de pages suivantes.

Le fournisseur et les tableaux/pagination attendus sont vérifiés. Une réponse `{}` à une recherche TMDb n’est pas un résultat vide valide. Une erreur HTTP429 conserve une échéance de reprise pour cette source. L’option manuelle ne la contourne pas. Cette protection porte sur la session de recherche ; elle n’est pas un quota global partagé avec tous les autres écrans de l’application.

Les résultats déjà reçus restent affichés avec les erreurs détaillées. Une source en erreur ne remplace pas les titres locaux. Les références retirées (`added=false`) ne masquent plus un candidat catalogue. Le bouton Ajouter conserve son parcours de fiche complète, sans ajout automatique des résultats.

L’affichage local commence par60 lignes et la recherche ignore la casse et les accents, sans supprimer les caractères non latins. La recherche porte sur toute la bibliothèque locale ajoutée, pas seulement sur les lignes affichées. Les résultats distants déjà reçus ne sont pas tous virtualisés dans cette passe ; la protection contre de très grands catalogues reste un chantier distinct.

## Annulation et identité d’une requête

Chaque requête appartient à une génération de texte/catégorie/configuration/bibliothèque. Changer de recherche ou fermer le dialogue invalide la génération et signale l’annulation au réseau. Une tâche encore en attente de place est retirée de la file sans patienter derrière une autre lecture. Une attente Retry-After annulée ne déclenche pas plus tard sa tentative réseau. Le résultat d’une ancienne recherche ne peut plus remplacer la nouvelle.

Une annulation client ne garantit jamais que le serveur distant n’a pas déjà reçu une requête. Cette règle est spécialement importante pour un POST d’historique Trakt, qui reste géré par son journal d’incertitude.

## Conservation des dates et des notes au réimport

L’ancien chemin fusionnait des dates avant d’avoir relevé le compteur correspondant : un historique d’une lecture et une nouvelle date importée pouvaient redevenir une seule date. La fusion réunit maintenant les dates valides distinctes, puis ajuste le compteur en conservant le maximum des compteurs et du nombre d’événements connus. Les comptes de lectures non datées ne sont pas additionnés aveuglément. Réimporter le même fichier n’ajoute pas de nouvelle lecture.

La date d’activité agrégée d’une série n’est pas convertie en visionnage en plus de ses épisodes. Les notes chiffrées et textes privés locaux ont priorité lors du réimport. Le fichier préparé reste transactionnel : un conflit d’identité est signalé avant application, sans modifier une moitié de la bibliothèque.

Le numéro de saison/épisode peut servir de secours seulement sans identité contradictoire. Des identifiants TMDb/TVDB connus en conflit empêchent le rapprochement. Pour TVmaze, le même identifiant d’épisode est nécessaire ; un autre ID au même numéro ne reprend pas l’historique.

## Diagnostic des doublons publics

Les identités TVmaze, Apple/iTunes et FreeToGame sont désormais contrôlées, ainsi que les formes `OL…W` et `/works/OL…W` équivalentes d’Open Library. Les rapprochements par titre restent des indices pour l’utilisateur, jamais une preuve autorisant une fusion automatique. Les noms non latins et emoji ne sont plus tous normalisés vers une chaîne vide.

Le diagnostic s’arrête à1000 couples candidats ou20000 comparaisons. Son résultat est alors marqué **partiel**. Ne pas l’interpréter comme un audit exhaustif. Deux IDs publics connus différents bloquent l’action de fusion, même lorsque leurs titres ressemblent.

## Trakt : aucune réponse d’un ancien compte n’est réutilisée

Les appels API, renouvellements de jeton et pages successives portent la génération du compte. La déconnexion interrompt les lectures actives et invalide les résultats. Une ancienne réponse401 ne peut plus lancer un renouvellement/réessai après déconnexion ; un ancien renouvellement ne peut plus effacer le verrou d’un renouvellement plus récent ni ses credentials. Les retours de connexion annulée sont écartés.

La boucle de synchronisation vérifie la génération avant d’écrire les résultats et les erreurs. Une réponse d’un compte quitté ne doit pas modifier la file du suivant. La pagination Trakt refuse les pages/capacités incohérentes et les listes partielles au-delà du plafond. Aucun nouvel automatisme ne revendique un acquittement si le statut distant est incertain.

## Sources Android

Les règles d’identités et de réunion des dates sont reliées au repository. Les tableaux de recherche manquants deviennent des erreurs dans les clients TMDb, RAWG et Open Library. Des vérifications de génération supplémentaires protègent l’autorisation et le renouvellement Trakt. Aucun écran natif de recherche multifornisseur avec curseurs indépendants n’est annoncé dans cette version. Les règles sont testées sur JVM ; le code non graphique est compilé contre des signatures de test. Android SDK, Compose, WorkManager et téléphones réels ne sont pas exécutés.

## Preuves et limites

`tests/baseline_v028_reproductions.cjs` charge les fichiers V0.28 non modifiés conservés dans `tests/fixtures/v029-baseline/`, puis compare trois défauts avant/après. La provenance et les SHA-256 sont documentés. `tests/results/v029/` contient les tests nouveaux, la campagne disponible et les captures à390/768/1360 pixels. Les fournisseurs, images, horloges et stockages sont contrôlés ; aucune requête d’historique réelle n’a été envoyée pour ces essais.

Pas de nouveau connecteur, pas de clé privée embarquée, pas de cloud général, pas d’APK et pas de garantie zéro bug. Les limites complètes sont dans FEATURE_MATRIX.md et QA_REPORT.md.
