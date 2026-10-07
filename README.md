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

À tout moment, **Sauvegarder les métadata** zippe tous les `.json` de `recordingDir`, à toute
profondeur — les `metadata.json` qui portent l'état de chaque upload, `playlist.json`, l'export
`schedule.json` — dans un `metadata-backup-AAAAMMJJ-HHMMSS.zip` posé dans ce même répertoire. Les
fichiers cachés sont ignorés (`._metadata.json` de macOS, `.datastore` du token). Un message sous les
boutons donne le nombre de fichiers et le nom du zip. À faire avant une manipulation risquée, comme
le `jq` des *Tips and tricks* : pour revenir en arrière, `unzip -o` du zip dans `recordingDir`.

**Supprimer les métadonnées YouTube** fait oublier tous les uploads, pour tout renvoyer depuis le
début. Dans chaque `.json` de `recordingDir`, `youtubeId` et `video_url` sont effacés où qu'ils
soient, et chaque `metadata.json` repasse à `NOT_STARTED`, sans `progression`, `descriptionStatus`
ni `thumbnailStatus`. Le reste est gardé : `loudness`, `playlist.json`, les autres champs du schedule
exporté. Les vidéos déjà en ligne **restent sur YouTube** : les renvoyer crée des doublons.

L'opération demande de recopier *« Oui, je suis bien un boulet et je veux reprendre les uploads à
zéro »*, vérifié aussi côté serveur. Elle commence par la même sauvegarde que le bouton précédent,
et ne touche à rien si celle-ci échoue. Elle est refusée pendant qu'une opération longue tourne : un
upload en cours réécrirait son `youtubeId` juste après. Un fichier illisible est laissé tel quel, et
signalé dans le message.

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

### Traiter une autre édition

L'application ne lit qu'un seul schedule : `<assetsDir>/schedule.json`. Pour reprendre les vidéos
d'une édition passée, lui donner son propre répertoire d'assets plutôt que d'écraser le courant :

```bash
mkdir -p assets-2025
cp 2025-schedule.json assets-2025/schedule.json    # le nom du fichier compte
cp assets/thumb.svg   assets-2025/                 # modèle de l'époque si tu l'as

java -jar build/libs/camaaloth-uploader-0.0.1-SNAPSHOT.jar \
  --camaaloth-uploader.assetsDir=assets-2025 \
  --camaaloth-uploader.recordingDir=/chemin/vers/les/videos/2025
```

Un event absent du schedule chargé est signalé ainsi, et la vidéo est sautée :

```
[838332] Not in the schedule, skipped. Is assetsDir pointing at the right edition?
```

C'est le symptôme d'un `assetsDir` qui pointe sur la mauvaise édition — à ne pas confondre avec
`No description in the schedule`, qui veut dire que l'event est bien là mais sans description.

### Fichiers écrits à côté des vidéos

Dans `recordingDir` :

```
<talk>/1080p.mp4             l'enregistrement original, jamais envoyé
<talk>/1080p.normalized.mp4  la vidéo au son normalisé, la seule envoyée sur YouTube
<talk>/thumb.png             la miniature, générée
<talk>/metadata.json         l'état de la vidéo
playlist.json                la playlist sélectionnée
metadata-backup-*.zip        les sauvegardes du bouton « Sauvegarder les métadata »
schedule.json                l'export du bouton « Exporter schedule.json »
```

Un `metadata.json` complet :

```json
{
  "status": "DONE",
  "youtubeId": "abc123",
  "descriptionStatus": "DONE",
  "thumbnailStatus": "NOT_STARTED",
  "loudness": { "integrated": -23.0, "truePeak": -16.8 }
}
```

`status` suit l'upload : `NOT_STARTED`, `WAITING`, `INITIALIZING`, `IN_PROGRESS`, `THUMBNAIL`,
`DONE`, `FAILED`.

`descriptionStatus` et `thumbnailStatus` valent `NOT_STARTED`, `IN_PROGRESS`, `DONE` ou `FAILED`.
Absents du fichier, ils valent `NOT_STARTED` : les fichiers écrits avant leur existence restent
lisibles, rien à migrer. `DONE` signifie qu'un envoi a réellement eu lieu — un talk sans description
au schedule, ou sans `thumb.png` sur le disque, reste à `NOT_STARTED` et sera repris plus tard.

`loudness` est écrit par `scripts/normalize.sh` et seulement lu par l'application, qui le conserve
quand elle réécrit le fichier. Absent, la vidéo n'est pas passée par le script.

### Normalisation du son des vidéos

