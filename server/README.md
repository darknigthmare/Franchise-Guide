# Serveur personnel FranchiseGuide 0.34

Node22+, modules standards uniquement. Depuis la racine de distribution : `node server/start.cjs`, puis http://127.0.0.1:47632/. Le serveur sert le Web et les passerelles locales. Aucun paramètre API requis pour les catalogues publics TVmaze, Apple et FreeToGame ; Open Library reste son client public existant.

Le nouveau `auto-services.cjs` possède une route de capacités sans secret et des routes fournisseur explicitement autorisées. Les identifiants éditeur facultatifs sont lus depuis l’environnement du processus au démarrage, jamais dans les fichiers Web. Guide éditeur : docs/AUTOMATIC_SERVICES.md dans la Suite, SERVICES_AUTOMATIQUES.md dans la distribution séparée.

Les anciennes passerelles Steam/Retro restent dans leur module respectif. Aucun compte réel ou clé partagée fournis. Le fichier `.env` n’est pas chargé automatiquement. L’hôte et l’origine sont limités au loopback ; ne pas publier tel quel sur Internet.
