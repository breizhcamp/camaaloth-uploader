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
# peak=true, sinon la section True peak du rapport n'est tout simplement pas produite
ffmpeg -hide_banner -i "${media}" -af ebur128=framelog=verbose:peak=true -f null - 2>&1
