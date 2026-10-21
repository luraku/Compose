# Compose
App Android (Jetpack Compose) pour composer une image à partir de plusieurs photos.

## Utilisation
- **Ajouter** : choisir une ou plusieurs images dans la galerie.
- **Glisser** : déplacer · **Pincer** : agrandir/réduire · **Deux doigts** : rotation centrale.
- Onglet **Ajuster** : taille, rotation centrale, rotation axiale (axe vertical / horizontal), opacité.
- Onglet **Calques** : sélection, ordre (devant/derrière), miroir, dupliquer, supprimer.
- Onglet **Page** : format (1:1, 4:3, 3:4, 16:9, 9:16) et fond (transparent, blanc, noir, gris).
- **Enregistrer** : valide et enregistre un PNG (2048 px) dans `Images/Compose`.

## Obtenir l'APK
Le workflow GitHub Actions « Build APK » compile l'application ; l'APK est téléchargeable
dans les *Artifacts* du run (`compose-apk`). En local : `./gradlew assembleRelease`
(JDK 17 + Android SDK), l'APK est dans `app/build/outputs/apk/release/`.
Android 10 (API 29) minimum.
