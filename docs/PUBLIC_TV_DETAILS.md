# Séries sans clés — épisodes et personnes TVmaze, V0.26

## Ce qui est réellement ajouté

La V0.25 proposait déjà la recherche et l’ajout des séries sans clé. La V0.26 raccorde les profils internes des personnes et les détails d’épisodes à la même source publique. Le bouton ne demande pas de créer une application développeur. Les lectures nécessitent Internet et une disponibilité du fournisseur ; la livraison ne contient pas de données catalogue préchargées.

### Profil d’une personne

Entrées : casting d’une série TVmaze, invités et équipe invitée du nouvel écran épisode. L’identifiant reste `tvmaze-person-N` pour distinguer les espaces de noms ; le numéro N n’est jamais ouvert comme une personne TMDb. Lien de secours et attribution vers TVmaze.

Trois lectures fixes : `/people/N`, `/people/N/castcredits?embed=show`, `/people/N/crewcredits?embed=show`. Les crédits sont ceux des séries, pas une filmographie cinéma ni toutes les apparitions invitées. Nom, image et dates/pays disponibles sont affichés ; pas de biographie inventée. Une information absente reste absente. Les rôles sont regroupés par identifiant de série. Le même titre avec deux IDs reste deux séries.

Web/PC : recherche dans tous les titres et rôles reçus (accents ignorés), tri titre ou année,40 références affichées d’abord. L’ajout d’une œuvre reste explicite et passe par son chargement complet. Une œuvre déjà ajoutée est retrouvée par son fournisseur et sourceId, pas par une simple égalité de titre. Le retour d’une série ajoutée rejoint le profil.

### Épisode

Entrées : Fiche épisode depuis une saison ou le gestionnaire. L’ouverture conserve le suivi local sans requête. Le clic **Lire les détails sans clé** appelle `/episodes/N?embed=show`, `/episodes/N/guestcast`, `/episodes/N/guestcrew`.

Le fournisseur doit retourner le même épisode et la série attendue. Un standard doit garder sa saison et son numéro connus ; un spécial à numéro absent reste consultable via son ID public exact. Invités et équipe invitée sont deux groupes séparés ; aucun casting principal de série n’est injecté à la place. Les rôles d’une personne sont réunis au sein d’un groupe et les noms contradictoires pour un même ID sont refusés. L’anti-spoiler protège aussi la recherche et ne se transmet pas au voisin après révélation.

Le titre, synopsis, date, durée et note fournisseur sont temporaires. `0` reste zéro ; une valeur absente reste inconnue. Les votes ou un code de production absents ne sont pas inventés. La note est explicitement TVmaze. Les champs personnels locaux et le dialogue de visionnage daté restent leurs propres actions.

### Réponses, limites et annulation

- Web/PC : trois réponses requises, concurrence au plus2 pour le lot,2 Mio maximum par réponse, décodage UTF-8 strict et dates validées. Un corps partiel/illégal ne constitue pas un nouveau relevé.
- Cache :20 épisodes et24 profils en mémoire, réutilisables5min sans nouvel horodatage. Actualiser relit sous réserve du délai fournisseur. Les erreurs conservent le dernier relevé réussi ; le profil propose explicitement de le revoir.
- Annulation/navigation/changement de bibliothèque : les réponses tardives ne s’appliquent pas. L’annulation ne vide pas le brouillon de l’épisode. Un délai429 (Retry-After ou minimum60s, maximum24h) est partagé par les lectures publiques TVmaze Web ; pas d’actualisation manuelle contournant cette attente.
- Le serveur personnel Node/Java accepte uniquement les nouveaux GET nécessaires. Il ne devient pas un proxy libre, n’accepte pas d’URL arbitraire et refuse les expansions non prévues. Ces lectures n’injectent aucun secret.

Ces limites sont des choix locaux de l’application, pas une promesse sur les quotas ou la fraîcheur du service.

## Correction d’identité à l’actualisation

Cas reproduit sur V0.25 : la série locale possède `tvmaze-episode-100`, S01E01, vu une fois, avec une note privée. Une réponse suivante fournit `tvmaze-episode-200`, lui aussi S01E01. L’ancien commit recherchait par numéro et pouvait donner au nouvel épisode l’identité et l’historique du premier.

V0.26 : le fournisseur TVmaze n’utilise plus le rapprochement de secours par saison/numéro. Le nouvel ID est ajouté sans historique hérité. Un ancien épisode suivi absent de la nouvelle réponse reste conservé et marqué orphelin côté Web. Une renumérotation du **même ID** peut conserver ses propres observations. Les règles native et Web sont raccordées séparément ; la validation native porte sur règles JVM et signatures non-UI, pas sur une vraie base Android.

## Android : périmètre honnête

Le client public et les panneaux source suivent le fournisseur TVmaze : détails d’épisodes, personnes, attribution, recherche par titre/rôle et retour. Les bornes réseau et règles d’identité sont présentes. Le cache natif est local au panneau ; il n’est pas identique au LRU mémoire Web. Une annulation UI rejette un résultat obsolète mais ne garantit pas l’interruption immédiate de la socket HttpURLConnection.

Pas de SDK/Compose, APK ou téléphone validés. Le partage des trois premières routes dans les passerelles Web/PC ne prouve pas l’exécution Android. Pas de rapprochement TVmaze → TMDb → Trakt automatique ajouté, pas de traduction française ou biographie garantie, pas de nouvel accès privé éditeur.

## Source officielle et attribution

Documentation : https://www.tvmaze.com/api — rubriques Episodes, People, Images, CORS, Rate limiting et Licensing. Données TVmaze / CC BY-SA, liens d’attribution dans les fiches. Les identités provider ne sont pas interchangeables. Les droits et conditions de chaque autre fournisseur restent applicables à leurs propres données. Cette documentation n’est pas une approbation commerciale.
