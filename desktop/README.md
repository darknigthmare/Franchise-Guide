# FranchiseGuide PC 0.36.0

Java17+ requis. Lancer FranchiseGuide-PC.bat ou `bash FranchiseGuide-PC.sh`. Le JAR compilé dans dist/ sert le même moteur Web sur `http://127.0.0.1:47631/`. Les données résident dans le profil navigateur, pas dans le JAR. Pas d’EXE autonome ni Java embarqué.

V0.36 fiabilise les check-ins, les passerelles locales et le build de la suite. Aucun traitement ne s’exécute une fois le lanceur/page fermé. Lire [le démarrage](../START_HERE.md), [les check-ins](../docs/CHECKINS_AUJOURDHUI_ET_SAUVEGARDES.md) et [le rapport QA](../docs/QA_REPORT.md). Le serveur est testé sous Linux ; les fenêtres graphiques Windows/macOS et comptes réels ne le sont pas.

Reconstruction depuis la Suite : `bash tools/build_pc.sh`.
