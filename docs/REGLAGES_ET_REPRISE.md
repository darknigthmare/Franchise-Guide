# Réglages et reprise — V0.31

## Les trois défauts reproduits

La preuve `tests/results/v031/before-v030.json` charge les scripts originaux du ZIP V0.30 dans un DOM Chromium avec fournisseurs et stockage contrôlés. Elle montre un refus d'écriture principale laissant de nouveaux accès sur disque malgré l'ancienne bibliothèque, une déconnexion Trakt avant ce refus et l'acceptation d'une langue libre/pays numérique invalides. Toutes les valeurs sont des fixtures.

## Validation et brouillon

Les modifications ne sont pas appliquées pendant la saisie. Une liste des différences publiques permet de relire l'action ; les valeurs d'accès ne sont jamais répétées. Les codes pays/langue sont normalisés ; les entiers vides, fractionnaires ou hors bornes ne sont pas remplacés par des valeurs implicites. Un Client ID Trakt sans secret reste accepté pour les fonctions publiques ; l'autorisation du compte garde ses prérequis.

La fermeture/Échap demande confirmation si modifié. Les valeurs par défaut ne sont qu'un brouillon des préférences publiques et gardent les accès initiaux. Après dix minutes ou après changement des réglages/session, le brouillon est refusé. Des notes ou lectures modifiées dans la bibliothèque pendant l'édition restent conservées.

## Préparer puis sélectionner les accès

V0.30 écrivait les accès séparés avant la bibliothèque principale. V0.31 conserve l'ensemble sélectionné et prépare un candidat. La référence qui l'active n'est publiée qu'avec l'écriture principale bibliothèque/file. Si elle échoue, recharger sélectionne l'ensemble précédent. Un enregistrement de favori/note n'écrit plus le magasin privé si les accès sont inchangés.

**Ce n'est pas une transaction atomique multi-clés.** Un candidat non sélectionné peut persister en clair après une tentative refusée. Deux ensembles Web au maximum sont stockés ; la prochaine préparation écarte les candidats inutiles. Ce mécanisme ne protège pas les accès contre un script hostile autorisé sur la même origine et ne chiffre pas la base. L'effacement local explicite vise le magasin complet, avec ses limites d'erreur.

Le store a un format et des tailles contrôlés. L'absence du slot sélectionné impose la récupération ; aucun autre slot n'est choisi automatiquement. Le format ancien plat reste lisible, mais ses champs étrangers comme language ne remplacent plus les préférences publiques.

## Autorisations et autres fenêtres

Un token Trakt possède la référence d'application qui l'a autorisé. Changer Client ID/secret demande une nouvelle autorisation seulement après succès de sauvegarde. Si le nettoyage de l'ancien token échoue, il peut rester physiquement présent mais sa liaison empêche sa réutilisation. Changer TMDb/RAWG ne change pas cette liaison.

Un événement d'écriture/effacement sur la bibliothèque ou l'effacement global du stockage suspend la vue périmée et invalide ses lectures annulables. La copie reste exportable. Préparer un slot inactif n'interrompt pas une vue dont les accès sélectionnés restent identiques. Les révisions et Web Locks réduisent les conflits ; ils ne constituent pas une transaction distribuée garantie. Sans Web Locks, garder un seul onglet. Un POST déjà reçu peut avoir été appliqué malgré son annulation locale.

## Formats et Android

Portable V11 et `.fgbackup` V1 inchangés. Ni slots, ni références, ni jetons ne sont exportés. La restauration conserve les accès du même appareil. Le brut de récupération peut être privé et ne doit pas être diffusé. La protection `.fgbackup` est celle d'un export, pas un chiffrement du stockage actif.

Android valide avant mutation, prépare un emplacement puis publie le sélecteur avec les préférences publiques. Un changement d'application prend le verrou partagé avec la synchro. Un commit refusé tente une remise des anciens sélecteurs dans le cache ; un nouvel échec impose le mode récupération. Des emplacements privés orphelins peuvent subsister après panne. Les règles JVM et la compilation de signatures ne prouvent pas le comportement d'un disque, SharedPreferences, Compose ou appareil réel. Le résumé et le bouton de préparation des valeurs initiales restent spécifiques à Web/PC dans cette passe.

## Preuves

Les nouveaux tests de règle, les tests DOM à stockage contrôlé et les régressions effectivement rejouées sont détaillés dans QA_REPORT.md. Un nouveau chargement des scripts avec une copie de stockage de test n'est pas une simulation complète d'un crash navigateur ni une vraie PWA persistante.
