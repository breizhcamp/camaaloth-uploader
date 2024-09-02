## Outil d'upload des vidéos du BreizhCamp

### Configuration

Télécharger le schedule.json de l'année en cours

```bash
curl -o assets/schedule.json https://raw.githubusercontent.com/breizhcamp/website/production/static/json/schedule.json
```

Créer un client OAuth

https://console.cloud.google.com

### Génération des thumbnails

Copier le modèle de thumbnails dans `assets/thumb.svg`.
Il doit contenir les chaines `TitreTalk` et `SpeakersTalk` qui seront remplacé par le générateur

Lancer `org.breizhcamp.video.uploader.thumb.ThumbGeneratorKt`

### Parametres

```
--camaaloth-uploader.recordingDir=REPERTOIRE 
```

### Normalisation du son des vidéos

Utiliser `scripts/normalize.sh` pour normaliser le son des vidéos avant upload YouTube 

Il faut avoir installé https://github.com/slhck/ffmpeg-normalize sur sa machine. C'est disponible dans un package AUR:

```commandline
yay -S python-ffmpeg-progress-yield ffmpeg-normalize
```
