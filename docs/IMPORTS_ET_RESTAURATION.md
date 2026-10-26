# Importer et restaurer avec aperçu — V0.18

## 1. Deux modes qui ne font pas la même chose

**FranchiseGuide portable V1–V8 : remplacement.** Les données absentes de la sauvegarde ne restent pas automatiquement dans la bibliothèque. Les clés et jetons existants restent sur l’appareil. Les commandes et contrôles internes de l’ancien fichier ne sont pas rejoués.

**SeriesGuide : fusion.** Les fichiers séries, films et listes sont préparés ensemble puis rapprochés par leurs identifiants pris en charge. Les références d’une liste peuvent être résolues même si le fichier de listes a été sélectionné avant les séries/films. Une ressemblance de titre ne crée pas une identité fournisseur. Une référence impossible à résoudre est indiquée dans le bilan, pas transformée en faux titre.

Le format officiel SeriesGuide est documenté dans `https://github.com/UweTrottmann/SeriesGuide/blob/main/docs/backup-json-schema.md`. L’aide explique les fichiers séparés et avertit qu’une synchronisation Cloud/Trakt peut remplacer des données importées : `https://www.seriesgui.de/help/how-to/backup/backup`. Les protections de ce guide sont celles de FranchiseGuide ; elles ne reproduisent pas SeriesGuide Cloud.

## 2. Préparation sans changement de bibliothèque

Depuis Plus → Importer FranchiseGuide, choisir le fichier portable ; depuis Importer depuis SeriesGuide, sélectionner les exports voulus. Limites de cette version : **un fichier portable** ou **1 à10 fichiers SeriesGuide**, **32 Mio par fichier**, **64 Mio cumulés**. Ce sont des limites de l’application, pas des limites annoncées par SeriesGuide.

Web/PC lit les octets par l’API File puis décode en UTF‑8 strict. Un fichier invalide, vide, trop grand ou une seconde partie invalide empêche toute application de la sélection. Le bouton Annuler pendant la préparation invalide les réponses tardives ; il ne promet pas d’interrompre immédiatement une lecture système déjà engagée.

Les sources Android lisent séquentiellement et appliquent le budget restant avant d’ouvrir/lire les fichiers suivants ; les octets BOM comptent dans le budget. Le décodage, les données de comparaison et les règles sont testés hors appareil. Cela ne prouve pas le fonctionnement de tous les fournisseurs Android de documents.

Les ZIP/RAR de l’application ne sont pas des sauvegardes de bibliothèque : extraire la distribution pour l’utiliser, choisir un JSON pour importer ses données.

## 3. Lire l’aperçu

Le tableau Actuel → Après indique les titres suivis par catégorie, épisodes, épisodes vus, dates de visionnage connues, notes privées, listes, franchises personnalisées, profils gaming, listes de succès et entrées de succès. Les dates sont dédupliquées à la seconde par fiche/épisode ; la dernière activité agrégée d’une série n’est pas comptée comme un visionnage supplémentaire.

Les compteurs d’attention indiquent les dates connues qui ne figurent plus sous le même identifiant, les états vus retirés et les notes privées/chiffrées modifiées ou absentes. Ils ne certifient pas une suppression distante, ne déduisent pas des dates inconnues et ne détaillent pas les textes des notes.

La comparaison des fiches est **par identifiant local**. Renommer une fiche conserve son identité ; un même titre avec un identifiant différent apparaît en ancien titre absent + nouveau titre ajouté. Le tableau ne fait pas de rapprochement approximatif et ne garantit pas que deux identités différentes ne représentent jamais la même œuvre.

La recherche et les filtres portent sur toutes les lignes de comparaison. Le Web/PC affiche50 lignes au départ puis permet d’en ajouter50. Le panneau Android source propose un filtre de titre et les catégories de différence disponibles dans son interface ; il n’a pas encore toute la parité de filtres Web. Aucun épisode caché par l’anti-spoiler n’est exposé comme détail dans cette liste de fiches.

Le mode SeriesGuide indique aussi le nombre d’éléments lus et de références non résolues. Le chiffre n’est pas un audit de toutes les extensions possibles du format. En particulier, les horaires de diffusion personnalisés, les clés de services externes et les éventuels champs inconnus ne sont pas promis comme intégralement reproduits. Conserver le JSON original si ces champs importent pour une migration ultérieure.

## 4. Choisir les préférences

