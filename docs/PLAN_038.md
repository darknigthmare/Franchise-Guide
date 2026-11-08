# Passe 0.38 — intégrité, confidentialité et continuité

Reprise de main `2c16df9` et du registre `docs/backlog.json`, sans réinitialiser le projet.
Les outils IA déclarés dans `.ai-tools` sont conservés. Le diagnostic local détecte Git/uvx,
mais pas les clients Codex/Claude ni leurs plugins ; aucun agent parallèle fictif n'est déclaré.

## Périmètre et critères

1. FG-014/015/077 : coffre Android pour les chaînes d'accès (AES-GCM, Android Keystore),
   migration vérifiée sans remplacement silencieux, effacement local explicite par Android.
   La bibliothèque active n'est pas annoncée chiffrée. Un compte distant n'est pas supprimé.
2. FG-023/025 : changement de service worker coordonné entre onglets ; un ancien client
   bloque ses écritures et ses envois avant de proposer le rechargement.
3. FG-073/074 : sauvegarde vers un fichier choisi sur les navigateurs compatibles,
   uniquement après consentement, vérification de l'écriture et sans clé dans l'export.
   Pas de promesse d'exécution lorsque l'application est fermée, ni de copie Drive implicite.
4. FG-002/008/009/010/094 : régressions nouvelles, qualification navigateur multi-onglets,
   tests instrumentés Android en CI lorsque l'émulateur est disponible.
5. FG-007/083/084/089/090 : distributions reproductibles, contrôle du manifeste,
   diagnostic partageable expurgé et inventaire dépendances ; aucune signature inventée.

## Méthode

Tests ciblés avant correctif lorsque le défaut est reproductible, puis contrôle global.
Source canonique `web/`, miroirs générés seulement via `tools/sync_web.cjs`.
Les secrets, comptes réels, appareils physiques, cloud public et autorisations fournisseurs
restent des limites explicites. Les statuts ne sont clôturés que sur le critère démontré.

Sources : Android Keystore et Cryptography (developer.android.com/privacy-and-security),
cycle de vie Service Worker (web.dev/articles/service-worker-lifecycle), File System Access
(developer.chrome.com/docs/capabilities/web-apis/file-system-access), consultés le 08/11/2026.
