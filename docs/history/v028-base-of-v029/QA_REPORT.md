# Rapport QA — FranchiseGuide V0.28.0

Base effectivement utilisée : **ZIP V0.27 fourni**, SHA-256 `b92b19733f0f85c2b54f1bfc90b0f6d86fc35ddc88a9734850101a12c31fd58e`. La campagne actuelle est conservée dans `tests/results/v028/available-qa.json` avec ses commandes exactes et leurs journaux. Les résultats d’anciennes versions livrés dans la Suite ne sont pas ajoutés aux résultats actuels.

## Résultat de la campagne disponible

**64 commandes exécutées, 64 réussies, aucun échec final.** Les exclusions ci-dessous empêchent d’appeler cette campagne une validation exhaustive ou un build Android complet.

| Groupe | Résultat exécuté | Portée |
|---|---:|---|
| JavaScript / Node | **1 310 tests réussis** | Domaine, transport, cache et tests disponibles ; 46 nouveaux cas ISBN/édition |
| Chromium | **1 195 assertions réussies** | Scripts, DOM et CSS réels avec fournisseurs, images, horloge et stockage contrôlés ; 55 nouveaux scénarios |
| Kotlin JVM | **834 assertions + 1 scénario franchises réussis** | Règles exécutées sur JVM ; 45 nouvelles assertions de sélection et une assertion supplémentaire de schéma V11 |
| Comparaisons JavaScript/Kotlin | **11 réussies** | ISBN/édition enregistrée, livres/éditions, détails TVmaze, fiche épisode, disponibilités, épisodes groupés, listes, bilan, franchises, aperçu d’import et calendrier |
| PC compilé | **104 contrôles HTTP + 9 contrôles de démarrage réussis** | Vrai JAR Java17 et ressources embarquées, pas simulation du serveur |
| Passerelles Java | **333 assertions réussies** | 28 Steam public, 53 SteamAuth, 184 RetroAchievements et 68 services automatiques ; amonts simulés |
| Structure | **168 contrôles réussis** | Fichiers, versions, XML, ressources et branchements ; pas des tests de comportement |
| Bootstrap | **8 contrôles réussis** | Intégrité du téléchargement/installation Gradle ; certains contrôles PowerShell sont statiques |
| Kotlin non graphique | **Compilation réussie contre signatures de test** | Modèles/clients/repository/codecs ; ni Android SDK/Compose ni parseur Android réel |

Les 1 195 assertions Chromium incluent toutes les campagnes DOM reprises depuis les versions précédentes et les 55 nouvelles. Le nombre d’assertions n’est pas un pourcentage de parité SeriesGuide, ni une garantie d’absence de défauts.

## Parcours nouveau vérifié

### ISBN sans clé et validation de l’identité

Le bouton apparaît dans Livres et dans la recherche. L’ouverture et les saisies invalides ne lancent pas de lecture. ISBN10, ISBN13 et équivalences sont contrôlés ; les produits EAN non ISBN sont refusés avant réseau. La recherche publique renvoie seulement des candidats limités. L’édition est ensuite lue séparément et doit déclarer le bon identifiant, le rattachement à l’œuvre et l’ISBN équivalent.

Les mauvaises réponses (autre édition, autre œuvre, autre ISBN, corps trop volumineux), références manquantes, résultats vides, pagination limitée, annulation, navigation et délai429 ont été exercés. Une réponse vide n’est pas présentée comme une preuve d’inexistence du livre. Les appels sont limités au serveur Open Library attendu, en GET sans credentials applicatifs.

### Association et conservation des données

La sélection explicite retrouve l’œuvre Open Library déjà ajoutée, sans rapprochement par titre. Les doublons d’identité sont refusés, de même que la réactivation implicite d’une fiche retirée. Une nouvelle œuvre est enrichie avant son ajout et reçoit ensuite la référence vérifiée.

La référence est sauvegardée et réaffichée : IDs, titre d’édition, ISBN valides, éditeur/format/langue, nombre de pages connu ou inconnu, couverture numérique, dates de consultation et de choix. Le titre/identité principaux de l’œuvre restent indépendants. Une actualisation de l’œuvre conserve cette sélection.

L’option pages est décochée par défaut. Son application ne crée pas une lecture terminée et n’efface pas l’historique. Un total incompatible ou une autre unité n’est pas imposé. Retirer la référence conserve livre, pages et anciennes fins. Les changements récents dans les champs ciblés rendent la confirmation obsolète ; les notes étrangères à l’action restent conservées.

Les scénarios exécutent un refus d’écriture : pas de faux succès, retour à l’état sauvegardé et présence de la référence proposée dans le brouillon exportable. La file Trakt n’est pas alimentée par ces actions. Le format portable V11 transporte la référence et écarte les champs supplémentaires non prévus ; une référence d’une autre œuvre/type est refusée.