L'application n'envoie **jamais** l'enregistrement original : seulement sa version au son
normalisé, écrite à côté par `scripts/normalize.sh`. Le script prend le répertoire de l'édition :

```bash
scripts/normalize.sh /Volumes/BrzhCampZ1/2026
```

Chaque `1080p.mp4` donne un `1080p.normalized.mp4` dans le même répertoire ; l'original n'est
jamais modifié. Prévoir donc le double de place sur le disque : la vidéo est recopiée telle quelle,
seul le son est réencodé.

Tant que la version normalisée manque, la colonne **Son** affiche « à normaliser » et le bouton
d'envoi reste grisé ; **Tout envoyer** passe ces vidéos sans les envoyer. Si l'original a été
supprimé pour gagner de la place, la vidéo normalisée seule suffit.

Le script se relance sans risque : une vidéo qui a déjà sa version normalisée est sautée. La vidéo
en cours s'écrit dans un fichier caché, `.1080p.normalizing.mp4`, renommé à la fin : l'application ne
la voit pas tant qu'elle n'est pas finie, et une interruption ne laisse pas de vidéo tronquée prise
pour terminée. Le script sort en erreur si une vidéo a échoué.

Chaque vidéo normalisée est ensuite mesurée, et son niveau rangé dans le `metadata.json` de son
répertoire, sans toucher aux autres champs :

```json
{
  "status": "NOT_STARTED",
  "loudness": { "integrated": -23.0, "truePeak": -16.8 }
}
```

L'application l'affiche dans la colonne **Son** de la liste — *integrated* en LUFS, *true peak* en
dBFS — en vert dans la cible, en rouge au-delà de 1 dB d'écart (-23 LUFS, et pas plus de -3 dBFS de
pic), et `?` pour une vidéo normalisée dont le son n'a pas pu être mesuré. Un clic sur l'en-tête trie
sur l'*integrated*, les vidéos non mesurées en premier. Une vidéo
déjà normalisée mais sans mesure, par une version précédente du script, est mesurée au passage
suivant. La mesure demande `ffmpeg` et `jq`, surchargeables par `FFMPEG` et `JQ`.

Il faut avoir installé https://github.com/slhck/ffmpeg-normalize sur sa machine. C'est disponible
dans un package AUR, ou via pip :

```commandline
yay -S python-ffmpeg-progress-yield ffmpeg-normalize
pipx install ffmpeg-normalize
```

Le binaire utilisé est `ffmpeg-normalize` du `PATH`, surchargeable :

```bash
FFMPEG_NORMALIZE=/chemin/vers/ffmpeg-normalize scripts/normalize.sh /Volumes/BrzhCampZ1/2026
```

La variable peut contenir une commande en plusieurs mots, pour passer par un lanceur sans rien
installer :

```bash
FFMPEG_NORMALIZE="uv tool run ffmpeg-normalize" scripts/normalize.sh /Volumes/BrzhCampZ1/2026
```

### Vérification du niveau sonore

Pour comparer toutes les vidéos d'un coup et repérer celle qui détonne :

```bash
scripts/check_loudness.sh /Volumes/BrzhCampZ1/2026
```

Seules les vidéos normalisées sont mesurées, puisque ce sont elles qui partent sur YouTube : une ligne
par fichier, rien n'est réencodé. Les originaux qui n'ont pas encore leur version normalisée sont
listés à la suite :

```
 I(LUFS) TP(dBFS)  FICHIER
   -23.1     -3.0  24.Amphi A.10-00 - … - 1181830/1080p.normalized.mp4
   -22.8     -4.2  24.Amphi B.13-30 - … - 1171038/1080p.normalized.mp4

Pas encore normalisées, donc ni mesurées ni envoyables :
  24.Amphi A.13-30 - … - 1173898/1080p.mp4
```

Cibles : *Integrated loudness* -23 LUFS et *True peak* -3 dBFS. Un écart de plus de 1 LU sur `I`
s'entend au passage d'une vidéo à l'autre. Un `?` signale un fichier que ffmpeg n'a pas su mesurer.

Compter environ une minute par heure de vidéo : le fichier est lu en entier, sans être réencodé.

Le binaire est `ffmpeg` du `PATH`, surchargeable — utile depuis un terminal d'IDE ou une tâche
planifiée, où le `PATH` de Homebrew est souvent absent :

```bash
FFMPEG=/opt/homebrew/bin/ffmpeg scripts/check_loudness.sh /Volumes/BrzhCampZ1/2026
```

Pour le rapport complet d'un seul fichier — *Integrated loudness*, *Loudness range* et *True peak* :

```bash
scripts/extract_data.sh target/videos/talk.mp4
```

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

