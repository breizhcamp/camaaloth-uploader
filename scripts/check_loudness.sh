#!/bin/bash
# Compare the loudness of every video under a directory, one line each.
#
# extract_data.sh reports a single file in full; this walks a whole edition and keeps only the two
# numbers worth comparing, so an odd talk stands out without reading a hundred reports.
set -uo pipefail

root="${1:-videos}"

if [ ! -d "${root}" ]; then
    echo "Usage: $(basename "$0") [directory]   (default: videos)" >&2
    exit 1
fi

target_i="-23"
target_tp="-3"

printf '%8s %8s  %s\n' "I(LUFS)" "TP(dBFS)" "FICHIER"

# a plain while loop rather than mapfile: macOS still ships bash 3.2, which has no mapfile
find "${root}" -name '*.mp4' -print0 | sort -z | while IFS= read -r -d '' video; do
    report=$(ffmpeg -hide_banner -nostats -i "${video}" -af ebur128=peak=true -f null - 2>&1 | tail -30)
    integrated=$(printf '%s\n' "${report}" | awk '/^ +I: +/ {print $2}' | tail -1)
    peak=$(printf '%s\n' "${report}" | awk '/^ +Peak: +/ {print $2}' | tail -1)

    # a file ffmpeg could not measure would otherwise print an empty, confusing line
    printf '%8s %8s  %s\n' "${integrated:-?}" "${peak:-?}" "${video#"${root}"/}"
done

echo
echo "Cibles : I ${target_i} LUFS, True peak ${target_tp} dBFS."
echo "Un écart de plus de 1 LU sur I s'entend au passage d'une vidéo à l'autre."
