# Desktop0.28

Le programme Java17 contient exactement les62 fichiers Web canoniques. Lancement par les scripts de desktop/ ; serveur au loopback uniquement, port47631 stable. L’application conserve ses données dans le navigateur/profil, pas dans le JAR.

Reconstruction : `bash tools/build_pc.sh`, puis `java -jar desktop/dist/FranchiseGuide-PC.jar --probe`. Les contrôles HTTP utilisent le vrai JAR, sans navigateur ni compte externe. Aucun EXE autonome ni validation graphique Windows/macOS.

La nouvelle recherche ISBN et la sélection d’édition sont les mêmes que dans Web. Les sources Android sont seulement dans la Suite. Configuration et migration : START_HERE.md. Statut des essais : QA_REPORT.md.
