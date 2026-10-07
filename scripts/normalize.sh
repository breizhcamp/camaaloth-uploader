#!/bin/bash
# Normalize the audio of every video under a directory, beside the original.
#
# 1080p.mp4 gives 1080p.normalized.mp4 in the same directory: the uploader only ever sends the
# normalized file, and the original recording is never modified. The loudness of each normalized
# video is then measured and stored in the metadata.json of its directory, where the uploader
# shows it.
set -uo pipefail

FFMPEG_NORMALIZE="${FFMPEG_NORMALIZE:-ffmpeg-normalize}"
# split into words, so a launcher works too: FFMPEG_NORMALIZE="uv tool run ffmpeg-normalize"
read -r -a normalize_cmd <<< "${FFMPEG_NORMALIZE}"
FFMPEG="${FFMPEG:-ffmpeg}"
JQ="${JQ:-jq}"

usage() {
    echo "Usage: $(basename "$0") DIRECTORY" >&2
    exit 1
}

[ $# -eq 1 ] || usage
[ -d "$1" ] || { echo "Not a directory: $1" >&2; usage; }
root="$1"

if ! command -v "${normalize_cmd[0]}" >/dev/null 2>&1; then
    echo "ffmpeg-normalize not found: ${FFMPEG_NORMALIZE}" >&2
    echo "Install it, or point FFMPEG_NORMALIZE at it: FFMPEG_NORMALIZE=/path/to/ffmpeg-normalize $(basename "$0") ..." >&2
    exit 1
fi

for tool in "${FFMPEG}" "${JQ}"; do
    if ! command -v "${tool}" >/dev/null 2>&1; then
        echo "${tool} not found, needed to store the loudness of the videos" >&2
        echo "Install it, or point FFMPEG / JQ at it" >&2
        exit 1
    fi
done

# Measure a video and store its loudness in the metadata.json beside it, keeping every other field
store_loudness() {
    local video="$1"
    local metadata report integrated peak
    metadata="$(dirname "${video}")/metadata.json"

    report=$("${FFMPEG}" -nostdin -hide_banner -nostats -i "${video}" -af ebur128=peak=true -f null - 2>&1 | tail -30)
    integrated=$(printf '%s\n' "${report}" | awk '/^ +I: +/ {print $2}' | tail -1)
    peak=$(printf '%s\n' "${report}" | awk '/^ +Peak: +/ {print $2}' | tail -1)
    # a silent track measures -inf, which is no JSON number either
    if ! [[ "${integrated}" =~ ^-?[0-9.]+$ && "${peak}" =~ ^-?[0-9.]+$ ]]; then
        echo "!! Could not measure the loudness of ${video}" >&2
        return 1
    fi

    # status is mandatory for the uploader, which would refuse a file holding only the loudness
    { if [ -f "${metadata}" ]; then cat "${metadata}"; else echo '{"status":"NOT_STARTED"}'; fi; } \
        | "${JQ}" --argjson i "${integrated}" --argjson tp "${peak}" \
            '.loudness = {integrated: $i, truePeak: $tp}' > "${metadata}.new" \
        && mv "${metadata}.new" "${metadata}" \
        || { rm -f "${metadata}.new"; echo "!! Could not write ${metadata}" >&2; return 1; }

    echo "   loudness: I ${integrated} LUFS, TP ${peak} dBFS"
}

failed=0

# a plain while loop rather than mapfile: macOS still ships bash 3.2, which has no mapfile, and the
# script then walked an empty list and exited 0 without normalizing anything.
# Hidden files are left out: the normalizations in progress, and the ._*.mp4 resource forks macOS
# leaves on an exFAT drive.
while IFS= read -r -d '' video; do
    relative="${video#"${root%/}"/}"
    output="${video%.mp4}.normalized.mp4"

    if [ -f "${output}" ]; then
        echo "== Already normalized ${relative}"
        # normalized by an earlier version of this script, or the measure failed last time
        if "${JQ}" -e '.loudness' "$(dirname "${video}")/metadata.json" >/dev/null 2>&1; then
            continue
        fi
        store_loudness "${output}" || failed=$((failed + 1))
        continue
    fi

    echo "== Normalizing ${relative}"
    # written to a hidden file then renamed: the uploader must not see a video still being written,
    # and an interrupted run must not leave a truncated one the next run would take as done.
    # stdin is closed, otherwise ffmpeg reads it and swallows the rest of the file list
    partial="$(dirname "${video}")/.$(basename "${video%.mp4}").normalizing.mp4"
    if "${normalize_cmd[@]}" "${video}" \
        -pr \
        -c:a aac -ar 48000 -b:a 96K \
        -tp -3 -lrt 12 \
        -f -o "${partial}" </dev/null; then
        mv "${partial}" "${output}"
        store_loudness "${output}" || failed=$((failed + 1))
    else
        echo "!! Failed ${relative}" >&2
        rm -f "${partial}"
        failed=$((failed + 1))
    fi
done < <(find "${root}" -type f -name '*.mp4' ! -name '.*' ! -name '*.normalized.mp4' -print0 | sort -z)

if [ "${failed}" -gt 0 ]; then
    echo "${failed} video(s) failed" >&2
    exit 1
fi
