# Rapport QA — FranchiseGuide V0.34.0

Base : ZIP V0.33 fourni, SHA-256 `65343acbb86b829b7ebdca93030ff32d1520b070130777507c7ce0a4fcd09540`.

## Vérifications de la reconstruction courante

- Node : **1 465 tests réussis, 0 échec** sur les 37 suites disponibles.
- Deux suites exclues faute de dépendances : `retro-orchestration-v014.test.cjs` (jsdom) et `web-dom-v014.test.cjs` (jsdom/fake-indexeddb).
- Scanner Node : 11 tests inclus dans le total.
- Scanner Kotlin/JVM : **9 assertions réussies** et sortie normalisée comparée à JavaScript.
- Scanner Chromium : **9 assertions réussies** avec DOM/CSS réels et catalogue/stockage contrôlés.
- JAR PC : probe V0.34 réussi, contrôles HTTP et démarrage réussis.

Le contrôle Kotlin non graphique complet a été lancé avec 2 Gio de tas ; s’il n’est pas terminé dans l’environnement, il n’est pas présenté comme une validation Android. Le test de règles Kotlin du scanner, lui, est exécuté réellement.

## Limites

Aucun compte fournisseur réel, Android SDK/Compose, APK/AAB, appareil, PWA installée, persistance après crash ou vraie concurrence multi-onglets. Les fixtures ne prouvent pas l’exhaustivité d’une franchise.

Le conditionnement final vérifie chemins, octets, permissions, manifestes, JAR extrait et serveur Web extrait. Voir `FranchiseGuide_V0_34_VERIFICATION_ARCHIVES.json`.
