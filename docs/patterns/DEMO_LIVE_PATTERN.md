# Demo Live Channel + Ramówka Mockup Pattern

**Status**: ✅ Makieta UX (A/B testy), pakiet `app/src/main/java/com/uxellence/tv/v3/demolive/`
**Cel**: zademonstrować pełny flow kanału live z ramówką (barker channel) — warstwa EPG jak pod zakładką Telewizja, zunifikowany player z przewijaniem (scrub preview), timeshift, detale programów. To prototyp UX — pomijamy kosmetykę, ale odwzorowujemy realne zachowania.

> ⚠️ To makieta dla A/B testów, nie kod produkcyjny. Nie miesza się z produkcyjnym EPG/playerem poza **reużyciem** komponentów wizualnych (`ChannelInfoOverlay`, `EpgDayItem`, `FrameCaptureManager`, `MovieDetailScreen`).

---

## Pliki i role

| Plik | Rola |
|---|---|
| `DemoLiveScreen.kt` | Główny ekran: maszyna stanów, stan Compose, akcje, integracja wideo (ExoPlayer + TextureView), warstwy. |
| `DemoLiveKeyController.kt` | Jedyny punkt obsługi klawiszy (delegation pattern): `DemoLayer` + `DemoLiveActions`. |
| `DemoChannelPlayerController.kt` | „Antena" barkera: playlista ExoPlayer (A,B) + REPEAT_ALL, oś wirtualna, seek, DVR, live edge. |
| `DemoChannelSchedule.kt` | Czysta logika ramówki: oś wirtualna, mapowanie pozycja↔materiał, bloki ramówki, metadane, okładki. |
| `DemoEpgLayer.kt` | Warstwa EPG (replika EpgDayScreen): tryb single/expanded, gradient, TIME SYNC, markery. |
| `DemoPlayerUi.kt` | Zunifikowany player: strefy BUTTONS/STRIP/SNIPPET, pasek stałej szerokości, fold opisu, zegar. |
| `DemoSeekOverlay.kt` | Filmstrip (taśma miniatur) + `formatWall`/`demoSp`. |
| `DemoFilmstripProvider.kt` | Ekstrakcja klatek z materiałów (2× `FrameCaptureManager`) dla scrub preview. |

---

## Maszyna stanów (DemoLiveKeyController)

```
EPG (start) --OK na "teraz live"--> PLAYER_UI
  | BACK (wielopoziomowy)            | strefy: BUTTONS / STRIP / SNIPPET / DETAIL
  v                                  |
FULLSCREEN <--timeout/BACK-----------+
  | LEFT/RIGHT → STRIP (scrub),  OK/UP/DOWN → EPG,  BACK → wyjście
```

- `DemoLayer { EPG, PLAYER_UI, FULLSCREEN }`.
- Strefy playera: `PlayerZone { BUTTONS, STRIP, SNIPPET, DETAIL }`.
- BACK obsługiwany **wyłącznie** przez systemowy `BackHandler` (dispatcher) — podwójna obsługa (View.dispatchKeyEvent + dispatcher) dawała double-trigger.

## Barker channel (oś wirtualna)

- Ramówka zakotwiczona w **zegarze ściennym**: start dziś 9:00. `virtualMs = wallClock - antennaStart`. Live edge = `virtualNow()`.
- Dwa materiały (Sintel, Big Buck Bunny) grane naprzemiennie w pętli (`REPEAT_MODE_ALL`); `trackedCycle` inkrementowany tylko przy AUTO-przejściu B→A.
- Czasy trwania doprecyzowywane z `Timeline` ExoPlayera po STATE_READY.
- DVR: 1 h wstecz (`DVR_WINDOW_MS`); seek clampowany do `dvrStartMs..virtualNow`.
- Materiały i okładki: archive.org Sintel + exoplayer-test-media BBB; okładki z Amazon/IMDb (`coverUrl` w `EpgBlock`).

## Warstwa EPG (single → expanded, jak Telewizja)

