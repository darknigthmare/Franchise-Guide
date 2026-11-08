# Registre des travaux encore ouverts — 0.38

87 actions ouvertes sur 103. Les statuts et critères détaillés du JSON/Excel font foi ; ce registre ne déclare aucune validation supplémentaire.

## Livraison

| ID | État | Prochaine action / preuve attendue |
|---|---|---|
| FG-007 | Partiel | Compléter archives Web/PC isolées et lancement après extraction sur les systèmes cibles. |
| FG-090 | Partiel | Générer inventaire résolu/SBOM, contrôler licences et vulnérabilités pertinentes, fixer les versions et documenter décisions. |
| FG-091 | À valider | Définir le canal et l’offre, revoir conditions applicables des fournisseurs, images, attributions et marques, puis résoudre les points bloquants. |
| FG-092 | Partiel | Actualiser destinataires, données, durées, contacts, contrôle utilisateur et déclarations de distribution selon le produit réellement livré. |

## Android

| ID | État | Prochaine action / preuve attendue |
|---|---|---|
| FG-008 | Partiel | Étendre la recette native aux migrations de bibliothèque historique et profils gaming, avec arrêt/reprise. |
| FG-009 | Partiel | Compléter ajout, fiche, suivi, import/export, rotation et reprise ; tester ensuite un téléphone physique. |
| FG-010 | À valider | Tester arrêt forcé, espace insuffisant, SharedPreferences illisible et restauration après import ou synchronisation. Corriger les opérations non atomiques. |
| FG-011 | À valider | Tester activation, intervalle, perte réseau, Doze, changement de compte et désactivation. Contrôler unicité et annulation du travail périodique. |
| FG-012 | À valider | Tester permission accordée/refusée, heure locale, redémarrage, changement de date et clic sur notification. Éviter notifications répétées ou obsolètes. |
| FG-013 | À valider | Tester ajout/redimensionnement, rafraîchissement après suivi et synchronisation, veille, processus détruit et clics. |
| FG-014 | Partiel | Sur appareil de test jetable, exercer l’action système finale et vérifier données, tâches, clés et caches après redémarrage ; ne pas effacer un appareil personnel. |
| FG-015 | Partiel | Exercer rotation/révocation réelles des sessions, transfert système et appareils physiques. La bibliothèque active entière n’est pas chiffrée. |
| FG-016 | À développer | Définir le point d’accès et l’identité de l’application native, connecter capacités et erreurs, puis supprimer la dépendance aux saisies techniques ordinaires. |
| FG-017 | À développer | Implémenter le parcours autorisé de connexion, retour navigateur, import de bibliothèque et succès, avec annulation et liaison de compte. |
| FG-018 | À développer | Ajouter association explicite du profil public, lecture paginée et actualisation de succès normal/Hardcore via accès autorisé. |
| FG-019 | Partiel | Vérifier langue absente, pagination, couvertures en échec et sélection sur téléphone. |
| FG-020 | Partiel | Comparer le périmètre natif, ajouter la liste des fiches incomplètes et les lots bornés manquants avec annulation. |
| FG-093 | À développer | Après build et recette, établir signature sécurisée, variantes de distribution, mises à jour et dossier de publication. |

## Web et PWA

| ID | État | Prochaine action / preuve attendue |
|---|---|---|
| FG-021 | À valider | Installer depuis HTTPS ou loopback pris en charge, relancer depuis l’icône et vérifier nom, thème, icônes, portée et stockage. |
| FG-022 | Partiel | Tester démarrage à froid après installation, navigation en mode avion et données sans image. Rendre les opérations indisponibles explicites. |
| FG-023 | Partiel | Étendre aux installations PWA, autres moteurs, ancienne bibliothèque et interruptions pendant une vraie opération distante. |
| FG-024 | À valider | Tester navigateurs ordinaires/privés, quota plein, permission de persistance et suppression de données du site. |
| FG-025 | Partiel | Modifier le même média, importer et synchroniser dans deux fenêtres. Implémenter un verrou ou une résolution conservatrice si nécessaire. |
| FG-030 | Partiel | Tester URL initiale, retour/avance, liens de notifications et reprise. Ajouter des raccourcis utiles uniquement s’ils ouvrent une route existante. |

## Interface

| ID | État | Prochaine action / preuve attendue |
|---|---|---|
| FG-026 | À valider | Parcourir recherche, ajout, fiche, tri, import, comptes et confirmations avec Tab, Entrée et Échap. Corriger focus initial et retour. |
| FG-027 | À valider | Auditer noms accessibles, titres, boutons iconiques, erreurs et annonces asynchrones sur Web et TalkBack Android. |
| FG-028 | À valider | Tester thèmes, agrandissement de texte/zoom et préférence de mouvement réduit. Corriger contrastes et animations concernées. |
| FG-029 | À valider | Exercer petites largeurs, paysage, écrans tactiles et grandes bibliothèques. Corriger débordements, menus et champs masqués. |

