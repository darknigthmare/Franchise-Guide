# Agenda, calendrier et visionnages datés — V0.16

## 1. Deux dates de nature différente

L'agenda est construit avec les **dates de diffusion ou de sortie de la fiche**. L'historique est construit avec les **instants auxquels l'utilisateur a réellement regardé** un film ou épisode. Un événement de sortie n'est jamais un visionnage ; exporter l'agenda ne change pas le suivi et ne lance aucune requête d'écriture chez un fournisseur.

## 2. Sélection de l'agenda

Le nouvel écran réunit les épisodes, films, jeux et livres datés. Une série entière ne produit pas d'événement de sortie supplémentaire : ses épisodes sont les événements. Les orphelins ou titres retirés sont exclus. Les fiches sans date restent dans la bibliothèque ; leur absence de date est comptée au lieu d'être remplacée par la date du jour. Web/PC signale aussi les dates invalides ignorées. Les doublons dotés de la même identité fournisseur ne créent pas deux événements.

La recherche et les compteurs portent sur tous les événements sélectionnés. L'écran construit d'abord 60 lignes. Filtres : type, favoris, non vus/non terminés, titres masqués et épisodes spéciaux. Par défaut, les épisodes ignorés ne figurent pas dans l'agenda. Le filtre anti-spoiler masque le titre d'un épisode non vu dans l'écran, la recherche et l'export. Le nom de la série et le numéro connu restent visibles. Une numérotation inconnue n'est pas inventée.

À venir / Récentes utilisent le nombre de jours configuré, avec 0 pour une plage non bornée. Plage choisie exige un intervalle cohérent. Les filtres propres à cet écran ne changent pas les préférences globales et ne sont pas sauvegardés dans un compte distant.

## 3. Fichier iCalendar

Le bouton d'export prépare une copie de la **sélection entière**, et non des seules 60 lignes affichées. L'aperçu doit être confirmé. Si la bibliothèque a changé entre l'aperçu et la confirmation, il faut préparer un nouvel export. Maximum 10 000 événements ; aucun fichier partiel n'est créé au-delà.

Format `.ics` iCalendar : CRLF, propriétés DATE sur une journée entière, fin exclusive au lendemain, identifiant UID stable, transparence TRANSPARENT et indication PRIVATE. Les sauts de ligne, antislashs, virgules et points-virgules des textes sont échappés ; les lignes sont pliées sans couper un caractère UTF-8, avec un maximum de 75 octets par ligne physique. Le dernier jour de l'année9999 omet la fin pour éviter un dépassement. Les tests comparent aussi octet par octet un export Web et un export Kotlin comportant accents, japonais et emoji.

Ce n'est pas un abonnement : une modification de catalogue ne met pas à jour un calendrier déjà importé. Les UID stables facilitent l'identification, mais ne garantissent pas la politique de fusion d'un logiciel externe. Aucun test Apple Calendar/Google Calendar/Outlook n'a été exécuté. Ne pas annoncer cette livraison comme une synchronisation avec ces services.

Il n'y a ni heure de diffusion supposée, ni alarme, invitation, participant, mot de passe ou connexion de compte. Les dates de catalogue ne garantissent pas une disponibilité dans le pays de l'utilisateur. Le fichier exporte uniquement les titres affichés et un texte explicatif de provenance, pas les notes personnelles, noms de profils, jetons ou succès. Vérifier le calendrier cible et son partage avant l'import : PRIVATE n'est pas un chiffrement.

## 4. Ajouter un visionnage daté

L'action est disponible sur un film ou un épisode de la bibliothèque. Elle **ajoute** une lecture ; elle ne retouche pas les anciennes dates. Saisir une date passée après le 1er janvier1970 et une heure réellement connue, HH:mm. Le fuseau de l'appareil est indiqué. Sans heure connue, ne pas fabriquer un instant pour remplir l'historique ; les anciens compteurs sans date restent acceptés comme tels.

Un horaire inexistant au printemps est refusé. Une heure répétée en automne offre les deux instants UTC et exige un choix. Les tests couvrent Europe/Paris et un chevauchement de 30minutes à Australia/Lord_Howe. Une date future ou un doublon à la même seconde est refusé. L'affichage du sélecteur natif de date peut suivre les conventions du navigateur, même lorsque l'application est en français.

L'ajout conserve les événements distincts, augmente le compteur et garde la plus récente comme dernière activité. Une lecture d'août ajoutée après une lecture de septembre ne fait donc pas reculer la dernière activité vers août. Un épisode ignoré devient vu si l'utilisateur lui ajoute explicitement un visionnage daté. Pour corriger/supprimer une ancienne lecture Trakt individuellement, cette version n'ajoute pas de nouvelle commande : ne pas utiliser Ajouter pour déplacer une date déjà envoyée.

## 5. File d'envoi Trakt

Les nouveaux événements datés sont comparés au suivi précédent. Chaque nouvelle date réellement ajoutée reçoit son propre `watched_at` UTC dans la file. Une augmentation de compteur sans nouvelle date connue ne renvoie pas la dernière date comme s'il s'agissait d'un nouveau visionnage. L'import/synchronisation des données déjà existantes n'est pas un ordre de republier tout l'historique.

Les comportements antérieurs de compte, consentement, pauses et opérations suspendues sont conservés. Un titre sans identifiant TMDb n'est pas envoyé sous un identifiant inventé. Une erreur réseau ou un accusé HTTP200 sans compteur exploitable ne justifie pas l'effacement de l'opération. Les envois d'historique incertains nécessitent une vérification avant reprise ; le réseau ne permet pas de promettre exactement une écriture en toutes circonstances. Aucun POST historique n'est rejoué aveuglément au motif qu'une date est ancienne.

## 6. Sources Android

La règle métier de date et le générateur ICS sont communs au sens fonctionnel mais écrits respectivement en JS et Kotlin. Les générateurs sont comparés en bytes sur un cas explicite. Android utilise ZoneRules pour les choix d'offset, un sélecteur système de document pour le texte ICS et une écriture bibliothèque/file atomique existante avec retour de l'état mémoire en cas d'échec. L'export préparé est figé avant d'ouvrir le sélecteur.

Les collisions d'identifiants d'alarmes et notifications ont aussi été traitées dans le code : l'identifiant complet d'épisode distingue les PendingIntents, la notification utilise son tag d'épisode et les anciennes requêtes fondées seulement sur le hash sont annulées lors de la replanification. Le récepteur relit le titre et les préférences courantes pour ne pas afficher un ancien spoiler. Les gardes refusent un titre supprimé/masqué ou un spécial exclu.

Ces points Android sont en **sources uniquement**, pas en APK ni validés sur un OS Android. La JVM teste les règles et les identités distinctes ; elle ne teste pas AlarmManager, le rendu Compose, le sélecteur de document, les notifications ou les permissions sur appareil.

## Références de conception

- RFC5545, iCalendar : https://www.rfc-editor.org/rfc/rfc5545
- Trakt, Sync et ajout à l'historique : https://trakt.docs.apiary.io/reference/sync
- Android NotificationManager, identification par tag et id : https://developer.android.com/reference/android/app/NotificationManager

Références consultées pendant cette passe. Les tests utilisent des réponses contrôlées ; aucune écriture sur un vrai compte Trakt et aucun calendrier externe modifié.
