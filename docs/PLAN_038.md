# Passe 0.38 — réalisation et limites

Base : `2c16df9`, registre unique `backlog.json`. Les outils IA déclarés sont conservés ; aucun plugin absent ni agent parallèle fictif n’est annoncé.

Réalisé : coffre Android avec migration protégée ; préparation d’effacement et confirmations ; protections de mise à jour multi-onglets ; sauvegarde périodique vers dossier choisi sur Web/PC compatible ; rapports d’assistance expurgés ; CI instrumentée et preuve négative ; métadonnées de version et suivi actualisés.

Reste ouvert : validation de l’effacement final et des résidus sur appareil jetable, sélecteur navigateur et destination native Android, cloud général, parcours complets avec comptes réels, installations PWA/OS, accessibilité, SBOM et licences résolus, distribution signée. Ces points ne sont pas réputés livrés par ce document.

La méthode et les résultats réels sont dans `qa/2026-11-08.md`. Les références techniques ont été consultées le 08/11/2026 : Android Keystore/Cryptography (developer.android.com), cycle de vie Service Worker (web.dev), File System Access (developer.chrome.com). Elles ne valent pas validation d’un compte ou d’un appareil.
