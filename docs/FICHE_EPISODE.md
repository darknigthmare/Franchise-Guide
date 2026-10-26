# Fiche épisode et champs personnels — V0.24

## Parcours livré

Une série ajoutée possède des épisodes identifiables localement. Le bouton **Fiche épisode** figure dans les lignes de saison et dans le gestionnaire d’épisodes, séparément de la case de sélection. La fiche ouvre les données locales, le suivi et les champs personnels sans requête implicite. Une disparition, un déplacement ou une identité ambiguë de l’épisode est signalé ; le formulaire ne s’applique pas à une autre référence.

Le relevé réseau est lu explicitement auprès de TMDb par la série, la saison et le numéro d’épisode. Un identifiant d’épisode TMDb déjà connu doit également correspondre à la réponse. Une numérotation inconnue n’est pas devinée. La langue est celle de la fiche série, sinon la langue des réglages. La réponse combine le détail et les crédits de l’épisode.

## Trois groupes de personnes

**Distribution**, **Invités** et **Équipe** gardent leurs rôles propres. Plusieurs rôles identiques sont dédupliqués pour une personne à l’intérieur du même groupe ; une même personne peut rester présente dans plusieurs groupes. La distribution générale de la série n’est pas utilisée comme substitut à une réponse épisode incomplète. Des identités contradictoires, tableaux manquants ou champs non valides provoquent un refus avant remplacement du relevé.

Le Web/PC affiche d’abord60 références de personnes. La recherche et le filtre s’appliquent à toute la sélection ; le bouton suivant en ajoute60. Une personne ouvre le panneau de personne déjà présent, avec un retour vers l’épisode. Le Kotlin source fournit également les groupes et une liste paresseuse, mais ne reproduit pas exactement la normalisation sans accents de la recherche Web.

## Respect des spoilers

Pour un épisode non vu, si le réglage le demande, titre, image, synopsis, noms et rôles sont masqués. La recherche ne révèle pas un invité caché. **Révéler les détails pour cet épisode** n’est pas un changement permanent du réglage ; la révélation ne passe pas automatiquement à l’épisode suivant. Masquer de nouveau vide la recherche de personnes. Les champs personnels restent les informations de l’utilisateur et ne sont pas transformés en contenu public.

## Ma note et mon texte privé

Le score personnel accepte un entier1–10 ou un champ vide. Le score TMDb est séparé. Un score catalogue0 reste0 ; il n’est pas remplacé par une appréciation utilisateur. Le texte privé est limité à2 000 caractères et accepte les retours de ligne ordinaires ; le dépassement est refusé sans troncature silencieuse.

L’ouverture mémorise l’identité et les valeurs des deux champs. Enregistrer vérifie de nouveau : même bibliothèque, même épisode, mêmes valeurs de départ, formulaire non expiré (10 minutes), compte Trakt inchangé et pas de synchronisation active. Une modification concurrente de ces champs est refusée. Les autres modifications, par exemple collection ou visionnage, sont conservées.

Note ou texte vide efface uniquement ce champ personnel. Aucun compteur, événement daté, statut ignoré, collection, titre ou synopsis catalogue n’est changé par cet éditeur. Le texte reste local et exportable dans la sauvegarde ; il n’est jamais envoyé comme avis Trakt par ce parcours. Le score d’un épisode identifié peut devenir une opération de note dans la file Trakt existante, sans envoi direct du formulaire au fournisseur.

Le Web demande confirmation avant de quitter un brouillon : précédent/suivant, retour, navigation principale et liens internes concernés. Un événement beforeunload signale aussi le brouillon, mais la décision d’afficher une boîte au rechargement dépend du navigateur et n’est pas certifiée à l’origine réelle ici. Le bouton **Recharger les champs enregistrés** demande également l’abandon explicite si le brouillon diffère.

## Suivi et navigation

Les boutons de suivi réutilisent les mécanismes existants : un effacement de visionnages n’est pas confondu avec l’effacement du texte privé. Le dialogue d’ajout daté reste distinct. Le Web présente les dates réellement connues de l’épisode par groupes de30 ; les lectures sans date restent comptées sans être placées artificiellement dans le calendrier.

Précédent/suivant suit saison puis numéro des données locales. Les spéciaux sont exclus si le réglage les masque, sauf l’épisode courant déjà ouvert ; des doublons de numérotation désactivent le déplacement ambigu. Aucune fin de série n’est déduite de l’absence de voisin. Le bouton de disponibilité ouvre la **saison** du contexte et garde le retour. Cela ne prouve pas une offre pour ce seul épisode.

## Politique réseau

- GET HTTPS TMDb uniquement ; une lecture de fiche ne modifie ni données catalogue durables ni historique.
- Réponse limitée à2 Mio, lecture des octets bornée et décodage UTF‑8 strict ; crédits max5 000 entrées au total et2 500 par groupe.
- Délai de lecture Web20 s, pas de retry automatique de ce panneau. Annuler invalide la réponse et conserve le dernier relevé de la même sélection.
- Changement de route, identité, numérotation, langue, clé ou bibliothèque invalide un résultat tardif.
- Cache propre au panneau Web :20 relevés en mémoire,5 minutes de réutilisation au même contexte ; horodatage conservé. Forcer une actualisation ne contourne pas le délai 429.
- HTTP429 crée une attente locale bornée entre60s et24h. Une lecture échouée ne remplace pas le dernier relevé valide.
- Images acceptées uniquement via les chemins TMDb attendus ; noms/synopsis/rôles rendus comme texte. Le lien de source est construit sur le titre/épisode validés, pas repris depuis un domaine arbitraire du payload.

Les limites sont des décisions de FranchiseGuide, pas des quotas promis par TMDb. Le cache générique a été corrigé pour inclure la politique de réponse : une lecture au plafond plus bas ne peut plus être satisfaite par une réponse conservée avec un plafond plus élevé ; no-store / reload / no-cache ne sont pas mémorisés.

## Durabilité et limites natives

Le relevé consulté reste temporaire : une actualisation de la série entière demeure nécessaire pour remplacer les métadonnées enregistrées. Seuls les champs personnels enregistrés passent au JSON portable V10 existant. Les brouillons, crédits temporaires et filtres ne sont pas exportés.

Android dispose des règles pures, du transportGET borné et du panneau source avec notes, groupes, révélation, navigation et retour. Le panneau natif affiche le nombre de dates connues mais ne reproduit pas la liste paginée complète des dates du Web. Son annulation de coroutine empêche l’application d’une réponse devenue obsolète ; elle ne garantit pas l’arrêt instantané d’un socket HttpURLConnection bloqué. Aucun build SDK/Compose, installation ou test téléphone réalisé.

## Sources primaires

Consultées le6octobre 2026 :
- https://developer.themoviedb.org/reference/tv-episode-details
- https://developer.themoviedb.org/reference/tv-episode-credits
- https://www.seriesgui.de/help

La consultation de la documentation ne constitue ni un accès réel avec compte, ni une validation de droits commerciaux ou d’exhaustivité des crédits. Les essais de cette livraison utilisent des données fictives identifiées comme fixtures.