## Trakt

| ID | État | Prochaine action / preuve attendue |
|---|---|---|
| FG-031 | À valider | Tester autorisation, refus, expiration, refresh, reconnexion et déconnexion avec application et compte de test autorisés. |
| FG-032 | À valider | Tester depuis deux clients avec lectures et modifications distantes réelles. Vérifier périmètre des données et absence d’envoi global involontaire. |
| FG-033 | À valider | Interrompre avant et après acceptation distante, puis vérifier/réconcilier sans réémettre aveuglément. Tester 429, 401 et retour réseau. |
| FG-034 | À valider | Tester films/épisodes, fin anticipée, durée atteinte, historique retardé et changement de compte. |
| FG-035 | Partiel | Recette de tous les écrans sur appareil et aller-retour Trakt réel encore ouverts. |
| FG-036 | À développer | Définir l’automatisme compatible avec chaque plateforme, sa reprise et son opt-in. Ne pas créer un faux service permanent sur navigateur. |
| FG-037 | Partiel | Exposer portée locale/distante, aperçu et résolution des conflits pour notes, collection, watchlist et historique. |
| FG-038 | Partiel | Ajouter résolution vérifiée via identifiants externes disponibles et confirmation des ambiguïtés. Garder les suivis d’épisodes attachés à la bonne identité. |
| FG-039 | À valider | Tester lecture, rédaction, validation, annulation, erreur distante et changement de compte avec données de test autorisées. |
| FG-040 | À valider | Exercer historiques et listes paginés, réponse incomplète, total variable et délai fournisseur. Afficher la progression et les données différées. |

## Données

| ID | État | Prochaine action / preuve attendue |
|---|---|---|
| FG-041 | À valider | Tester UTC, Europe/Paris, changement d’heure, voyage et date sans heure. Distinguer diffusion fournisseur et jour d’activité personnel. |
| FG-070 | À valider | Tester exports personnels autorisés de plusieurs versions avec listes, séries, films, IDs legacy et épisodes renumérotés. |
| FG-071 | À valider | Restaurer chaque version supportée, réexporter et comparer les champs métier. Tester version future, valeurs extrêmes et références manquantes. |
| FG-072 | À valider | Exporter PC/Web vers Android puis retour sur appareils réels, incluant épisodes, listes, franchises, éditions et succès. |
| FG-073 | Partiel | Valider le sélecteur et refus de permissions dans les navigateurs supportés ; ajouter la destination native Android et la recette de restauration externe. |
| FG-074 | À développer | Ajouter un fournisseur de destination autorisé, choix de dossier, chiffrement optionnel et rétention. Prévoir déconnexion et révocation. |
| FG-075 | À valider | Simuler contenu tronqué, JSON invalide, refus d’écriture et perte du stockage principal. Tester le vrai parcours de récupération. |
| FG-076 | À valider | Documenter les plafonds effectifs, mesurer le volume en octets et tester les frontières sur client lent. |
| FG-077 | Partiel | Définir le verrouillage de bibliothèque sur appareil partagé et la gestion des clés Web/PC ; ne pas confondre export chiffré et stockage actif. |
| FG-081 | À valider | Importer dans les clients de calendrier/tableur visés, réimporter puis contrôler accents, fuseaux et identifiants. |

## Catalogues

| ID | État | Prochaine action / preuve attendue |
|---|---|---|
| FG-042 | À valider | Tester séries longues, spéciales, saisons à venir, pagination, interruption et reprise. Contrôler nombre attendu et identités d’épisodes. |
| FG-043 | À valider | Tester images présentes, absentes, expirées, hors ligne et refusées. Vérifier proportions, chargement différé et cache. |
| FG-044 | À valider | Tester recherche, détail, pagination et indisponibilité de chaque source via les canaux supportés. |
| FG-045 | Partiel | Configurer les accès autorisés côté service ou build approprié, tester capacités et message d’absence de configuration. |
| FG-046 | À valider | Tester homonymes, remakes, séries animées, renommages, éditions et franchises. Préparer un rapprochement explicite lorsqu’il est nécessaire. |
| FG-047 | À valider | Recetter changement de langue, absence de traduction, statut terminé/en cours et acteurs principaux/invités. Garder la provenance des notes. |
| FG-048 | À valider | Tester plusieurs pays, saison absente, relevé vide, erreur et lien fournisseur réel. Revalider les attributions et conditions d’utilisation. |
| FG-049 | À valider | Tester frappe rapide, changement de catégorie/langue/source, annulation et réseau lent sur vrai navigateur/appareil. |
| FG-050 | Partiel | Affiner les filtres utiles par média, indiquer les sources épuisées et conserver une reprise après erreur sans recommencer les sources réussies. |
| FG-051 | À valider | Vérifier homonymes, crédits multiples, pagination, invités et retour vers l’œuvre d’origine. Masquer les informations spoilers avant révélation. |

## Jeux et succès

