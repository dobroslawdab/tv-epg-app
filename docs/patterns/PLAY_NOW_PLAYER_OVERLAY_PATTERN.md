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
| 0 | dwie belki | Zatrzymaj / Odtwórz |
| 1 | szewron « w kółko | Zacznij od początku |
| 2 | chip `LIVE` | Wróć do live |
| 3 | kropka + `REC` | Nagraj od początku |
| 4 | kółko z „i" | Zobacz opis |
| 5 | ekran z listą | Podgląd programu TV |
| 6 | dymek z zębatką | Napisy, dźwięk, jakość |

> ⚠️ **Kolejność jest z FIGMY, nie z launchera.** Launcher Play zaczyna rząd od
> chipa LIVE; makieta idzie za Figmą „Nowy player – scrubb preview 2026"
> (node `5730:3571`), gdzie pierwsza jest pauza. Zmierzone na boxie zostają
> pozycje (krok 134 px, środek rzędu 926) — Figma ma tam 152 px.

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

## 4b. Trzy poziomy fokusa i warianty paska

Nad wideo są **trzy** poziomy, licząc od dołu:

```
pas kontrolek  ──GÓRA──▶  pas przewijania  ──GÓRA──▶  miniaturka / karta
      │                          │                          │      │
      │                          │                          │      ├─ LEWO/PRAWO: ramówka kanału
      │                          └─ LEWO/PRAWO: przewijanie  │      └─ OK: detal (opis + akcje)
      │                                                      │
      └──────────── DÓŁ ──▶  MINI-EPG  ◀── GÓRA ─────────────┘
                                │
                                └─ OK: dostraja i wraca do playera (BACK: poziom niżej)
```

**Mini-EPG leży NAD playerem**, więc wchodzi się w nie GÓRĄ z miniaturki —
czyli przechodząc przez wszystkie trzy poziomy playera. Dla wygody otwiera się
też DOŁEM z pasa kontrolek, z drugiego końca stosu. W środku GÓRA/DÓŁ chodzą
WYŁĄCZNIE po kanałach: do playera wraca się OK-iem (dostrojenie) albo BACK-iem,
nigdy kierunkiem — inaczej przy pierwszym kanale DÓŁ byłby dwuznaczny.

### Kiedy pasek pokazuje bullet i bąbelek

To NIE jest dowolność — wynika ze zrzutów:

| stan | wypełnienie + poświata | bullet + bąbelek | dowód ze zrzutu |
|---|---|---|---|
| kontrolki / pasek | ✔ | ✔ | `box_osd1`: koło na 1501 i bąbelek „10:58:42" |
| mini-EPG | ✔ (do POZYCJI ODTWARZANIA) | ✘ | jasne odcinki na wysokości paska: `0-264`, kropki `296-304` i `1256-1264` — same 8-px kropki, żadnego szerokiego koła ani prostokąta bąbelka |
| karta pokazuje INNY program (detal) | ✘ | ✘ | wyłącznie dwie 12-px kropki `284-296` i `1556-1568`, poza tym nic |

W kodzie: `PnBarStyle.PLAYING / POSITION_ONLY / DOTS_ONLY`.

> Sens tego jest taki, że bullet z godziną mówi „tu jesteś w odtwarzaniu".
> Gdy karta wędruje po ramówce, pokazywany program nie ma nic wspólnego
> z pozycją odtwarzania — więc bullet znika, zostają same granice bloku.

### Detal programu

Karta rozwija się w górę ekranu: etykieta dnia (165), okładka (203), tytuł (250),
metadane (318), rząd akcji „Nagraj" / „Przypomnij" (392, wys. 64) i opis
(485, szer. 910, 28 px, interlinia 48). Akcje są w makiecie bez skutków —
chodzi o układ i stan fokusa.

---

## 5. Mini-EPG (stan po DÓŁ)

Widoczne są **TRZY rzędy** — poprzedni kanał, zafokusowany, następny — tak samo
jak w 1. wersji (`DemoMiniEpgBar` renderuje `chIdx-1 .. chIdx+1`). Zafokusowany
stoi na 745, pas przewijania pod nim oddziela go od reszty listy.

To **siatka, która się przewija**, nie przerysowywana lista:
- pionowo kanały, krok 208 px; zafokusowany rząd zawsze na 745,
- poziomo programy kanału, krok 961 px; zafokusowany program zawsze w kolumnie startowej,
- przewijanie przez dwa `AnimatedContent` ze slide+fade (250 ms) — **dokładnie
  jak w 1. wersji** (`DemoMiniEpgBar`): blok wierszy wjeżdża z kierunku nawigacji,
  karta programu zjeżdża w bok, a nowa wjeżdża,
