# Note de sécurité V0.24

Les détails d’épisode et leur distribution restent en mémoire. Les snapshots ne font pas partie du JSON portable V10. Seuls les deux champs personnels explicitement enregistrés sont appliqués à l’état actuel ; l’historique, la collection et les champs non ciblés ne sont pas remplacés.

Le score chiffré d’un épisode TMDb identifié peut rejoindre la file Trakt existante. Le texte privé n’est pas envoyé comme commentaire. Changement de compte, opération Trakt active, identité déplacée, valeur ciblée modifiée ou formulaire expiré conduisent à un refus. Les brouillons ne sont pas autoexportés lors d’une fermeture ; un refus de stockage utilise la récupération de copie de travail déjà présente.

Le GET de détail possède un plafond de corps2 Mio et un décodage UTF‑8 strict. Les noms et biographies/rôles ne sont pas interprétés comme HTML ; les chemins images acceptés se limitent au CDN TMDb construit par l’application. La source externe utilise les identifiants validés de série/saison/épisode. Le cache global sépare désormais les politiques de lecture et ne mémorise pas les modes no-store / reload / no-cache.

Ces changements ne font pas du stockage local un coffre-fort chiffré, ni du logiciel un backend public multiutilisateur. Les essais network sont contrôlés ; pas de clé ou compte réel utilisé, pas de buildAndroid SDK validé.

---

# Sécurité de la sélection d’épisodes V0.22

Les plans sont uniquement mémoire/session et reconstruits avant application. Les champs modifiables sont explicitement listés, jamais un patch arbitraire chargé d’un JSON. Une confirmation vise toutes les lignes sélectionnées, même celles devenues hors filtre. Une copie locale de récupération est requise ; elle reste dans le même profil et n’est pas un backup externe.

L’effacement de visionnages est distinct du statut ignoré. Les dates/compteurs concernés sont annoncés, les notes personnelles restent conservées. La synchronisation Trakt peut produire des retraits après cet effacement, mais ignorer seul ne produit pas une demande de suppression d’historique. Aucun mécanisme n’annule automatiquement une requête distante déjà exécutée.

Le Web vérifie génération de bibliothèque/session et disponibilité de la synchronisation avant d’appliquer. Le natif utilise une transformation préparée et un verrou ; les courses runtime Android n’ont pas été exécutées ici. Les plans expirent en dix minutes. Pas de sauvegarde ni transfert de sélection/confirmation dans un export portable.

---

# Données et sécurité — état V0.20

Le bilan V0.20 repose sur la Suite V0.19 disponible. Les règles antérieures conservées ci-dessous ne constituent pas une certification de sécurité, de confidentialité ou une validation contractuelle actuelle.

## Progression, statistiques et exports

Les fins historiques et leur compteur sont séparés du parcours jeu/livre courant. Les notes, plateforme personnelle, édition et minutes réellement saisies sont des données utilisateur. Le nouveau schéma portableV9 conserve ces champs et reste sans clés, jetons ou journal d’exécution interne. Une ancienne version ne doit pas réécrire les données après migration.

L’export numérique de bilan omet titres, notes et identifiants/libellés de profils. Ses comptes, dates de période et préférences peuvent néanmoins révéler un usage personnel : il n’est pas présenté comme juridiquement anonyme. L’historique détaillé JSON/CSV contient des titres et des dates connues. Ne pas le partager comme un simple journal technique. Le CSV neutralise les cellules interprétables comme formules, mais l’import réel dans des tableurs reste à vérifier.

## Entretien catalogue

`_catalogMaintenance` est un état local d’exécution, enregistré avec la bibliothèque et exclu des exports portables/snapshots transférables. Il ne prouve ni une authentification ni une autorisation de fournisseur. Les identifiants catalogue précis sont requis ; aucune fusion automatique par ressemblance de titre n’est ajoutée. Les délais429 appliqués par fournisseur ne garantissent pas le respect de tout contrat/quota externe.

L’actualisation prépare les réponses avant de modifier l’état, vérifie l’identité/génération et préserve le suivi personnel. L’annulation coupe le lot ; une réponse obsolète n’a pas le droit de recréer une fiche. Les erreurs et timeouts deviennent des états différés plutôt qu’un succès fictif. Le moteur reste en localStorage : quotas et écritures synchrones demeurent, même si une sauvegarde refusée conserve un brouillon exportable.