| ID | État | Prochaine action / preuve attendue |
|---|---|---|
| FG-052 | À valider | Tester connexion, annulation, Steam Guard via site officiel, retour, expiration, relance et déconnexion. |
| FG-053 | À valider | Tester profil accessible/privé, jeux sans succès, compte changé, grandes bibliothèques et liste partielle. |
| FG-054 | À valider | Lire un profil autorisé, changer de pseudo, reprendre les pages et comparer les succès aux données du fournisseur. |
| FG-055 | À valider | Recetter pause, annulation, délais fournisseur, reprise, fenêtre invisible et listes différées faute de capacité. |
| FG-056 | À valider | Tester imports structurés, remasters, éditions différentes et ensembles supplémentaires. Ajouter une correction manuelle sourcée si nécessaire. |
| FG-057 | À développer | Vérifier l’accès officiellement autorisé disponible pour ce projet, définir le parcours et implémenter lecture des trophées si l’accès le permet. |
| FG-058 | À développer | Identifier un export ou accès autorisé, définir attribution et fraîcheur, puis intégrer lecture et import sans contourner de protection. |
| FG-059 | À développer | Vérifier accès permis, authentification et champs disponibles. Implémenter séparément bibliothèque et succès avec refus/expiration. |
| FG-060 | À développer | Établir la disponibilité réelle d’un accès autorisé pour bibliothèque et succès. Implémenter seulement le périmètre confirmé. |
| FG-061 | À développer | Établir la méthode autorisée d’import/connexion et la compatibilité des listes, puis réaliser l’adaptateur et ses contrôles. |
| FG-062 | À valider | Tester données incomplètes, doublons, version inconnue, trophées secrets, dates et plusieurs profils/éditions. |
| FG-063 | À valider | Charger un corpus proche des plafonds, mesurer recherche/tri/filtre, mémoire et export. Améliorer pagination/virtualisation si nécessaire. |

## Livres et franchises

| ID | État | Prochaine action / preuve attendue |
|---|---|---|
| FG-064 | À valider | Tester ISBN10/13, édition inconnue, langues, pages absentes et œuvre liée à plusieurs éditions. |
| FG-065 | À valider | Changer d’édition avant/après progression, changer d’unité et diminuer le total. Tester refus et reprise de lecture. |
| FG-066 | Partiel | Définir des inventaires vérifiés, provenance et périmètre par franchise. Inclure œuvres au titre différent, dérivés et exclusions explicites. |
| FG-067 | À valider | Vérifier ordre de sortie, chronologie et ordre personnalisé. Préserver dates inconnues, supports distincts et éditions. |
| FG-068 | À valider | Tester quatre catégories, page en erreur, homonymes, annulation et identité ajoutée pendant la recherche. |
| FG-069 | À valider | Tester reprise, abandon, relecture, changement d’unité et jeu présent sur plusieurs comptes. Comparer les sorties Web/Android. |

## Cloud

| ID | État | Prochaine action / preuve attendue |
|---|---|---|
| FG-078 | À développer | Définir comptes, sessions, isolation, stockage de secrets, quotas et révocation. Implémenter le backend avec périmètre séparé des passerelles locales. |
| FG-079 | À développer | Définir protocole incrémental, identités, versions, conflits, suppressions et fonctionnement hors ligne. Inclure notes, listes et progression personnelle. |
| FG-080 | À développer | Ajouter export complet cloud, fermeture de compte, révocation de sessions et procédure de récupération correspondant au backend choisi. |

## PC

| ID | État | Prochaine action / preuve attendue |
|---|---|---|
| FG-082 | À valider | Exercer installation, double clic, chemins avec espaces/accents, port occupé, navigateur absent et fermeture. |
| FG-083 | À développer | Choisir les systèmes cibles, intégrer runtime approprié, icônes, désinstallation et signature avec clés protégées. |
| FG-084 | À développer | Définir versions, signature/empreinte des paquets, restauration et traitement des mises à jour échouées. |

## Sécurité

| ID | État | Prochaine action / preuve attendue |
|---|---|---|
| FG-085 | À valider | Auditer traversée de chemin, hôtes, méthodes, redirections, corps volumineux et charge simultanée. Corriger les divergences Node/Java. |
| FG-086 | À valider | Tester droits du fichier, relance, fichier corrompu, session expirée, suppression et concurrence sur chaque OS cible. |
| FG-087 | À valider | Tester HTML hostile, URL dangereuse, prototype pollution, JSON très profond et images importées. Contrôler CSP et navigation externe. |

## Qualité

| ID | État | Prochaine action / preuve attendue |
|---|---|---|
| FG-088 | À valider | Mesurer démarrage, recherche, tri, import, statistiques, mémoire et sauvegarde avec corpus petit/moyen/grand sur appareils cibles. |
| FG-094 | Partiel | Comptes fournisseurs réels, téléphone physique, installation PWA/OS, accessibilité complète et distribution signée restent à qualifier. |