- szyna kanału (MOJE / numer / nazwa) jedzie **tylko pionowo** — należy do kanału,
- rzędy nad zafokusowanym są przycinane (na boxie nad nim nie ma nic poza wideo).

### ⚠️ Pułapka: `Modifier.offset` nie powiększa zmierzonego rozmiaru

Kontener kolumn miał `clipToBounds` i wrap-height. Ponieważ dzieci są ustawiane
przez `offset`, rodzic zmierzył się **wysokością okładki (120 px)** i przycinał
metadane siedzące na `DY_META = 125`. Objaw: znikająca linia metadanych
w zafokusowanym rzędzie — i tylko tam, bo tylko tam jest rysowana.

Rozwiązanie: kontener kolumn ma **jawną wysokość rzędu** i stoi na `y = topPx`,
a kolumny liczą swoje `dy` względem rzędu. Przycinanie jest wtedy wyłącznie
poziome (żeby programy nie wjeżdżały na szynę kanału).

### Kanały mockupowe

Okładki mocków są **podłożone z paczek nagrań** — mock nie ma własnych klatek,
a puste prostokąty w mini-EPG wyglądały jak błąd. To jedyne miejsce w makiecie,
gdzie obrazek nie odpowiada tytułowi.

`PnMockChannels.kt` dokłada 8 kanałów **wyłącznie ramówkowych** (TVP2, TVN, TVN 7,
Polsat, TV4, TVP Sport, TVP Kultura, Discovery) — mają numer, nazwę i pełną siatkę
programów zakotwiczoną na dzisiejszej 6:00, ale NIE MA pod nimi materiału wideo,
więc `tunable = false` i OK na nich tylko zamyka mini-EPG. Grają wyłącznie kanały
z nagrań anteny.

> Dlatego `PnProgram` trzyma czasy w **zegarze ściennym**, nie na osi wirtualnej:
> mini-EPG zestawia kanały o różnych osiach (każde nagranie ma własny
> `recordedAtWallMs`, mocki nie mają żadnej). Przed tą zmianą drugi kanał
> z nagrania pokazywałby godziny policzone na osi pierwszego.

### Pasek postępu należy do WIERSZA, nie do ekranu

W mini-EPG **każdy kanał ma własny pasek**, rysowany wewnątrz swojego wiersza
(`DY_BAR = 184` = zmierzone 929 − 745), więc przewija się razem z nim — tak jak
w 1. wersji, gdzie pasek jest częścią `DemoMiniEpgChannelRow`. Wypełnienie
pokazuje pozycję odtwarzania **tylko na kanale dostrojonym**; pozostałe wiersze
wypełniają się do live. Poświata (wskaźnik live) jest wyłącznie na wierszu
zafokusowanym — również jak v1.

### Mini-EPG: detal, oglądanie wstecz, nagrywanie (jak v1)

- Lista pokazuje programy **wstecz i w przód** (`EPG_BEFORE = 3`, `EPG_AFTER = 4`),
  fokus startuje na programie bieżącym (`liveIndex`).
- **OK na programie, który TRWA** → od razu włącza kanał, bez detalu.
- **OK na minionym / przyszłym** → detal: miniony ma `[Oglądaj] [Nagraj]`
  (oglądanie = timeshift od początku), przyszły `[Nagraj] [Przypomnij]`.
- Pasek w detalu: miniony **cały wypełniony**, bieżący do pozycji, przyszły
  same kropki.
- „Nagraj" otwiera `DemoRecordingModal` z v1 (odcinek / seria).
- Ikony rzędu kontrolek to **te same zasoby co v1** (`demo_ic_*`), więc kształty
  nie rozjeżdżają się między wersjami.

### ⚠️ Trzy pułapki, które dały „Oglądaj nie działa"

