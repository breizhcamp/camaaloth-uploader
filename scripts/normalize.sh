#!/bin/bash

mkdir -p target/videos target/audiotracks

mapfile -d '' videos < <(find src -name '*.mp4' -print0)
for video in "${videos[@]}" ; do
    echo "Processing ${video}"
    output_video="$(echo $(dirname "$video") | sed -e 's|^src/||' -e 's|/medias/videos$||' -e s'|/|---|g')"

    ffmpeg-normalize "${video}" \
      -pr \
      -c:a aac -ar 48000 -b:a 96K \
      -tp -3 -lrt 12 \
      -f -o "target/videos/${output_video}.mp4"

    ffmpeg -hide_banner -loglevel error -y -i "target/videos/${output_video}.mp4" "target/audiotracks/${output_video}.wav"
    ebur128 --lufs "target/audiotracks/${output_video}.wav"
done
