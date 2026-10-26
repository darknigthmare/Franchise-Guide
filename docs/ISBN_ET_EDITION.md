# ISBN et édition retenue — FranchiseGuide 0.28.0

## Trouver un livre sans clé sur Web/PC

Ouvrir **Livres → Rechercher un ISBN · sans clé**, ou **Livre par ISBN · sans clé** dans la recherche. Saisir les caractères imprimés, sans compte développeur : ISBN-10 ou ISBN-13, avec espaces/tirets et préfixe ISBN éventuels. L’ISBN-10 est normalisé en son équivalent ISBN-13. Un ISBN-13 doit commencer par 978 ou 979 et avoir un chiffre de contrôle valide.

La V0.27 acceptait à tort un EAN produit tel que `4006381333931` comme un ISBN. Ce cas est reproduit et corrigé. Le contrôle reste arithmétique : il ne garantit pas que l’ISBN a été réellement attribué.

**Rechercher sans clé** demande au moteur public Open Library jusqu’à cinq œuvres candidates. L’application ne prétend pas qu’un résultat vide signifie que le livre n’existe pas, ni que cette liste est exhaustive. **Vérifier cette édition** relit ensuite la fiche d’édition par son identifiant, confirme son lien vers l’œuvre et exige l’ISBN demandé ou son équivalent ISBN-10/13. Un titre ressemblant ne suffit pas.

Le code n’utilise pas de scanner caméra, n’obtient pas d’accès privé et ne fait pas de collecte en masse. Les requêtes sont explicitement déclenchées par l’utilisateur, réutilisent la file de lectures Open Library (espacement1050ms, maximum huit en attente), les réponses bornées à2Mio, l’UTF-8 strict et le délai429. Une nouvelle tentative manuelle ne contourne pas ce délai.

## Ajouter ou retrouver l’œuvre

Après vérification, **Ajouter / retrouver l’œuvre et retenir cette édition** ouvre la confirmation. Une œuvre déjà ajoutée est retrouvée par son identifiant exact. Elle n’est pas dupliquée et son suivi reste conservé. Plusieurs fiches de la même œuvre imposent d’abord de résoudre le doublon ; une fiche retirée n’est pas réactivée silencieusement.

Pour un livre absent, sa fiche complète d’œuvre est récupérée avant l’ajout local et la référence choisie sont enregistrés. Une réponse arrivée après un changement de bibliothèque, de navigation ou une annulation est rejetée. Annuler le dialogue pendant une lecture demande l’annulation réseau ; la réponse ne doit pas créer tardivement le livre.

L’identité principale reste **celle de l’œuvre**, pas celle d’un exemplaire. Le système n’est pas un inventaire de plusieurs exemplaires physiques indépendants. Aucune propriété, prix, disponibilité d’emprunt, licence ou droit de lecture n’est inféré.

## Retenir une édition depuis une fiche existante

Dans la fiche Open Library : **Éditions et nombre de pages · sans clé → Retenir cette édition**. Les éditions sont toujours consultées par30, jusqu’à300, avec les filtres de la V0.27. La nouvelle sélection enregistre une référence unique pour cette œuvre : identifiants, titre de l’édition, éditeurs, langues, format, publication déclarée, pages connues ou inconnues, ISBN valides, identifiant de couverture et dates de consultation/choix.

Le bloc **Mon édition** apparaît dans la fiche après confirmation. La date du relevé est distincte de la date de choix ; ce n’est pas une consultation actuelle. Charger une deuxième page ne rajeunit pas la première. Une actualisation de l’œuvre conserve la référence retenue et le suivi personnel. Le titre principal de l’œuvre peut être actualisé depuis sa source, mais une simple sélection ne le remplace pas par celui de l’édition.

### Pages : une option distincte

**Utiliser aussi ces pages comme total personnel** est décoché par défaut sur Web/PC. Sans cette option, seul le choix de référence change : ni total, avancement, unité, statut, notes, format personnel ou anciennes fins ne sont modifiés.

Avec l’option, seul le total change en plus de la référence. Il faut que le suivi soit déjà en pages, que le nombre soit positif et qu’il ne soit pas inférieur à l’avancement. Un total égal à l’avancement ne termine pas automatiquement une lecture. Une édition aux pages inconnues ou un suivi en chapitres peuvent conserver la référence sans conversion de progression.

**Oublier seulement cette référence** retire le choix, pas le livre ni un total précédemment appliqué. L’ancienne action **Utiliser ce total de pages** reste disponible séparément.

## Confirmation, sauvegarde et conflits

Une confirmation expire après dix minutes. Elle vérifie de nouveau l’identité, la référence en place, la bibliothèque et, lorsqu’un total est appliqué, les champs de progression concernés. Un choix concurrent n’est pas écrasé. Une note personnelle modifiée entre-temps reste conservée si elle ne fait pas partie de l’action.

Une copie locale de sécurité doit réussir avant l’écriture. Le refus d’écriture principale laisse l’état sauvegardé inchangé et propose le brouillon exportable via le mécanisme de récupération existant. Une synchronisation Trakt active bloque la mutation. Le choix ou l’effacement de référence n’envoie rien à Trakt.

Le format portable est **V11**, lisible par la V0.28 avec les anciennes versionsV1–V10. Les références sont validées et normalisées lors de l’import/export. Une référence attachée à une autre œuvre/catégorie ou contenant des ISBN invalides est refusée ; les propriétés arbitraires supplémentaires ne sont pas conservées. Les fusions refusent des références concurrentes et des transferts vers une autre source sans résolution explicite.

## Android et limites

Les sources Android transportent la référence dans le modèle et le codec. Le panneau d’un livre existant peut retenir/oublier une édition et chercher un ISBN lié à cette œuvre. La sélection et le changement de total sont deux actions séparées. **Le parcours global d’ajout par ISBN Web/PC n’est pas intégralement reproduit dans l’interface native.** Aucun APK/AAB ni build SDK/Compose ou essai téléphone ne valide ce panneau.

Le catalogue demeure public et incomplet. Ni toutes les couvertures ni toutes les éditions/langues ne sont garanties. Les fournisseurs et images du test d’interface sont simulés. Les lectures réelles ont été empêchées par la résolution DNS de l’environnement, et le navigateur a bloqué sa navigation à l’origine locale. Voir QA_REPORT.md.

## Sources consultées le 7 octobre 2026

- Open Library Search API, résultats œuvres et éditions imbriquées : https://openlibrary.org/dev/docs/api/search
- Open Library API, faible volume et routes JSON : https://openlibrary.org/developers/api
- International ISBN Agency, structure et préfixes978/979 : https://www.isbn-international.org/fr/content/quest-ce-ce-que-lisbn/10