0. **STALE VAL w `AndroidView.factory` — najgroźniejsza z trzech.** `factory`
   wykonuje się RAZ i zamyka w sobie `handleKey` z **pierwszej** kompozycji, a wraz
   z nim wszystkie `val` obliczane w kompozycji: `detailProgram`, `detailTiming`,
   `detailActions`, `shownBlock`, `miniRows`. Delegaty `by remember { mutableStateOf }`
   są bezpieczne (czytają State), ale zwykłe `val` — nie. Objaw: „Oglądaj" na
   minionym programie zawsze odtwarzało ten sam materiał — ten, który był bieżący
   **w chwili startu ekranu**. Poprawka jak w v1: handler przez `rememberUpdatedState`,
   a widok woła `keyHandler.value(code, repeat)`. Patrz też memory
   `feedback_stale_val_in_effects`.

### ⚠️ Pozostałe dwie, które dały „Oglądaj nie działa"

1. **Detal otwarty z mini-EPG zostaje w strefie `MINI_EPG`.** Obsługa klawiszy
   detalu siedziała tylko w gałęzi `PnZone.CARD`, więc OK w detalu z listy
   trafiał do gałęzi listy i nic nie robił. Obsługa `detailOpen` musi być
   **przed** `when (zone)`.
2. **`positionMs` bywa jeszcze 0**, zanim tick odczyta pozycję z playera.
   `epgBlockAt(0)` zwraca wtedy PIERWSZY blok nagrania, więc „Oglądaj"
   seekowało do zera. Ten sam strażnik co w `moveCursor` (0 albo pozycja
   starsza niż okno DVR → `virtualNow()`) musi być też przy `shownBase`.

> Powiązane: mini-EPG dla kanału DOSTROJONEGO musi używać `controller.schedule`,
> a nie świeżego `BarkerSchedule` — ten drugi ma tylko nominalne długości
> z manifestu i przy ~200 cyklach bloki wypadają na początku nagrania.

### Rozpoczęte przewinięcie trzyma UI przez MINUTĘ

Zwykła nakładka chowa się po 6 s, ale **dopóki przewinięcie nie jest zatwierdzone
OK-iem** (`cursorMs != null`), cały scrub — pasek i taśma podglądu — zostaje na
ekranie **60 s** od ostatniego ruchu. User przegląda wtedy materiał i UI nie ma
mu uciekać. Zmierzone na boxie: zniknęło między 54. a 63. sekundą.

> Warunek siedzi w kluczu `LaunchedEffect`, więc zatwierdzenie OK-iem (kursor
> wraca do `null`) natychmiast przestawia timeout z powrotem na 6 s.

### Przewijanie: OK zatwierdza, granica blokuje przy trzymaniu

Seek następuje **dopiero po OK** — tak jak w 1. wersji. Wcześniej był auto-commit
po 800 ms bezczynności, przez co kanał „wstrajał się" w miejscu, w którym user
tylko przystanął przy przewijaniu.

Blokada na granicy materiału działa wzorcem z v1 (`scrubStepWithSnap`): przy
TRZYMANIU strzałki (autorepeat, `repeatCount > 0`) kursor staje na granicy i stoi,
aż klawisz zostanie puszczony; NOWE fizyczne naciśnięcie (`repeatCount == 0`)
zwalnia blokadę. Sygnałem jest **repeatCount, nie timing** — dzięki temu szybkie
klikanie nigdy nie daje trwałej blokady, a trzymanie nie przelatuje przez programy.

> Wymaga to przekazywania `repeatCount` z `dispatchKeyEvent` aż do handlera —
> `PnVideoView` podaje parę `(keyCode, repeatCount)`, jak `DemoVideoView` w v1.

### Przewijanie zatrzymuje się na granicy programu

Krok, który przeskoczyłby do sąsiedniego bloku ramówki, jest **przycinany do
granicy**; dopiero kolejne naciśnięcie przechodzi dalej. Dzięki temu trzymanie
strzałki nie przelatuje przez programy — zatrzymuje się na każdym przejściu.
Gdy kursor już stoi na granicy, warunki są fałszywe i krok wchodzi normalnie
w sąsiedni blok.

Zweryfikowane logiem: `base=3295s` w bloku 3300 s, krok +30 s → `stepped`
przekracza koniec, `bounded` = koniec bloku; następny krok startuje już
z `base=0s` kolejnego bloku.

### Zmiana programu przewija CAŁĄ TAŚMĘ

Pierwsze podejście animowało sam bullet po nieruchomym pasku (`animateFloatAsState`
na X). Efekt był zły: godziny granic podmieniały się natychmiast, a kulka jechała
przez pusty pasek — wyglądało to „jak sprężyna".

