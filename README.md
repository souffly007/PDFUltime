# PDF Ultime

Application Android libre et moderne pour lire et gérer ses documents PDF.

## Version 1.7.0

Nouveautés :

- Accueil réorganisé en catégories : Organiser, Éditer, Convertir, Optimiser et protéger
- **Organiser les pages** : miniatures, réordonner par glisser-déposer, pivoter, dupliquer, supprimer
- **Extraire des pages** avec une plage libre (`1-3,5,8-`), réordonnable (`3,1,1-2`)
- **Images en PDF** (A4 ajusté ou taille de l’image, orientation EXIF respectée)
- **PDF en images** (PNG ou JPEG) dans le dossier de votre choix
- **Compresser** un PDF (recompression des images internes, avant/après affichés avant l’enregistrement)
- **Protéger par mot de passe** (AES 256 bits) avec choix des autorisations, et **Retirer la protection**
- **Filigrane texte** et **numérotation des pages** (gère les pages pivotées)
- **Métadonnées** : lire, modifier ou tout effacer (propriétés + XMP) avant de partager
- Lecteur : **mode nuit** (inversion des couleurs, mémorisé), **aller à la page** (toucher le compteur),
  ouverture des **PDF protégés** par mot de passe
- Menu Partager : un PDF partagé depuis une autre application s’ouvre dans le lecteur
- Correction : « Ajouter du texte » acceptait mal les retours à la ligne et les caractères hors Helvetica

Fonctions déjà présentes :

- Lecture PDF avec navigation horizontale et zoom
- Fusion de plusieurs fichiers PDF
- Signature manuscrite et ajout de texte dans un PDF
- Suppression de pages
- Thèmes clair, sombre et CyanogenMod
- Enregistrement choisi par l’utilisateur après chaque opération, nettoyage des fichiers temporaires
- Interface locale, sans compte, sans réseau

## Importation dans Android Studio

1. Décompressez l’archive.
2. Ouvrez le dossier `PDFUltime-1.7.0-stable` dans Android Studio.
3. Attendez la synchronisation Gradle.
4. Connectez votre smartphone avec le débogage USB activé.
5. Lancez l’application avec **Run**.

## Identité

- Nom : PDF Ultime
- Package : `fr.bonobo.pdfultime`
- Version : 1.7.0 (versionCode 8)

## Dépendances

Uniquement des bibliothèques libres : AndroidX, Material Components, RecyclerView et PdfBox-Android (Apache 2.0).
Aucune dépendance propriétaire, aucun service Google.

Licence : GNU General Public License v3 (GPL v3).
