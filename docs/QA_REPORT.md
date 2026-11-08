# Qualification courante — FranchiseGuide 0.38.0

**Verdict commercial : NO-GO.** La compilation et les suites passent ; les comptes réels, les appareils physiques et les distributions signées restent ouverts.

La preuve détaillée est dans [la passe du 08/11](qa/2026-11-08.md), avec [données structurées](qa/current.json). Les résultats distinguent explicitement la révision exécutée en CI, les ajouts locaux et la revalidation de la tête finale de PR nº2.

La CI comprend quatre jobs : Web/PC, navigateur réel, Android JVM/build/lint et émulateur Android (Keystore/Compose). Une branche séparée a démontré un échec volontaire de consentement ; elle ne fait pas partie de la livraison.

Le succès du coffre et des trois parcours de confidentialité ne valide pas toute l’interface Android, les effets finaux destructifs, les sauvegardes externes natives ou la synchronisation fournisseur réelle. Voir [registre](backlog.json), [Excel](FranchiseGuide_Reste_a_faire.xlsx) et [portes de sortie](RELEASE_GATES.json).
