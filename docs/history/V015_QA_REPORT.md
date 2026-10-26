# Rapport QA — FranchiseGuide 0.15.0

**Date : 6 octobre 2026. Version de développement, sans validation commerciale.**

Base : Suite V0.14 réellement livrée, ZIP SHA-256 `13f819e95fb242574554eb35d9374c85e27dbc156b16e24e840800788dda6a81` et RAR SHA-256 `0f2a98e9523a0c99a626473dc393844045770b30327936034d353527aa0c29e6`. Le manifeste de la base a été intégralement vérifié avant copie.

## Campagne exécutée dans cet environnement

Commande : `python3 tools/run_available_checks.py`. **27 commandes terminées avec code 0, aucune commande exécutée en échec ; trois blocs explicitement non exécutés.** Le rapport machine `tests/results/v015/available-qa.json` contient la commande, le statut, la durée et le journal de chaque étape. Ce résultat ne doit pas être présenté comme une exécution intégrale de `tools/run_checks.sh` : les dépendances manquantes ci-dessous empêchent deux suites Node et deux exécutables de codecs JSON.

| Groupe | Résultat obtenu dans cette passe | Méthode et limites |
|---|---:|---|
| Node / JavaScript disponible | **591 tests réussis**, aucun échec | Domaine, stockage simulé, réseau/serveurs ; inclut 59 nouveaux tests de la politique de synchronisation |
| Chromium, scripts/DOM | **398 assertions réussies** | 64 + 45 + 39 + 69 + 64 + 54 régressions, puis 63 nouveaux scénarios V0.15 ; API, images, horloge et stockage contrôlés |
| Kotlin/JVM | **198 assertions + 1 scénario franchises réussis** | 42 suivi, 22 historique, 15 intégration, 47 règles de publication, 52 gaming, 20 metadata Steam ; JVM réelle, pas runtime Android |
| Kotlin non graphique | **Compilation sur signatures réussie** | Modèles, clients réseau, import et repository compilés contre des stubs ; ne constitue pas un build Android/Compose |
| JAR PC compilé | **62 contrôles HTTP + 9 de démarrage réussis** | Vrai JAR, ressources servies, sécurité du serveur local et collisions de port ; pas de session graphique Windows/macOS |
| Passerelles Java | **265 assertions réussies** | 28 succès Steam + 53 OpenID/session Steam + 184 RetroAchievements ; vraies routes HTTP locales, fournisseurs/vérificateurs simulés |
| Structure | **42 contrôles réussis** | Versions, modules, branchements, formats et ressources |
| Bootstrap | **8 contrôles réussis** | Artefacts corrompus refusés ; certaines vérifications PowerShell sont statiques |

Les nombres sont des tests/assertions, pas un pourcentage de reproduction de SeriesGuide. Les tests de handlers peuvent couvrir les mêmes familles de scénarios que les tests Node : ne pas les additionner pour annoncer autant de fonctionnalités distinctes.

## Vérifications bloquées et non comptées

- `tests/retro-orchestration-v014.test.cjs` : **non exécuté**, dépendance jsdom absente.
- `tests/web-dom-v014.test.cjs` : **non exécuté**, jsdom et fake-indexeddb absents.
- `GamingCodecRetroTest` et `MediaIdentityCodecTest` Kotlin avec le véritable `org.json:json:20240303` : **non exécutés**, JAR absent. Leurs **78 assertions de la version antérieure ne sont pas comptées** parmi les 198 de cette passe.

L'installation `npm ci --ignore-scripts` a été tentée ; résolution réseau indisponible. Les essais hors ligne, y compris avec le cache disponible, échouent avec `ENOTCACHED` pour xmlchars. Aucun faux paquet jsdom/fake-indexeddb n'a été créé pour déclarer ces tests passants. L'accès au dépôt Maven n'a pas permis de récupérer le parseur JSON réel. Les tests qui utilisent des stubs sont nommés séparément et ne remplacent pas le parseur.

La recette complète `tools/run_checks.sh` reste livrée avec les tests non exécutés. La CI installe maintenant les dépendances de tests verrouillées avant d'exécuter Node ; **elle n'a pas été publiée ni exécutée chez un hébergeur**. Les serveurs Web/PC de production n'exigent pas ces dépendances npm de test.

## Nouveaux scénarios V0.15

### Centre de synchronisation et lots

Une campagne de 18 listes Steam est traitée en passages de huit, huit puis deux, sans renvoi des listes déjà acquittées. Le lot enregistre ses compteurs de succès, échecs, références retirées et listes restantes avec les données de la bibliothèque. Les scénarios couvrent pause, reprise, suppression, lot fermé, changement de bibliothèque, passage hors ligne et fenêtre masquée. Après rechargement, une campagne manuelle attend une reprise explicite. Les anciennes reprises périodiques des fournisseurs ne doivent pas contourner le coordinateur.

Les filtres portent sur toutes les listes connues, avec 50 lignes initiales ; un cas de 65 listes vérifie la recherche d'une ligne non encore rendue. Les tests de politique comprennent jusqu'à 2 000 références pour contrôler tri, unicité et borne du journal, sans prétendre avoir validé ce volume dans le stockage réel d'un téléphone.

### Délais, erreurs et annulations

