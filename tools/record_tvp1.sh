#!/bin/bash
# Nagrywa kanał TVP1 (HLS) do MP4 720p i tnie na programy wg ramówki epg.ovh.
# Wynikowa paczka (pliki + manifest.json) to gotowy kanał "TVP1 Retro" makiety.
#
# Użycie:  ./record_tvp1.sh <sekundy> [katalog_wyjściowy]
#   np.    ./record_tvp1.sh 14400 ~/tvp1rec     # 4 h
#
# Deploy na urządzenie (emulator lub TV):
#   adb -s <device> shell mkdir -p /sdcard/Android/data/com.uxellence.tv.prod/files/tvp1rec
#   adb -s <device> push <katalog>/program_*.mp4 <katalog>/manifest.json \
#       /sdcard/Android/data/com.uxellence.tv.prod/files/tvp1rec/
set -euo pipefail

DUR="${1:?podaj czas nagrania w sekundach, np. 14400}"
OUT="${2:-$HOME/tvp1rec}"
HLS="https://ec06-krk3.cache.orange.pl/dai4/org1/vb/104/tvp1hd/index.m3u8"

mkdir -p "$OUT"
START_EPOCH=$(date +%s)
echo "$START_EPOCH" > "$OUT/start_epoch.txt"
echo "Start nagrania: $(date) (epoch $START_EPOCH), czas: ${DUR}s → $OUT/tvp1_raw.mp4"

ffmpeg -y -loglevel warning -i "$HLS" -t "$DUR" \
  -vf scale=-2:720 -c:v libx264 -preset veryfast -b:v 2M -maxrate 2.5M -bufsize 5M \
  -c:a aac -b:a 128k -movflags +faststart "$OUT/tvp1_raw.mp4"

echo "Nagranie gotowe. Tnę wg ramówki EPG..."
python3 "$(dirname "$0")/cut_by_epg.py" "$OUT/tvp1_raw.mp4" "$START_EPOCH" "$OUT"

echo "Paczka gotowa w $OUT — patrz nagłówek skryptu (adb push)."
