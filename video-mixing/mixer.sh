#!/bin/bash -eu
set -o pipefail

HERE=$(readlink -f "$(dirname $0)")

BOX1_X=30
BOX1_Y=100
BOX1_W=505
BOX1_H=575
BOX2_W=718

SPEAKER_FILE=$1
SLIDE_FILE=$2

gst-launch-1.0 -vv \
  videomixer name=mixer sink_1::xpos=$BOX1_X sink_1::ypos=$BOX1_Y sink_2::xpos=$(($BOX1_X + $BOX1_W)) sink_2::ypos=$BOX1_Y ! video/x-raw,width=1280,height=720,framerate=25/1 ! videoconvert ! autovideosink \
  filesrc location="$HERE/template.png" ! decodebin ! imagefreeze ! video/x-raw,width=1280,height=720,framerate=25/1 ! videoconvert ! queue2 ! mixer.sink_0 \
  filesrc location="$SPEAKER_FILE" ! decodebin ! aspectratiocrop aspect-ratio=$BOX1_W/$BOX1_H ! videorate ! autovideoconvert ! videoscale ! video/x-raw,width=$BOX1_W,height=$BOX1_H,framerate=25/1 ! autovideoconvert ! queue2 ! mixer.sink_1 \
  filesrc location="$SLIDE_FILE"   ! decodebin ! videorate ! autovideoconvert ! videoscale dither=true ! video/x-raw,width=$BOX2_W,framerate=25/1 ! autovideoconvert ! queue2 ! mixer.sink_2
