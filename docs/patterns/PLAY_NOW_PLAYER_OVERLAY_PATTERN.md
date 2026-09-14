# PLAY NOW PLAYER OVERLAY — warstwa playera odwzorowana z launchera Play

**Status**: ✅ Makieta UX (`demolive2`), obok nietkniętego `demolive`
**Data**: 2026-09-10
**Pakiet**: `app/src/main/java/com/uxellence/tv/v3/demolive2/`
**Wejście**: menu deweloperskie (klawisz „1") → „📡 Demo: Kanał live 2 (player Play Now)"

Nowa wersja **wyłącznie warstwy nad wideo** (pas kontrolek, pas przewijania, karta
programu, mini-EPG) odwzorowana 1:1 z aplikacji operatora **`pl.play.playnow.box`
3.10.10** na dekoderze **PLAY BOX TV 4B** (Android 11). Silnik anteny jest
**re-użyty** z `demolive` — tam nie zmieniamy ani linijki.

---

## 1. Jak zmierzono referencję (metoda, nie zgadywanie)

```bash
adb connect 192.168.31.112:5555
adb shell am start -n pl.play.playnow.box/com.n7mobile.playbox.ui.MainActivity
adb exec-out screencap -p > osd.png     # 1920x1080
```

**Kluczowa okoliczność**: wideo pod nakładką jest chronione DRM, więc `screencap`
zwraca w jego miejscu **czysty czarny**. Dzięki temu piksel nakładki nad czarnym
to jej **własny kolor pomnożony przez alfę** — całą warstwę dało się rozłożyć
arytmetycznie, bez pipety „na oko":

| Element | Zmierzone | Jak |
|---|---|---|
| brand purple | `#48227C` | piksel w obszarze pełnej alfy |
| akcent/fokus | `#5AECD3` | kafel fokusa |
| tekst | `#EEEEEE` / 40% / 70% | piksele tekstu i etykiet |
| tor paska (obejrzane) | `#DFDFDF` | piksel toru przed playheadem |
| tor paska (dalej) | `#EEEEEE` @ 40% | `#9481AE` nad `#48227C` → rozwiązane |
| poświata pod paskiem | `#5AECD3` @ **0.60** → 0 na **68 px** | z równania mieszania; R/G/B zgadzają się **co do bitu** |

### Świadome odejścia od launchera

| co | Play Now | u nas | dlaczego |
|---|---|---|---|
| podlanie / gradienty | `#48227C` | **`#281443`** (`PN_SCRIM`) | ciemny fiolet projektu, ten sam co overlay-scrub w V4 i tła modali. Geometria pola alfy zostaje zmierzona — zmienia się wyłącznie kolor docelowy. `PN_PURPLE` zostaje tam, gdzie jest kolorem „na akcencie" (glif na kaflu mint, chip LIVE). |
| przewijanie | sam playhead | **taśma miniatur** (`PlayNowScrubTape`) | podgląd przewijania z naszego playera — patrz sekcja 4a |

> Zasoby w APK launchera są zaciemnione (`res/` to same `color/*.xml`), więc ikon
> **nie da się wyciągnąć z pliku** — są odtworzone wektorowo z geometrii zbliżeń
> (`PlayNowIcons.kt`).

### Etykiety kontrolek

Etykieta pojawia się **tylko pod zafokusowaną** ikoną, więc każdą pozycję trzeba
było zafokusować i zrzucić osobno (`input keyevent KEYCODE_DPAD_RIGHT` + screencap):

| # | ikona | etykieta |
|---|---|---|
| 0 | chip `LIVE` | Oglądasz LIVE |
| 1 | kropka + `REC` | Nagraj od początku |
| 2 | strzałka w kółko | Zacznij od początku |
| 3 | dwie belki | Zatrzymaj |
| 4 | ekran z listą | Podgląd programu TV |
| 5 | kółko z „i" | Zobacz opis |
| 6 | dymek z zębatką | Napisy, dźwięk, jakość |

---

## 2. Gradient tła — JEDNA warstwa liniowa NIE wystarcza

Pole alfy wyliczone z siatki 7 × 11 próbek pokazuje, że po prawej stronie ekranu
alfa **saturuje na y = 720 niezależnie od x**, a po lewej sięga 1.0 już przy
y ≈ 480. Pojedynczy `linearGradient` nie potrafi się „spłaszczyć" — izolinie
liniowego gradientu są równoległymi prostymi.

Dopasowanie numeryczne (77 próbek, **RMSE 0.030**) daje **dwie warstwy**,
B rysowana **POD** A:

```
A — pionowa kurtyna dolna:     przezroczysta y=520 → pełna y=720
B — diagonalna poświata lewa:  izolinia 0.5 przez (40, 325), nachylenie 0.30,
                               rampa 250 px w kierunku normalnej n=(-0.2873, 0.9578)
```

Stałe w `PlayNowTheme.kt` (`GRAD_A_TOP/BOTTOM`, `GRAD_B_START/END`).
**Kolejność rysowania ma znaczenie** — odwrotna daje inne złożenie alfy.

> ⚠️ Nie „upraszczaj" tego do jednego gradientu. Objaw regresji: wideo prześwituje
> przez metadane programu (y ≈ 760–800) po prawej stronie ekranu.

---

## 3. Pas przewijania — mapowanie osi

Zmierzone na boxie, **nie** wyprowadzone z proporcji:

```
segment poprzedniego bloku   x =    0 .. 258
kropka granicy (start bloku) x =  290           ← pod nią godzina startu
BIEŻĄCY BLOK                 x =  325 .. 1530   ← tu mapuje się start..koniec
kropka granicy (koniec)      x = 1562           ← pod nią godzina końca
segment następnego bloku     x = 1598 .. 1920
```

Kropki granic leżą **poza** zakresem bieżącego bloku, w przerwach toru.

**Weryfikacja mapowania** (dlatego wiemy, że zakres to 325..1530, a nie 290..1562):

| stan na boxie | wyliczone | playhead na zrzucie |
|---|---|---|
| 10:58:42 w bloku 10:00–11:00 | 325 + 0.978·1205 = **1503** | 1501 |
| 11:00:08 w bloku 11:00–12:00 | 325 + 0.002·1205 = **328** | 326 |

**Wypełnienie**: jasne `#DFDFDF` **tylko do playheada**, dalej wygaszone — dotyczy
też segmentów sąsiednich (ogon poprzedniego bloku jest jasny w całości).
Pod jasną częścią poświata mint (patrz tabela wyżej).

**Bąbelek czasu** to prostokąt w kolorze podlania (`PN_SCRIM`) **na** poświacie — czyta się jako
„wycięcie" w niej. Szerokość 122 px designu: przy 104 px `HH:mm:ss` się ucinało.

---

## 4. Pas kontrolek

- 7 ikon, pierwsza w `cx = 524`, krok `134` → **środek rzędu na 926 px, nie 960**
- kafel fokusa 64×64, `r=10`, `#5AECD3`; glif przemalowany na `#48227C`
- etykieta pod **zafokusowaną** ikoną, mint, 26 px
- `LIVE` to jedyna pozycja-plakietka, nie glif: niezafokusowana biały chip
  z granatowymi literami, zafokusowana **odwraca się** na granatowy chip
  z mint literami (kafel jest wtedy mint)

### Pułapka: glify rysowane wektorowo przy 64 px

Dwa kształty wyszły nieczytelne w pierwszym podejściu i wymagały zmiany **konstrukcji**,
nie parametrów:

| glif | co nie działało | rozwiązanie |
|---|---|---|
| zębatka | 8 wypełnionych kulek zębów → „słoneczko" | **pierścień** (koło minus wnętrze) + 8 promienistych kresek |
| szewron « | szerokość szewronu ≈ grubość kreski → kleks | szewron musi być **wyraźnie** szerszy od `stroke` (0.135 s) |

---

## 4a. Podgląd przewijania (nasz dodatek)

Taśma miniatur z prototypu „Nowy player – scrubb preview 2026" (ta sama geometria
co `V4ScrubStrip` w `demolive`): sloty 292x175, kadr kursora 486x292 w białej
ramce, gap 20, tytuł materiału wyśrodkowany pod taśmą.

