# RetroAchievements — FranchiseGuide 0.14

## Ce qui est disponible

Sur PC et sur le Web avec le serveur personnel fourni, **Associer un profil RetroAchievements** retrouve un profil public, importe ses jeux présentant une progression, puis lance un premier lot de huit listes de succès maximum. Les listes restantes peuvent être lues manuellement ou par actualisation automatique si elle est activée. Les modes normal et Hardcore ont leurs propres états et dates. L’association ne constitue pas une authentification du propriétaire du compte.

Le site RetroAchievements fournit une clé Web API dans les réglages du compte. Cette clé est configurée une fois dans le serveur personnel, via `FG_RETRO_API_KEY`. Elle n’est demandée dans aucun formulaire de FranchiseGuide, n’est pas intégrée aux sources Web et n’est pas copiée dans les sauvegardes.

La connexion Steam existante reste disponible avec OpenID et `FG_STEAM_API_KEY`. Les deux services disposent de réglages et de profils indépendants.

## Configuration

Récupérer la **Web API Key** depuis les réglages du compte sur [RetroAchievements](https://retroachievements.org/controlpanel.php). Les instructions officielles sont dans [Getting Started](https://api-docs.retroachievements.org/getting-started.html).

### Windows PowerShell

Dans le dossier du programme PC :

```powershell
$env:FG_RETRO_API_KEY = [System.Net.NetworkCredential]::new('', (Read-Host 'Clé Web API RetroAchievements' -AsSecureString)).Password
.\FranchiseGuide-PC.bat
```

Dans la Suite, ouvrir d’abord le sous-dossier `desktop`. Pour le serveur Web, lancer `node server/start.cjs` depuis la racine de la distribution Web, dans la même fenêtre PowerShell. La saisie masquée évite de mettre la valeur de la clé dans l’historique des commandes.

### Linux ou macOS — Bash

```bash
read -rsp 'Clé Web API RetroAchievements : ' FG_RETRO_API_KEY
export FG_RETRO_API_KEY
bash desktop/FranchiseGuide-PC.sh
```

Pour la distribution PC seule, le lanceur se trouve à la racine : `bash FranchiseGuide-PC.sh`. Pour la distribution Web, remplacer la dernière commande par `node server/start.cjs`.

Ces exemples ne configurent pas une clé permanente dans le système. Au prochain lancement depuis une autre fenêtre, il faudra à nouveau fournir la variable ou utiliser sa propre configuration sécurisée d’environnement.

## Parcours dans l’application

1. Ouvrir **Jeux → Trophées & comptes**.
2. Dans **RetroAchievements**, cliquer sur **Vérifier le serveur**. Une clé absente produit un message explicite.
3. Cliquer sur **Associer un profil RetroAchievements**, saisir le pseudo public et choisir la récupération immédiate des jeux.
4. L’actualisation automatique est facultative et décochée par défaut lors de la première association.
5. Après l’import, ouvrir un jeu puis **Trophées & succès**. Les listes restent distinctes pour chaque profil et version console.

Un jeu déjà présent dans la bibliothèque peut être associé individuellement avec **Relier RetroAchievements**, en indiquant l’ID de sa fiche RA. Les imports automatiques réutilisent les identifiants exacts ; les ressemblances de titre ne suffisent pas à fusionner des jeux.

## Identité, progression et données personnelles

- Le pseudo sert à retrouver le profil. L’application conserve ensuite son **ULID stable**, et le pseudo séparément pour l’affichage et le lien public. Une modification de pseudo ne crée donc pas un autre compte lors d’une actualisation par ULID.
- Les jeux importés correspondent à la progression exposée par RA. Leur import ne coche pas automatiquement possession, campagne terminée ou progression personnelle.
- Les déblocages normal et Hardcore sont conservés séparément. Les filtres Hardcore distinguent les succès obtenus, les restants et les états inconnus.
- Les notes, guides, épingles et cases personnelles restent locaux. Une case personnelle n’est jamais envoyée comme déblocage.
- Un ensemble de succès complet ne crée aucun platine. Les ensembles supplémentaires RA conservent leur ID et ne sont pas automatiquement qualifiés de DLC.
- Les dates absentes restent inconnues. La rareté n’est affichée que lorsque les compteurs nécessaires sont disponibles ; les RetroPoints pondérés ne sont pas traités comme un pourcentage.

## Actualisation et erreurs

Les jeux sont lus par pages de **500 entrées maximum**. Toutes les pages doivent être complètes et cohérentes avant l’application de l’import. Si le total change, si une page manque ou si un jeu se répète dans plusieurs pages, l’import est refusé et les données enregistrées restent présentes.

La récupération initiale lance **un seul lot de huit listes de succès maximum**. L’actualisation automatique étant décochée par défaut à la première association, les autres listes restent alors en attente. Pour poursuivre manuellement, utiliser **Actualiser les listes Steam et RetroAchievements**, **Récupérer les jeux et succès** sur la fiche du profil, ou actualiser une liste depuis la fiche d’un jeu. Les listes les moins récemment lues sont prioritaires.

Avec l’automatisme activé, les passages suivants traitent les listes encore inconnues et les données datant d’au moins six heures lorsque l’application est ouverte, visible et connectée. Il peut être suspendu depuis la fiche du profil. Les compteurs indiquent les listes restant à lire et les éventuels délais de reprise peuvent différer leur traitement.

Après une limitation temporaire, la récupération de la bibliothèque conserve les pages déjà lues et respecte le délai indiqué par le serveur, avec deux reprises maximum par page et une durée totale limitée. L’action **Annuler** interrompt aussi cette attente. Une fermeture de l’application ne crée pas un service permanent d’arrière-plan.

Une réponse privée, inexistante, incohérente, provenant d’un autre compte ou d’un autre jeu ne remplace pas le dernier relevé valide. Une bibliothèque vide ne supprime aucun jeu. Les listes de plus de 5 000 succès, les bibliothèques de plus de 20 000 jeux et les états dépassant les capacités de stockage ne sont pas acceptés silencieusement. La capacité actuelle est de 2 000 listes et de 100 000 succès par bibliothèque ; les créations de listes différées sont signalées.

## Sauvegardes et Android

Le format portable reste **V8**. Les profils, identifiants, alias fournisseurs et relevés sont transportés dans les JSON. Restaurer une sauvegarde suspend les automatismes de lecture ; il faut les réactiver volontairement sur l’appareil de destination.

Les sources Android conservent ces informations et affichent les modes normal et Hardcore. **La connexion et la lecture réseau RetroAchievements natives Android ne sont pas livrées** : transférer les relevés par sauvegarde depuis Web/PC. Aucun APK/AAB n’est inclus.

## Portée technique

Les passerelles restent des serveurs personnels attachés à `127.0.0.1`. Elles ne sont pas un service public multi-utilisateur. Les requêtes sont en lecture seule, avec contrôle de l’origine, délais, limites de taille et de concurrence, cache mémoire borné et réponses non mises en cache par le navigateur. Le serveur ne journalise pas la clé et ne renvoie pas de jeton d’authentification RA.

Les fournisseurs ont été simulés pour les tests. Aucun vrai compte RetroAchievements n’a été utilisé ; la validation du parcours réel reste à faire avec la configuration personnelle de l’utilisateur. Les parcours d’interface V0.15 utilisent jsdom et des stockages simulés, dont fake-indexeddb. Les rapports Chromium historiques ne constituent pas une exécution Chromium de cette livraison. Aucun build Android complet avec SDK, essai Compose ou essai sur téléphone n’est validé ; voir `QA_REPORT.md`.

## Sources techniques officielles

- [Clé Web API et confidentialité](https://api-docs.retroachievements.org/getting-started.html)
- [Profil et identifiant ULID](https://api-docs.retroachievements.org/v1/get-user-profile.html)
- [Pagination des jeux avec progression](https://api-docs.retroachievements.org/v1/get-user-completion-progress.html)
- [Catalogue et progression d’un jeu](https://api-docs.retroachievements.org/v1/get-game-info-and-user-progress.html)