Retry-After est accepté en secondes ou en date HTTP et borné. Un HTTP429 Steam ou RetroAchievements impose un délai à toutes les listes de ce fournisseur, y compris celles d'un autre profil. Un clic manuel ne le contourne pas. Le délai et les pauses survivent au rechargement. Les échecs sans délai fourni adoptent une reprise progressive de 5 à 60 minutes.

Une annulation volontaire conserve les dernières données et ne devient pas une fausse erreur. Un dépassement du délai de lecture de succès est au contraire comptabilisé avec son prochain essai, au lieu d'être perdu comme une simple annulation. Les lectures tardives sont ignorées après remplacement, retrait, changement d'identité ou pause. Les scénarios RetroAchievements confirment la conservation des états normal/Hardcore et la reprise de la page échouée d'un import de 501 jeux au bon offset, sans application anticipée d'un résultat incomplet.

### Identité Steam et durabilité

Une réponse de session Steam ancienne ne réaffiche pas un compte après déconnexion. Deux sondes de session ne peuvent pas s'écraser dans l'ordre inverse de leur lancement. Après révocation serveur réussie, un refus de sessionStorage ne laisse pas les automatismes locaux actifs. Ces tests portent sur des réponses de session contrôlées ; aucun véritable compte n'a été connecté.

Les nouveaux contrôles `_gamingControl` sont sauvegardés dans l'enveloppe locale commune à la bibliothèque et au journal Trakt. Ils ne sont pas exportés dans le schéma portable 8. Une sauvegarde importée ne peut pas imposer ses champs internes de révision ou de file ; les champs racine préfixés par `_` sont retirés. Les erreurs de quota testées conservent l'état durable et une copie de travail exportable, sans fausse confirmation de pause.

### Régression visuelle découverte et corrigée

Le bandeau d'erreur de stockage était ajouté directement au `body`, qui sert de grille sur PC. Il devenait une colonne supplémentaire et déplaçait tout le contenu dans une colonne étroite. Le bandeau est maintenant inséré/replacé dans `#appShell`. Le scénario déclenche le refus de stockage, contrôle le brouillon et vérifie aussi l'emplacement du bandeau.

Les nouveaux écrans ont été rendus à **390, 768 et 1360 pixels**, en thèmes clair et sombre sur les cas testés, avec absence de débordement horizontal, contrôles ciblés de zones tactiles et aucune erreur JavaScript non interceptée. Les captures `tests/results/v015/sync-center-fixture-*.png` présentent explicitement des comptes/listes fictifs, pas les données de l'utilisateur. Ce contrôle n'est pas un audit exhaustif d'accessibilité ni une matrice multi-navigateurs.

## Déroulement des corrections

Une première exécution a relevé deux incompatibilités dans les tests hérités. Le test de reprise manuelle Steam a été adapté pour avancer son horloge contrôlée **au-delà du délai désormais réellement persisté**, et non pour contourner ce délai. Le scénario d'effacement en origine opaque traite maintenant le refus IndexedDB comme un **effacement incomplet signalé**, pas comme une suppression réussie. La campagne finale a rejoué ces deux scripts puis tous les autres contrôles disponibles avec succès. Le rapport d'essai initial est conservé avec un nom distinct, sans être présenté comme le résultat final.

## Ce qui n'a pas été validé

- L'outil `agent-browser` a été tenté et n'est pas installé. Chromium/Playwright a ensuite été utilisé pour les suites DOM autorisées.
- La navigation vers le serveur local a retourné **ERR_BLOCKED_BY_ADMINISTRATOR**. Ce blocage n'a pas été contourné. Trace : `tests/results/v015/fg15_origin_attempt.json`.
- Pas de véritable origine persistante, installation PWA, reprise offline/update, IndexedDB réel ou concurrence de deux véritables onglets. Les résultats DOM utilisent HTML/scripts réels mais stockage, fournisseurs et temporisations contrôlés.
- Pas d'Android SDK, compilation Compose, APK/AAB, émulateur ni appareil ; la version Android est fournie en sources, sans nouveau centre natif ni connexion Steam/RetroAchievements native.
- Pas de compte Steam, Trakt ou RetroAchievements réel utilisé, pas de requête portant une clé personnelle, pas de publication ni de modification distante de l'historique utilisateur.
- Pas de session graphique Windows/macOS, ni EXE autonome, ni audit de sécurité indépendant ou validation commerciale.

## Conditionnement

Le programme PC est reconstruit depuis les sources Java avec `--release 17`, et les trois copies Web (`web`, `web-preview`, ressources PC) sont alignées avant archivage. La Suite est proposée en RAR5 stocké et en ZIP ; PC et Web disposent de RAR5 séparés. Chaque archive est extraite par libarchive, puis comparée aux sources (chemins, octets, SHA-256 et permissions exécutables) ; les manifestes internes sont vérifiés. Le JAR extrait de l'archive PC et le serveur Web extrait font l'objet de contrôles supplémentaires listés dans le rapport externe `FranchiseGuide_V0_15_VERIFICATION_ARCHIVES.json`.

Un manifeste prouve que les fichiers livrés correspondent aux fichiers vérifiés, pas qu'ils sont sans défaut. **Le verdict commercial reste NO-GO pour une offre annoncée comme application complète validée.**
