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

### Utiliser l'interface

Trois zones : **Gestion Locale** à gauche, **YouTube** à droite, la **liste des vidéos** en dessous.

#### Gestion Locale

Dans l'ordre d'un début d'édition :

1. **Corriger les IDs manquants** — donne un UUID aux events de `schedule.json` qui n'en ont pas.
   À lancer **avant** de créer les répertoires : leur nom se termine par l'id, et c'est lui qui relie
   un répertoire à son talk. Sans id, la vidéo est inuploadable.
   ⚠️ L'UUID est aléatoire. Re-télécharger le schedule puis recliquer produira des ids **différents**,
   sans correspondance avec les répertoires et les `metadata.json` déjà écrits.
2. **Créer / Recréer les répertoires** — un répertoire par talk dans `recordingDir`.
3. **Générer les miniatures** — voir la section suivante.
4. **Exporter schedule.json** — écrit dans `recordingDir` une copie du schedule enrichie du
   `video_url` de chaque vidéo uploadée.

#### YouTube

**S'authentifier sur YouTube** ouvre le consentement Google, avec sélecteur de compte. Une fois
connecté, **Changer** relance ce choix et **Déconnecter** efface le token enregistré.

La **playlist** choisie est mémorisée dans `recordingDir/playlist.json` et resélectionnée au
démarrage suivant, à condition que la chaîne la possède toujours. Une playlist supprimée côté YouTube
est ignorée, avec un avertissement dans les logs.

Si le compte est joignable mais inutilisable — aucune chaîne, plusieurs chaînes sans choix possible,
ou aucune playlist — une fenêtre l'explique et propose **Se déconnecter et se reconnecter**, qui
efface le token et repart directement sur le consentement Google.

Si un token est enregistré mais que Google le refuse, la carte propose **Effacer le token
enregistré**. C'est la seule sortie : supprimer `videos/.datastore/StoredCredential` à la main
pendant que l'application tourne n'a aucun effet, le fichier est chargé en mémoire au démarrage.

#### Liste des vidéos

La colonne **État** montre trois icônes — vidéo, description, miniature — chacune avec sa marque :

| Marque | Sens |
|---|---|
| `−` gris | rien n'est parti |
| `⟳` bleu | en cours |
| `✓` vert | envoyé à YouTube |
| `✗` rouge | en erreur |

Le pourcentage d'avancement s'affiche sous les icônes pendant l'envoi d'une vidéo.

Chaque ligne porte jusqu'à quatre actions : envoyer la vidéo, renvoyer la description, renvoyer la
miniature, ouvrir la vidéo sur YouTube. Les boutons de ligne **forcent** l'envoi, même si c'est déjà
fait — c'est ce qui permet de corriger une description modifiée dans le schedule.

Les boutons globaux **Pousser les descriptions** et **Pousser les miniatures** ne traitent que ce qui
n'est pas déjà `✓`, et affichent le nombre restant entre parenthèses. À zéro ils sont grisés : rien à
faire et bouton cassé se ressemblent trop pour laisser le doute.

**Tout envoyer** exige qu'une playlist soit sélectionnée.

#### Filtrer et trier

Au-dessus du tableau : un champ **Nom** et une liste par état, cumulables, avec un compteur
`12 / 96 vidéos` et un bouton **Effacer**. Un clic sur un en-tête trie sur cette colonne, un
deuxième inverse le sens. Les états se trient par avancement, donc l'ordre croissant remonte ce qui
reste à traiter.

Tout se passe dans le navigateur, et l'état se retrouve dans l'URL — une vue se recharge et se
partage :

```
http://localhost:8080/?nom=micronaut&description=NOT_STARTED&tri=dirName&sens=desc
```

Paramètres : `nom`, `video`, `description`, `miniature` (valeurs `NOT_STARTED`, `IN_PROGRESS`,
`DONE`, `FAILED`), `tri` (`dirName`, `video`, `description`, `thumbnail`) et `sens` (`desc`).

#### Opérations longues

La génération des miniatures, les deux poussées et les envois de vidéos affichent une barre de
progression entre les cartes et la liste, une par opération en cours. Recharger la page pendant une
opération retrouve la barre là où elle en est.

### Génération des thumbnails

Copier le modèle de thumbnails dans `assets/thumb.svg`.
Il doit contenir les chaines `TitreTalk` et `SpeakersTalk` qui seront remplacé par le générateur

Le plus simple est le bouton **Générer les miniatures** de l'interface, qui affiche l'avancement et
recharge la liste à la fin. En ligne de commande, c'est le même traitement :

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

### Fichiers écrits à côté des vidéos

Dans `recordingDir` :

```
<talk>/thumb.png        la miniature, générée
<talk>/metadata.json    l'état de la vidéo
playlist.json           la playlist sélectionnée
schedule.json           l'export du bouton « Exporter schedule.json »
```

Un `metadata.json` complet :

```json
{
  "status": "DONE",
  "youtubeId": "abc123",
  "descriptionStatus": "DONE",
  "thumbnailStatus": "NOT_STARTED"
}
```

`status` suit l'upload : `NOT_STARTED`, `WAITING`, `INITIALIZING`, `IN_PROGRESS`, `THUMBNAIL`,
`DONE`, `FAILED`.

`descriptionStatus` et `thumbnailStatus` valent `NOT_STARTED`, `IN_PROGRESS`, `DONE` ou `FAILED`.
Absents du fichier, ils valent `NOT_STARTED` : les fichiers écrits avant leur existence restent
lisibles, rien à migrer. `DONE` signifie qu'un envoi a réellement eu lieu — un talk sans description
au schedule, ou sans `thumb.png` sur le disque, reste à `NOT_STARTED` et sera repris plus tard.

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

Supprimer les metadata des videos en attente après un redémarrage :

```bash
rg -l '"status": ?"WAITING"' --glob '**/metadata.json' --null | xargs -0 rm
```

⚠️ Le motif exact `{"status":"WAITING"}` ne fonctionne plus : le fichier contient désormais aussi
`descriptionStatus` et `thumbnailStatus`.

Refaire tous les envois de description et de miniature, sans toucher aux vidéos déjà uploadées : il
suffit de retirer les deux champs, ils sont relus comme `NOT_STARTED`.

```bash
find . -name metadata.json -exec sh -c \
  'jq "del(.descriptionStatus, .thumbnailStatus)" "$1" > "$1.new" && mv "$1.new" "$1"' _ {} \;
```

