# 🔑 LIVE TOKEN + REMOTE CHANNELS PATTERN

**Status**: ✅ Zaimplementowane (czeka na token JWT od zespołu CDN)
**Data**: 2026-08-03
**Cel**: podmiana URL-i kanałów live **i tokenu JWT bez rebuilda APK**

---

## 🎯 Problem

Kanały live Play są za CDN-em Redge Media wymagającym krótkożyciowego tokenu JWT:

```
https://r.playcdn.tv/livedash/play/playtv/indigo/live/<ID>/live.livx?jwt=<TOKEN>
```

Dwa niezależne wymagania z ticketu (Bartłomiej Czechowski, 29/07/26):

1. **„Pytanie czy URLe można zmieniać zdalnie?"** — lista kanałów musi być edytowalna bez releasu
2. **Token podawany „na życzenie"** — i rotujący, więc nie może być zapiekany w APK

Gdyby token był w `assets/tv_channels_with_streams.json`, każda rotacja = nowy build + nowy
release + aktualizacja na wszystkich urządzeniach badawczych. Podczas sesji z użytkownikami
to nie do zaakceptowania.

---

## 🏗 Architektura

```
Supabase live_config.jwt ──┐
                           ├─→ LiveTokenProvider (StateFlow + SharedPreferences cache)
Supabase live_channels ────┤            │
                           │            ▼
      asset JSON (fallback)┴─→ ChannelManager ─→ TvChannelData.streamUrl (SZABLON z {JWT})
                                                          │
                                                          ▼
                                          LiveMediaItemFactory.build()
                                          • wstrzykuje token LENIWIE
                                          • ustawia MIME (DASH dla .livx)
                                                          │
                                                          ▼
                                                    ExoPlayer
                                                          │
                                            401/403 ──────┤
                                                          ▼
                                              LiveTokenRetryHandler
                                              → refresh token → prepare()
```

### Kluczowa zasada: rozdziel szablon od tokenu

| Co | Jak często się zmienia | Gdzie leży |
|---|---|---|
| Szablon URL-a (`.../live.livx?jwt={JWT}`) | rzadko | `live_channels.stream_url` |
| Token JWT | często (godziny) | `live_config.jwt` |

**Nigdy nie przechowujemy URL-a z już wklejonym tokenem.** Gdyby lista kanałów trzymała
gotowe URL-e, po rotacji tokenu wszystkie zcache'owane URL-e byłyby martwe do przeładowania
listy. Token wstrzykiwany jest **w momencie budowania MediaItem** — czyli przy każdym
przełączeniu kanału.

---

## 📂 Pliki

| Plik | Rola |
|---|---|
| `channels/LiveTokenProvider.kt` | magazyn tokenu, `resolve()` wstrzykujące token do szablonu |
| `channels/LiveMediaItemFactory.kt` | buduje `MediaItem` z tokenem + poprawnym MIME |
| `channels/LiveTokenRetryHandler.kt` | `Player.Listener` — retry po 401/403 |
| `channels/SupabaseLiveChannelsRepository.kt` | REST client do `live_channels` + `live_config` |
| `channels/ChannelManager.kt` | warstwa remote + fallback + polling tokenu |
| `admin-panel/sql/create_live_channels.sql` | DDL + seed 6 kanałów + gotowe UPDATE-y |

---

## ⚠️ Pułapka #1: ExoPlayer nie rozpoznaje `.livx`

CDN Play podaje manifest DASH pod rozszerzeniem **`.livx`**, którego
`Util.inferContentType()` nie zna. Bez jawnego MIME ExoPlayer potraktuje manifest jako plik
progresywny i playback padnie (`ParserException` / `ERROR_CODE_PARSING_CONTAINER_MALFORMED`).

```kotlin
// ❌ ŹLE — ExoPlayer nie wie, że to DASH
MediaItem.fromUri("https://.../live.livx?jwt=abc")

// ✅ DOBRZE
LiveMediaItemFactory.build(rawUrl)   // ustawia MimeTypes.APPLICATION_MPD
```

MIME wnioskujemy ze **ścieżki**, nie z całego URL-a — token w query zaburzyłby detekcję
rozszerzenia (`substringBefore('?')`).

### Wymagana zależność

```kotlin
// app/build.gradle.kts
implementation("com.google.android.exoplayer:exoplayer-dash:2.19.1")
```

Bez tego modułu `setMimeType(APPLICATION_MPD)` rzuci `IllegalStateException` przy `prepare()`.
Projekt miał wcześniej tylko `exoplayer` + `exoplayer-hls`.

---

## ⚠️ Pułapka #2: pusty token z Supabase kasował działający token

`live_config.jwt` startuje jako `NULL` i bywa czyszczony przy edycji wiersza. Naiwne
`setToken(config.jwt)` ubiłoby wtedy poprawny token z cache w środku sesji badawczej.

```kotlin
// LiveTokenProvider.setToken()
if (clean.isEmpty() && hasToken) {
    Log.w(TAG, "Supabase zwrócił pusty token — zachowuję poprzedni")
    return false
}
```

Do celowego wyczyszczenia jest osobne `clear()`.

---

## ⚠️ Pułapka #3: ekrany nie widzą zdalnej aktualizacji listy

Ekrany trzymające kanały w `remember { ChannelManager.getAllChannels() }` **nie** odświeżą
się, gdy lista dojdzie z Supabase po pierwszej kompozycji. Dlatego `ChannelManager` wystawia
licznik wersji:

