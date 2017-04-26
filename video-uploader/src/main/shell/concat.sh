#!/usr/bin/env bash

#if using -s to remove video from beginning, only cut to a keyframe will work
# you can obtain keyframe with the command : ffprobe -select_streams v -show_frames -show_entries frame=pict_type -of csv video.mp4 | grep -n I

#convert intro :
#ffmpeg -i BZC.avi -c:a aac -ar 48000 -af "volume=0.2" -c:v libx264 -preset slow -profile:v main -r 25 -vf "scale=1280:720" -pix_fmt yuvj420p -color_primaries bt709 -color_trc bt709 -colorspace bt709 bzc.mp4
#ffmpeg -i bzc.mp4 -map 0 -c copy -f mpegts -bsf h264_mp4toannexb -absf aac_adtstoasc -y intro.ts

while getopts ":f:s:t:" option; do
    case "$option" in
        h)   echo "Usage $0: [-f file] [-s start sec] [-t timestamp stop]";;
        f)   INPUT=$OPTARG;;
        s)   START=$OPTARG;;
        t)   TO=$OPTARG;;
        :)   echo "Option -$OPTARG requires an argument !" >&2;;
        \?)  echo "Unsupported option: -$OPTARG !" >&2;;
    esac
done

NAME="$(basename "${INPUT}" .mp4)"
DIR="$(dirname "${START}")"

ARGS=""

if [ -n "${START}" ]; then
	ARGS="-ss 00:00:${START}.0"
fi

if [ -n "${TO}" ]; then
	ARGS="${ARGS} -to ${TO}"
fi

ffmpeg -i "${INPUT}" ${ARGS} -map 0 -c copy -f mpegts -bsf h264_mp4toannexb -absf aac_adtstoasc -y conf.ts
ffmpeg -i "concat:intro.ts|conf.ts" -c copy -y "${DIR}/${NAME}_intro.mp4"

rm conf.ts