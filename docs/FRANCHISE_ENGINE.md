# Mes univers : franchises, ordre et suggestions — V0.19

## 1. Créer sans inventer d’œuvres

Ouvrir **Franchises → Créer mon univers**, ou **Plus → Nouvelle franchise**. Les deux actions Web utilisent le même formulaire. Le nom est obligatoire (200 caractères maximum), les alias facultatifs. Sur Web/PC, une description peut être renseignée. Les noms identiques après normalisation sont refusés ; noms non latins et emoji différents restent distincts. Un univers personnel vide reste visible.

Les 32 définitions intégrées sont alignées entre Web et Android, mais ne sont pas un catalogue complet et ne sont pas modifiables directement. Créer un univers personnel permet de choisir son libellé et son ordre. Les alias font uniquement une détection par mots dans le titre : ils ne garantissent pas le canon. Ni un éditeur ni un créateur seul ne déclenche ce regroupement.

## 2. Classer une fiche

Dans la fiche, choisir la franchise désirée. Chaque fiche possède un classement principal. Le choix manuel reste prioritaire face au nom, à des alias concurrents et à une collection TMDb récupérée ultérieurement.

**Aucune** signifie « ne pas classer cette fiche », pas « redeviner automatiquement au prochain affichage ». **Réactiver la détection par titre** est une action séparée. Les notes, visionnages, progression, listes, succès et comptes ne changent pas pendant ces actions locales.

Les œuvres ajoutées mais masquées gardent leur association. Un titre retiré de la bibliothèque ne devient pas une suggestion par effet de bord. Une même référence fournisseur présente en bibliothèque et dans les suggestions ne compte pas deux fois ; deux fiches locales distinctes sans identifiant fournisseur commun ne sont pas fusionnées parce que leurs titres et années se ressemblent.

## 3. Univers importé sans définition

Un fichier peut référencer un identifiant de franchise sans transporter sa définition. L’interface le montre comme **Référence importée sans définition** au lieu de cacher ses titres. Ouvrir **Restaurer la définition** permet de lui donner un nom et des alias explicites en conservant son identifiant. Son ordre personnel devient éditable après cette restauration.

## 4. Mon ordre de lecture, visionnage ou jeu

Dans une définition personnelle enregistrée, **Position dans mon ordre** choisit une position parmi toutes les références de l’univers, y compris les suggestions et les lignes masquées par les filtres. C’est l’identifiant du titre qui est mémorisé, pas son seul nom. Les nouveaux titres sans rang viennent après les références classées, par année connue puis titre. Une année absente est placée après les années connues.

Les tris **Année de sortie**, **Titre**, **Catégorie puis année** sont des vues, pas une chronologie officielle. L’ordre personnel est conservé dans `customFranchises.sortOrder`, déjà prévu par le format portable V8. Il ne modifie ni les playlists ni les listes d’un compte distant.

## 5. Rechercher puis retenir des suggestions

Ouvrir **Rechercher des suggestions**. Choisir une catégorie et saisir la requête ; cliquer sur **Rechercher**. Les clés catalogue nécessaires doivent être configurées et le réseau disponible. Une requête de film ne se transforme pas silencieusement en recherche de livre ou de jeu.

Les résultats restent temporaires tant que **Garder comme suggestion** n’est pas choisi. Une recherche par nom peut retourner des homonymes ou des œuvres étrangères à la franchise : l’utilisateur valide la pertinence. La suggestion retenue est grisée, n’ajoute pas d’épisode et ne marque rien comme vu ou possédé.

Un titre déjà ajouté est signalé ; le classer depuis sa fiche, sans le cloner. Une suggestion déjà connue n’est pas dupliquée dans un autre univers. Pour suivre réellement un candidat, utiliser ensuite **Ajouter** depuis le gestionnaire ou la fiche : le parcours catalogue complet habituel s’applique. Sur une série, une saison attendue illisible ne devient pas un ajout complet fictif.

L’échec d’une page conserve les résultats précédents ; **Réessayer cette page** ne saute pas à la suivante. Fermer le dialogue, changer la requête/la catégorie/la bibliothèque/la définition invalide les anciens résultats. Aucun résultat n’est appliqué tardivement à un autre univers. Une actualisation ultérieure n’introduit pas automatiquement les films d’une collection distante dans un univers personnel.

## 6. Les compteurs et filtres

Le Web affiche d’abord 60 univers puis 50 références dans un univers ; les boutons de suite chargent davantage de lignes déjà connues. La recherche et les filtres portent sur toute la sélection, pas seulement sur ces premières lignes.

**Ajoutés** compte les références locales actives ; **suggestions connues** compte les candidats retenus hors bibliothèque. Un groupe affichant 4 ajoutés et 1 suggestion contient cinq références connues ici. Il ne prouve pas qu’il existe seulement cinq œuvres dans cette franchise. Une suggestion absente de la bibliothèque ou du catalogue n’est pas fabriquée pour compléter un compteur.

Les filtres Web de la liste générale comprennent le nom, la catégorie et personnel/importé ou suggestions présentes. La liste native source offre recherche et filtre personnel ; elle n’a pas la même richesse de filtres généraux. À l’intérieur d’un univers, les deux sources proposent catégorie, ajoutés/suggérés et ordre.

## 7. Renommer, écarter, supprimer

Renommer l’univers actualise son libellé, pas ses identifiants ni le suivi des titres. **Écarter cette suggestion** ne supprime que la référence suggérée, pas une œuvre ajoutée ou un épisode.

**Supprimer le classement** demande confirmation et une copie locale de sécurité réussie. Les titres, épisodes, historiques, notes, listes de succès et suggestions sont conservés ; les références détachées reçoivent un choix manuel sans franchise. Les suggestions détachées ne s’affichent plus dans l’univers supprimé, mais restent dans le stock de suggestions et dans l’export. Aucune suppression distante.

Sur Web/PC, les formulaires destructifs et d’ordre vérifient la génération et le contenu local avant application. Si la bibliothèque a changé, recommencer depuis une vue actualisée. Un refus de stockage ne produit pas de succès ; les mêmes mécanismes de récupération/brouillon que les versions précédentes s’appliquent. Android utilise les règles pures et une sauvegarde gardée par le verrou de synchro ; pas de validation de stockage réel effectuée dans cette livraison.

## 8. Sauvegarde et limites

Exporter régulièrement un JSON portable V8 hors du profil navigateur. Il conserve définitions, alias, description, ordre et classement manuel. Les sélections, requêtes de recherche temporaires, pages chargées et formulaires ouverts ne sont pas exportés. Trakt ne synchronise pas ces classements ; transfert interappareil par export/import, pas cloud général.

Les recherches et tests catalogue de cette campagne sont simulés. Aucun catalogue fournisseur complet, chronologie canonique, collecte exhaustive tous médias, nouveau service de connexion ou découverte automatique permanente n’est livré. Les écrans Android sont en sources ; les règles et signatures ne remplacent ni l’APK ni les essais appareil.
