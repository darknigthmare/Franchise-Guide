# Démarrage — FranchiseGuide V0.34.0

Exporter V0.33 avant migration, fermer les anciens onglets/lanceurs et extraire V0.34 dans un nouveau dossier. Le portable reste V11 et le conteneur protégé V1.

## PC
Java17+ : lancer `desktop/FranchiseGuide-PC.bat` ou `bash desktop/FranchiseGuide-PC.sh`. Le navigateur utilise `http://127.0.0.1:47631/`.

## Web
Node22+ : `node server/start.cjs`, puis `http://127.0.0.1:47632/`. Ne pas ouvrir `index.html` en file://.

## Scanner une franchise
Ouvrir Franchises, choisir un univers puis **Scanner séries, films, jeux et livres**. Sélectionner les catégories, lancer les pages, corriger ou reprendre une catégorie en erreur, sélectionner les nouvelles références puis enregistrer le lot grisé.

La recherche par termes n’est pas un canon exhaustif. Une ambiguïté bloque le lot. Aucun suivi, épisode ou historique n’est créé automatiquement.

Android est fourni en sources, sans APK/AAB ni validation SDK/Compose. Lire `docs/SCANNER_FRANCHISES.md` et `docs/QA_REPORT.md`.