### Affichage

Le parcours et le dialogue ont été capturés à **390, 768 et 1 360 pixels**, avec contrôle de largeur et fermeture Échap. Images : fixtures publiques contrôlées, pas des couvertures réellement obtenues auprès d’un fournisseur. Aucun message JavaScript non intercepté dans les 55 nouveaux scénarios.

Captures de la dernière campagne : `fixture-isbn-*.png` et `fixture-selection-*.png`. Une inspection visuelle a également vérifié le dialogue à390px après retrait du bandeau de stockage du scénario précédent.

## Défaut confirmé dans la base

Le validateur extrait du véritable ZIP V0.27 retournait `4006381333931` comme ISBN valide : il s’agit d’un EAN produit au chiffre de contrôle valide, mais sans préfixe ISBN978/979. Le nouveau validateur retourne null et le formulaire le refuse sans requête. La trace de comparaison est `confirmed-base-defect.json`.

Le titre renvoyé par la fiche d’œuvre Open Library était auparavant ignoré lors de l’enrichissement Web. Il est désormais validé et pris en compte dans cette lecture, sans remplacer les champs personnels. Les dates propres aux pages d’éditions sont aussi conservées : charger la page suivante ne rajeunit pas les entrées déjà consultées.

Une première exécution intermédiaire a échoué parce qu’un ancien test Kotlin exigeait encore le rejet du schéma11 devenu courant. Son seuil futur est passé à12, les anciens formats valides sont restés testés, puis **la totalité des 64 commandes a été rejouée avec succès**. La trace intermédiaire reste distincte dans `earlier-attempt/` ; ce cas n’est pas présenté comme un bug de production.

## Tests non exécutés et obstacles réellement observés

- `tests/retro-orchestration-v014.test.cjs` : dépendance jsdom indisponible.
- `tests/web-dom-v014.test.cjs` : jsdom et fake-indexeddb indisponibles.
- `GamingCodecRetroTest` et `MediaIdentityCodecTest` contre **le vrai org.json** : `FG_JSON_JAR` absent. Les sources incluent les nouveaux cas de référence d’édition, mais ils ne sont pas comptés comme exécutés. Le typecheck sur signatures reste un contrôle distinct.
- La récupération des dépendances supplémentaires a échoué en résolution DNS. Aucun ancien résultat de ces suites n’est repris comme un résultat V0.28.
- Le serveur HTTP local a répondu200, mais Chromium a bloqué la navigation vers l’origine avec **ERR_BLOCKED_BY_ADMINISTRATOR**. Trace : `origin-check.json`. Les essais d’interface utilisent donc set_content et des fixtures ; installation PWA, persistance réelle, Web Locks entre vraies fenêtres et hors ligne ne sont pas validés.
- L’appel Open Library réel a échoué en résolution DNS (curl6), consigné dans `public-provider-network.txt`. La documentation publique a été consultée séparément ; cela ne valide pas les réponses applicatives réelles.
- Aucun compte réel Steam/Trakt/RetroAchievements ni accès éditeur privé utilisé. Aucun succès, avis, historique ou fichier personnel distant n’a été modifié par les essais applicatifs.
- Pas de SDK/Compose build, d’APK/AAB ou d’appareil Android. Le nouveau parcours natif reste limité à la recherche ISBN dans une œuvre déjà ajoutée et n’est pas déclaré équivalent au parcours global Web.
- Le JAR a été compilé/testé sous Linux en bytecode Java17. Pas de qualification de fenêtre graphique Windows/macOS, de signature de distribution, d’EXE autonome ou de certification commerciale.

## Conditionnement et intégrité

Les quatre archives (Suite RAR/ZIP, PC RAR, Web RAR) sont produites à partir de la même version. Chaque distribution reçoit un manifeste SHA-256 ; les archives sont extraites puis comparées intégralement aux dossiers préparés (chemins, octets, empreintes et permissions exécutables). Le RAR est un véritable RAR4 en mode stockage, pas un ZIP renommé.

Les **62 fichiers du moteur Web canonique** sont identiques à `web-preview/`, à `desktop/resources/web/` et aux ressources du JAR. Le JAR extrait doit repasser `--probe`, puis le serveur Web extrait est soumis au contrôle HTTP dédié. Les résultats de conditionnement, tailles, nombres de fichiers et empreintes sont consignés dans `FranchiseGuide_V0_28_VERIFICATION_ARCHIVES.json` et `FranchiseGuide_V0_28_SHA256.txt` après leur exécution. Ils ne remplacent pas les tests de fonctionnement.

Le dossier Google Drive est une destination de livraison demandée par l’utilisateur, pas une synchronisation intégrée des données de l’application. L’état des envois est confirmé par lecture des métadonnées après transfert ; aucun partage public n’est demandé.
