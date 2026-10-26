# Confidentialité technique — FranchiseGuide 0.36.0

La sauvegarde portable courante utilise le **schéma 11**, avec lecture des anciennes sauvegardes **1 à 11**. Les ajouts historiques V0.31/V0.35 décrits plus bas restent applicables au périmètre indiqué. Les preuves de cette passe, distinctes des anciens rapports, sont dans [QA_REPORT.md](QA_REPORT.md).

Ce document décrit le traitement des données dans la distribution locale. Il ne constitue pas la politique d’un futur service commercial identifié. Aucun hébergement public FranchiseGuide n’est livré.

## Ce qui est conservé

| Emplacement | Données |
|---|---|
| Navigateur Web / PC, localStorage | Bibliothèque, suivis, préférences, profils et relevés gaming, instantanés locaux, file Trakt et diagnostics. Les clés, jetons et brouillons d’avis utilisent leurs stockages locaux séparés. |
| Navigateur, IndexedDB | Jusqu’à cinq copies portables de récupération. Elles sont distinctes des instantanés localStorage, mais rattachées au même profil et à la même origine. |
| Navigateur, caches | Interface PWA et images mises en cache selon les règles de l’application. |
| Serveur personnel Steam | Fichier contenant empreinte du jeton de session, SteamID et expiration, avec un cookie HttpOnly dans le navigateur. Durée maximale de sept jours. |
| Serveurs personnels Steam / RetroAchievements | Clés API fournies par variables d’environnement et caches mémoire temporaires des lectures. Les caches expirent et disparaissent à l’arrêt du serveur. |
| Sources Android | État, file et brouillons dans le stockage applicatif prévu, avec gestion séparée des identifiants de connexion. Le comportement réel sur appareil reste à valider. |

Le PC utilise le stockage du navigateur pour la bibliothèque, sans base chiffrée intégrée au JAR. Les données de l’origine, y compris les clés et brouillons, ne sont pas chiffrées contre les scripts exécutés sur cette origine. Un autre utilisateur du même profil navigateur ou système peut accéder à ses données selon les droits de ce profil.

## Services contactés

Selon la source choisie et les accès configurés, les recherches et actualisations catalogue interrogent TMDb, RAWG, Open Library, TVmaze, Apple/iTunes ou FreeToGame, directement ou par la passerelle personnelle. Voir [AUTOMATIC_SERVICES.md](AUTOMATIC_SERVICES.md) pour leurs périmètres. Les affiches et portraits contactent les hôtes des images retournées ou les URL HTTPS présentes dans les imports. Les destinataires reçoivent les paramètres demandés et les informations réseau nécessaires, notamment l’adresse IP. La réduction du referrer ne masque pas cette adresse.

Trakt reçoit les requêtes de connexion, les lectures et les modifications de suivi prévues par les actions et réglages. Les avis sont envoyés après confirmation ; publier un avis constitue une action publique. La déconnexion ou l’effacement local ne supprime pas les données déjà enregistrées sur Trakt.

Steam reçoit les requêtes OpenID de connexion et, pour les lectures demandées, le SteamID64, les App ID et les paramètres nécessaires. Le mot de passe et Steam Guard sont saisis sur Steam. La clé **FG_STEAM_API_KEY** reste côté serveur personnel. La connexion vérifie une identité, sans rendre accessibles les données privées du profil.

RetroAchievements reçoit le pseudo public ou l’ULID stable du profil, ainsi que les identifiants des jeux consultés. La clé **FG_RETRO_API_KEY** reste côté serveur. **Associer un profil public n’authentifie pas son propriétaire** et ne crée aucun jeton de connexion RA dans FranchiseGuide. Les succès normal et Hardcore ont des états et dates distincts. Le relevé de progression ne prouve ni la possession du jeu ni la fin de sa campagne.

Les lectures gaming automatiques exigent leur activation et se produisent pendant que l’application est ouverte, visible et connectée. Les notes, guides, épingles et cases de checklist ne sont pas envoyés pour débloquer des succès. Un lien PSNProfiles est ouvert sur action de l’utilisateur ; cette intégration ne scrape pas les profils et ne demande pas de cookie Sony ou de NPSSO.

Le jingle original est un fichier local. Aucun module publicitaire ou de mesure d’audience d’éditeur n’est ajouté dans ce code ; cela n’exclut pas les journaux des fournisseurs ou d’un éventuel hébergeur choisi séparément.

