#!/bin/bash
# Compare the loudness of every video under a directory, one line each.
#
# extract_data.sh reports a single file in full; this walks a whole edition and keeps only the two
# numbers worth comparing, so an odd talk stands out without reading a hundred reports.
set -uo pipefail

root="${1:-videos}"
FFMPEG="${FFMPEG:-ffmpeg}"

if [ ! -d "${root}" ]; then
    echo "Usage: $(basename "$0") [directory]   (default: videos)" >&2
    exit 1
fi

# without this, a missing ffmpeg reads as a whole edition of unmeasurable files: every line comes
# back with a question mark, instantly, which looks like a problem with the videos
if ! command -v "${FFMPEG}" >/dev/null 2>&1; then
    echo "ffmpeg not found: ${FFMPEG}" >&2
    echo "Install it, or point FFMPEG at it: FFMPEG=/opt/homebrew/bin/ffmpeg $(basename "$0") ..." >&2
    exit 1
fi

target_i="-23"
target_tp="-3"

printf '%8s %8s  %s\n' "I(LUFS)" "TP(dBFS)" "FICHIER"

# a plain while loop rather than mapfile: macOS still ships bash 3.2, which has no mapfile
find "${root}" -name '*.mp4' -print0 | sort -z | while IFS= read -r -d '' video; do
    report=$("${FFMPEG}" -hide_banner -nostats -i "${video}" -af ebur128=peak=true -f null - 2>&1 | tail -30)
    integrated=$(printf '%s\n' "${report}" | awk '/^ +I: +/ {print $2}' | tail -1)
    peak=$(printf '%s\n' "${report}" | awk '/^ +Peak: +/ {print $2}' | tail -1)

    # a file ffmpeg could not measure would otherwise print an empty, confusing line
    printf '%8s %8s  %s\n' "${integrated:-?}" "${peak:-?}" "${video#"${root}"/}"
done

echo
echo "Cibles : I ${target_i} LUFS, True peak ${target_tp} dBFS."
echo "Un écart de plus de 1 LU sur I s'entend au passage d'une vidéo à l'autre."
