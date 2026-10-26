# Sauvegardes protégées — FranchiseGuide V0.30

## Ce que protège cette option

Elle crée un **fichier exporté chiffré**, terminé par `.fgbackup`, à partir de la sauvegarde portable normale de la bibliothèque. Elle n'ajoute pas de compte distant, ne nécessite pas de clé développeur et ne transmet pas la phrase secrète à un serveur.

**Elle ne chiffre pas la bibliothèque utilisée par l'application, le profil du navigateur, les anciennes copies JSON, les snapshots locaux, les identifiants des fournisseurs ni la mémoire de l'appareil.** Une personne ayant accès à une session ouverte ou à un appareil compromis peut toujours lire les données qui y sont accessibles. Les notes privées, profils et historiques font partie de la bibliothèque exportée : c'est précisément ce contenu qui est protégé dans le fichier.

Un mot de passe oublié ne peut pas être réinitialisé pour ouvrir ce fichier. Choisis une phrase longue et unique, garde-la séparément, par exemple dans ton gestionnaire de mots de passe, et conserve un moyen de récupération qui te convient. Ne dépose pas la phrase dans le même dossier que la sauvegarde. Le fichier révèle encore son format et approximativement la taille du contenu. Un mot de passe faible reste attaquable hors ligne.

## Exporter sur Web et PC

1. Ouvrir **Plus → Exporter avec une phrase secrète**. Le bouton existe aussi dans **Importer et vérifier**.
2. Saisir et confirmer une phrase de **12 à 256 caractères Unicode**. Les espaces, accents et emoji sont significatifs ; aucune normalisation ou suppression silencieuse des espaces n'est faite. Les caractères de contrôle et les phrases composées uniquement d'espaces sont refusés. Limite UTF-8 : 1 024 octets.
3. Confirmer avoir compris l'absence de récupération du mot de passe.
4. Cliquer **Chiffrer et créer le fichier**. Vérifier ensuite sa présence dans les téléchargements du navigateur.

L'export ordinaire JSON reste disponible et clairement indiqué en clair. Aucun repli automatique vers le JSON n'est fait si la protection échoue. L'export ne change ni les titres, ni les suivis, ni la file Trakt.

Pendant le calcul, la validation et les champs sont désactivés. Les champs de phrase sont vidés dès le lancement. Une fermeture ou une annulation empêche le résultat de sortir ; le calcul cryptographique natif déjà engagé peut toutefois finir en arrière-plan de cette action. Si la bibliothèque, ses réglages ou sa révision ont changé, l'ancien résultat est refusé. Le formulaire expire après dix minutes.

## Importer et examiner avant remplacement

**Plus → Importer FranchiseGuide**, puis choisir un JSON normal ou un fichier `.fgbackup`. Le format protégé est aussi reconnu à son en-tête s'il a été renommé en `.json`.

L'application contrôle l'en-tête, les tailles et les paramètres avant de demander du travail cryptographique. Après saisie de la phrase, elle vérifie l'authenticité du chiffrement, décode strictement le texte UTF-8 puis utilise les validations portables habituelles. Un mot de passe incorrect ou une modification non authentifiée du contenu ne produit aucun aperçu utilisable.

**Déchiffrer n'applique pas l'import.** L'aperçu existant présente encore les compteurs avant/après, différences et réglages à conserver. Il faut une seconde confirmation et une copie locale de sécurité réussie pour remplacer la bibliothèque. La file Trakt antérieure suit les mêmes protections que pour un import JSON. Une synchronisation active bloque l'application.

Un contenu correctement chiffré peut néanmoins être un JSON invalide ou une sauvegarde incompatible. Le chiffrement ne prouve pas l'identité de l'auteur : il ne faut pas ignorer l'aperçu sous prétexte que le mot de passe fonctionne.

## Android

Un écran source **Sauvegarde protégée · exporter / importer** est raccordé depuis Plus. L'export utilise le sélecteur de destination Android ; l'import protégé conduit à l'aperçu de remplacement existant. Les phrases ne sont pas stockées avec `rememberSaveable`. Les entrées et sorties binaires sont bornées, et les tampons mutables possédés par l'opération sont remis à zéro quand ils ne servent plus.

