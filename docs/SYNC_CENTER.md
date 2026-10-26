# Centre de synchronisation — V0.15

## Périmètre

Nouveau panneau Web/PC pour **les listes de succès déjà associées** à Steam ou RetroAchievements. Il ne remplace pas la recherche catalogue ni l’association initiale d’un profil. Il n’ajoute pas de connecteur PSNProfiles/Xbox/Epic/GOG. Les médias et listes non associés restent locaux. Ce panneau n’existe pas encore dans l’interface Android source.

## États et commandes

- **À lire** : lecture disponible, jamais effectuée, ancienne d’au moins6h, ou en erreur dont le délai est terminé.
- **À jour** : dernier relevé réussi il y a moins de6h sans erreur signalée.
- **Délai de reprise** : date minimale fournie par le serveur ou calculée après un échec.
- **En pause** : pause globale ou explicite de cette liste.
- **Connexion requise** : une liste Steam liée à une session nécessite le bon compte actif et non expiré.
- **Profil manquant / à associer** : identifiant absent, incompatible ou pseudo RetroAchievements non encore relié à son identifiant stable.
- **Local / import seulement** : aucune lecture de ce fournisseur n’est implémentée.

Le filtre recherche toute la liste connue ; seules50 lignes sont montées initialement, avec chargement des50 suivantes. Les profils et éditions restent distincts. Chaque ligne possède l’accès aux trophées, une pause/réactivation et une relecture lorsque possible. Les champs sont des contrôles clavier et tactiles usuels.

## Lots manuels et automatismes

Lancer **toutes les listes à actualiser** crée un lot d’identifiants locaux. Les succès, échecs et références retirées sont comptés séparément. Une seule lecture de succès est en vol, huit au maximum par passage. Entre passages :30secondes. Masquer la fenêtre ou perdre le réseau suspend la progression ; le lot autorisé peut continuer au retour. Après un rechargement, la progression est retrouvée mais la poursuite exige de cliquer Reprendre.

Un clic explicite sur un lot autorise ses lectures pendant cette session ; il ne transforme pas une case autoSync d’un profil en consentement permanent. Les automatismes existants continuent seulement pour les profils autorisés, hors pause/délai, et ne démarrent pas automatiquement un ancien lot manuel interrompu.

Annuler une lecture ou mettre le lot en pause ne compte pas comme un échec API. La réponse qui arrive après annulation est ignorée même si le fournisseur de test n’obéit pas à AbortSignal. Une référence retirée est sautée, pas recréée. Fermer un lot laisse les relevés et les pauses/délais.

## Délais durables

`Retry-After` est lu en secondes ou sous forme de date HTTP. Le délai est borné entre1minute et24heures. En l’absence d’indication exploitable :5,10,20,40,puis60minutes selon les échecs consécutifs. Un HTTP429 place **tout le fournisseur** en attente, y compris un autre profil ou une autre liste. Une limite RetroAchievements n’arrête pas Steam, et inversement.

Le succès remet à zéro le compteur d’échecs de cette liste, pas une pause explicite ni une limite globale encore active. Une annulation n’efface pas le quota connu. Le serveur conserve aussi ses propres protections : l’interface ne les contourne pas.

Pour les imports RetroAchievements paginés, les pages validées restent préparées en mémoire. Une limite peut enregistrer le délai local sans enregistrer une moitié de catalogue. La reprise redemande la page échouée. Annuler abandonne cet import en mémoire et conserve le quota connu. Le test V0.15 utilise501 jeux synthétiques pour vérifier ce parcours ; aucun profil réel n’a été lu.

## Persistance et absence d’effets distants

`_gamingControl` est une donnée interne versionnée de l’enveloppe localStorage. Elle est enregistrée avec les changements de bibliothèque et le journal Trakt dans une même écriture principale. L’état comprend les pauses, échéances, dernière tentative, échecs et lot interrompu. Les identités de contrôle incluent fournisseur, compte, jeu, plateforme et édition : une référence réutilisée n’hérite pas aveuglément du réglage d’une autre identité.

Ce champ n’est pas dans le JSON portable. Un import supprime tous les champs internes commençant par `_`, plutôt que d’accepter une révision ou une file forgée. Un stockage principal invalide/futur reste en mode récupération. Une écriture refusée restaure le dernier état enregistré et garde une copie de travail exportable.

Les requêtes de succès sont des lectures. Ce centre ne publie pas d’avis, ne débloque ni n’efface de trophée et ne marque pas une campagne terminée. Trakt reste une autre file de synchronisation avec ses propres confirmations.

## Limites

- Aucun service continu quand l’application est fermée, ni garantie de réveil en arrière-plan PWA.
- Les pauses sont locales, pas synchronisées entre appareils ni transportées sur Android.
- Le stockage principal reste localStorage synchrone ; le centre n’est pas une migration IndexedDB.
- Pas de vérification réelle de compte Steam/RetroAchievements dans cette campagne. Les requêtes/erreurs/date/quotas des tests sont contrôlés.
- Le comportement de calendriers, fournisseurs et notifications non touchés reste celui de la version précédente ; aucune parité exhaustive SeriesGuide n’est affirmée.
