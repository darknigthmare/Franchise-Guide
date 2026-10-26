# Démarrage — FranchiseGuide V0.30.0

## Avant la mise à jour

Exporter la bibliothèque V0.29 en JSON et garder le fichier hors du navigateur. Fermer ses anciens onglets et lanceurs ; extraire V0.30 dans un nouveau dossier. Conserver le même navigateur/profil et la même adresse locale pour retrouver les données. Ne pas ouvrir les deux versions sur le même stockage.

Le portable reste **V11**, avec lecture des anciens formats. Le nouveau fichier `.fgbackup` est un conteneur chiffré facultatif que les versions précédentes ne peuvent pas ouvrir. Il ne remplace pas les archives ZIP/RAR du logiciel ni les exports JSON existants.

## PC

**Java17+ requis**, non embarqué. Depuis la Suite, lancer `desktop/FranchiseGuide-PC.bat` sous Windows ou `bash desktop/FranchiseGuide-PC.sh` sous Linux/macOS. Dans l'archive PC séparée, les lanceurs sont à la racine.

Le JAR compilé est dans `desktop/dist` (Suite) ou `dist` (PC). L'interface s'ouvre dans le navigateur à `http://127.0.0.1:47631/`. Garder le lanceur actif et fermer l'ancienne instance occupant le port. Ce n'est pas un EXE autonome. Les tests HTTP/JAR sous Linux ne valident pas la fenêtre graphique Windows/macOS.

## Web personnel

**Node22+ requis.** Depuis la racine de la Suite ou de la distribution Web, lancer :

```sh
node server/start.cjs
```

Ouvrir `http://127.0.0.1:47632/`. Aucune dépendance npm n'est requise pour l'utilisation ; package.json concerne les tests. Le serveur personnel n'est pas un backend Internet multiutilisateur. Ne pas ouvrir index.html par double-clic file://.

Les ports47631 et47632 représentent deux origines différentes : ils ne partagent pas automatiquement le stockage. Transférer les données par export/import si nécessaire. Les snapshots automatiques dans le même navigateur ne protègent pas contre l'effacement de son profil.

## Utiliser sans clé utilisateur

Séries, Films, Jeux ou Livres → **+ Ajouter / rechercher**. Les catalogues publics TVmaze, Apple/iTunes, FreeToGame et Open Library restent accessibles sans clé saisie, sous réserve de réseau et de leurs limites. FreeToGame ne couvre pas tous les jeux payants/consoles, et les films Apple ne sont pas des fiches TMDb exhaustives. Les anciennes fiches TMDb/RAWG gardent leur fournisseur et nécessitent un accès éditeur configuré ou un override personnel pour leur actualisation riche.

Aucun accès privé partagé n'est préactivé dans l'archive. Les guides Trakt, Steam et RetroAchievements restent dans docs/. Aucune bibliothèque utilisateur distante n'a été modifiée lors des tests.

## Nouveau : protéger un export

**Plus → Exporter avec une phrase secrète** crée un fichier `.fgbackup`. Choisir une phrase longue et unique, saisir sa confirmation et lire l'avertissement. La phrase n'est pas enregistrée par l'application. **Si elle est perdue, le fichier ne peut pas être récupéré.** Garder la phrase séparément du fichier.

Cette option chiffre seulement le fichier exporté. Le profil navigateur, les copies JSON et snapshots locaux restent dans leur mode actuel ; ils ne sont pas chiffrés rétroactivement. L'export JSON ordinaire reste disponible. Pas de repli silencieux en clair si Web Crypto manque.

**Importer FranchiseGuide** accepte le JSON ou `.fgbackup`. Après déchiffrement et validation, un aperçu avant/après est présenté. Il faut encore confirmer et réussir une copie de sécurité avant tout remplacement. Une annulation, un mot de passe incorrect ou une modification de la bibliothèque pendant l'opération ne valide pas l'import.

Guide complet : `docs/SAUVEGARDES_PROTEGEES.md` dans la Suite, `SAUVEGARDES_PROTEGEES.md` dans les distributions séparées. Limite : 32 Mio de JSON ; phrase de 12 à 256 caractères Unicode, sans contrôles ; espaces et accents significatifs.

## Android et validations

Android est fourni en sources, avec un nouveau panneau dédié aux sauvegardes protégées. **Pas d'APK/AAB, de compilation SDK/Compose ni d'essai téléphone.** Les tests JVM exécutent les primitives cryptographiques et les règles ; la compilation contre signatures ne valide pas le runtime Android.

Dans cet environnement la navigation du navigateur vers une origine sûre est bloquée. Les scénarios UI utilisent Chromium et un pont de test vers la vraie implémentation Node WebCrypto. Ils ne valent pas test du chiffrement natif dans un navigateur installé, installation PWA ou persistance réelle. Voir QA_REPORT.md pour les résultats exacts.

Le dossier Google Drive demandé reçoit les fichiers du projet et les rapports quand leur transfert est confirmé. Ce dépôt n'ajoute pas une synchronisation automatique Drive de la bibliothèque personnelle, et ne crée aucun partage public.
