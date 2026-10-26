# Mes listes personnelles — V0.21

## Une liste n’est ni une franchise ni une watchlist distante

Une œuvre ajoutée peut appartenir à plusieurs listes locales sans créer de doublon de fiche ou d’historique. La suppression de liste ne retire pas l’œuvre de la bibliothèque. Les suggestions grisées restent des références de franchises ; elles ne sont pas comptées comme des membres ajoutés. Les anciens titres retirés sont conservés dans l’état local, mais exclus du nombre de membres affiché.

Le gestionnaire se trouve dans **Plus → Gérer toutes mes listes**. Il garde les listes vides visibles, cherche dans leurs noms/descriptions et affiche les 60 premiers résultats avec chargement supplémentaire. Le résumé de Plus affiche au maximum 30 listes et donne accès au gestionnaire complet.

## Créer et modifier

Nom nouveau ou modifié : 1 à 120 caractères. Description : 3 000 caractères, retours à la ligne ordinaires acceptés. Noms identiques après normalisation Unicode, casse et espaces refusés pour les créations/renommages. Des noms non latins différents restent distincts. Les anciennes définitions stockées plus longues demeurent lisibles ; pas de troncature automatique lors de la migration. Les limites éditoriales doivent être respectées si elles sont ensuite modifiées.

Modifier une définition la compare à celle chargée au début du formulaire. Si elle a changé, fermer puis rouvrir : aucune écriture partielle ne doit écraser la nouvelle définition.

## Ajouter et retirer des membres

« Choisir les titres » ouvre les œuvres déjà ajoutées des quatre catégories, masquées comprises. Recherche dans l’ensemble, 60 lignes initiales. Les cases restent cochées lorsqu’un filtre change. « Sélectionner tous les résultats » ne se limite pas aux 60 lignes visibles ; jusqu’à 10 000 membres par opération.

Premier clic : aperçu exact des ajouts, retraits et membres inchangés. Aucune écriture dans cette préparation. Second clic : confirmation, validation de l’aperçu contre l’état courant, copie de sécurité puis application atomique. Si une définition, une appartenance cible ou un titre sélectionné a changé, tout est refusé. Une note plus récente, un visionnage ou une appartenance à une autre liste restent conservés lorsque le changement ne porte pas sur eux.

Ces opérations sont bloquées pendant une synchronisation Trakt active. Elles utilisent `save(false)` côté Web : pas de nouvel envoi de suivi. La file existante est conservée. Les mêmes gardes de repository sont intégrées en source Android ; tests de concurrence sur appareil non réalisés.

## Trier, filtrer et revenir

Tri personnel ou automatique par titre, année, ajout, activité, progression, prochain épisode, statut, note et restants. Changer de tri automatique n’efface pas l’ordre personnel. Déplacer à une position utilise tous les membres ajoutés (y compris masqués et autres catégories non visibles dans le filtre). L’ordre est lié aux identifiants locaux, pas à la ressemblance des noms. Les identifiants d’ordre absents ne créent pas de nouveaux membres.

Recherche sur tous les membres ; filtres Web/PC de catégorie, visibilité et état. 60 œuvres affichées au départ puis chargement supplémentaire. Les jeux/livres déjà terminés historiquement mais repris ont l’état de leur parcours courant. Une série « à jour chargée » exige une fiche marquée complète, des épisodes standards à numérotation connue et non dupliquée, tous vus/ignorés, et aucun déficit face au nombre annoncé. Ce n’est pas une déclaration de fin définitive de la série.

Ouvrir un titre depuis la liste permet de revenir à cette liste avec ses filtres et son volume affiché pendant la session. La navigation acteur → série garde aussi sa chaîne de retour : une régression introduite pendant cette passe a été détectée par les anciens tests et corrigée.

## Dupliquer, supprimer, exporter

Dupliquer référence les mêmes membres ajoutés, masqués compris, dans une nouvelle liste et conserve leur ordre. Ni œuvres ni observations ne sont clonées. Une liste trop grande pour cette action est refusée avant mutation.

Supprimer exige confirmation et copie de sécurité réussie. La définition et toutes ses appartenances sont retirées, y compris les liens sur références retirées ou suggérées. Les titres, épisodes, notes, événements et relevés gaming sont conservés. Aucune suppression distante n’est envoyée.

Exporter la sélection donne un aperçu : tous les résultats filtrés, jusqu’à 10 000, ou refus explicite au-delà. JSON ou CSV UTF-8 avec BOM, échappement et neutralisation des amorces de formules dans les cellules textuelles. Le rapport contient nom de liste, filtres, titres, catégories, années et état masqué ; pas description, notes privées, historique, jetons ou profils. **Il reste personnel** et n’est pas une sauvegarde importable. Aucun tableur externe n’a été ouvert pendant ces tests.

## Persistance et Android

Le backup passe à V10 : `manualOrder` et `sortOrder` sont exportés et restaurés, les anciens formats 1–9 sont lus. Les états de recherche/sélection/aperçu sont éphémères. Ne pas réenregistrer avec V0.20 après migration.

Android source : gestionnaire, recherche, édition, sélection avec aperçu, ordre, duplication/suppression et transaction de repository intégrés. LazyColumn pour l’affichage. Les filtres détaillés d’état et le rapport CSV/JSON de liste ne sont pas ajoutés à l’écran natif. La navigation de retour vers une liste n’a pas été validée sur appareil. Aucun build Android SDK/Compose.

## Si une erreur apparaît

Une copie de sécurité impossible bloque l’action destructive. Une écriture principale refusée ne produit pas de faux succès : l’état sauvegardé est remis en mémoire et le mécanisme de brouillon exportable propose la copie de travail. Ne pas relancer une action tant que la cause de stockage n’est pas traitée. Les snapshots restent locaux et ne protègent pas contre l’effacement du profil complet.
