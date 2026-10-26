# Gérer les épisodes sans effacer accidentellement le suivi — V0.22

## Ouvrir et sélectionner

Dans la fiche d’une **série déjà ajoutée**, choisir **Gérer / sélectionner les épisodes**. Le Web/PC ouvre le gestionnaire depuis cette fiche ; le retour explicite et le bouton arrière retrouvent la même série. Les suggestions non ajoutées, fiches films/jeux/livres et séries retirées ne sont pas des cibles de ce gestionnaire.

Filtrer la saison, l’état (vus, à voir, ignorés, dans/hors collection), la date (diffusé, futur, inconnue) et le titre ou numéro. Inclure les spéciaux est un choix explicite. Un épisode sans numéro exploitable reste à identifier, sans numéro inventé. Les épisodes connus hors du catalogue actuel restent des données locales suivies ; aucune actualisation n’est lancée par une sélection.

La sélection est conservée entre les filtres. Le compteur montre le nombre **hors filtre** : décocher l’affichage des spéciaux ne retire pas automatiquement des spéciaux déjà sélectionnés. Utiliser « Désélectionner les résultats » ou « Vider la sélection » pour les retirer. La recherche couvre les résultats non encore affichés ; elle n’interroge pas les titres non vus cachés par l’anti-spoiler.

Le Web/PC affiche d’abord **60 épisodes** puis 60 de plus par clic. **Sélectionner tous les résultats** sélectionne tous ceux qui correspondent aux filtres, pas seulement les 60 visibles. Maximum **10 000 épisodes par opération**, sans troncature silencieuse. L’écran Android utilise LazyColumn ; son rendu n’a pas été testé sur appareil.

## Actions disponibles

| Action | Comportement |
|---|---|
| Marquer vus maintenant | Ajoute une seule lecture à un épisode réellement non vu, sans historique résiduel et non ignoré. Ne rejoue pas les épisodes déjà vus. La date est celle proposée dans l’aperçu. |
| Autoriser dates inconnues/futures | Option uniquement pour « vu », décochée par défaut. Elle ne change pas la date de diffusion. Sans elle, ces épisodes sont exclus du lot. |
| Effacer les visionnages / marquer non vus | Efface l’état vu, le compteur et les dates de lecture des épisodes concernés. Notes privées, notes chiffrées et collection restent intacts. L’aperçu indique le nombre de dates connues et le compteur de lectures effacés. |
| Ignorer les non vus | Pose le drapeau ignoré seulement si aucun historique n’est présent. Un épisode vu, avec lectures comptées ou dates résiduelles, est protégé. |
| Réactiver les ignorés | Retire uniquement le drapeau ignoré. Ne crée ni lecture ni date. |
| Ajouter / retirer de la collection | Ne change que la collection. Conserve historiques, notes, état ignoré et dates de diffusion. |

Pour **ajouter un revisionnage ou une lecture passée**, revenir à la fiche et utiliser l’action datée de la V0.16. Un lot « vu » n’est pas une façon d’ajouter plusieurs lectures. Ne pas utiliser « non vu » pour simplement masquer une série.

## Prévisualisation et confirmation

Cliquer sur **Prévisualiser l’action** ne change rien. L’aperçu affiche les épisodes sélectionnés, ceux qui changent et les protections. Il affiche les 40 premières lignes puis permet d’en voir davantage. Le nombre modifié porte sur toute la sélection.

Cocher la confirmation explicite, puis **Appliquer le lot**. Une copie locale de sécurité doit réussir ; garder également une sauvegarde JSON hors du navigateur. Le stockage local et ses copies peuvent tous être perdus si le profil est effacé.

Les garde-fous au moment d’appliquer contrôlent les identités et les champs touchés. Si un épisode a disparu, si un compteur/état visé a changé, si la bibliothèque a été remplacée ou le compte modifié, **tout le plan est refusé**. Les changements non ciblés restent conservés : par exemple, ajouter à la collection n’écrase pas une note ou un visionnage enregistré après l’aperçu. Même un épisode sélectionné qui était déjà dans l’état demandé est vérifié de nouveau.

L’aperçu expire après **dix minutes**. Annuler ou Échap ne modifie rien. La sélection est vidée après application réussie ; elle n’est ni une sauvegarde ni une file en arrière-plan. Aucun plan importé n’est exécuté : les changements sont reconstruits et vérifiés avant application.

En cas de refus de la copie de sécurité, l’opération ne commence pas. En cas de refus de l’écriture principale Web, l’état sauvegardé est retrouvé et le brouillon de travail reste exportable via le mécanisme existant, sans faux message de réussite. Les snapshots peuvent eux-mêmes être limités par quota. Les variantes de panne sur le stockage Android réel restent à tester.

## Effet sur Trakt

L’état **ignoré est local**. Ignorer ou réactiver ne crée pas d’opération de suppression d’historique Trakt. Référence de conception : SeriesGuide documente également que l’état ignoré n’est pas synchronisé avec Trakt, dans son aide « Watched, collected and skipped » : https://www.seriesgui.de/help/how-to/basics/buttons.

Les nouveaux visionnages d’épisodes TMDb identifiés peuvent être ajoutés à la file Trakt, avec leur nouvelle date seulement. Le lot ne renvoie pas tout l’ancien historique. Un effacement explicite peut ajouter une opération de retrait d’historique. Les changements de collection peuvent aussi rejoindre la file. Aucun identifiant fournisseur n’est inventé.

Les opérations déjà en attente restent dans la file. L’aperçu signale les changements identifiés **susceptibles** d’être envoyés ; ce n’est pas un accusé distant. Une synchronisation Trakt active bloque l’application du lot. Il n’y a pas d’annulation automatique d’une requête déjà partie. Vérifier les opérations incertaines avant une nouvelle tentative et ne pas supposer qu’une restauration JSON annule une écriture distante.

## Compatibilité et limites

L’export portable reste **V10**. Les données de suivi modifiées utilisent les champs existants ; les filtres, sélections et plans ne sont pas exportés, ne survivent pas au rechargement et ne sont pas synchronisés vers Android. Les sources Android comprennent le gestionnaire et les règles correspondantes, mais aucune compilation SDK/Compose, signature ou validation téléphone n’a eu lieu dans cette livraison.

Cette fonction ne fournit ni une liste exhaustive d’épisodes absents, ni un nouveau connecteur gaming, ni une synchronisation cloud générale. Elle traite les épisodes présents dans la bibliothèque ajoutée. Les tests de fournisseurs utilisent des réponses contrôlées, sans compte réel.
