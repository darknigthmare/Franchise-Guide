# À reprendre et organisation groupée — V0.17

## Retrouver le prochain titre

Le nouvel écran est accessible dans Plus et depuis chaque bibliothèque Web/PC. Il travaille uniquement sur les titres déjà ajoutés, pas sur les suggestions grisées.

### Séries

Un seul prochain épisode non vu/non ignoré est retenu par série, dans l’ordre saison puis numéro. Les spéciaux sont exclus sauf activation explicite. Un épisode antérieur à date inconnue ou future bloque le saut vers un épisode ultérieur : la série se trouve dans À compléter ou À venir, plutôt que d’être proposée incorrectement comme prête.

Une numérotation inconnue ou dupliquée dans les épisodes restants empêche le choix arbitraire d’un épisode. Une fiche sans épisodes ou dont le catalogue connu est incomplet ne devient pas « À jour » par défaut. La mention **À jour sur les épisodes chargés** décrit les données locales, pas la fin définitive d’une série ni une garantie qu’aucun épisode ne sera ajouté ensuite.

La recherche n’utilise pas les titres cachés des épisodes lorsque l’anti-spoiler est activé. Les nombres de restants décrivent les épisodes chargés ; un avertissement de catalogue incomplet est conservé quand nécessaire.

### Films, jeux et livres

Les films déjà vus sont exclus ; une sortie connue passée permet la vue À reprendre, une sortie future la vue À venir, une date absente la vue À compléter. Les jeux et livres non terminés peuvent être proposés à commencer ou poursuivre avec leur progression enregistrée. Une date de diffusion/sortie ne prouve pas la disponibilité chez un fournisseur de streaming ni la possession d’un exemplaire.

Le filtre de durée concerne uniquement les films/épisodes dont la durée est un entier positif connu. Il n’assimile ni le temps total d’un jeu à une séance, ni le nombre de pages d’un livre à une durée. Les durées zéro ou absentes ne sont pas inventées.

### Filtres et commandes

Catégorie, état, titre/numéro, favoris, titres masqués, spéciaux et durée maximale sont combinables. La sélection complète est filtrée, puis l’écran affiche 60 lignes ; le bouton suivant en charge 60 autres. Les filtres restent des réglages de session, pas de nouveaux champs exportés.

**Marquer vu maintenant** est disponible pour un film/épisode prêt. Avant mutation, les données sont recalculées : un ancien bouton ne doit pas marquer un autre épisode, rejouer une lecture déjà vue ou sauter un nouvel épisode antérieur. La commande ajoute une lecture normale avec la date courante, utilise le journal Trakt existant lorsque connecté et n’envoie rien de plus lors de la seule navigation.

**Ajouter une date passée** réutilise le dialogue V0.16 avec fuseau, contrôle du futur/doublon et ambiguïtés de changement d’heure. Ce n’est pas une invitation calendrier. Sur un jeu ou livre, **Ouvrir la fiche** permet de mettre à jour le suivi existant : aucune campagne terminée n’est déduite d’une simple ouverture.

## Organiser plusieurs titres

1. Ouvrir Organiser ma bibliothèque depuis Plus ou une bibliothèque.
2. Filtrer par catégorie, titre/auteur et visibilité.
3. Cocher les titres ou **Sélectionner tous les résultats**. Cela vise aussi les titres filtrés non encore affichés. Limite : 10 000 titres par action. La sélection est conservée entre filtres ; **Désélectionner** la vide.
4. Choisir une action : ajouter/retirer des favoris, masquer/rendre visible, ajouter/retirer d’une liste existante.
5. **Prévisualiser**, vérifier les compteurs puis confirmer.

L’écran construit au plus 80 premières lignes puis charge 80 autres à la demande. L’aperçu sépare les titres sélectionnés, ceux à modifier et ceux déjà conformes. Aucun aperçu n’écrit la bibliothèque. Une liste inexistante ou une sélection retirée est refusée ; une action sans modification n’est pas envoyée inutilement.

### Ce qui ne change pas

Les historiques, dates, notes, scores, succès, profils, états de visionnage et opérations distantes ne sont pas effacés. Ajouter à une liste locale n’ajoute pas à une watchlist Trakt. **Masquer** n’est pas **supprimer** et ne constitue pas une pause des synchronisations gaming : employer le centre dédié pour les pauses de connecteurs.

Les actions locales visent les champs `favorite`, `hidden` et une appartenance précise de `listIds`. Elles sauvegardent l’état sans ajouter de commande au journal Trakt. Sur Android, l’opération prend le verrou de synchronisation ; si occupé, elle demande de réessayer.

### Préconditions et refus d’écriture

Une copie locale de sécurité réussie est obligatoire avant application. Les valeurs concernées sont vérifiées pour tous les titres avant mutation. Si un champ visé a une autre valeur depuis l’aperçu, si la liste a disparu ou si un titre a été retiré, l’action entière est refusée. Un import/restauration pendant l’aperçu l’invalide aussi.

Sur Web/PC, la sauvegarde utilise l’enveloppe atomique existante. Un refus du stockage annule l’état en mémoire et conserve un brouillon exportable par le dispositif de récupération. Le panneau n’annonce pas une action réussie ni une annulation disponible si la sauvegarde échoue. Les tests injectent ces pannes ; ce n’est pas un essai sur tous les stockages réels.

### Annuler la dernière action

Une seule dernière action groupée est annulable pendant la session Web/PC. Sur Android, cette mémoire ne survit pas à la sortie/destruction de l’écran. Elle n’est ni incluse dans un export ni rejouée après redémarrage.

L’annulation compare les valeurs actuelles des champs visés avec le résultat de l’action. Si elles correspondent encore, elle inverse **uniquement ces champs**. Un nouveau visionnage, une note modifiée ensuite ou une appartenance à une autre liste restent conservés. Si un champ concerné diffère, l’annulation est refusée au lieu d’écraser la modification récente. Ce contrôle compare les valeurs actuelles ; il n’enregistre pas toutes les mutations intermédiaires d’un champ modifié puis revenu à sa même valeur.

## Android et validation

Les écrans Compose, les règles Kotlin et les points d’entrée Plus sont intégrés aux sources. Les règles pures ont été exécutées sur JVM, la couche repository compilée contre signatures de test. L’interface Compose complète, son ergonomie sur appareil, son stockage et ses notifications ne sont pas validés par un build Android SDK dans cette livraison.
