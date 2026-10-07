#!/bin/bash
# Compare the loudness of every video under a directory, one line each.
#
# extract_data.sh reports a single file in full; this walks a whole edition and keeps only the two
# numbers worth comparing, so an odd talk stands out without reading a hundred reports.
#
# Only the normalized videos are measured, being the ones uploaded; the originals still waiting for
# scripts/normalize.sh are listed afterwards.
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

# a plain while loop rather than mapfile: macOS still ships bash 3.2, which has no mapfile.
# Hidden files are left out: the normalizations in progress, and the ._*.mp4 resource forks macOS
# leaves on an exFAT drive. stdin is closed, otherwise ffmpeg reads it and swallows the file list
find "${root}" -type f -name '*.normalized.mp4' ! -name '.*' -print0 | sort -z | while IFS= read -r -d '' video; do
    report=$("${FFMPEG}" -nostdin -hide_banner -nostats -i "${video}" -af ebur128=peak=true -f null - 2>&1 | tail -30)
    integrated=$(printf '%s\n' "${report}" | awk '/^ +I: +/ {print $2}' | tail -1)
    peak=$(printf '%s\n' "${report}" | awk '/^ +Peak: +/ {print $2}' | tail -1)

    # a file ffmpeg could not measure would otherwise print an empty, confusing line
    printf '%8s %8s  %s\n' "${integrated:-?}" "${peak:-?}" "${video#"${root}"/}"
done

# originals without their normalized version: never uploaded until scripts/normalize.sh runs
pending=$(find "${root}" -type f -name '*.mp4' ! -name '.*' ! -name '*.normalized.mp4' -print0 | sort -z \
    | while IFS= read -r -d '' video; do
        [ -f "${video%.mp4}.normalized.mp4" ] || printf '  %s\n' "${video#"${root}"/}"
    done)
if [ -n "${pending}" ]; then
    echo
    echo "Pas encore normalisées, donc ni mesurées ni envoyables :"
    printf '%s\n' "${pending}"
fi

echo
echo "Cibles : I ${target_i} LUFS, True peak ${target_tp} dBFS."
echo "Un écart de plus de 1 LU sur I s'entend au passage d'une vidéo à l'autre."
