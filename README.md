## Outil d'upload des vidéos du BreizhCamp

### Téléchargement du schedule.json

```bash
./fetch-schedule.sh
```

### Configuration auth YT

Créer un client OAuth sur https://console.cloud.google.com. 

Stocker le token dans `src/main/resources/oauth-google.json`

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

### Tips and tricks

Supprimer les metadata des videos en attente après un redémarrage:

```
rg -l '\{"status":"WAITING"\}' **/metadata.json  --null| xargs -0 -I {} rm {}
``` 

