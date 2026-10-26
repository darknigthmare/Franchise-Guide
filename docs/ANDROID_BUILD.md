# Android — construction 0.36.0

Le projet utilise AGP 8.10.1, Kotlin/Compose 2.0.21, Gradle 8.11.1, JDK 17, compile/target SDK 36 et minSdk 26. Build Tools 35.0.0 est requis. [Compatibilité officielle AGP 8.10](https://developer.android.com/build/releases/agp-8-10-0-release-notes).

Installer les prérequis avec Android Studio ou les outils Android officiels, puis ouvrir la racine du dépôt. Les dépendances Google Maven/Maven Central demandent une connexion autorisée pendant le premier build.

```bash
bash tools/bootstrap-gradle.sh :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Sous PowerShell :

```powershell
powershell -File tools/bootstrap-gradle.ps1 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Le bootstrap télécharge Gradle 8.11.1 et vérifie son SHA-256 officiel avant extraction. Le premier build résout également les bibliothèques Android et Compose.

L’APK de développement attendu est `app/build/outputs/apk/debug/app-debug.apk`. Les résultats réels de compilation, de lint et les corrections de cette passe figurent dans [QA_REPORT.md](QA_REPORT.md). GitHub Actions conserve l’APK de développement lorsqu’un job Android aboutit.

Cette sortie de développement doit encore être testée sur téléphone : navigation et rotation, import/restauration, interruptions de processus, Trakt réel, widgets, notifications et travail en arrière-plan. La publication magasin exige sa signature de distribution et ses recettes propres.

Le schéma portable est 11 et la version de l’application 0.36.0 (`versionCode 36`). Les clés de distribution et les comptes utilisateurs restent hors du dépôt.