Teraz cała zawartość paska (tor, kropki, godziny, wypełnienie, bullet) należy do
**konkretnego bloku** i siedzi w `AnimatedContent` kluczowanym parą
`(blockStart, blockEnd)`. Przy zmianie programu stara taśma wyjeżdża, nowa
wjeżdża z przeciwnej strony (320 ms) — oś czasu przesuwa się jak taśma, a bullet
jedzie razem z nią.

Dwie rzeczy, bez których wraca sprężyna:
1. Zawartość liczy pozycje z **bloku przekazanego z klucza**, nie z aktualnych
   parametrów — inaczej wyjeżdżająca taśma przeskakiwałaby na nową oś w locie.
2. `animateFloatAsState` na bullecie zostaje, ale działa już tylko **wewnątrz**
   bloku (drobne kroki przewijania). Przy zmianie bloku powstaje nowa instancja,
   więc bullet startuje od razu na swojej pozycji i nie dubluje ruchu taśmy.

### Poświata pod paskiem sięga DO LIVE, nie do playheada

Poświata oznacza „materiał dostępny do live edge", więc przy przewijaniu **stoi
w miejscu** — rusza się tylko playhead i wypełnienie toru. Ta sama zasada jest
w `demolive` V4 (zapisana tam jako uwaga z 2026-08-26).

Zmierzone przy przewinięciu wstecz: wypełnienie i playhead na x≈666, poświata do
x≈714, kreska LIVE na x≈716 — poświata kończy się na znaczniku, nie na kropce.

### „Oglądaj poprzednie"

Podpowiedź, że LEWO cofa do wcześniejszego programu — kółko `◀` + dwuwierszowy
tekst po LEWEJ od karty (105, 686). **Przeniesiona z 1. wersji**: w V4 stała na
(73, 675) i została stamtąd usunięta, a w jej miejsce zjechała karta kanału
(logo + numer, z y=452 na y=600 — kolumna ma 184 px, a timeline zaczyna się na
804). Z zafokusowanej miniaturki V4 zniknęły też białe strzałki `‹ ›`.

Element czysto wizualny — nie bierze fokusa, przewijanie robi LEWO na poziomie karty.

### Znacznik LIVE

Plakietka „LIVE" + pionowa linia na pozycji live edge — jest w OBU wersjach
playera. W demolive2 siedzi na `y = 792..820`, czyli w jedynej wolnej luce
**między metadanymi karty** (kończą się ~790) **a playheadem** (822..854).
W `demolive` V4 była na `-18` i kropka playheada na nią wjeżdżała — podniesiona
na `-34`, linia urosła o te 16 px, żeby nadal sięgać toru.

> ⚠️ Długość kreski LICZ WZGLĘDEM PASKA, nie do absolutnego `y`. Pas przewijania
> zjeżdża na 988 przy taśmie podglądu — przy stałej absolutnej (900) wychodziła
> ujemna długość, kreska znikała i plakietka „wisiała" nad torem.

Ramki fokusa: `sx(6)` — tyle, ile ma zafokusowana miniaturka w 1. wersji
(`DemoMiniEpgBar`). Biała ramka kursora na taśmie zostaje `sx(4)`, bo taśma jest
przepisana 1:1 z V4.

---

## 5a. Mini-EPG — układ rzędu

Zafokusowany rząd kanału stoi **nad** pasem przewijania, kolejne kanały pod nim —
pas pełni rolę separatora i przenosi się wtedy z `y=832` na `y=929`.

Kolumny rzędu: szyna (`MOJE` / numer / logo) → okładka + opis bieżącego programu
(ramka fokusa mint) → zajawka następnego programu (wygaszona).

---

## 6. Nawigacja

```
OK/GÓRA/DÓŁ  → na CZYSTYM obrazie wszystkie trzy robią to samo: nakładka,
               fokus na pasie kontrolek (do paska i mini-EPG wchodzi się
               dopiero Z nakładki)
LEWO/PRAWO   → na czystym obrazie: od razu przewijanie; w nakładce: wybór ikony
GÓRA         → SCRUB (playhead na mint); LEWO/PRAWO przewija ±30 s
               i odsłania taśmę podglądu
GÓRA z paska → CARD: ramka mint na miniaturce; LEWO/PRAWO chodzi po ramówce,
               OK rozwija detal
DÓŁ          → MINI_EPG (GÓRA/DÓŁ kanał, LEWO/PRAWO program, OK dostraja)
BACK         → detal → karta → schowaj nakładkę → wyjście z ekranu
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
