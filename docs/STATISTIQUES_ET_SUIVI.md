# Statistiques, parcours et entretien — V0.20

## Trois mesures distinctes

| Mesure | Ce qu’elle signifie | Ce qu’elle ne signifie pas |
|---|---|---|
| Bibliothèque actuelle | Titres ajoutés, statut actuel, pages du parcours, favoris/collections | Nombre d’événements durant une période |
| Historique | Dates réellement connues de visionnages et de fins | Temps joué Steam, cases de trophées ou dates devinées |
| Relevés gaming | Compteurs de succès et temps Steam observés par profil | Campagne terminée ni nouveaux visionnages |

Une série n’ajoute pas un événement en plus de ses épisodes : sa date d’activité agrégée est ignorée pour le calcul des lectures. Seuls les épisodes de séries ajoutées et correspondant aux filtres sont retenus. Un épisode ignoré n’est pas une lecture. Une série incomplète ou vide ne devient pas « à jour » simplement parce que rien d’autre n’est chargé.

## Livre relu, jeu recommencé

Exemple : un livre terminé une fois en septembre peut être repris en octobre à la page20/200. Il garde sa lecture terminée de septembre ; son parcours actuel est « En cours ». Le total courant des pages comprend20, pas220 ou200. Si le lecteur atteint200, une deuxième fin datée est enregistrée. Sauvegarder à nouveau cette fin n’ajoute rien.

Les unités page/pages (insensibles à la casse) sont additionnées dans le compteur de pages. Pourcentages, chapitres et unités vides ne sont pas transformés en pages. Aucun temps de lecture de livre ni durée de campagne n’est déduit de ces valeurs.

Une minute personnelle absente est inconnue ; une minute personnelle égale à0 est une déclaration connue. Les temps Steam sont des relevés distincts. Pour un même App ID répété sur plusieurs fiches d’un profil, le maximum connu est retenu une seule fois, et le doublon est signalé. Les profils différents ne sont pas additionnés entre eux.

## Historique et temps vidéo

Seules les dates connues valides apparaissent dans les graphiques et exports datés. Les futurs et compteurs sans date sont comptabilisés séparément. Un ancien compteur de6 lectures avec une seule date connue reste6 lectures comptées et1 événement daté, pas6 journées inventées.

Films et épisodes utilisent leur durée catalogue actuelle lorsqu’elle est positive. Les durées0/absentes sont exclues de la somme et signalées. L’estimation unique et celle avec revisionnages sont distinctes. Le filtre temporel concerne les événements connus, pas le compteur global. Les séries de jours consécutifs utilisent les journées réellement datées et ne comblent pas les jours manquants.

Genres et franchises sont regroupés à partir des titres de la sélection actuelle ; un titre peut avoir plusieurs genres, mais un classement principal. Les libellés sont affichés comme texte. Le nom d’un genre ressemblant à une propriété JavaScript n’est pas utilisé comme un accès au prototype.

## Exports et confidentialité

Le bilan JSON évite titres, noms de profils, identifiants et notes. Ses dates et compteurs restent des informations personnelles potentielles : il ne s’agit pas d’une garantie d’anonymat juridique. L’historique JSON/CSV contient les titres, dates et minutes connues ; pas de commentaires privés. Le CSV échappe les guillemets/retours et neutralise les cellules de titre commençant par une formule courante. Des tests lisent les octets produits, pas un import Excel/LibreOffice réel.

Un export détaillé de plus de10 000 événements est refusé : réduire les filtres. Le rendu limite d’abord à100 événements ; la recherche et l’export utilisent toute la sélection. Aucun de ces rapports ne remplace le JSON de sauvegarde portable V9.

## Réparation des métadonnées sans famine des suivantes

Les identifiants compatibles choisissent le fournisseur : TMDb pour séries/films, RAWG pour jeux, Open Library pour livres. Les champs propres au profil, le suivi et les choix de franchise restent prioritaires au commit, y compris après une attente réseau. Les épisodes avec données de suivi ou note privée sont conservés lorsqu’une nouvelle réponse les omet.

Une fiche en erreur reçoit un délai ; le plan continue avec les autres fiches éligibles. Une attente fournisseur429 suspend ses autres références. Les manuels sans identifiant compatible ne reçoivent aucune recherche par titre automatique. L’absence d’affiche seule ne provoque plus des lectures répétées de fiches complètes.

Le lot manuel est limité à8 fiches et la passe automatique à3. L’application vérifie l’identité/langue, la génération de bibliothèque et les droits d’écriture avant commit. Les appels sont des lectures catalogue ; ils ne programment pas des changements Trakt. Une réponse obsolète ou une annulation ne devient pas une nouvelle fiche.

La file de ce centre n’est pas un service système après fermeture. `_catalogMaintenance` est local et exclu de la sauvegarde portable. La copie IndexedDB portable ne conserve donc pas non plus ces échéances opérationnelles.

## Périmètre natif

Android source reçoit les mêmes règles de parcours et un moteur d’analyse testé en JVM. Les panneaux natifs couvrent catégories, masqués, spéciaux, période, recherche, heures par profil et succès distincts. Les exports de bilan/CSV et le nouveau centre catalogue restent Web/PC. L’écran natif de statistiques a les périodes prédéfinies ; la plage manuelle est dans son historique. Pas de SDK/Compose ni de validation téléphone dans cette livraison.
