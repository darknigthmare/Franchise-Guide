# Check-ins, Aujourd’hui et sauvegardes — V0.35

## Référence SeriesGuide et choix FranchiseGuide

SeriesGuide documente qu’un check-in est envoyé immédiatement à Trakt puis devient vu après la durée de l’épisode ou du film. Il précise aussi que le suivi local n’est mis à jour qu’à la synchronisation suivante. FranchiseGuide conserve cette séparation entre check-in et historique local, mais ne prétend pas exécuter une tâche Web lorsque l’application est fermée.

SeriesGuide recommande également une copie d’Auto Backup vers un emplacement hors de l’appareil. FranchiseGuide V0.35 n’automatise pas cette copie : il distingue seulement fichier proposé et fichier vérifié, puis rappelle à l’utilisateur de produire une nouvelle sauvegarde externe.

Sources officielles :
- https://www.seriesgui.de/help/how-to/trakt/checkins
- https://www.seriesgui.de/help/how-to/backup/sync
- https://www.seriesgui.de/help/how-to/basics/buttons
- https://www.seriesgui.de/help/how-to/backup/autobackup
- https://www.seriesgui.de/help/how-to/backup/backup

## Modèle d’une session de check-in

Une session locale contient uniquement les identifiants nécessaires, le compte, la référence d’application, la catégorie, les dates, la durée estimée et son état. Le titre sert à l’affichage mais n’est pas envoyé dans le corps Trakt. Taille et champs sont bornés ; une session illisible est conservée pour export de récupération au lieu d’être remplacée silencieusement.

États : démarrage, actif, démarrage incertain, confirmation, fin incertaine, nettoyage et annulation incertaine. Une seule session active est admise. Elle est liée au compte et à l’application Trakt ayant démarré l’opération.

## Démarrage

La référence locale est écrite avant le POST `/checkin`. En cas de refus explicite, elle est supprimée. En cas de coupure sans accusé, elle devient « démarrage incertain » : aucun nouvel envoi automatique n’est produit. Une cible retirée, ambiguë ou sans identifiant TMDb est refusée avant réseau.

Démarrer n’ajoute aucun visionnage, aucune date ou progression locale.

## Fin avant la durée

Lorsque la durée estimée n’est pas atteinte — ou inconnue — l’utilisateur peut terminer volontairement. Une date de fin unique est préparée et réutilisée lors des reprises. L’historique Trakt est écrit ; la réponse doit annoncer un élément ajouté ou existant et ne pas contenir l’identifiant dans `not_found`.

Le suivi local est appliqué seulement après cet accusé. La même seconde ne peut pas créer deux lectures locales. Le check-in distant est ensuite supprimé ; si ce nettoyage échoue, l’historique confirmé reste local et une action de nettoyage séparée est proposée.

## Fin après la durée et prévention des doublons

Trakt peut déjà avoir transformé le check-in en historique après la durée. FranchiseGuide commence donc par lire les100 entrées récentes du bon type. Si une entrée TMDb correspondant à la session est trouvée après son démarrage, sa date distante devient la date locale et aucun POST `/sync/history` n’est envoyé.

Si l’historique n’est pas lisible ou pas encore visible, la session devient « fin incertaine » et aucune lecture locale/distance supplémentaire n’est ajoutée. L’utilisateur peut vérifier plus tard. Cette prudence évite de compter deux visionnages avec des dates différentes, cas que la simple déduplication par seconde ne résoudrait pas.

## Vérification et annulation

Vérifier est une lecture distante bornée. Une correspondance exacte permet la mise à jour locale puis le nettoyage. Aucune correspondance laisse la session récupérable. Annuler supprime le check-in distant et n’ajoute rien localement. Une réponse d’annulation perdue conserve une référence incertaine.

Oublier seulement la référence locale est volontairement séparé : cela ne prouve rien sur Trakt. L’effacement général de FranchiseGuide est refusé tant qu’une session existe.

## Écran Aujourd’hui

Le tableau calcule, sans écriture automatique :
- événements datés aujourd’hui ;
- prochains épisodes/titres prêts selon les filtres locaux ;
- opérations Trakt incertaines ou suspendues ;
- check-in et compte à rebours ;
- état de sauvegarde externe.

Il ne crée pas de date de diffusion, de durée ou de disponibilité absente. Les listes sont bornées à l’écran. Android dispose d’un panneau et d’un widget source ; le widget n’est pas validé sur appareil.

## Rappel de sauvegarde

Un export réussi au sens de l’interface signifie seulement que le navigateur ou Android a proposé/écrit vers la destination choisie. Web ne peut pas garantir que l’utilisateur conserve le fichier, et le sélecteur Android ne vérifie pas sa survie ultérieure.

Le registre local distingue : dernière proposition, dernier fichier déclaré vérifié et report de rappel. La confirmation est possible uniquement après une proposition. Délai de rappel :14 jours ; report :7 jours. Ce registre ne contient pas la bibliothèque et n’est pas une sauvegarde.

## Effacement local

Web/PC : arrêt des lectures, révocation de la session Steam locale si présente, suppression des clés préfixées, sessionStorage accessible, caches, IndexedDB et service worker de l’origine, puis vérification. Un stockage inaccessible devient un avertissement résiduel après poursuite des autres étapes. Aucun effacement distant.

Android : cette passe ne livre pas un écran d’effacement équivalent validé. Les fonctions de sauvegarde et récupération précédentes restent disponibles ; la différence est explicitement conservée dans la matrice.

## Limites

- Aucun traitement en arrière-plan Web lorsque fermé.
- Aucun compte Trakt réel utilisé dans les tests.
- L’historique récent limité à100 entrées peut ne pas contenir une très ancienne session ; aucune correspondance n’est alors inventée.
- Les horloges locale/distance peuvent différer ; la correspondance est bornée entre le démarrage local et cinq minutes dans le futur au moment de la vérification.
- Une requête distante déjà reçue ne peut pas être annulée localement.
- Pas de SeriesGuide Cloud, Auto Backup hebdomadaire complet ou copie automatique Drive dans cette version.
- Pas d’APK, build SDK/Compose, notification ou widget sur appareil.
