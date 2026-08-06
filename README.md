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

### Lancer l'application

Prérequis : un JDK 21 (voir `.tool-versions`) et un client OAuth configuré comme ci-dessus.

En développement :

```bash
./gradlew bootRun --args="--oauth-google-path /chemin/oauth-google.json"
```

Depuis le jar :

```bash
./gradlew build
java -jar build/libs/camaaloth-uploader-0.0.1-SNAPSHOT.jar --oauth-google-path /chemin/oauth-google.json
```

L'interface web est alors sur http://localhost:8080. Si ce port est déjà pris (Docker, OrbStack…),
l'application refuse de démarrer avec `Port 8080 was already in use` : utiliser `--server.port=PORT`.

Le lien d'authentification YouTube renvoie vers le consentement Google ; le token obtenu est conservé
dans `./videos/.datastore` et réutilisé aux lancements suivants.

Par défaut l'application lit les vidéos dans `videos/` et les assets (`schedule.json`, `thumb.svg`)
dans `assets/`.

### Génération des thumbnails

Copier le modèle de thumbnails dans `assets/thumb.svg`.
Il doit contenir les chaines `TitreTalk` et `SpeakersTalk` qui seront remplacé par le générateur

La génération est un batch séparé de l'application web :

```bash
./gradlew thumb
```

Les options se passent via `--args` :

```bash
./gradlew thumb --args="--camaaloth-uploader.recordingDir=/Volumes/BrzhCampZ1/2026"
./gradlew thumb --args="--inkscape-path=/chemin/vers/inkscape"
```

Depuis l'IDE, lancer la classe `org.breizhcamp.video.uploader.thumb.ThumbGeneratorKt`. À partir du
jar :

```bash
java -Dloader.main=org.breizhcamp.video.uploader.thumb.ThumbGeneratorKt \
  -cp build/libs/camaaloth-uploader-0.0.1-SNAPSHOT.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher
```

Une vignette `thumb.png` de 1280x720 est écrite dans le répertoire de chaque talk, sauf si elle
existe déjà : supprimer le fichier pour le régénérer.

La génération appelle Inkscape. Son chemin est détecté automatiquement (`Inkscape.app` sur macOS,
`/usr/bin/inkscape` sinon, puis le `PATH`) et peut être forcé :

```bash
--inkscape-path /chemin/vers/inkscape     # argument
INKSCAPE_PATH=/chemin/vers/inkscape       # variable d'environnement
```

### Parametres

```
--camaaloth-uploader.recordingDir=REPERTOIRE   # vidéos à traiter (défaut: videos)
--camaaloth-uploader.assetsDir=REPERTOIRE      # schedule.json et thumb.svg (défaut: assets)
--videos.dir=REPERTOIRE                        # emplacement du .datastore du token (défaut: ./videos)
--server.port=PORT                             # port HTTP (défaut: 8080)
--oauth-google-path /chemin/oauth-google.json
--oauth-google '{"installed":{"client_id":"xxx", ...}}'
--inkscape-path /chemin/vers/inkscape
```

⚠️ `--videos.dir` est une propriété distincte de `--camaaloth-uploader.recordingDir` : déplacer les
vidéos ne déplace pas le token, et inversement.

### Normalisation du son des vidéos

Utiliser `scripts/normalize.sh` pour normaliser le son des vidéos avant upload YouTube 

Il faut avoir installé https://github.com/slhck/ffmpeg-normalize sur sa machine. C'est disponible dans un package AUR:

```commandline
yay -S python-ffmpeg-progress-yield ffmpeg-normalize
```

Le binaire utilisé est `ffmpeg-normalize` du `PATH`, surchargeable :

```bash
FFMPEG_NORMALIZE=/chemin/vers/ffmpeg-normalize scripts/normalize.sh
```

### Vérification du niveau sonore

`scripts/extract_data.sh` mesure le loudness EBU R128 d'un fichier, sans rien réencoder :

```bash
scripts/extract_data.sh target/videos/talk.mp4
```

Le résumé en fin de sortie donne l'*Integrated loudness* (cible -23 LUFS), la *Loudness range* et le
*True peak* (cible -3 dBTP).

### Tips and tricks

Supprimer les metadata des videos en attente après un redémarrage:

```
rg -l '\{"status":"WAITING"\}' **/metadata.json  --null| xargs -0 -I {} rm {}
``` 

