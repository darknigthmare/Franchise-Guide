# Web/PWA0.28

Le moteur canonique `web/` contient62 fichiers, dont les deux nouveaux modules `book-selection-core.js` et `book-selection-ui.js`. Les fonctions antérieures sont conservées. Le service worker inclut ces modules ; cela ne constitue pas une validation de PWA installée.

Recherche ISBN sans clé depuis Livres ou la recherche, vérification édition/œuvre/ISBN et choix persistant séparé du suivi. Le portable V11 transporte la référence ; la lecture des schémas1–10 reste supportée. Les anciennes versions ne doivent pas réenregistrer ces données. Voir ISBN_ET_EDITION.md, START_HERE.md et QA_REPORT.md.

Le serveur Node22 personnel utilise47632. Le profil/navigateur garde localStorage ; son quota et ses écritures synchrones restent des contraintes. Réseau/provider et stockage sont contrôlés dans la QA DOM ; persistance réelle et comptes réels non validés.
