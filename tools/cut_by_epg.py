#!/usr/bin/env python3
"""
Tnie nagranie kanału TV na pliki per-program wg ramówki XMLTV (epg.ovh)
i generuje manifest.json dla kanału "TVP1 Retro" w makiecie (demolive).

Użycie:
  cut_by_epg.py <raw.mp4> <start_epoch_s> <out_dir> [--channel "TVP 1"] [--epg pltv.gz]

- <start_epoch_s>  — unix time startu nagrania (sekundy; z record_tvp1.sh)
- ramówka: pobiera https://epg.ovh/pltv.gz (albo lokalny plik przez --epg)
- programy przycinane do okna nagrania (pierwszy/ostatni mogą być częściowe)
- cięcie: ffmpeg -ss/-t -c copy (keyframe'y co ~2 s przy naszym nagraniu 720p)
"""
import gzip
import json
import re
import subprocess
import sys
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from xml.etree import ElementTree

EPG_URL = "https://epg.ovh/pltv.gz"


def parse_xmltv_time(s: str) -> float:
    # "20260713140000 +0200"
    m = re.match(r"(\d{14})\s*([+-]\d{4})?", s)
    dt = datetime.strptime(m.group(1), "%Y%m%d%H%M%S")
    tz = m.group(2) or "+0000"
    off_s = (int(tz[1:3]) * 3600 + int(tz[3:5]) * 60) * (1 if tz[0] == "+" else -1)
    return dt.replace(tzinfo=timezone.utc).timestamp() - off_s


def ffprobe_duration(path: Path) -> float:
    out = subprocess.check_output([
        "ffprobe", "-v", "error", "-show_entries", "format=duration",
        "-of", "default=nw=1:nk=1", str(path)
    ])
    return float(out.strip())


def main() -> None:
    raw = Path(sys.argv[1])
    start_epoch = float(sys.argv[2])
    out_dir = Path(sys.argv[3])
    channel = "TVP 1"
    epg_path = None
    args = sys.argv[4:]
    while args:
        a = args.pop(0)
        if a == "--channel":
            channel = args.pop(0)
        elif a == "--epg":
            epg_path = Path(args.pop(0))

    out_dir.mkdir(parents=True, exist_ok=True)
    rec_dur = ffprobe_duration(raw)
    rec_end = start_epoch + rec_dur
    print(f"Nagranie: {rec_dur:.0f}s  okno {datetime.fromtimestamp(start_epoch)}"
          f" – {datetime.fromtimestamp(rec_end)}  kanał: {channel}")

    if epg_path is None:
        epg_path = out_dir / "pltv.gz"
        print(f"Pobieram ramówkę: {EPG_URL}")
        urllib.request.urlretrieve(EPG_URL, epg_path)

    opener = gzip.open if str(epg_path).endswith(".gz") else open
    with opener(epg_path, "rb") as f:
        tree = ElementTree.parse(f)

    programs = []
    for p in tree.getroot().iter("programme"):
        if p.get("channel") != channel:
            continue
        ps, pe = parse_xmltv_time(p.get("start")), parse_xmltv_time(p.get("stop"))
        if pe <= start_epoch or ps >= rec_end:
            continue
        title = (p.findtext("title") or "Program").strip()
        desc = (p.findtext("desc") or "").strip()
        cats = [c.text.strip() for c in p.findall("category") if c.text]
        rating = p.findtext("rating/value") or ""
        programs.append({
            "start": ps, "end": pe, "title": title, "desc": desc,
            "genre": ", ".join(cats[:2]), "age": rating.strip(),
        })
    programs.sort(key=lambda x: x["start"])
    if not programs:
        sys.exit(f"BŁĄD: brak programów '{channel}' w oknie nagrania — sprawdź start_epoch/EPG")

    manifest_items = []
    for i, prog in enumerate(programs):
        seg_start = max(prog["start"], start_epoch) - start_epoch
        seg_end = min(prog["end"], rec_end) - start_epoch
        seg_dur = seg_end - seg_start
        if seg_dur < 20:  # skrawki < 20 s pomijamy
            continue
        fname = f"program_{i:02d}.mp4"
        print(f"  [{i:02d}] {prog['title'][:50]!r}  {seg_dur:.0f}s → {fname}")
        subprocess.check_call([
            "ffmpeg", "-y", "-loglevel", "error",
            "-ss", f"{seg_start:.3f}", "-i", str(raw),
            "-t", f"{seg_dur:.3f}", "-c", "copy", "-movflags", "+faststart",
            str(out_dir / fname),
        ])
        real_dur = ffprobe_duration(out_dir / fname)
        manifest_items.append({
            "file": fname,
            "title": prog["title"],
            "genre": prog["genre"] or "program TV",
            "description": prog["desc"] or "Nagranie z anteny TVP1.",
            "age": prog["age"] or "12 lat",
            "durMs": int(real_dur * 1000),
        })

    manifest = {
        "channelName": "TVP1 Retro",
        "recordedAtWallMs": int(start_epoch * 1000),
        "sourceChannel": channel,
        "items": manifest_items,
    }
    (out_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    total = sum(it["durMs"] for it in manifest_items) / 1000
    print(f"OK: {len(manifest_items)} programów, łącznie {total:.0f}s → {out_dir}/manifest.json")


if __name__ == "__main__":
    main()
