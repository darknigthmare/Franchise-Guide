# Où regarder — FranchiseGuide 0.23.0

## Données et portée

Le panneau lit les **disponibilités JustWatch via TMDb** pour un film, une série ou une saison précise. La liste des pays peut être chargée depuis TMDb. Il ne devine pas de correspondance au titre : un identifiant TMDb numérique confirmé est requis. Les saisons proposées sont celles de la bibliothèque, y compris les spéciaux explicitement connus.

Chaque consultation est attachée à quatre éléments : type/identifiant du titre, pays, portée (ensemble ou saison) et heure de lecture. L’offre d’une série entière n’implique pas une offre identique pour chaque saison. Les noms stockés par les anciens clients ne comportaient pas ce contexte ; ils sont affichés comme un ancien relevé sans pays/date conservés, pas comme le résultat du pays actuellement choisi.

## Accès proposés

- **Abonnement** : catégorie flatrate du fournisseur ; cela ne veut pas dire que l’utilisateur possède cet abonnement.
- **Gratuit** : catégorie free rapportée ; conditions d’accès toujours à confirmer.
- **Avec publicité**, **Location**, **Achat** : catégories séparées. Ni prix ni résolution/DRM ne sont déduits.

Un même service peut apparaître dans plusieurs catégories. Les compteurs distinguent services uniques et offres par catégorie. Les répétitions identiques au sein d’une catégorie sont dédupliquées ; une identité contradictoire fait refuser tout le relevé.

## Pas de fausse indisponibilité

Trois états sont distincts : pas encore consulté ; pays absent de la réponse ; pays présent sans offre renseignée. Aucun de ces états n’est une preuve d’indisponibilité mondiale. Un filtre de nom/type ne trouvant rien n’efface pas le relevé reçu et ne devient pas une affirmation sur les autres services.

## Actualiser et revenir

Les lectures sont déclenchées par l’utilisateur, pas automatiquement à l’ouverture. Le cache Web garde au maximum20 relevés, en mémoire, et peut réutiliser une lecture de moins de5minutes **sans changer sa date**. La commande Actualiser relit réellement ; le délai429 a priorité. Les relevés ne sont ni sauvegardés sur disque par cette fonctionnalité, ni ajoutés aux exports portables, ni partagés entre appareils. Android conserve aussi un cache de20 relevés dans l’écran source, non persistant.

Une sélection nouvelle n’affiche pas l’offre d’une ancienne sélection. Une erreur garde l’ancien relevé valide du même contexte. Les réponses tardives après annulation, navigation, nouvelle identité, clé API remplacée ou import/restauration sont rejetées. Un changement de pays/portée annule la lecture précédente. Le bouton retour restitue la fiche ou la file À reprendre d’origine sur Web/PC.

## Liens, images et requêtes

Les logos acceptés sont les chemins d’images TMDb attendus ; les noms restent lisibles si le logo est manquant ou refusé. La sortie utilise uniquement le lien TMDb fourni pour le bon type et le bon identifiant. HTTPS requis, hôtes TMDb autorisés, sans identifiants incorporés, port inhabituel ni fragment. Les paramètres arbitraires sont supprimés ; seul le pays choisi est transmis. Sans lien valide fourni, aucun lecteur ou lien fournisseur n’est reconstruit.

Les réponses GET sont limitées à1Mio (pays) et2Mio (offres) puis validées : tableaux attendus, identifiants, pays, catégories, priorités et doublons. Les champs supplémentaires ne deviennent pas un prix ou une promesse d’accès. Une réponse trop grande, mal formée, négative ou contradictoire ne remplace pas l’état consulté auparavant. Le partage réseau historique conserve ses comportements par défaut en dehors des appels explicitement bornés.

HTTP429 : report de la reprise, minimum60s, maximum24h dans cette couche. Un délai invalide utilise le repli de l’application. Ces plafonds/cache5min ne sont **pas des quotas officiels TMDb**. La garde est seulement en mémoire et ne promet pas une suspension universelle de tous les autres clients existants.

Aucun POST fournisseur, watchlist, visionnage, note, collection ou opération gaming n’est créé par ce panneau. Le changement de pays ne modifie pas les préférences publiques. Les requêtes TMDb portent la clé personnelle déjà configurée ; aucune clé ne figure dans les nouveaux modèles ni exports.

## Android : portée du travail

Règles pures et clients non-UI compilés sur signatures, écran Compose/navigations intégrés aux fiches et saisons. La connexion réseau native est exécutée sur Dispatchers.IO avec garde de génération au retour ; l’annulation UI ne garantit pas une interruption immédiate du socket HttpURLConnection. Aucun build SDK/Compose, émulateur ou appareil exécuté dans cette livraison. Le bouton À reprendre natif n’est pas annoncé comme identique au Web ; l’accès principal natif est la fiche/saison.

## Sources officielles consultées le 6 octobre 2026

- TMDb Movies / Watch Providers : https://developer.themoviedb.org/reference/movie-watch-providers
- TMDb TV Series / Watch Providers : https://developer.themoviedb.org/reference/tv-series-watch-providers
- TMDb TV Seasons / Watch Providers : https://developer.themoviedb.org/reference/tv-season-watch-providers
- TMDb Available Regions : https://developer.themoviedb.org/reference/watch-providers-available-regions

Ces pages décrivent des offres par pays, l’absence de liens complets de lecture dans ces réponses et le lien TMDb à utiliser. **L’attribution JustWatch est exigée** pour ces données et est affichée dans le panneau, ses états vides et les vieux relevés. Cette attribution ne constitue pas une validation de droits commerciaux pour toute l’application. Aucun compte catalogue réel ni disponibilité commerciale réelle d’un titre n’a été vérifié par les fixtures.
