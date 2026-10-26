# Android — construction à valider, sources V0.22

**Aucun APK/AAB n’est livré ou compilé dans cet environnement.** La configuration est AGP8.10.1, Kotlin/Compose2.0.21, Gradle8.11.1, Java17, compile/target SDK36, minSdk26. AGP8.10 documente Gradle8.11.1 et le support API36 : [référence officielle](https://developer.android.com/build/releases/agp-8-10-0-release-notes).

Installer Android Studio/SDK, JDK17 et les paquets plateforme36/build-tools35.0.0, puis ouvrir la racine Suite. Les dépendances Maven nécessitent un accès réseau autorisé.
```sh
sh tools/bootstrap-gradle.sh :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```
Sous PowerShell : `powershell -File tools/bootstrap-gradle.ps1 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` selon la politique de scripts de la machine.

Le bootstrap télécharge la distribution officielle Gradle8.11.1 et vérifie l’empreinte binaire `f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6` avant extraction. Source : [checksums officiels](https://gradle.org/release-checksums/). Aucun wrapper JAR factice n’est fourni. Le test local vérifie le refus d’un téléchargement corrompu simulé, pas une installation Gradle réelle. Après première installation, le cache local est supposé de confiance ; protéger les droits du dossier.

Après un build réussi, la sortie debug attendue serait `app/build/outputs/apk/debug/app-debug.apk` ; **ce chemin n’est pas un fichier présent dans l’archive**. Une release nécessite une clé de signature propre conservée hors du dépôt, un build release et ses validations. La CI manuelle prépare un builddebug et lint, mais n’a pas été exécutée.

Sur appareil, tester en priorité RecoveryScreen, TraktReviewsPanel, ContentLanguagePanel et TraktLibraryScreen ; échec org.json/SharedPreferences sans écrasement ; rotation/navigation/compte changé pendant requête ; import volumineux et absence de blocage du thread UI ; sauvegarde brouillon avant sortie ; cycles OAuth ; arrêt/reprise de processus ; notifications et widget en veille. Tester versions/constructeurs correspondant à la promesse de distribution. Les signatures Kotlin de QA ne sont pas le SDK ni une garantie Compose.


## Données et écrans V0.22

Version0.22.0 / versionCode22, export portable V9 avec lecture V1–V8. Les nouveaux champs de parcours personnel disposent d’un codec explicite PersonalProgressCodec. Il est compilé contre des signatures de test, **pas exécuté avec le runtime JSON Android** dans cet environnement. Les deux tests précédents utilisant le véritable org.json ne sont pas comptés comme exécutés.

ProgressRules et InsightsRules sont exécutés sur JVM et comparés aux compteurs JavaScript d’un jeu de données déterministe. InsightsPanel, DatedInsightsPanel et PersonalProgressPanel sont des sources UI intégrées ; aucune validation Compose, rotation, retour appareil ni restauration de processus n’est revendiquée. Tester particulièrement le maintien des historiques lors de diminution/reprise, les profils Steam non associés, les listes de succès, les codecs V9 et le refus de stockage réel.

Le nouveau centre catalogue et les exports de bilan/CSV sont Web/PC ; aucune parité native de ces fonctions n’est annoncée. Les autres fonctions sources, y compris les notifications, doivent être testées sur appareil avant publication.
