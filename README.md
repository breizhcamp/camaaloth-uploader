## Outil d'upload des vidéos du BreizhCamp

### Téléchargement du schedule.json

```bash
./fetch-schedule.sh
```

### Configuration auth YT

Créer un client OAuth sur https://console.cloud.google.com.

Le JSON du client doit être fourni au lancement, par l'une de ces sources (par ordre de priorité) :

```bash
--oauth-google '{"installed":{"client_id":"xxx", ...}}'   # JSON en ligne de commande
--oauth-google-path /chemin/oauth-google.json             # chemin vers le fichier
OAUTH_GOOGLE='{"installed":{"client_id":"xxx", ...}}'     # variable d'environnement
OAUTH_GOOGLE_PATH=/chemin/oauth-google.json               # variable d'environnement
```

Les deux formes `--option valeur` et `--option=valeur` sont acceptées.

Si aucune de ces sources n'est fournie, l'application refuse de démarrer avec un message listant les
options disponibles. Le fichier `src/main/resources/oauth-google.json` n'est plus lu.

⚠️ Le JSON passé via `--oauth-google` est visible dans `ps` et dans l'historique du shell : sur une
machine partagée, préférer `--oauth-google-path` ou la variable d'environnement `OAUTH_GOOGLE`.

### Génération des thumbnails

Copier le modèle de thumbnails dans `assets/thumb.svg`.
Il doit contenir les chaines `TitreTalk` et `SpeakersTalk` qui seront remplacé par le générateur

Lancer `org.breizhcamp.video.uploader.thumb.ThumbGeneratorKt`

### Parametres

```
--camaaloth-uploader.recordingDir=REPERTOIRE
--oauth-google-path /chemin/oauth-google.json
--oauth-google '{"installed":{"client_id":"xxx", ...}}'
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

