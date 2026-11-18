# D-Lock

Verrouillage d'applis pour Android. Une application **Thousand Labs**.

**Télécharger la dernière version : [D-Lock.apk](https://github.com/ThousandLabs/D-Lock/releases/latest/download/D-Lock.apk)**

Site officiel : https://thousandlabs.github.io

## Fonctions

- **YouTube réservé au VPN** : l'appli YouTube officielle ne s'ouvre que si un VPN (Privado VPN par défaut) est activé. Sans VPN, un écran de blocage propose d'activer le VPN en un appui ; YouTube se rouvre tout seul dès la connexion. Si le VPN se coupe pendant la lecture, le blocage revient aussitôt.
- Accès exceptionnel à YouTube sans VPN avec votre code (désactivable).
- Les autres versions de YouTube (ReVanced…) ne sont jamais concernées par la règle VPN.
- **Verrouillage des autres applis** au choix, par code PIN, schéma, mot de passe ou empreinte, sans VPN.
- Une appli déverrouillée le reste tant qu'on ne la quitte pas ; tout se reverrouille quand l'écran s'éteint.
- D-Lock lui-même est protégé par le même code.
- Blocage temporaire après 5 erreurs (30 s, puis de plus en plus long).
- Guide « Comment faire » intégré et mises à jour intégrées.
- Aucune donnée envoyée : D-Lock voit seulement le nom de l'appli ouverte, et Internet ne sert qu'à vérifier les mises à jour.

## Installation (une seule fois)

1. Installez `D-Lock.apk` et créez votre code.
2. Dans D-Lock : **Autoriser l'accès aux données d'utilisation** › D-Lock › activer (pour savoir quelle appli est ouverte).
3. Dans D-Lock : **Autoriser l'affichage par-dessus** › activer (pour afficher l'écran de verrouillage).
4. Dans D-Lock : **Autoriser en arrière-plan**. Sur HiOS (Tecno), autorisez aussi le démarrage automatique dans Phone Master si besoin.

Une notification discrète « D-Lock actif » reste affichée : Android l'exige pour que la surveillance continue.

## Nouveautés

- **1.2** : D-Lock n'utilise plus le service d'accessibilité, que Google Play Protect bloquait à l'installation. Il fonctionne avec « Accès aux données d'utilisation » et « Affichage par-dessus les autres applis », sans paramètres restreints à débloquer ; relance automatique après un redémarrage du téléphone.
- **1.1** : première version. YouTube officiel réservé au VPN avec retour automatique après connexion et accès exceptionnel par code ; verrouillage des autres applis par code PIN, schéma, mot de passe ou empreinte ; D-Lock protégé par le même code ; blocage après 5 erreurs ; mises à jour intégrées.