Le nouveau centre est Web/PC. Android n’a pas reçu une copie intégrale de ses politiques de reprise dans cette version. Aucun stockage Android réel, service WorkManager ou concurrence d’appareil n’a été validé avec ces modifications.

---

# Données et sécurité — FranchiseGuide V0.14

Ce document décrit les protections et limites du code livré. Les serveurs PC et Web sont des serveurs personnels sur **127.0.0.1**. Aucun service public multi-utilisateur ni audit indépendant de sécurité n’est fourni.

## Bibliothèque, concurrence et récupération

Sur Web/PC, bibliothèque et file d’envoi Trakt sont enregistrées dans une même entrée localStorage, avec révision. Web Locks fournit un éditeur unique lorsque le navigateur le prend en charge. Le contrôle de révision seul n’est pas un verrou universel : conserver une seule fenêtre éditrice sur les navigateurs qui n’offrent pas ce mécanisme.

Un refus d’écriture conserve l’entrée précédemment enregistrée et une copie de travail exportable. La récupération supplémentaire IndexedDB conserve au plus cinq copies portables. Insertion et rotation utilisent une transaction commune. Ce stockage supplémentaire ne supprime pas les limites de quota et ne remplace pas un JSON conservé indépendamment du navigateur.

Une bibliothèque illisible ou d’un schéma futur n’est pas remplacée silencieusement par une bibliothèque vide. Le mode de récupération protège l’original et propose un export brut. Les imports, restaurations et fusions passent par les contrôles et copies de sécurité prévus ; voir DATA_INTEGRITY.md.

## Connexions et références de profils

| Mécanisme | Signification et stockage |
|---|---|
| Steam OpenID | Identité du compte vérifiée côté serveur personnel, puis session locale propre au navigateur. La connexion ne donne pas accès aux données Steam privées. |
| Ancienne référence Steam saisie manuellement | Référence publique ; elle n’est pas une preuve d’authentification. |
| RetroAchievements | Association d’un profil public par pseudo, puis ULID stable. Elle n’authentifie pas le propriétaire et ne crée pas de session de connexion RA. |
| PSN / PSNProfiles, Xbox, Epic, GOG | Références ou relevés importés ; aucun nouveau connecteur automatique livré. |
| Trakt | Connexion existante par code appareil et synchronisation selon les préférences. |

La clé Steam reste dans **FG_STEAM_API_KEY** et la clé RetroAchievements dans **FG_RETRO_API_KEY**, côté serveur. Elles ne sont pas saisies dans les formulaires FranchiseGuide et ne sont pas incluses dans les sauvegardes. Les mots de passe et Steam Guard restent sur le site Steam ; aucun cookie Sony ou jeton NPSSO n’est demandé.

La session Steam utilise un cookie HttpOnly, SameSite=Lax, avec une durée maximale de sept jours. Le fichier serveur conserve l’empreinte SHA-256 du jeton, l’identité et l’expiration, sans le jeton brut. Il se trouve dans le profil système ; ces fichiers ne constituent pas une protection contre un utilisateur ou un logiciel ayant accès à ce profil. Voir STEAM_SIGN_IN.md.

## Envois et réponses incertaines

Un import ou une restauration suspend les anciennes opérations Trakt. Une opération dont la réception est incertaine reste incertaine : lever une suspension ne constitue pas une confirmation distante. Les envois d’historique et publications d’avis ne sont pas répétés automatiquement après une réponse ambiguë. Les brouillons Web sont sauvegardés avant publication ; un refus de stockage bloque l’envoi. Il n’existe pas de garantie générale d’envoi exactement une fois à travers une panne réseau.

Steam et RetroAchievements sont utilisés en lecture seule. Les notes, guides, épingles et checklists de succès restent locaux. Les automatismes gaming demandent un choix explicite et fonctionnent pendant que l’application est ouverte, visible et connectée. Restaurer un fichier portable les désactive ; un import ne crée jamais une session authentifiée.

## Secrets, exports et effacement

