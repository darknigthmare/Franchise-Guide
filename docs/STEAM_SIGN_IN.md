# Connexion Steam — FranchiseGuide V0.15

## Ce qui est livré

Le parcours **Se connecter avec Steam → retour dans FranchiseGuide → récupération des jeux → lecture des succès** est intégré au Web/PC. Steam OpenID 2.0 identifie le compte auprès du serveur personnel. La Steam Web API fournit ensuite les données accessibles, avec une clé configurée côté serveur.

La V0.15 ajoute séparément RetroAchievements : il s’agit d’une association de profil public par pseudo et ULID, sans authentification du propriétaire ni session RA. Ce parcours utilise une autre clé serveur ; voir `RETROACHIEVEMENTS.md`.

La connexion n’est pas une autorisation de lire des données privées. Aucun mot de passe Steam, cookie Sony, NPSSO ou clé de l’utilisateur n’est saisi dans les formulaires du navigateur. La session ne signifie pas que le joueur possède tous les jeux affichés localement : l’application conserve aussi les fiches manuelles et les imports.

## Parcours utilisateur

Ouvrir Jeux, puis Se connecter avec Steam. La fenêtre présente deux choix : importer les jeux accessibles à la connexion et actualiser les jeux/succès pendant l’utilisation. La navigation se fait dans le même onglet, vers le domaine Steam, puis revient à l’origine locale du départ. Le retour est vérifié côté serveur avant d’afficher le compte connecté.

La bibliothèque est récupérée avec les noms, icônes fournies, App ID, temps de jeu et dernière partie lorsqu’ils sont disponibles. Le pseudo et l’avatar sont facultatifs : une panne de profil ne bloque pas forcément les jeux. Les fiches ainsi créées ne prétendent pas contenir toute la base descriptive RAWG : synopsis détaillé, développeurs et grandes jaquettes ne sont pas inventés.

Une fiche déjà liée au même App ID est réutilisée. Un titre ressemblant n’est pas fusionné automatiquement avec un remaster ou une autre édition. Les cas ambigus sont affichés, sans choix silencieux. Les comptes ont des relevés et temps de jeu distincts ; les notes de jeu, favoris, listes et progression de campagne restent personnels.

La récupération initiale lance **un premier lot de huit listes de succès maximum**. Les listes les moins récemment actualisées sont prioritaires. Si l’actualisation automatique est désactivée, les listes suivantes restent en attente : utiliser **Actualiser les listes Steam et RetroAchievements**, **Récupérer mes jeux et succès** pour le compte Steam, ou actualiser une liste depuis sa fiche.

Avec l’automatisme activé, les besoins sont vérifiés chaque minute lorsque l’application est ouverte, visible et connectée. Les listes encore inconnues et les données datant d’au moins six heures sont éligibles ; les lectures reprennent aussi après un retour au premier plan ou du réseau. Les erreurs peuvent imposer un délai supplémentaire. Aucun service permanent ne fonctionne application fermée. Une grande bibliothèque demande plusieurs passages : le compteur indique les listes à lire, relues et en erreur.

Les succès sont en lecture seule : obtenu/non obtenu/inconnu, date disponible, description, icône et rareté lorsque fournis. Un succès secret reste masqué. Une case de checklist ne débloque aucun succès officiel. Les heures Steam ne marquent pas une campagne terminée. Aucun platine n’est créé par déduction.

Le résultat « aucun catalogue de succès exposé » n’est pas une liste complétée ; il n’efface pas un ancien catalogue possédant déjà des succès. Un profil privé, une réponse tronquée, un autre SteamID ou une erreur ne supprime pas le relevé précédent. Zéro jeu retourné est signalé comme ambigu, pas traité comme une commande de suppression.

## Configuration requise une fois

Voir START_HERE.md. **FG_STEAM_API_KEY** doit être configurée dans l’environnement du lanceur Java ou Node avant son démarrage. La clé n’est pas rendue au navigateur et n’est pas incluse dans le JSON portable. Ce prérequis d’exploitation ne disparaît pas grâce au bouton OpenID.