## Sauvegardes et transfert

L’export portable courant, au **schéma 11**, exclut les clés de configuration, jetons, cookies de session, file Trakt et brouillons d’avis. Il conserve les champs gaming introduits au schéma 8. Il contient des titres, notes, historiques, profils publics, éditions et dates de déblocage : **un export sans clé n’est pas anonyme**. Les copies IndexedDB utilisent le format portable courant.

Le fichier brut de récupération est différent : il conserve l’original sans nettoyage et peut contenir des secrets. Les JSON téléchargés restent sur le disque jusqu’à leur suppression par l’utilisateur. Restaurer une sauvegarde suspend les automatismes gaming ; un transfert de profil ou de relevé ne vaut pas authentification.

## Effacement local

**Confidentialité & données** permet d’effacer les brouillons ou les données locales FranchiseGuide avec les confirmations prévues. L’effacement global est bloqué pendant une synchronisation ou une publication d’avis. La session Steam concernée est d’abord révoquée auprès du serveur personnel ; un échec empêche de poursuivre.

Le navigateur cible les entrées et caches FranchiseGuide, puis ses copies IndexedDB. Ces étapes ne sont pas atomiques entre les différents stockages. Si la suppression IndexedDB échoue après celle du stockage principal et des caches, l’interface annonce un **effacement incomplet**, indique les suppressions déjà effectuées et propose de recharger pour reprendre. Un message d’erreur ne signifie donc pas que toutes les données sont restées intactes.

Cette action ne supprime ni les données distantes chez les fournisseurs, ni les JSON déjà téléchargés, ni les variables **FG_STEAM_API_KEY** et **FG_RETRO_API_KEY**, ni les autres sessions du serveur. Les fichiers de session et caches serveur sont distincts du stockage navigateur ; voir STEAM_SIGN_IN.md et SECURITY_AND_DATA.md.

## Portée des vérifications

Les essais de cette passe utilisent des fournisseurs simulés. Aux parcours DOM contrôlés s’ajoutent des vérifications sur une vraie origine Chrome, avec CSP, localStorage et IndexedDB réels. Aucun compte Steam, Trakt ou RetroAchievements réel, ni installation PWA au niveau du système, n’a été validé. Les scénarios d’anciennes versions restent historiques et ne sont pas comptés comme une nouvelle exécution.

La passe 0.36 a produit une compilation SDK Android et un APK de développement. Les règles pures JVM et les parités exécutées sont distinguées des tests de codecs et des essais appareil encore manquants. Aucun fonctionnement du stockage sur téléphone n’est déduit de la compilation. Les résultats de build/lint, les groupes exécutés et les limites sont dans [QA_REPORT.md](QA_REPORT.md) et [FEATURE_MATRIX.md](FEATURE_MATRIX.md).

## V0.31 — préparations d'accès

Un candidat non sélectionné peut subsister en clair après échec du commit principal. Le format Web garde au plus deux ensembles ; la prochaine préparation écarte les anciens slots inutiles. Ce n'est ni du chiffrement ni une transaction atomique multi-stockages. Les exports portables excluent ces accès/références ; le brut de récupération peut rester privé. La liaison d'application bloque l'usage d'un ancien token non nettoyé après changement. Détails et preuves dans REGLAGES_ET_REPRISE.md et QA_REPORT.md.

## V0.35 — check-ins, rappel de sauvegarde et effacement

Une session de check-in contient compte, référence d’application, identifiants TMDb/local, dates et état. Elle est hors export portable et peut rester localement après une réponse distante incertaine afin d’éviter un nouvel envoi aveugle. Après la durée estimée, l’historique distant est lu avant toute écriture supplémentaire. Les tests utilisent des réponses fictives, pas un compte réel.

Le registre de sauvegarde externe ne contient pas la bibliothèque. Il mémorise seulement la proposition, la confirmation déclarative et le report. Une proposition de téléchargement ne devient jamais automatiquement « sauvegarde vérifiée ».

L’effacement Web/PC vise les stockages FranchiseGuide de l’origine et signale toute zone inaccessible. Il ne supprime ni compte, historique distant, fichiers téléchargés ou secrets configurés côté serveur. Android n’a pas d’effacement complet équivalent validé dans cette passe.
