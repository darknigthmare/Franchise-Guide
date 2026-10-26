# Fonctions et limites — 0.36.0

| Fonction | Web / PC | Android |
| --- | --- | --- |
| Séries, films, jeux, livres et franchises | Code et contrôles de domaine/DOM présents | Sources Kotlin/Compose présentes ; recette appareil ouverte |
| Recherche et boutons d’ajout | Quatre catégories vérifiées dans le navigateur | Sources présentes |
| Fiches, saisons, épisodes et casting | Moteurs présents, images selon source catalogue | Sources présentes |
| Imports et sauvegardes | Aperçu/confirmation, JSON, fichiers protégés, copies locales et IndexedDB | Sources de lecture/écriture et protection présentes |
| Trakt et check-ins | Reprise sans second POST après résultat incertain, accusé unique, isolation des contextes | Parité source de ces gardes et correction des nullable de session |
| Steam et RetroAchievements | Passerelles locales et moteurs de synchronisation existants ; comptes réels non qualifiés | Parité native automatique encore incomplète |
| PSN/PSNProfiles, Xbox, Epic, GOG | Références et listes manuelles selon les fonctions existantes ; pas de nouveau connecteur authentifié livré | Non livré en connexion automatique |
| Agenda, Aujourd’hui, statistiques et listes | Présents ; contrôles automatisés | Sources présentes, widgets/notifications à qualifier sur appareil |
| Hors ligne et plusieurs onglets | Vrai Chrome : stockage, rechargement hors ligne et garde d’écriture vérifiés | Pas équivalent à une synchronisation multiappareil |
| Installation PWA et mises à jour | Recette complète par navigateur encore ouverte | Sans objet pour l’APK natif |
| Synchronisation générale multiappareil | Non livrée | Non livrée |
| Sauvegarde automatique externe périodique | Export manuel et rappel présents ; copie externe automatique non livrée | Travail restant |
| Distribution signée/qualifiée | JAR reconstruit, installateurs OS et recette utilisateur à compléter | État de compilation dans QA_REPORT ; qualification et signature magasin à compléter |

Les preuves actuelles sont dans [QA_REPORT.md](QA_REPORT.md). Le [classeur](FranchiseGuide_Reste_a_faire.xlsx) et [son registre](backlog.json) décrivent les tâches de développement et de validation avec leurs critères de fermeture.

La présence d’un moteur ou d’un bouton ne prouve pas le fonctionnement avec un compte fournisseur réel. Les tests Node/Java contrôlent les réponses fournisseur ; les 22 contrôles navigateur utilisent une vraie origine et de vrais stockages avec des données fictives.