L’option **Importer également les réglages publics du fichier** est décochée par défaut pour une sauvegarde portable. La bibliothèque sera remplacée, mais les réglages de langue, apparence, anti-spoiler, etc. de cet appareil seront conservés tant que l’option reste décochée.

Cocher l’option adopte les préférences publiques prises en charge. Les clés/API secrets/jetons n’en font jamais partie. Les profils gaming importés ne sont pas authentifiés par cette opération ; leurs automatismes sont désactivés. Changer l’option réinitialise la case de confirmation pour éviter une validation d’un choix différent de celui examiné.

Sur Android source, les préférences publiques retenues sont enregistrées dans le même commit SharedPreferences que l’état et sa file Trakt, puis relues au lancement. Les credentials restent dans leur stockage séparé. Ce mécanisme n’est pas présenté comme une transaction distribuée entre tous les stockages Android/serveur.

## 5. Appliquer — ou annuler

Lire l’aperçu, cocher la compréhension puis choisir Remplacer/Fusionner. Une copie locale préalable doit réussir. L’import n’est pas appliqué si le stockage refuse la copie, si une autre fenêtre a pris l’écriture ou si Trakt est en train de synchroniser.

Une modification locale ou de réglages, une génération de bibliothèque différente ou une révision de stockage différente rend l’aperçu périmé. Relire les fichiers est nécessaire. Le Web vérifie également que son candidat préparé n’a pas été modifié. Les sources Android comparent le snapshot complet et la génération sous les protections du repository.

En cas d’échec de l’écriture principale Web/PC, le mécanisme existant restaure l’état sauvegardé et garde, lorsque disponible, un brouillon exportable ; aucune réussite n’est affichée. Sur Android, le repository restaure ses anciennes collections/réglages en mémoire lorsque le commit échoue. Une copie de sécurité déjà créée peut rester présente : elle n’est pas effacée pour donner l’illusion qu’aucune étape n’a eu lieu.

Fermer l’aperçu, utiliser Escape sur Web/PC ou Annuler n’applique pas la bibliothèque et ne crée pas la copie préalable. Les diagnostics locaux peuvent enregistrer une erreur de préparation ; « sans modification » désigne ici la bibliothèque et les suivis, pas l’absence de toute activité technique.

## 6. Restaurer des snapshots

Les boutons de restauration des instantanés locaux et IndexedDB Web/PC passent par le même aperçu. La préparation capture le contenu examiné plutôt que de retenir uniquement un index qui pourrait changer après rotation des copies.

Un snapshot partage les limites de son stockage ; IndexedDB n’est pas devenu le stockage principal de l’application. Les tests DOM contrôlent le point d’entrée de restauration avec une copie simulée ; le test du vrai module IndexedDB avec fake-indexeddb n’a pas été exécuté sur cet environnement faute de dépendance. La persistance de l’origine réelle reste à vérifier.

## 7. Rapport exportable

**Exporter le bilan des compteurs** enregistre un JSON local avec mode, fichiers sélectionnés et tailles, compteurs avant/après, nombres de fiches différentes et avertissements. Pas de textes de notes, de détails de profils, de titres d’épisodes ni de clés copiés dans ce rapport.

Les noms de fichiers peuvent néanmoins contenir une information personnelle. Vérifier le rapport avant de le partager. Il ne permet pas de restaurer la bibliothèque. Le rapport détaillé portable reste une sauvegarde personnelle, même sans secrets de connexion.

## 8. Précisions sur SeriesGuide

Les chaînes ISO8601 complètes de date de série (par exemple une valeur finissant par `T08:00:00Z`) et les millisecondes d’époque d’épisodes/films sont acceptées. Les dates d’affichage sont ramenées au jour UTC. Les jours impossibles ou heures invalides sont refusés plutôt que normalisés. Les sentinelles de date inconnue restent inconnues.

Une durée d’épisode connue, y compris0, ne disparaît pas au réimport. Les notes privées existantes sont conservées ; sur Android le champ est maintenant inclus dans le codec épisode. Les booléens JSON et drapeaux0/1 pris en charge sont acceptés ; une chaîne telle que `"false"` n’est pas considérée comme une valeur vraie par accident.

Une réimportation ne crée pas volontairement des doublons pour les identités reconnues. Les épisodes minimaux avec identifiants mais sans numérotation gardent leur règle antérieure. Cela ne fournit pas une conversion automatique de toutes les divergences de saison/épisode entre TVDB et TMDb. Un extrait incomplet pourra nécessiter un fichier complémentaire ou une actualisation catalogue.
