# Validation courante — FranchiseGuide 0.37.0

La recette détaillée et les limitations sont dans [le rapport du 7 novembre 2026](qa/2026-11-07.md).
Les trois jobs [GitHub 42386272622](https://github.com/darknigthmare/Franchise-Guide/actions/runs/42386272622) sont réussis.

Résultats : 1 597 tests Node, 369 assertions Java, 216 contrôles structure, 118 contrôles PC, 22 contrôles navigateur, 30 groupes JUnit natifs. Compilation SDK/Compose et APK debug réussies ; lint : 0 erreur, 31 avertissements et 1 conseil.

Ces 30 groupes testent les règles et les codecs, pas l'interface sur appareil. Aucun compte fournisseur réel, téléphone ou installation PWA autonome n'est validé. Le cloud général et les sauvegardes externes automatiques restent absents.

Le [registre JSON](backlog.json) et [l'Excel](FranchiseGuide_Reste_a_faire.xlsx) conservent 103 tâches : 14 terminées au cumul et 89 ouvertes. Le nombre de tâches n'est pas un pourcentage global du produit.

La [recette 0.36](archives/v036/QA_REPORT.md) est conservée séparément et n'est pas additionnée à la présente passe. Preuves lisibles par machine : [current.json](qa/current.json).
