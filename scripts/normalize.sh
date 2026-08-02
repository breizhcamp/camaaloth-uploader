#!/bin/bash
FFMPEG_NORMALIZE="${FFMPEG_NORMALIZE:-ffmpeg-normalize}"

mkdir -p target/videos

mapfile -d '' videos < <(find src -name '*.mp4' -print0)
for video in "${videos[@]}"; do
    echo "Processing ${video}"
    output_video="$(dirname "${video}" | sed -e 's|^src/||' -e 's|/.*||')"

    if [ -f "target/videos/${output_video}.mp4" ]; then
        echo "== Already normalized ${output_video}"
        continue
    fi

    echo "== Normalized ${output_video}"
    "${FFMPEG_NORMALIZE}" "${video}" \
        -pr \
        -c:a aac -ar 48000 -b:a 96K \
        -tp -3 -lrt 12 \
        -f -o "target/videos/${output_video}.mp4"
done