L’export portable **V8** inclut les médias, suivis, profils gaming, éditions, identifiants, dates et notes. Il exclut les clés de configuration, jetons, cookies de session, file Trakt et brouillons d’avis. Un export sans clé reste personnel et n’est pas anonyme.

L’export brut de récupération préserve les données originales sans nettoyage et peut contenir des secrets. Les clés et jetons Web restent sur l’origine, séparés des exports mais non chiffrés contre les scripts de cette origine. Un secret Trakt partagé ne doit pas être placé dans les fichiers d’un site statique distribué.

L’effacement local demande la saisie de **EFFACER** et une confirmation. Il est refusé pendant une écriture Trakt active. Lorsque la session Steam locale est concernée, sa révocation précède l’effacement ; une erreur à cette étape arrête la suite. Seules les entrées et caches FranchiseGuide sont ciblés.

Les étapes d’effacement ne forment pas une transaction unique entre serveur, localStorage, Cache Storage et IndexedDB. Une erreur peut survenir après certaines suppressions. En particulier, un échec de suppression IndexedDB après effacement du stockage principal et des caches produit un message d’**effacement incomplet** et un bouton pour recharger avant de reprendre. La réussite est annoncée après la résolution de cette suppression lorsqu’elle est disponible. Les données déjà effacées ne sont pas restaurées fictivement.

Les fichiers JSON exportés, les variables d’environnement, les autres sessions du serveur et les comptes des fournisseurs ne sont pas supprimés par cet effacement navigateur.

## Transport et limites de validation

Les passerelles contrôlent les routes, paramètres, hôtes et origines prévus ; les appels fournisseurs ont des délais et limites de taille. RetroAchievements ajoute des limites de concurrence, débit et cache. Les réponses destinées au navigateur ne sont pas des caches publics partagés. Les données externes sont validées avant leur application à la bibliothèque.

Le Web fournit des règles d’échappement, des restrictions d’URL, une politique CSP et des en-têtes HTTP. Leur présence et plusieurs comportements sont testés, sans garantie générale contre les injections. Le serveur Java utilise un pool de huit threads ; sa file d’attente n’est pas présentée comme bornée. Aucun audit complet de charge hostile ou de toutes les dépendances transitives n’est revendiqué.

Pour la V0.14, les services sont simulés, les parcours DOM utilisent jsdom et IndexedDB est simulé. Les scénarios Chromium historiques restent conservés sans être présentés comme des essais exécutés sur cette livraison. Les sources et codecs Android sont contrôlés sur JVM, avec vérification du code non graphique contre des signatures de test ; aucun APK/AAB, build SDK complet, essai Compose ou stockage sur téléphone n’est validé. Voir QA_REPORT.md.

## V0.15 : contrôles réseau internes et déconnexion

Les pauses/délais/lots gaming sont stockés sous `_gamingControl` dans l’enveloppe principale. Aucun identifiant secret ni bearer n’y est ajouté. Ils sont exclus du portableV8, et les champs internes `_…` fournis par un import ne sont pas repris. Une annulation de lecture ne réinitialise pas une limite fournisseur connue. Les lectures restent sans effet distant sur les trophées ou le suivi Trakt.

Un compteur de générations de requêtes protège le résultat de la vérification de session Steam : une réponse plus ancienne ou obtenue après déconnexion ne peut plus rétablir un compte connecté dans l’interface. Un échec de nettoyage du marqueur sessionStorage après révocation est un avertissement distinct ; il n’empêche plus de suspendre les automatismes du profil. Les tests de cette protection utilisent des sessions et réponses contrôlées, pas un aller-retour Steam réel.

Une alerte de quota est désormais placée dans le shell de l’application, et non comme une colonne supplémentaire du layout PC. Le brouillon exportable et le refus d’écriture conservent leur comportement. La nouvelle vue est contrôlée sur fixtures aux largeurs390/768/1360, sans affirmer une validation d’accessibilité exhaustive.

## Ajouts V0.16

Un fichier calendrier peut divulguer les titres au calendrier dans lequel il est importé. PRIVATE est un attribut indicatif, pas un chiffrement ni une permission garantie. Le fichier ne contient pas les notes privées, comptes ou jetons et n'effectue aucune connexion de calendrier. Les dates de visionnage personnelles restent dans la sauvegarde JSON et peuvent être envoyées à Trakt si l'utilisateur l'a configuré ; elles ne figurent pas dans l'export d'agenda de sorties. Le dialogue ajoute une lecture, pas une correction silencieuse d'événement distant. Un accusé vide reste incertain ; la file n'est pas un mécanisme distribué exactly-once.

