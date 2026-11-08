# Signaler un problème — 0.38

Le suivi du dépôt n’est pas un service d’assistance contractuel. Aucun diagnostic n’est envoyé automatiquement.

## Préserver avant de réparer

Ne pas effacer les données pour « voir si cela marche ». Dans un mode récupération, conserver d’abord le fichier brut **en privé** ; il peut contenir notes, comptes et accès. Ne pas le joindre à une issue publique. Ne pas répéter un envoi Trakt dont le résultat est incertain : conserver l’état et utiliser sa vérification.

## Préparer le signalement

Sur Web/PC, ouvrir le diagnostic et choisir l’export du rapport d’assistance. Sur Android, utiliser le panneau Rapport d’assistance dans les préférences. Le JSON partageable contient uniquement version, compteurs et indicateurs techniques autorisés. Il ne contient pas les messages bruts du journal ni la bibliothèque. Les compteurs renseignent néanmoins sur l’utilisation : relire le fichier avant de le partager.

Dans une issue du dépôt, indiquer version exacte, canal (Android, Web, PC), système et navigateur ; décrire les étapes minimales, le résultat attendu et le résultat obtenu. Préciser si les données étaient fictives ou réelles sans les copier. Les captures doivent être relues et masquer noms de comptes, titres privés, notes et codes de connexion. Ne joindre ni mot de passe, cookie, NPSSO, clé API, code appareil, export portable complet, fichier brut, ni fichier de session serveur.

## Triage et fermeture

Une perte ou corruption de données est P0 ; un parcours essentiel bloqué P1 ; une amélioration P2. Le mainteneur relève la version/commit d’origine, reproduit sur des données synthétiques, crée une régression, relie issue et correctif, puis note la version et le résultat de retest avant fermeture. Sans reproduction, conserver « à reproduire » plutôt que « corrigé ».

Un problème de sécurité ne doit pas être détaillé publiquement avec ses secrets. Utiliser le canal privé de signalement du dépôt lorsqu’il est activé ; son activation n’est pas présumée. Ne transmettre aucune donnée sensible en l’absence de canal sûr.

## Trois actions distinctes

Exporter un rapport d’assistance ne sauvegarde pas la bibliothèque. Oublier un dossier de sauvegarde n’efface pas ses fichiers. Effacer l’application localement ne supprime pas les comptes/historiques distants. Les exports déjà créés restent à gérer séparément.
