#!/usr/bin/env sh

if ! type "ffmpeg" > /dev/null; then
  echo "FFMPEG missing"
fi

if ! type "streamer" > /dev/null; then
  echo "streamer missing"
fi

if ! type "motion" > /dev/null; then
  echo "motion missing"
fi