## V0.18 : aperçu, lecture et application des imports

Le plan d’import est en mémoire et n’est ni exporté dans le portable ni exécuté au rechargement. Les collections/réglages de départ, la révision et la génération sont recontrôlés au clic. Les profils/sets/réglages internes d’un fichier ne servent pas à fabriquer une connexion. Une copie de sécurité de l’état courant précède la suspension des envois et l’écriture. La copie peut subsister après un échec du commit principal. Le rapport de comparaison exporté n’a pas de notes/profils détaillés mais ses noms de fichiers peuvent être personnels.

Les fichiers restent traités en mémoire ;32Mio/fichier64Mio total et dix fichiers sont des garde-fous, pas une preuve de résistance à toute charge hostile ni une garantie de réactivité. Les tests de caractères malveillants vérifient l’échappement du nouveau tableau, pas un audit de sécurité indépendant.

Sur Android, les préférences publiques retenues sont désormais écrites avec l’état/file Trakt sous public_preferences_v18 puis rechargées au lancement. Le choix d’import ne remplace pas les identifiants privés. La lecture asynchrone ne s’accompagne d’aucune validation du cycle d’autorisation des URI sur appareil. Les règles de comparaison/lecture sont testées sur JVM ; SharedPreferences et les services Android restent des signatures de test.

## V0.19 : classement principal et univers

Créer, renommer, ordonner, affecter ou supprimer une franchise ne produit pas d’opération fournisseur. Supprimer une définition détache les références sous un choix manuel nul, sans effacer les titres, épisodes, notes, historique ou succès. Les recherches réseau sont explicites et une suggestion n’est enregistrée qu’après clic. Les nouveaux contrôles réutilisent les gardes de génération, copie locale et écriture atomique disponibles. Le formulaire Web vérifie le snapshot avant application ; le repository Android utilise son verrou de synchro et conserve les listes précédentes si save échoue. Les limites de validation restent celles du rapport V0.19, pas des anciennes campagnes.

Les noms/alias/description/ordre peuvent contenir des informations personnelles dans le JSON. Leur échappement est testé, mais ce n’est pas un chiffrement ni un audit indépendant. Aucun jeton de recherche n’est ajouté au format portable. Le nom du groupe ne garantit pas la légitimité d’une relation canonique : la sélection appartient à l’utilisateur.


## V0.21 — listes locales

Les actions de liste n’ont aucun endpoint distant. L’écriture utilise le même enregistrement atomique principal, mais ne génère pas de différence de suivi Trakt. Définition/membres sont validés avant application contre l’état courant. Suppression et changements de membres exigent une copie de sécurité réussie ; cette copie reste locale. Un refus d’écriture principale restaure le dernier état sauvegardé et conserve le brouillon exportable selon les règles existantes.

Les titres retirés et suggérés ne deviennent pas ajoutés par une sélection/duplication. Supprimer la liste les détache également pour éviter des références orphelines. Aucun historique, compte, note ou succès n’est supprimé. L’ordre personnel contient des identifiants de titres, pas des tokens. Le format portable V10 transporte cet ordre, pas les aperçus de session.

Le rapport JSON/CSV de liste contient des noms de listes/titres et des filtres saisis : il est personnel malgré l’exclusion des notes privées, descriptions, profils et credentials. Le CSV échappe les guillemets et neutralise les amorces de formules ; les applications tableur externes ne sont pas testées. Limite de10k résultats explicite ; pas de troncature d’export silencieuse.

## V0.23 — consultations de disponibilité

Les nouveaux relevés sont en mémoire uniquement et n’entrent pas dans le JSON portable. Pays, type et saison sont liés à la réponse ; une ancienne réponse n’est pas réutilisée pour un autre pays. Navigation, nouvelle clé, changement d’identité et remplacement de bibliothèque invalident les lectures correspondantes. Le remplacement/effacement de bibliothèque purge également le cache du panneau. Cela ne supprime pas les champs historiques providers stockés dans les fiches ; ceux-ci sont signalés comme anciens, sans attribution de pays/date rétrospective.