Reużywa stałych i komponentów `EpgDayScreen` (1:1 wygląd):
- **Single** (start, kanał na ekranie): viewport 142, focusY 1000, gradient subtelny 0.61→0.82, bez paddingu.
- **Expanded** (po pierwszym DOWN): viewport 446, focusY 960, gradient 0.45→0.63, padding 152. UP w single nic nie robi; pierwszy DOWN rozwija + przechodzi na kolejny kanał.
- **TIME SYNC**: wszystkie kanały przewijają się tak, by program zawierający `focusedTime` był w kolumnie fokusu (X=330). Klucz efektu zawiera `isExpanded` + `focusedChannelIndex` + delay na layout — kanały poza viewportem w single nie są skomponowane, więc `scrollToItem` działa dopiero po rozwinięciu.
- **Markery** (opcjonalne parametry `EpgDayItem`, domyślne nie zmieniają produkcji): `liveNowBackground` (ciemne tło programu na żywo, bez zaokrągleń), `isWatchedNow` (migająca aqua playka na oglądanym programie wg pozycji odtwarzania), `watchProgress`+`liveDotAt` (przy timeshifcie: aqua wypełnienie do pozycji oglądania + biała kropka „gdzie jest live").

### Pułapka: luka danych przez północ
Realne kanały ładuje się przez `getFullDayPrograms(now)`, którego DAO filtruje po `startUtc` w obrębie doby kalendarzowej — program nadawany **przez północ** (start poprzedniego wieczoru) wypada z zapytania. Barker ma ciągłą oś i celuje w tę porę → desync (match=-1). **Fix**: ładować dobę **wczoraj+dziś** i scalać po starcie.

## Zunifikowany player (DemoPlayerUi)

- **Siatka wspólna z EPG**: badge+logo kanału (`ChannelInfoOverlay`) na X=40, główna kolumna (tytuł/metadane/pasek/przyciski/opis) na X=330. Gradient od dołu jak w warstwie EPG. Zegar ścienny w prawym górnym rogu.
- **Pasek stałej szerokości**: bieżący materiał zawsze zajmuje stały segment (~1230px) niezależnie od długości; sąsiednie materiały to ścieśnione segmenty od krawędzi. W STRIP materiał pod kursorem staje się głównym.
- **STRIP (scrub)**: LEFT/RIGHT przesuwa kursor co `SEEK_STEP_MS=10s` z akceleracją x2/x5/x10 (`getSeekStep`, próg 600ms; mnożnik niepokazywany). 7-klatkowy filmstrip nad paskiem, czasy nad paskiem. OK = `seekToVirtual(kursor)`.
- **Fold opisu**: w BUTTONS opis przycięty pod krawędzią; DOWN (SNIPPET) podjeżdża kolumnę o 150px (350ms) i pokazuje pełny opis w aqua ramce.
- **„Wróć do live" vs „Oglądasz live"**: status „Oglądasz live" tylko na live edge i bez pauzy; każde odsunięcie (seek lub pauza) → przycisk „Wróć do live" (slot pomijany w nawigacji gdy to status).
- **DETAIL**: prawdziwy `MovieDetailScreen` (tryb WIDEO) dla programów minionych/przyszłych. PAST „Oglądaj od początku", CURRENT „Oglądaj", FUTURE „Nagraj"+„Przypomnij".

## Wielopoziomowy BACK w EPG

Jak w dekoderze: BACK na innym programie (przeszłość/przyszłość) lub innym kanale → wraca do **oglądanego kanału + bieżącego programu** (re-home przez `openEpg()`, tryb single); dopiero kolejny BACK (na oglądanej pozycji) zamyka warstwę do fullscreena. Helpery: `liveProgramIndexFor()`, `isEpgAtWatchedPosition()`.

## Reużycie produkcji

- `com.uxellence.tv.v3.epg.ChannelInfoOverlay`, `EpgDayItem` — wygląd 1:1 z Telewizją (markery dodane jako opcjonalne parametry z domyślnymi).
- `com.uxellence.tv.v3.epg.FrameCaptureManager` — ekstrakcja klatek do filmstripa.
- `com.uxellence.tv.v3.moviedetail.MovieDetailScreen` — detal programu.
- `ChannelManager` + `EpgRepository` — realne kanały i EPG.

## Wejście

Menu deweloperskie (klawisz „1" w TopMenu) → „📡 Demo: Kanał live + ramówka" (`NavigationScreen.DEMO_LIVE`).

## Debugowanie

`adb logcat -s DemoLive` — akcje, przejścia stanów, seek, tick pozycji co 5 s. Emulator `Television_1080p` z `-no-snapshot` (snapshot bywa zepsuty).
