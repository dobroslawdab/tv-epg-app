#!/bin/bash
# Przedłużenie nagrań 2026-07-14 do 16:00: czeka na koniec części 1 (8:00–14:00,
# ffmpeg z -t 21600), nagrywa część 2 (14:00→16:00), skleja bezstratnie
# (concat -c copy) i tnie CAŁOŚĆ (8 h) wg ramówki na paczki tvp1rec/pnewsrec.
set -uo pipefail

TOOLS="$(cd "$(dirname "$0")" && pwd)"
BASE="$HOME/nagrania_0714"
UA="Mozilla/5.0 (Linux; Android 11) ExoPlayerLib/2.19.1"
TVP1_HLS="https://ec06-krk3.cache.orange.pl/dai4/org1/vb/104/tvp1hd/index.m3u8"
PNEWS_DASH="https://lb2-e3-20.pluscdn.pl/lv/1511888/322/dash/52a9b70b/live.mpd"
LOG="$BASE/scheduler.log"

echo "$(date) — extend: czekam na koniec części 1 (ffmpeg -t 21600)" >> "$LOG"
while pgrep -f "ffmpeg.*nagrania_0714.*raw.mp4" >/dev/null 2>&1; do sleep 20; done
echo "$(date) — część 1 zakończona" >> "$LOG"

# Część 2: do 16:00 (dokładnie; jeśli już po 16:00 — pomiń)
END=$(date -j -f "%Y-%m-%d %H:%M:%S" "$(date +%Y-%m-%d) 16:00:00" +%s)
NOW=$(date +%s)
DUR2=$((END - NOW))
if [ "$DUR2" -gt 30 ]; then
  echo "$(date) — część 2: ${DUR2}s (do 16:00)" >> "$LOG"
  rec2() {
    ffmpeg -y -loglevel warning -user_agent "$UA" -i "$1" -t "$DUR2" \
      -vf scale=-2:720 -c:v libx264 -preset veryfast -b:v 2M -maxrate 2.5M -bufsize 5M \
      -c:a aac -b:a 128k -movflags +faststart "$2" 2>"$3"
  }
  rec2 "$TVP1_HLS"  "$BASE/tvp1_raw2.mp4"  "$BASE/ffmpeg_tvp1_2.log"  &
  Q1=$!
  rec2 "$PNEWS_DASH" "$BASE/pnews_raw2.mp4" "$BASE/ffmpeg_pnews_2.log" &
  Q2=$!
  wait $Q1; wait $Q2
  echo "$(date) — część 2 zakończona" >> "$LOG"

  # Sklejka bezstratna (te same parametry kodowania → concat demuxer -c copy)
  for CH in tvp1 pnews; do
    if [ -s "$BASE/${CH}_raw2.mp4" ]; then
      printf "file '%s'\nfile '%s'\n" "$BASE/${CH}_raw.mp4" "$BASE/${CH}_raw2.mp4" > "$BASE/${CH}_list.txt"
      ffmpeg -y -loglevel error -f concat -safe 0 -i "$BASE/${CH}_list.txt" \
        -c copy -movflags +faststart "$BASE/${CH}_full.mp4" \
        && mv "$BASE/${CH}_full.mp4" "$BASE/${CH}_raw.mp4" \
        && rm -f "$BASE/${CH}_raw2.mp4" "$BASE/${CH}_list.txt"
      echo "$(date) — sklejka $CH OK" >> "$LOG"
    fi
  done
else
  echo "$(date) — po 16:00, pomijam część 2" >> "$LOG"
fi

START_EPOCH=$(cat "$BASE/start_epoch.txt")
echo "$(date) — cięcie TVP1 (całość od 8:00)" >> "$LOG"
python3 "$TOOLS/cut_by_epg.py" "$BASE/tvp1_raw.mp4" "$START_EPOCH" "$BASE/tvp1rec" \
  --channel "TVP 1" --name "TVP1 Retro" --number 130 >> "$LOG" 2>&1

echo "$(date) — cięcie Polsat News Polityka" >> "$LOG"
python3 "$TOOLS/cut_by_epg.py" "$BASE/pnews_raw.mp4" "$START_EPOCH" "$BASE/pnewsrec" \
  --channel "Polsat News Polityka" --name "Polsat News Retro" --number 131 \
  --epg "$BASE/tvp1rec/pltv.gz" >> "$LOG" 2>&1

echo "$(date) — GOTOWE (8 h): $BASE/tvp1rec + $BASE/pnewsrec" >> "$LOG"