Les lectures n’envoient pas d’état personnel à Trakt ou aux comptes gaming et n’appellent aucun POST. Les corps sont bornés avant parsing, décodés strictement ; pas d’exécution HTML de noms/services. Liens externes limités à la page TMDb reçue du bon type/ID, HTTPS, paramètres restreints au pays. Même un lien sûr ouvre un site externe qui reçoit une navigation utilisateur ; ce n’est pas un lecteur embarqué ni une garantie de prix/entitlement. Logos à hôte image.tmdb.org uniquement ; une image distante reste une requête réseau.

Les secrets API existants ne sont pas une nouvelle session de streaming. Le client TMDb Web utilise la clé personnelle configurée et les nouvelles erreurs évitent de réafficher cette clé ou le corps brut. La compilation Kotlin sur signatures ne constitue pas une vérification du TLS, du parseur ou des sockets sur Android réel.

## V0.26 : détails publics TVmaze

Les nouvelles lectures sont des GET fixes sans credentials. Les noms/identifiants des personnes TVmaze ne sont pas ouverts comme des IDs TMDb. Les réponses nécessitent un show parent cohérent, des tableaux invités/équipe valides et une taille bornée. Les profils temporaires et détails d’épisodes ne modifient pas la bibliothèque et ne sont pas sérialisés ; les contrôles de brouillon/compte/époque de l’éditeur local restent distincts. Les caches sont effacés au remplacement ou à l’effacement de la bibliothèque.

Les dates publiques à suffixe incohérent sont refusées. Les images publiques n’acceptent pas un port arbitraire ni des identifiants dans l’URL. Les profils et les séries ne sont pas rapprochés à un autre fournisseur par titre ou numéro. La correction des épisodes préserve l’historique d’un ancien ID plutôt que de le donner à une nouvelle entrée occupant sa position.

## V0.29 — générations réseau et réimports

La déconnexion Trakt invalide les appels/renouvellements/pages du compte précédent, y compris leurs continuations asynchrones. Une ancienne réponse ne peut pas être réessayée avec de nouveaux credentials ni écrire dans la nouvelle file. Un POST dont l’issue est incertaine reste à vérifier : abort côté client n’est pas une révocation à distance. La recherche retire immédiatement les requêtes encore dans la file et invalide les réponses d’une ancienne génération de texte/catégorie.

Les fusions au réimport conservent les dates connues avant ajustement du compteur et refusent les identités fournisseur contradictoires. Le diagnostic de doublons est borné et signale les résultats partiels. Ces protections ne restituent pas une date déjà absente d’une sauvegarde et ne rendent pas localStorage chiffré ou illimité.

## V0.31 — préparations d'accès

Un candidat non sélectionné peut subsister en clair après échec du commit principal. Le format Web garde au plus deux ensembles ; la prochaine préparation écarte les anciens slots inutiles. Ce n'est ni du chiffrement ni une transaction atomique multi-stockages. Les exports portables excluent ces accès/références ; le brut de récupération peut rester privé. La liaison d'application bloque l'usage d'un ancien token non nettoyé après changement. Détails et preuves dans REGLAGES_ET_REPRISE.md et QA_REPORT.md.

## V0.35 — check-ins, rappel de sauvegarde et effacement

Une session de check-in contient compte, référence d’application, identifiants TMDb/local, dates et état. Elle est hors export portable et peut rester localement après une réponse distante incertaine afin d’éviter un nouvel envoi aveugle. Après la durée estimée, l’historique distant est lu avant toute écriture supplémentaire. Les tests utilisent des réponses fictives, pas un compte réel.

Le registre de sauvegarde externe ne contient pas la bibliothèque. Il mémorise seulement la proposition, la confirmation déclarative et le report. Une proposition de téléchargement ne devient jamais automatiquement « sauvegarde vérifiée ».

L’effacement Web/PC vise les stockages FranchiseGuide de l’origine et signale toute zone inaccessible. Il ne supprime ni compte, historique distant, fichiers téléchargés ou secrets configurés côté serveur. Android n’a pas d’effacement complet équivalent validé dans cette passe.