**Aucun APK/AAB, compilation SDK/Compose ou essai sur téléphone n'est livré.** Les primitives Kotlin ont été exécutées sur JVM avec les fournisseurs cryptographiques Java. L'interopérabilité JavaScript/JVM ne prouve pas à elle seule le fonctionnement du fournisseur de documents, du clavier ou du moteur cryptographique sur chaque version Android. Le panneau natif sélectionne explicitement les fichiers protégés ; l'import JSON ordinaire garde son parcours séparé.

## Format et limites techniques

- Nom du conteneur : `FGPROT01`, version de protection 1.
- Chiffrement : **AES-256-GCM**, nonce de 12 octets et tag de 16 octets.
- Dérivation : **PBKDF2-HMAC-SHA-256, 600 000 itérations**, sel aléatoire de 16 octets par export.
- L'en-tête complet de 48 octets est authentifié comme données additionnelles (AAD).
- Taille maximale du JSON clair : **32 Mio** ; taille maximale du fichier : **32 Mio + 64 octets**.
- Le mot de passe est utilisé exactement en UTF-8 ; les équivalents Unicode composés/décomposés ne sont pas fusionnés.
- Le format portable interne reste **V11**, comme la V0.29. La version du chiffrement est distincte.

Disposition binaire, entiers non signés big-endian : octets0–7 = marqueur ASCII ; 8–11 = 600000 ; 12–15 = longueur du JSON clair ; 16–31 = sel ; 32–43 = nonce ; 44–47 = zéro réservé ; puis ciphertext et tag GCM. Un coût différent, un octet réservé non nul, un fichier trop grand, tronqué ou augmenté sont refusés. Il n'y a ni compression, ni archive ZIP intermédiaire, ni clé de secours.

L'implémentation utilise **Web Crypto natif** sur le Web et les primitives **javax.crypto** sur JVM/Android source. Elle ne réimplémente ni AES, ni PBKDF2. Les tampons mutables sont effacés au mieux, mais JavaScript, Kotlin, les bibliothèques et leurs copies de chaînes ne permettent pas de garantir l'effacement de toute trace mémoire. Les gestionnaires de mots de passe du navigateur/système restent sous le contrôle de l'utilisateur.

Web Crypto exige un contexte pris en charge : HTTPS ou une origine locale reconnue comme sûre. Si l'API est absente, le bouton indique l'indisponibilité et ne crée pas de fichier en clair. L'import n'accepte pas les ZIP/RAR logiciels ni les formats chiffrés d'autres applications. Une ancienne version de FranchiseGuide ne lit pas `.fgbackup`.

## Preuves et limites

Les tests ont exécuté les vraies primitives Node WebCrypto et JVM, puis échangé des fichiers dans les deux sens avec phrases ASCII, accents, espaces, caractères combinés et emoji. Les tests UI utilisent les vrais scripts et DOM Chromium mais un **pont de test vers Node WebCrypto** : la navigation vers une origine HTTPS/locale sûre est bloquée dans cet environnement. Aucun contournement du blocage n'a été effectué.

Aucune certification FIPS, aucun audit cryptographique indépendant ni garantie d'absence de bugs n'est annoncé. Le rapport QA détaille les régressions et les vérifications non disponibles.

## Références techniques

Consultées pour cette passe :
- Web Crypto deriveKey/PBKDF2 : https://developer.mozilla.org/en-US/docs/Web/API/SubtleCrypto/deriveKey
- Paramètres AES-GCM, IV, AAD et tag : https://developer.mozilla.org/en-US/docs/Web/API/AesGcmParams
- Java GCMParameterSpec : https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html
- OWASP Cryptographic Storage : https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html
- OWASP Password Storage, discussion PBKDF2-HMAC-SHA256 : https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html

Ces références expliquent les primitives et compromis. Leur présence ne vaut pas validation du logiciel ou de son intégration.