```kotlin
val version by ChannelManager.channelsVersion.collectAsState()
val channels = remember(version) { ChannelManager.getAllChannels() }
```

Zrobione w `LiveScreen`. **Pozostałe ekrany (EpgDayScreen, DemoLiveScreen, EpgAdapter) wciąż
czytają listę raz** — po zdalnej zmianie listy kanałów wymagają restartu aplikacji. Token
natomiast działa wszędzie od razu, bo jest w kluczu `LaunchedEffect`.

---

## ⚠️ Pułapka #4: trzy niezależne źródła URL-i live

W projekcie były **trzy** miejsca z URL-ami kanałów:

1. `assets/tv_channels_with_streams.json` → `ChannelManager`
2. `ChannelStreamMapping` (prywatny obiekt w `TopMenuScreen2.kt:18952`) — podgląd live w TopMenu
3. `EpgAdapter.getLiveChannelsAsVodContent()` — czyta asset JSON bezpośrednio

`ChannelStreamMapping.getStreamUrl()` odpytuje teraz najpierw `ChannelManager`, a lokalną mapę
traktuje jako ostatni fallback. Bez tego zdalna konfiguracja pomijałaby podgląd w TopMenu.

---

## 🔄 Retry po wygaśnięciu tokenu (401/403)

Bez tego wygaśnięcie tokenu w trakcie badań = czarny ekran do restartu aplikacji.

```kotlin
LiveTokenRetryHandler:
  onPlayerError(401/403) → delay 2s → refreshLiveToken() → jeśli token INNY → prepare()
```

Zabezpieczenia przed pętlą:
- **3 próby** na URL, licznik resetowany po powrocie do `STATE_READY`
  (bez resetu trzy rotacje w długiej sesji wyczerpałyby limit na stałe)
- **2 s cooldown** — nie DDoS-ujemy ani Supabase, ani CDN
- **Ten sam token po odświeżeniu → stop.** Nie ma czego poprawiać: operator nie wgrał jeszcze
  nowego tokenu. Dalsze próby tylko generowałyby ruch.

Podpięte w `LiveScreen` (z komunikatem w UI) i `EpgDayScreen` (delegacja z istniejącego
listenera — uwaga, `onPlaybackStateChanged` już tam był, nie dodawaj drugiego override'u).

---

## 🛠 Operacje

### Podmiana tokenu — cała operacja „bez rebuilda"

```sql
UPDATE public.live_config
   SET jwt = 'NOWY_TOKEN', note = 'token z 03.08 14:00', updated_at = now()
 WHERE id = 1;
```

Zadziała na urządzeniach w ciągu ≤ `poll_interval_seconds` (domyślnie 120 s), a natychmiast
przy każdym przełączeniu kanału.

### Zacieśnienie pollingu na czas badań

```sql
UPDATE public.live_config SET poll_interval_seconds = 30 WHERE id = 1;
```

Interwał jest sterowany zdalnie — też bez rebuilda.

### Kill-switch: powrót na kanały z asset JSON

```sql
UPDATE public.live_config SET channels_override_enabled = false WHERE id = 1;
```

---

## 🐛 Debugging

```bash
adb logcat | grep -E "LiveTokenProvider|LiveMediaItemFactory|LiveTokenRetry|ChannelManager"
```

Diagnostyka jednym stringiem: `ChannelManager.diagnostics()` →
`"Supabase • 6 kanałów (6 z JWT) • token: 12min • eyJhbG…c2Fz (284 zn.)"`

| Objaw | Przyczyna | Sprawdź |
|---|---|---|
| 401 od razu przy starcie | brak tokenu w `live_config` | `LiveTokenProvider.maskedToken()` |
| `ParserException` na `.livx` | brak `exoplayer-dash` albo MIME nie ustawiony | czy używasz `LiveMediaItemFactory` |
| Lista kanałów stara | ekran nie używa `channelsVersion` | Pułapka #3 |
| `PGRST205` w logach | tabele nie założone w Supabase | uruchom `create_live_channels.sql` |
| Retry się nie odpala | URL bez `{JWT}` → handler pomija | `LiveTokenProvider.requiresToken(url)` |

**Token nigdy nie trafia do logów w całości** — `maskUrl()` / `maskedToken()` go przycinają.

---

## 🔒 Ograniczenie bezpieczeństwa (świadome)

Token leży w tabeli czytanej **anon keyem**, który jest w APK. Każdy z APK-iem może go
odczytać. Dla makiety UX na badania to akceptowalne (token krótkożyciowy, wydawany na czas
badań), ale **nie jest to model produkcyjny** — produkcyjnie token powinien być wydawany
per-user przez backend po autoryzacji.

---

## ❓ Nierozwiązane / do potwierdzenia z zespołem CDN

1. **TTL tokenu** — determinuje `poll_interval_seconds`. Nieznany.
2. **Czy jest DRM Widevine?** Jeśli tak, trzeba dołożyć `DrmConfiguration` + licence URL.
   Kod tego jeszcze nie obsługuje.
3. **Czy istnieje endpoint do odświeżania tokenu?** Jeśli tak, można pominąć ręczne
   wklejanie do Supabase i odpytywać go bezpośrednio.

Do momentu otrzymania tokenu cała ścieżka jest niezweryfikowana end-to-end — sprawdzone jest
tylko to, że wszystkie 6 URL-i istnieje na CDN (302 → 401, a nie 404) i że kod się kompiluje
i degraduje łagodnie przy braku tabel.
