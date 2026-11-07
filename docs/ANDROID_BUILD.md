# Android — construction 0.37.0

Prérequis : JDK 17, Android SDK 36, Build Tools 35.0.0. Le projet conserve AGP 8.10.1, Kotlin/Compose 2.0.21 et Gradle 8.11.1. minSdk 26, targetSdk 36, versionCode 37. Les accès de distribution restent hors dépôt.

```bash
bash tools/bootstrap-gradle.sh :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
python3 tools/check_junit_results.py app/build/test-results/testDebugUnitTest
```

Sous PowerShell, remplacer le bootstrap par `powershell -File tools/bootstrap-gradle.ps1` avec les mêmes tâches. Le bootstrap vérifie le SHA-256 de Gradle. Les dépendances demandent une connexion autorisée lors de leur première résolution.

## Résultats vérifiés

La [CI 42386272622](https://github.com/darknigthmare/Franchise-Guide/actions/runs/42386272622) a réussi la compilation Compose, l'assemblage debug, les **30 groupes JUnit** et le lint (**0 erreur, 31 avertissements, 1 conseil**). Contrairement à 0.36, `testDebugUnitTest` n'est plus `NO-SOURCE`. Les classes de tests partagées sont dans `tests/`, l'adaptateur JUnit dans `app/src/test/java/NativeRegressionSuitesTest.kt`.

Les codecs utilisent `org.json` réel pour ces tests JVM. Cela ne qualifie pas le stockage, les migrations ni le moteur JSON d'un appareil réel. Les groupes de règles ne constituent pas des tests Compose instrumentés.

L'APK est produite dans `app/build/outputs/apk/debug/app-debug.apk`. [APK GitHub](https://github.com/darknigthmare/Franchise-Guide/actions/runs/42386272622/artifacts/13580130579) ; [rapports JUnit/lint](https://github.com/darknigthmare/Franchise-Guide/actions/runs/42386272622/artifacts/13580021196).

## Restant à tester

Téléphone/émulateur : navigation, rotation, import/restauration, arrêt de processus, Trakt réel, widget, permissions et notifications, Doze, couvertures et filtre de langue. La connexion native Steam/RetroAchievements et l'effacement local complet restent des développements séparés.

Une APK debug n'est pas une version magasin signée. Les clés et le dossier de publication restent à établir. Consulter [le rapport courant](QA_REPORT.md) et le [classeur](FranchiseGuide_Reste_a_faire.xlsx).
