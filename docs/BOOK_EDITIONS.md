# Livres sans clé : éditions et total de pages — V0.27

## Œuvre, édition et suivi personnel ne sont pas la même donnée

La recherche Open Library renvoie principalement des œuvres. Une même œuvre peut regrouper plusieurs langues, formats, éditeurs et paginations. Son champ de recherche `number_of_pages_median` n’est pas une preuve du nombre de pages de l’exemplaire lu par l’utilisateur.

La V0.26 utilisait cette médiane comme total courant dès l’ajout. La V0.27 laisse les nouveaux livres à **0 = total inconnu**. Elle préserve les anciens totaux : aucun nettoyage silencieux des bibliothèques existantes. L’identifiant court `OL123W` est normalisé en `/works/OL123W` avant tout appel ; les lectures retournant une autre œuvre sont rejetées.

## Consulter sans modifier

Depuis une fiche **livre ajouté via Open Library**, cliquer sur **Éditions et nombre de pages · sans clé**. L’ouverture ne déclenche pas de lecture distante. **Lire les éditions sans clé** charge 30 entrées maximum ; chaque page suivante demande un nouveau clic. La consultation s’arrête à 300 entrées, même si le fournisseur en annonce davantage.

Le panneau Web/PC expose le titre, les éditeurs, la langue déclarée, la date de publication telle que fournie, le format, les pages et la couverture lorsqu’ils sont présents. Les couvertures utilisent des identifiants numériques positifs, pas une URL arbitraire contenue dans le JSON. Les ISBN10/13 sont nettoyés et contrôlés : ceux dont le format ou la clé est invalide sont écartés et comptés. **Un checksum valide n’authentifie pas un ouvrage ou sa possession.**

Une date de publication sous forme de texte n’est ni inventée ni convertie en événement d’agenda. Une page absente reste inconnue ; une page explicitement à zéro reste zéro et ne peut pas devenir le total. Les liens Source pointent vers l’édition sur Open Library. Aucun prix, prêt, téléchargement, disponibilité commerciale ou droit de lecture n’est affirmé.

La recherche titre/éditeur/date/ISBN et les filtres langue/pages connues couvrent **les éditions déjà chargées seulement**. Ce n’est pas une recherche distante exhaustive par ISBN et il n’y a pas de scan de code-barres dans cette livraison.

## Pagination sûre

Les IDs d’édition sont distincts des IDs d’œuvre. Le rattachement reçu dans `works` doit désigner l’œuvre consultée. Une entrée sans ce lien explicite reste visible mais son total n’est pas applicable. Une entrée désignant seulement une autre œuvre fait échouer la page.

Le nombre annoncé, la position, les doublons et les liens de pagination sont contrôlés. Une page suivante ne peut pas être répétée ni changer silencieusement le total annoncé. Les URL `links.next` sont validées mais **ne sont jamais suivies** : le client reconstruit le chemin autorisé. En cas de page échouée, les précédentes et leur date de lecture restent disponibles ; Réessayer reprend le même offset.

L’actualisation revient à la première page et ne remplace le résultat qu’après validation. Annuler, fermer, changer de fiche ou remplacer la bibliothèque invalide un résultat tardif. Les données d’édition restent en mémoire de panneau, pas dans le portable. La présentation ne garantit pas une instantanéité du catalogue extérieur.

## Appliquer uniquement le total

**Utiliser ce total de pages** prépare une confirmation ciblée : identité locale, œuvre, unité, avancement, ancien total et nouveau total. Elle expire après dix minutes. Au moment d’appliquer, ces valeurs sont relues. Un changement de progression ou d’identité refuse l’action plutôt que d’écraser une valeur récente.

Une copie de sécurité locale réussie est obligatoire. Seul `progressTotal` est modifié. L’avancement, les lectures terminées, leurs dates, le score, le texte privé, le statut et le format personnel restent inchangés. L’application ne marque pas un livre comme terminé si le nouveau total est égal à l’avancement. Elle ne réduit pas l’avancement pour le faire entrer dans un total plus petit.

Le suivi doit déjà utiliser l’unité **pages**. Les chapitres, pourcentages, durées et autres unités ne sont pas convertis automatiquement. Il reste possible de saisir un total manuellement depuis le suivi de la fiche.

Une note ou un visionnage modifié depuis la préparation est conservé lorsqu’il ne touche pas les champs ciblés. Le refus du stockage revient à l’état enregistré et conserve le mécanisme de brouillon récupérable. Cette action n’envoie rien à Trakt et n’écrit rien sur Open Library.

**La sélection ne persiste pas une identité d’édition ni l’ISBN** : cette version utilise ses pages uniquement. Un total confirmé reste conservé lors d’une actualisation de l’œuvre ou d’un export/import V10.

## Réseau

Le même client Web pace les recherches de livres, fiches d’œuvre et éditions : une seule lecture prise en charge à la fois et au moins **1,05 seconde entre leurs démarrages**, avec une file de huit demandes maximum. Pas de clés ou cookies du fournisseur dans ces lectures GET. Les corps sont plafonnés à2Mio, décodés en UTF‑8 strict, sans redirection arbitraire ni répétition automatique d’une erreur.

Un HTTP429 impose une attente de60s minimum, jusqu’à24h, selon `Retry-After`. Réessayer manuellement respecte ce délai. Il s’agit de limites prudentes de l’application, pas d’un engagement du fournisseur. Les consultations sont ponctuelles et ne constituent pas une récupération massive d’un catalogue.

Le client Android source impose les mêmes bornes principales et le pacing. Son annulation empêche l’application d’une réponse devenue inutile ; elle n’est pas présentée comme une garantie d’arrêt instantané d’un socket HttpURLConnection déjà bloqué.

## Android et vérification

Le client, les règles et un panneau Compose sont intégrés aux sources. Le panneau natif propose la recherche dans les pages reçues, le filtre pages connues, pagination, source et confirmation. Il n’affiche pas les couvertures et n’a pas le sélecteur de langue Web.

La preuve disponible comprend règles JS, règles Kotlin JVM, comparaison de sortie normalisée et tests DOM avec un fournisseur simulé. Aucun appel Open Library réel réussi depuis l’environnement, aucune qualification Android SDK/Compose, PWA installée ou import de bibliothèque personnelle ne sont annoncés. Les exclusions détaillées figurent dans QA_REPORT.md.

## Sources officielles consultées

- Open Library Search API : https://openlibrary.org/dev/docs/api/search — distingue œuvres/éditions et documente les identifiants courts ou sous forme de chemin.
- Open Library APIs : https://openlibrary.org/developers/api — découverte humaine à faible volume, cache, identification de l’application et limites de lecture.

Ces documents ne constituent pas une validation commerciale du projet. Les lectures de test utilisent des données fictives explicitement identifiées.
