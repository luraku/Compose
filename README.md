# Compose

[![Licence](https://img.shields.io/badge/Licence-Apache%202.0-blue)](LICENSE)
[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-pocketdock-FFDD00?logo=buymeacoffee&logoColor=black)](https://buymeacoffee.com/pocketdock)
[![Proudly made with Claude](https://img.shields.io/badge/Proudly%20made%20with-Claude-D97757?logo=anthropic&logoColor=white)](https://claude.ai/code)

Application Android pour **composer une image à partir de plusieurs photos** : on importe des images depuis la galerie, on les place, redimensionne, fait pivoter et superpose au doigt, puis on exporte le résultat en PNG.

Écrite en Kotlin avec Jetpack Compose, sans serveur ni compte : tout se passe sur l'appareil.

## Fonctionnalités

- **Import multiple** depuis la galerie, via le sélecteur de photos système (aucune permission de stockage requise).
- **Gestes tactiles** : glisser pour déplacer, pincer pour agrandir ou réduire, deux doigts pour la rotation centrale.
- **Rotation axiale** autour de l'axe vertical ou horizontal de l'image, avec perspective.
- **Calques** : ordre devant/derrière, miroir, duplication, suppression, opacité.
- **Page** : formats 1:1, 4:3, 3:4, 16:9 et 9:16 ; fond transparent, blanc, noir ou gris.
- **Export PNG** de 2048 px sur le grand côté, enregistré dans `Images/Compose`. Le rendu est identique à l'aperçu, car les deux utilisent le même code de dessin.

## Utilisation

1. **Ajouter** : choisir une ou plusieurs photos.
2. Toucher une image pour la sélectionner, puis la déplacer, la pincer ou la faire pivoter.
3. Onglet **Ajuster** : réglages précis (taille, rotation, axes, opacité).
4. Onglet **Calques** : ordre, miroir, duplication, suppression.
5. Onglet **Page** : format et fond.
6. **Enregistrer** : le PNG est écrit dans la galerie.

## Installer l'application

L'APK est construit par GitHub Actions (workflow *Build APK*). Pour le récupérer :

1. Ouvrir l'onglet **Actions** du dépôt et choisir le dernier run réussi.
2. Télécharger l'artefact `compose-apk`, puis installer l'APK sur le téléphone (autoriser l'installation depuis des sources inconnues).

Android 10 (API 29) ou supérieur est requis.

## Compiler soi-même

Prérequis : JDK 17 et le SDK Android (plateforme 35).

```sh
./gradlew assembleRelease
```

L'APK est généré dans `app/build/outputs/apk/release/`. Il est signé avec la clé de debug : il convient pour un usage personnel, mais pas pour une publication sur le Play Store.

## Structure du projet

| Fichier | Rôle |
| --- | --- |
| `app/src/main/java/com/luraku/compose/MainActivity.kt` | Interface Compose et gestes tactiles |
| `ComposerViewModel.kt` | État de la composition, chargement des images, export PNG |
| `Renderer.kt` | Dessin des calques (aperçu et export), inclinaison 3D, sélection |
| `Layer.kt` | Modèles : calque, format de page, fond |

## Limites connues

- Un geste commencé hors de toute image agit sur l'image déjà sélectionnée.
- La sélection au doigt ignore l'inclinaison 3D : elle est approximative pour une image très inclinée.
- Les images importées sont réduites à 2048 px maximum sur leur grand côté.

## Soutenir le projet

Si l'application vous est utile, vous pouvez soutenir son développement sur [Buy Me a Coffee](https://buymeacoffee.com/pocketdock) (pocketdock). Merci !

## Licence

Distribué sous licence [Apache 2.0](LICENSE).

---

Proudly made with [Claude](https://claude.ai/code).
