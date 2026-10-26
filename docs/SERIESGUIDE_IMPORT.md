# Import SeriesGuide — V0.18

Le parcours utilisateur passe maintenant par **préparation → comparaison → confirmation**. Lire `IMPORTS_ET_RESTAURATION.md` pour les limites, les données non résolues et la distinction avec le remplacement d’une sauvegarde FranchiseGuide.

Sélectionner ensemble les exports JSON séries/films/listes permet de résoudre leurs liens quel que soit l’ordre. Les types et champs pris en charge sont validés avant toute fusion. Le rapport ne prétend pas migrer toutes les extensions possibles du format SeriesGuide. Les données privées anciennes et le fichier d’origine restent à conserver hors de l’application.

Les éléments suivants documentent le format pris en charge antérieurement ; le nouveau parcours d’aperçu s’applique aux entrées décrites.

---

# Import depuis SeriesGuide — V0.10

FranchiseGuide lit les sauvegardes JSON utilisateur décrites par le schéma public de SeriesGuide :

- `seriesguide-shows-backup.json`
- `seriesguide-movies-backup.json`
- `seriesguide-lists-backup.json`

Référence amont : `docs/backup-json-schema.md` du dépôt officiel SeriesGuide.

## Séries

Sont récupérés quand disponibles : TMDb/TVDb/IMDb, titre, année, résumé, poster, statut, réseau, durée, genres, favori, masqué, notifications, note utilisateur et note chiffrée. Les saisons et épisodes conservent numéros, IDs externes, date, résumé/image, `watched`, `plays`, `skipped` et `collected`.

## Films

Sont récupérés : TMDb/IMDb, titre, année/date, durée, poster, résumé, `watched`, `plays`, `in_collection` et `in_watchlist`.

## Listes

- `tmdb-show` : relié par TMDb ;
- `show` : relié par TVDb ;
- `movie` / `tmdb-movie` : relié par TMDb ;
- `imdb-movie` : relié par IMDb ;
- `episode` et `season` legacy : tentative de retrouver la série par les IDs TVDb des épisodes/saisons.

Les éléments impossibles à résoudre sont comptabilisés comme ignorés au lieu de faire échouer tout l'import.

## Fusion et sécurité

L'import est **fusionnant et transactionnel** :

1. l'état local est mémorisé ;
2. tous les fichiers sélectionnés sont analysés ;
3. médias, épisodes et listes sont fusionnés via leurs identifiants ;
4. si une erreur non récupérable survient, médias, épisodes, suggestions, listes et franchises sont remis dans leur état précédent.

Une sauvegarde FranchiseGuide V6 peut être exportée immédiatement après migration pour figer le résultat.

Valider la migration avec un export personnel et conserver l’original. Les fixtures de test ne prouvent pas la compatibilité avec toutes les anciennes versions. Les remappages de numérotation anime/TVDb/TMDb peuvent nécessiter une vérification manuelle ; aucune date de visionnage absente n’est inventée.


## Imports minimaux V0.8

Les IDs TMDb/TVDb sont prioritaires. Si saison/numéro sont absents, les flags `seasonUnknown`/`numberUnknown` sont conservés ; le numéro inconnu n’est pas confondu avec celui d’un autre épisode. La résolution complète charge ensuite les métadonnées par ID et garde les états vus. Une entrée sans ID ni numérotation exploitable ne reçoit pas une fausse correspondance.
Sur Web, une copie de sécurité réussie est exigée avant commit. Un quota plein doit bloquer l’opération plutôt qu’effacer l’ancien état. Les fichiers invalides ne provoquent pas d’import partiel. Le convertisseur est testé sur fixtures complètes et minimales, pas sur tous les exports réels possibles.
L’historique SeriesGuide sans dates d’épisodes n’est pas transformé en dates inventées : compteur préservé, dates inconnues signalées.

## Sécurité V0.10

Web/PC et sources Android exigent maintenant la sauvegarde avant le commit. Pendant la synchronisation, attendre sa fin. Les anciennes opérations Trakt sont conservées mais suspendues : les examiner dans Bibliothèque Trakt avant reprise/annulation explicite. Le suivi importé n’est pas renvoyé globalement au compte distant. Ces parcours sont testés en JavaScript sur fixtures ; la source native est compilée hors SDK, pas exécutée avec org.json/SharedPreferences réels.

## V0.11
Le champ language d’une série importée alimente contentLanguage lorsque nécessaire ; une préférence locale existante est préservée au réimport. Les trophées ne proviennent pas de SeriesGuide : ils restent dans l’espace gaming séparé du schéma portable 8.

## Sauvegardes protégées V0.30

`.fgbackup` est réservé au portable FranchiseGuide chiffré, pas aux exports SeriesGuide JSON multiples. Après déchiffrement, toutes les validations de structure, l'aperçu et la confirmation habituelle restent exigés. Les archives ZIP/RAR du logiciel ne sont pas des fichiers d'import de bibliothèque. Le portable interne reste V11 ; l'enveloppe de chiffrement possède sa propre version1.
