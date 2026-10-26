# Intégrité des données et récupération — V0.14

L’écran **Plus → Intégrité des données** examine la bibliothèque locale. Un titre ressemblant constitue un candidat à examiner ; il ne prouve pas que deux fiches désignent la même œuvre.

## Contrôles Web / PC

L’audit détecte notamment :

- Les identifiants de médias, épisodes ou listes absents ou répétés.
- Les épisodes sans série parente, y compris ceux reliés à un autre type de média.
- Les références de listes inexistantes ou répétées.
- Les relevés de trophées dont le jeu ou le compte local manque.
- Les candidats doublons de même type et titre normalisé, avec des années compatibles lorsqu’elles sont connues.
- Les contradictions entre identifiants TMDb, TVDb, IMDb, Trakt, RAWG, Open Library, Steam et RetroAchievements.

Le contrôle des fournisseurs compare aussi source/sourceId, les champs d’identité propres aux fournisseurs et steam.appId. Une identité contradictoire à l’intérieur d’une même fiche bloque également sa fusion.

## Réparation limitée

Une confirmation et une copie de sécurité précèdent la réparation Web/PC. Celle-ci retire les références de listes inexistantes ou répétées et les épisodes orphelins sans suivi. Un épisode vu, sauté, collectionné, noté, commenté, possédant un nombre de lectures ou des dates de suivi est conservé et signalé.

La réparation ne résout pas automatiquement les identifiants dupliqués, ne réattribue pas un épisode suivi à une série supposée et ne supprime pas les relevés de trophées orphelins. Une bibliothèque illisible ou d’un schéma plus récent reste protégée par le mode de récupération et son export brut.

## Fusion manuelle contrôlée

Sur Web/PC, choisir la fiche principale exige une confirmation et une copie de sécurité. Les identifiants locaux dupliqués, les contradictions entre fournisseurs, les unités de progression différentes et les collisions de relevés de trophées bloquent l’opération.

Pour une fusion acceptée :

- Les appartenances aux listes et les états positifs de suivi sont réunis.
- Les deux commentaires personnels sont conservés. Une note chiffrée secondaire remplit une note principale vide ; si les deux évaluations diffèrent, l’évaluation principale reste sélectionnée et la valeur secondaire est ajoutée au commentaire.
- Les dates de visionnage connues sont réunies. Le nombre de lectures tient compte de ces dates distinctes, sans additionner arbitrairement les lectures non datées.
- Un épisode vu ne devient pas sauté parce que son doublon l’était. Les correspondances utilisent les identifiants et les numéros connus ; des épisodes inconnus ou ambigus restent distincts.
- Les relevés Steam de plusieurs comptes restent présents. Pour un même compte, chaque compteur de temps de jeu et date de dernière partie conserve son maximum connu.
- Les identifiants secondaires RAWG, Open Library, Steam et RetroAchievements sont conservés dans les champs rawgId, openLibraryId, steamAppId et retroGameId, même sans liste de succès associée.
- Les listes de trophées sont reliées au média conservé en gardant compte, plateforme et édition. Si cette reliaison produirait deux listes de même identité, la fusion est refusée.

Une fusion suspend les anciennes opérations Trakt et invalide les lectures de catalogue ou de jeux devenues obsolètes. Elle ne publie pas automatiquement le résultat sur un service distant.

## Copies de récupération IndexedDB

Le stockage principal Web/PC reste **localStorage**. Lorsqu’une écriture principale échoue, l’application conserve le dernier état enregistré, propose une copie de travail exportable et tente de déposer une copie portable dans IndexedDB.

**Données & hors ligne** permet de créer, exporter, restaurer ou supprimer ces copies. L’écriture et la rotation des cinq copies sont réalisées dans une même transaction. Une annulation ne devient pas un succès ; les ouvertures et transactions en erreur sont refermées. L’ordre des sauvegardes est attribué dans la transaction pour départager les écritures rapprochées.

IndexedDB reste un stockage du navigateur sur la même origine. Ses copies ne sont ni un export indépendant ni une garantie contre la perte du profil, l’effacement des données du site ou les quotas. La restauration nécessite encore de réussir la copie de sécurité prévue et l’enregistrement dans le stockage principal ; un quota saturé peut donc la bloquer. Exporter le JSON de récupération permet de conserver une copie extérieure au navigateur.

L’effacement global tente aussi de supprimer les copies IndexedDB. Si cette dernière étape échoue après la suppression du stockage principal et des caches, l’interface annonce un **effacement incomplet**, précise que des données ont déjà été effacées et propose de recharger pour reprendre. Elle ne prétend pas avoir annulé les suppressions déjà réalisées.

## Android et compatibilité

Les sources Android contiennent un écran d’audit et une réparation limitée ; la fusion manuelle complète n’est pas exposée. Les contrôles Android ne sont pas présentés comme une parité intégrale avec la fusion Web/PC. Les nouveaux alias d’identité sont transportés dans les sauvegardes.

La clé principale Web est **franchiseguide-web-v8**. Les clés historiques compatibles restent lues à la migration. Le format portable reste au **schéma 8** ; les champs ajoutés sont facultatifs. Une ancienne version peut les perdre au réenregistrement : exporter avant la mise à jour et éviter de rouvrir le même profil avec un ancien lanceur.

## Portée des essais

La V0.14 dispose de tests de règles JavaScript et de parcours DOM avec stockage simulé. IndexedDB est couvert par des événements contrôlés et par fake-indexeddb ; ce n’est pas une validation de sa durabilité sur appareil. Les scénarios Chromium conservés des versions antérieures ne constituent pas une exécution Chromium de cette livraison. La restauration sur une véritable origine de navigateur et l’application Android avec SDK et appareil restent à valider. Voir QA_REPORT.md.