- wchodzi **dopiero po pierwszym LEWO/PRAWO** na pasku — samo wejście na pasek
  pokazuje tylko playhead,
- gdy jest widoczna, karta programu i pas kontrolek ustępują; pas przewijania zostaje,
- klatki z **re-użytego** `DemoFilmstripProvider` (trwały cache `frames/<program>/`
  obok materiału, więc kolejne wejścia mają taśmę od razu).

### ⚠️ Pułapka: oś wirtualna rozjeżdża się po poznaniu realnych długości

`BarkerSchedule` startuje z **nominalnymi** długościami z `manifest.json`, a
`onTimelineChanged` **nadpisuje** je tym, co zmierzył ExoPlayer. `trackedCycle`
policzony przy `preparePlayer` na nominalnych przestaje wtedy pasować:

```
pozycja wirtualna = cycle × materialCycleMs + prefix + pos
```

Przy ~480 cyklach (nagranie sprzed 2 miesięcy) nawet **sekundy** różnicy na cykl
dają **godziny** odjazdu. Objaw w demolive2: pierwsze LEWO/PRAWO wyrzucało kursor
na brzeg okna DVR (bąbelek sprzed doby, taśma pusta po lewej — bo wszystkie sloty
wypadały przed `dvrStart`).

**Dwie warstwy obrony** (obie w `DemoLive2Screen`):
1. `LaunchedEffect(prepared)` → `delay(1500)` → `controller.seekToLiveEdge()` —
   przekotwiczenie `trackedCycle` już na realnych długościach,
