#!/bin/bash
# Report the EBU R128 loudness of a media file. Nothing is written, only measured.
media="$1"

if [ -z "${media}" ]; then
    echo "Usage: $(basename "$0") <media file>" >&2
    exit 1
fi

if [ ! -f "${media}" ]; then
    echo "File not found: ${media}" >&2
    exit 1
fi

echo "==== ${media}"
ffmpeg -hide_banner -i "${media}" -af ebur128=framelog=verbose -f null - 2>&1