Java 17 ou supérieur : serveur et interface à [127.0.0.1:47631](http://127.0.0.1:47631). Node 22 ou supérieur : [127.0.0.1:47632](http://127.0.0.1:47632). Les deux ne partagent pas automatiquement le stockage navigateur ni leurs sessions. Ne pas changer de profil ou passer entre localhost et 127.0.0.1 au milieu d’une connexion.

Un site purement statique ne peut pas effectuer cette vérification serveur. Cette distribution ne livre pas un service public hébergé où les utilisateurs n’auraient aucune configuration à faire. Un tel service demanderait un backend HTTPS, la séparation des utilisateurs, l’exploitation des clés, une politique de confidentialité applicable et des validations supplémentaires.

## Session et protections

State et cookie de liaison aléatoires de 256 bits, durée maximale 10 minutes, réponses en double refusées, identité HTTPS Steam canonique, champs signés requis, adresse de retour exacte, nonce récent et non réutilisé. La réponse est contrôlée auprès de l’endpoint HTTPS Steam fixé dans le code, sans suivre une redirection arbitraire. Les tailles de réponse, temps d’attente et fréquences sont limités.

Le serveur conserve une session locale de sept jours. Le cookie est HttpOnly et SameSite=Lax ; il est destiné à la boucle locale HTTP, pas à un déploiement Internet. Seules les empreintes SHA-256 des jetons sont écrites dans les fichiers de sessions, avec identité et expiration. Ces fichiers restent sous le profil système, avec permissions restrictives sur les systèmes qui les prennent en charge. Ce n’est pas un chiffrement contre le propriétaire du compte système ni une isolation contre un logiciel malveillant local.

Fichiers : `.franchiseguide/steam-sessions-pc.properties` pour Java ; `.franchiseguide/steam-sessions-web.json` pour Node. Ils ne sont pas distribués, exportés ni partagés entre les deux lanceurs. Un fichier invalide n’est pas remplacé silencieusement. Une session peut être retrouvée après redémarrage avec le même navigateur et le même fichier intact, avant son expiration.

Déconnecter Steam révoque la session de ce navigateur, suspend les automatismes de ce profil et conserve les données importées. Effacer les données locales commence par cette révocation : un échec à cette étape arrête la suite. Les autres sessions du serveur et la variable d’environnement ne sont pas supprimées par le navigateur. Aucun effacement distant sur Steam n’est demandé.

Les suppressions locales suivantes ne sont pas une transaction unique. Si l’effacement IndexedDB échoue après la suppression du stockage principal et des caches, le message indique un effacement incomplet et propose de recharger pour reprendre ; les données déjà supprimées ne sont pas restaurées. Voir `SECURITY_AND_DATA.md`.

## Limites quantitatives et reprise

Import de bibliothèque borné à 20 000 entrées ; espace gaming limité à 100 profils, 2 000 listes, 5 000 succès par liste et 100 000 succès au total. L’interface affiche les listes différées quand la limite est atteinte ; elle ne prétend pas avoir tout synchronisé. Le test de 2 100 jeux confirme 2 100 titres préservés, 2 000 liaisons créées et 100 différées. Ces limites ne constituent pas une garantie de stockage : le quota du navigateur peut être atteint plus tôt.

Les refus de quota conservent l’enregistrement précédent et une copie de travail exportable quand la bibliothèque a été préparée. L’application tente également une copie portable dans IndexedDB, sans garantir que ce stockage sera disponible. Les exports portables restent au schéma **8** et excluent les clés et sessions ; ils contiennent néanmoins des profils et historiques personnels. L’export brut préserve l’original sans nettoyage et peut contenir des secrets.

## Autres connexions — ne pas confondre les états

| Service | État dans cette livraison |
|---|---|
| Steam Web/PC | OpenID côté serveur personnel, récupération de bibliothèque et succès ; interface intégrée, tests avec amont simulé. |
| RetroAchievements Web/PC | Profil public par pseudo/ULID, jeux avec progression et succès normal/Hardcore ; association sans authentification du propriétaire. |
| Trakt | Parcours par code appareil et synchronisation existants ; aucun compte réel utilisé dans les essais. |
| Steam et RetroAchievements Android natifs | Métadonnées et relevés transportés au schéma 8 ; connexions et lectures réseau natives absentes. |
| PSN / PSNProfiles | Références et import au format neutre ; pas de connexion Sony ou synchronisation PSNProfiles |
| Xbox, Epic, GOG | Références et imports existants ; aucun nouveau connecteur réseau livré. |

## Sources de conception consultées

Valve documente la connexion navigateur par OpenID, la liste de jeux et les méthodes de succès. Les pages ci-dessous documentent les services, pas un test de ce logiciel :

- [OpenID](https://partner.steamgames.com/doc/features/auth)
- [Bibliothèque](https://partner.steamgames.com/doc/webapi/IPlayerService)
- [Succès](https://partner.steamgames.com/doc/webapi/ISteamUserStats)
- [Clés côté serveur](https://partner.steamgames.com/doc/webapi_overview)

**Aucune authentification réelle Steam n’a été effectuée.** Les tests HTTP utilisent des réponses Steam contrôlées. Les essais d’interface de la V0.15 exécutent les scripts et le DOM de l’application dans jsdom, avec réseau et stockages simulés. Les anciens scénarios et rapports Chromium sont conservés ; ils ne constituent pas une exécution Chromium de cette livraison.

Le trajet réel Steam, les cookies lors d’un aller-retour OpenID et l’installation PWA restent à valider. Android est livré en sources avec des contrôles JVM et une vérification du code non graphique contre des signatures de test, sans build SDK complet, APK/AAB ou essai sur appareil. La portée exacte des essais figure dans `QA_REPORT.md`.