2. strażnik bazy kursora w `moveCursor`: baza `<= 0` albo starsza niż całe okno DVR
   nie jest realną pozycją → bierzemy `virtualNow()`.

> Stary `demolive` tego nie widzi, bo ma okno DVR = 1 h (odjazd jest tam clampowany
> do godziny i maskowany przez kolejne seeki). Przy oknie 24 h wychodzi wprost.

---

## 5. Mini-EPG (stan po DÓŁ)

Zafokusowany rząd kanału stoi **nad** pasem przewijania, kolejne kanały pod nim —
pas pełni rolę separatora i przenosi się wtedy z `y=832` na `y=929`.

Kolumny rzędu: szyna (`MOJE` / numer / logo) → okładka + opis bieżącego programu
(ramka fokusa mint) → zajawka następnego programu (wygaszona).

---

## 6. Nawigacja

```
OK (wideo)   → nakładka, fokus na pasie kontrolek
LEWO/PRAWO   → CONTROLS: wybór ikony
GÓRA         → SCRUB (playhead na mint); LEWO/PRAWO przewija ±30 s
               i odsłania taśmę podglądu
DÓŁ          → MINI_EPG (GÓRA/DÓŁ kanał, LEWO/PRAWO program, OK dostraja)
BACK         → schowaj nakładkę; przy schowanej — wyjście z ekranu
```

Przewijanie zatwierdza się samo po 800 ms bezczynności (albo OK).
Auto-hide nakładki: 6 s.

---

## 7. Co jest re-użyte, a co nowe

**Re-użyte z `demolive` (bez zmian tam!)**:
`BarkerSchedule` (oś wirtualna + bloki ramówki), `DemoChannelPlayerController`
(playlista ExoPlayer, DVR, seek do pozycji wirtualnej), `RecordedChannelLoader`
(paczki nagrań anteny), `ChannelManager` (logo kanału).

**Nowe w `demolive2`**: wyłącznie warstwa wizualna + ekran spinający.
`PnVideoView` jest własną kopią wzorca TextureView — `DemoVideoView` w `demolive`
jest `private`, a ekran 2 nie ma zależeć od prywatnych klas ekranu 1.

---

## 8. Materiał

Kanały z paczek nagrań anteny (`manifest.json` + `program_NN.mp4` + `covers/`),
domyślnie **TVP1 Retro** (`tvp1rec/`, 5 programów). Brak paczki → plansza
z instrukcją, bez wywalania się.

DVR ustawione na **pełną dobę** (nie godzinę jak barkery) — nagranie ma realne
godziny emisji z manifestu, więc oś pokrywa się z ramówką.

---

## 9. Debug

```bash
adb logcat | grep -E "RecordedChannel|DemoLive"
```

⚠️ **Emulator `Television_1080p`** dorzuca fantomowe eventy (BACK/DPAD) po kilku
sekundach od startu ekranu — zrzuty stanu nakładki trzeba robić w pierwszych
~5 s po uruchomieniu, inaczej ekran sam odjedzie do HOME. Patrz też
`project_tv_emulator_quirks`.
