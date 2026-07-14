#!/bin/bash
# Zaplanowane nagranie 2026-07-14: TVP1 + Polsat News Polityka, 8:00–14:00 (6 h).
# Odpalany przez nohup — przeżywa zamknięcie sesji. Po nagraniu tnie oba
# kanały wg ramówki epg.ovh i zostawia gotowe paczki (tvp1rec/, pnewsrec/).
set -uo pipefail

TOOLS="$(cd "$(dirname "$0")" && pwd)"
BASE="$HOME/nagrania_0714"
DUR=21600   # 6 h
UA="Mozilla/5.0 (Linux; Android 11) ExoPlayerLib/2.19.1"

TVP1_HLS="https://ec06-krk3.cache.orange.pl/dai4/org1/vb/104/tvp1hd/index.m3u8"
PNEWS_DASH="https://lb2-e3-20.pluscdn.pl/lv/1511888/322/dash/52a9b70b/live.mpd"

mkdir -p "$BASE"
LOG="$BASE/scheduler.log"
echo "$(date) — scheduler start, czekam do 08:00" >> "$LOG"

TARGET=$(date -j -f "%Y-%m-%d %H:%M:%S" "$(date +%Y-%m-%d) 08:00:00" +%s)
NOW=$(date +%s)
if [ "$NOW" -ge "$TARGET" ]; then
  echo "$(date) — 08:00 już minęło, startuję OD RAZU" >> "$LOG"
else
  while [ "$(date +%s)" -lt "$TARGET" ]; do sleep 20; done
fi

START_EPOCH=$(date +%s)
echo "$START_EPOCH" > "$BASE/start_epoch.txt"
echo "$(date) — START nagrań (epoch $START_EPOCH), ${DUR}s" >> "$LOG"

rec() {  # rec <url> <out.mp4> <log>
  ffmpeg -y -loglevel warning -user_agent "$UA" -i "$1" -t "$DUR" \
    -vf scale=-2:720 -c:v libx264 -preset veryfast -b:v 2M -maxrate 2.5M -bufsize 5M \
    -c:a aac -b:a 128k -movflags +faststart "$2" 2>"$3"
}

rec "$TVP1_HLS"  "$BASE/tvp1_raw.mp4"  "$BASE/ffmpeg_tvp1.log"  &
P1=$!
rec "$PNEWS_DASH" "$BASE/pnews_raw.mp4" "$BASE/ffmpeg_pnews.log" &
P2=$!
wait $P1; R1=$?
wait $P2; R2=$?
echo "$(date) — nagrania zakończone (tvp1=$R1 pnews=$R2)" >> "$LOG"

echo "$(date) — cięcie TVP1" >> "$LOG"
python3 "$TOOLS/cut_by_epg.py" "$BASE/tvp1_raw.mp4" "$START_EPOCH" "$BASE/tvp1rec" \
  --channel "TVP 1" --name "TVP1 Retro" --number 130 >> "$LOG" 2>&1

echo "$(date) — cięcie Polsat News Polityka" >> "$LOG"
python3 "$TOOLS/cut_by_epg.py" "$BASE/pnews_raw.mp4" "$START_EPOCH" "$BASE/pnewsrec" \
  --channel "Polsat News Polityka" --name "Polsat News Retro" --number 131 \
  --epg "$BASE/tvp1rec/pltv.gz" >> "$LOG" 2>&1

echo "$(date) — GOTOWE: $BASE/tvp1rec + $BASE/pnewsrec" >> "$LOG"
