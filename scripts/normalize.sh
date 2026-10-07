#!/bin/bash
# Normalize the audio of every video under a directory, into a copy of its tree.
#
# The source is never modified: each video is written at the same relative path under the
# destination, and the other files (thumb.png, metadata.json...) are copied alongside, so the
# destination can be handed to the uploader as its recordingDir.
#
# The loudness of each normalized video is then measured and stored in its metadata.json, where the
# uploader shows it.
set -uo pipefail

FFMPEG_NORMALIZE="${FFMPEG_NORMALIZE:-ffmpeg-normalize}"
# split into words, so a launcher works too: FFMPEG_NORMALIZE="uv tool run ffmpeg-normalize"
read -r -a normalize_cmd <<< "${FFMPEG_NORMALIZE}"
FFMPEG="${FFMPEG:-ffmpeg}"
JQ="${JQ:-jq}"

usage() {
    echo "Usage: $(basename "$0") SOURCE_DIR DESTINATION_DIR" >&2
    exit 1
}

[ $# -eq 2 ] || usage
[ -d "$1" ] || { echo "Not a directory: $1" >&2; usage; }

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

# Measure a video and store its loudness in the metadata.json beside it, keeping every other field.
# Starts from the source's metadata.json when the destination has none yet: find reaches 1080p.mp4
# before metadata.json, which would otherwise never be copied.
store_loudness() {
    local video="$1" source_dir="$2"
    local metadata report integrated peak base
    metadata="$(dirname "${video}")/metadata.json"

    report=$("${FFMPEG}" -nostdin -hide_banner -nostats -i "${video}" -af ebur128=peak=true -f null - 2>&1 | tail -30)
    integrated=$(printf '%s\n' "${report}" | awk '/^ +I: +/ {print $2}' | tail -1)
    peak=$(printf '%s\n' "${report}" | awk '/^ +Peak: +/ {print $2}' | tail -1)
    # a silent track measures -inf, which is no JSON number either
    if ! [[ "${integrated}" =~ ^-?[0-9.]+$ && "${peak}" =~ ^-?[0-9.]+$ ]]; then
        echo "!! Could not measure the loudness of ${video}" >&2
        return 1
    fi

    if [ -f "${metadata}" ]; then
        base="${metadata}"
    elif [ -f "${source_dir}/metadata.json" ]; then
        base="${source_dir}/metadata.json"
    else
        base=""
    fi

    # status is mandatory for the uploader, which would refuse a file holding only the loudness
    { if [ -n "${base}" ]; then cat "${base}"; else echo '{"status":"NOT_STARTED"}'; fi; } \
        | "${JQ}" --argjson i "${integrated}" --argjson tp "${peak}" \
            '.loudness = {integrated: $i, truePeak: $tp}' > "${metadata}.new" \
        && mv "${metadata}.new" "${metadata}" \
        || { rm -f "${metadata}.new"; echo "!! Could not write ${metadata}" >&2; return 1; }

    echo "   loudness: I ${integrated} LUFS, TP ${peak} dBFS"
}

[ -d "$2" ] && created="" || created="$2"
mkdir -p "$2" || exit 1
src="$(cd "$1" && pwd -P)"
dest="$(cd "$2" && pwd -P)"

# a destination inside the source would be walked by find, and its videos normalized again
case "${dest}/" in
    "${src}/"*)
        echo "The destination must not be inside the source: ${dest}" >&2
        [ -n "${created}" ] && rmdir "${dest}"
        exit 1
        ;;
esac

failed=0

# a plain while loop rather than mapfile: macOS still ships bash 3.2, which has no mapfile, and the
# script then walked an empty list and exited 0 without normalizing anything
while IFS= read -r -d '' file; do
    relative="${file#"${src}"/}"
    output="${dest}/${relative}"
    mkdir -p "$(dirname "${output}")"

    if [[ "${file}" != *.mp4 ]]; then
        # never overwrite: the uploader updates metadata.json in the destination
        [ -e "${output}" ] || cp -p "${file}" "${output}"
        continue
    fi

    if [ -f "${output}" ]; then
        echo "== Already normalized ${relative}"
        # normalized by an earlier version of this script, or the measure failed last time
        if "${JQ}" -e '.loudness' "$(dirname "${output}")/metadata.json" >/dev/null 2>&1; then
            continue
        fi
        store_loudness "${output}" "$(dirname "${file}")" || failed=$((failed + 1))
        continue
    fi

    echo "== Normalizing ${relative}"
    # written aside then renamed: an interrupted run must not leave a truncated video that the next
    # run would take as already normalized. stdin is closed, otherwise ffmpeg reads it and swallows
    # the rest of the file list
    partial="${output%.mp4}.partial.mp4"
    if "${normalize_cmd[@]}" "${file}" \
        -pr \
        -c:a aac -ar 48000 -b:a 96K \
        -tp -3 -lrt 12 \
        -f -o "${partial}" </dev/null; then
        mv "${partial}" "${output}"
        store_loudness "${output}" "$(dirname "${file}")" || failed=$((failed + 1))
    else
        echo "!! Failed ${relative}" >&2
        rm -f "${partial}"
        failed=$((failed + 1))
    fi
done < <(find "${src}" -type f ! -name '.*' ! -name '*.partial.mp4' -print0 | sort -z)

if [ "${failed}" -gt 0 ]; then
    echo "${failed} video(s) failed" >&2
    exit 1
fi
