# In-App Update Flow Pattern

**Status**: ✅ Production-ready (v5.9.5)
**Implementation date**: 2026-05-28
**Last incident fix**: v5.9.5 — loader na buttonie "Zainstaluj" (filesystem race)
**Related files**:
- `app/src/main/java/com/uxellence/tv/v3/update/UpdateManager.kt`
- `app/src/main/java/com/uxellence/tv/v3/update/UpdateDialog.kt`
- `app/src/main/java/com/uxellence/tv/v3/update/UpdateRepository.kt`
- `app/src/main/java/com/uxellence/tv/v3/MainActivity.kt` (state + 2 call-sites)
- `app/build.gradle.kts` (versionCode / versionName)

---

## Po co istnieje ten dokument

System auto-update (Supabase row → APK z GitHub Releases → instalator systemowy) ma kilka miejsc gdzie łatwo coś popsuć — albo introdukując regresje, albo dodając "improvement" który łamie istniejące hardening. Każda z poniższych zasad była wprowadzona po **konkretnym incydencie z produkcji**. Nie usuwaj ich bez czytania **Why**.

---

## Pełen przepływ (high level)

```
App start
  └─> UpdateManager.checkForUpdate()
        └─> Supabase GET /rest/v1/app_updates (order=version_code.desc&limit=1)
            └─> if remote.versionCode > local → return AppUpdateInfo
              └─> UpdateDialog (READY state)
                  └─> [Aktualizuj] → updateState = DOWNLOADING
                      └─> UpdateManager.downloadApk(updateInfo, onProgress, onComplete)
                          ├─> HttpURLConnection (follows GH redirects)
                          ├─> FileOutputStream → write buffer in loop
                          ├─> output.flush() + output.fd.sync()   ⚠️ KRYTYCZNE
                          ├─> size verification (totalSize == written)
                          └─> onComplete(true) → updateState = INSTALLING
                              └─> UpdateDialog (INSTALLING state)
                                  ├─> LaunchedEffect poll updateManager.isApkReady()
                                  ├─> while !isApkReady → CircularProgressIndicator + "Przygotowuję plik…"
                                  └─> isApkReady = true → button "Zainstaluj teraz" + auto-refocus
                                      └─> [Zainstaluj] → UpdateManager.installApk(): InstallResult
                                          ├─> Success → system PackageInstaller
                                          ├─> NotReady → retry 250ms × 20 + Toast
                                          └─> Error → Toast + state = READY
```

---

## Krytyczne zasady (NIE łam ich)

### 1. `output.close()` ≠ flush do dysku

**Co**: W `UpdateManager.downloadApk()` musi być `output.flush() + output.fd.sync()` **PRZED** `withContext(Dispatchers.Main) { onComplete(true) }`.

**Why**: `FileOutputStream.close()` zwraca natychmiast — bufor systemu plików może wciąż czekać na zapis. Jeśli UI flipuje state do INSTALLING natychmiast po `onComplete(true)`, użytkownik może kliknąć "Zainstaluj" zanim plik faktycznie istnieje na dysku → `PackageInstaller` widzi pusty/half-written plik → silent failure → **"klikam i nic się nie dzieje"**.

**Incident**: v5.9.1 → v5.9.2 (2026-05-28) — masowe zgłoszenia od użytkowników "Zainstaluj nie reaguje".

```kotlin
// ❌ NIE TAK
apkFile.outputStream().use { output ->
    /* write loop */
}
onComplete(true)   // plik może być wciąż buffered

// ✅ TAK
val output = FileOutputStream(apkFile)
try {
    /* write loop */
    output.flush()
    output.fd.sync()
} finally {
    output.close()
}
// + size verification
val written = apkFile.length()
if (totalSize > 0 && written != totalSize) {
    apkFile.delete()
    onComplete(false)
    return
}
onComplete(true)
```

### 2. `installApk()` MUSI zwracać `InstallResult`, nie `Unit`

**Co**: Sealed class:
```kotlin
sealed class InstallResult {
    object Success : InstallResult()
    object NotReady : InstallResult()
    data class Error(val message: String) : InstallResult()
}
```

**Why**: `Intent.ACTION_VIEW` z APK może rzucić:
- `ActivityNotFoundException` (box ROM bez `PackageInstaller`)
- `IllegalArgumentException` z `FileProvider.getUriForFile` (zły path config)
- `SecurityException` (REQUEST_INSTALL_PACKAGES odebrane)
- Plik nie istnieje / 0 bajtów

Jeśli `installApk()` zwraca `Unit` i tylko Loguje błąd, UI nie wie że coś poszło źle → user klika → nic się nie dzieje → frustracja.

**Incident**: v5.9.2 — fix dodawał sealed result + Toast feedback w UI.

### 3. Po fliper do INSTALLING, **NIE** pokazuj od razu buttona "Zainstaluj"

**Co**: `UpdateDialog` ma parametr `isApkReady: Boolean`. W stanie INSTALLING:
- `!isApkReady` → `CircularProgressIndicator` + "Przygotowuję plik instalacyjny…"
- `isApkReady` → button "Zainstaluj teraz" + `LaunchedEffect { focusedButton = 0; updateButtonFocus.requestFocus() }`

W `MainActivity`:
```kotlin
var isApkReadyForInstall by remember { mutableStateOf(false) }
LaunchedEffect(updateState) {
    if (updateState == UpdateState.INSTALLING) {
        isApkReadyForInstall = updateManager.isApkReady()
        while (!isApkReadyForInstall) {
            kotlinx.coroutines.delay(150)
            isApkReadyForInstall = updateManager.isApkReady()
        }
    } else {
        isApkReadyForInstall = false
    }
}
```

**Why**: Nawet z flush+sync z punktu 1, sam akt sprawdzenia `apkFile.exists() && canRead() && length() > 0` po flipie state'u to dodatkowa warstwa obrony. Plus **dla użytkownika**: spinner mówi "system pracuje", a brak buttona uniemożliwia premature click. Bez tego retry-loop działa w tle ale UI wygląda jakby kliknięcie zostało zignorowane.

**Incident**: v5.9.3 → v5.9.5 — sam fix z punktu 1 i 2 nie wystarczył; user wciąż widział "klikam i pustka" bo retry leciał ciszą.

### 4. **Oba** call-site'y `onInstall` w `MainActivity` muszą mieć retry+Toast

W `MainActivity.kt` są **dwa** miejsca które odpalają instalator:
- Linia ~726: `onUpdateInstall` w TopMenuScreen2 (badge Konto)
- Linia ~1480: `onInstall` w `UpdateDialog`

**Why**: Jeśli zmieniasz logikę instalacji, zmień **w obu**. Inaczej user instalujący przez badge dostaje stary, niezabezpieczony flow.

Wzorzec do skopiowania:
```kotlin
coroutineScope.launch {
    var result = updateManager.installApk()
    var waited = 0
    while (result is InstallResult.NotReady && waited < 5000) {
        kotlinx.coroutines.delay(250)
        waited += 250
        if (updateManager.isApkReady()) {
            result = updateManager.installApk()
        }
    }
    when (result) {
        InstallResult.Success -> Log.d("UPDATE", "Installer launched")
        InstallResult.NotReady -> {
            updateState = UpdateState.READY
            Toast.makeText(context, "Plik się jeszcze zapisuje, spróbuj ponownie", Toast.LENGTH_SHORT).show()
        }
        is InstallResult.Error -> {
            updateState = UpdateState.READY
            Toast.makeText(context, "Instalator niedostępny — pobierz ponownie", Toast.LENGTH_LONG).show()
        }
    }
}
```

### 5. UpdateDialog: release_notes z `maxLines=4 + TextOverflow.Ellipsis`

**Why**: Dialog jest renderowany na 1080p. Bez cappingu długie release notes pushują button "Aktualizuj"/"Później" poza viewport — niemożliwe do kliknięcia. Jednocześnie **release_notes w Supabase też powinny być krótkie** (max 3–4 zdania), bo ellipsis ucina kontekst.

**Incident**: v5.9.0 → v5.9.1.

### 6. Force update warning **pod** buttonami, nie nad

**Why**: Trzymane razem z punktem 5 — przy `forceUpdate=true` dialog ma dodatkowy tekst "Ta aktualizacja jest wymagana". Jeśli położysz go nad buttonami, zwiększasz risk wypchnięcia ich poza ekran.

---

## Release workflow (krok po kroku)

### Krok 1 — bump wersji
W `app/build.gradle.kts`:
```kotlin
versionCode = <previous + 1>
versionName = "<MAJOR>.<MINOR>.<PATCH>"
```

`versionCode` MUSI rosnąć monotonicznie. `versionName` to SemVer; bug fix → bump PATCH.

### Krok 2 — build debug APK
```bash
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk (~57 MB)
```

### Krok 3 — GitHub Release
```bash
gh release create v<X.Y.Z> \
  --repo dobroslawdab/tv-epg-app \
  --title "v<X.Y.Z> — <krótki opis>" \
  --notes "<dłuższy opis tego co naprawione/dodane>" \
  app/build/outputs/apk/debug/app-debug.apk
```

Public URL: `https://github.com/dobroslawdab/tv-epg-app/releases/download/v<X.Y.Z>/app-debug.apk`

### Krok 4 — Supabase row (RLS!)

Tabela `app_updates`, projekt `kexrkaqxoadxugnnbnjh`.

**RLS na `app_updates` blokuje INSERT z anon key** — potrzeba **service_role key** z `Dashboard → Project Settings → API → service_role`. Anon key tylko do SELECT (działa w apce).

```bash
curl -X POST "https://kexrkaqxoadxugnnbnjh.supabase.co/rest/v1/app_updates" \
  -H "apikey: <SERVICE_ROLE_KEY>" \
  -H "Authorization: Bearer <SERVICE_ROLE_KEY>" \
  -H "Content-Type: application/json" \
  -H "Prefer: return=representation" \
  -d '{
    "version_code": <code>,
    "version_name": "<X.Y.Z>",
    "apk_url": "https://github.com/dobroslawdab/tv-epg-app/releases/download/v<X.Y.Z>/app-debug.apk",
    "release_notes": "<3-4 zdania, \\n między liniami>",
    "force_update": false
  }'
```

`force_update: true` rezerwowane na krytyczne security fixes / breaking schema changes — pokazuje warning i ukrywa "Później".

### Krok 5 — weryfikacja
```bash
curl -s "https://kexrkaqxoadxugnnbnjh.supabase.co/rest/v1/app_updates?order=id.desc&limit=1" \
  -H "apikey: <ANON_KEY>"
```

### Krok 6 — test na emulatorze (opcjonalny)

Żeby zobaczyć dialog, emulator musi mieć **niższą** wersję niż w Supabase:
```bash
# Pobierz starszą wersję z GH
curl -sL -o /tmp/old.apk https://github.com/dobroslawdab/tv-epg-app/releases/download/v<X.Y.Z-1>/app-debug.apk

# Downgrade install (flag -d)
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 install -r -d /tmp/old.apk

# Force-stop + relaunch żeby checkForUpdate poszedł od nowa
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 shell am force-stop com.uxellence.tv.prod
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 shell monkey -p com.uxellence.tv.prod -c android.intent.category.LAUNCHER 1
```

---

## Chicken-and-egg deployment

Każdy fix flow-update z założenia można dostarczyć **tylko via flow-update**. Jeśli flow jest popsuty, masz problem.

**Jeśli wypuszczasz fix update-flow**:
1. Najpierw zweryfikuj na emulatorze że poprzednia wersja (popsuta) faktycznie potrafi pobrać i zainstalować nową (działający flow z prev → fixed).
2. Jeśli regresja jest tak głęboka że nawet pobieranie nie działa — przygotuj alternatywę:
   - "Pobierz APK ręcznie z GitHub Releases" (link + instrukcja w komunikacie)
   - `adb sideload` dla developerów

**Zasada**: Releasing update-flow fix testuj **dwukrotnie**:
- Test 1: prev → new (czy popsute → naprawione działa)
- Test 2: new → new+1 (czy naprawione → kolejne działa, czyli sam fix nie wprowadził nowej regresji)

---

## Common pitfalls

❌ **NIE używaj** `apkFile.outputStream().use { … }` — `.use` woła `close()`, ale nie `flush()+fsync()`. Pisz `FileOutputStream` + ręczny `try/finally`.

❌ **NIE zwracaj** `Unit` z `installApk()` — silne wymuszone `InstallResult` (sealed class) żeby compiler wymuszał obsługę wszystkich przypadków w UI.

❌ **NIE pokazuj** "Zainstaluj teraz" buttona od razu po flipie do INSTALLING — czekaj na `isApkReady`.

❌ **NIE usuwaj** retry-loopa w `onInstall` — to last-line-of-defense dla edge case'ów gdy `isApkReady()` zwróci true ale faktyczny `installApk()` jeszcze rzuci `NotReady`.

❌ **NIE wstawiaj** force-update warningu **nad** buttonami — wypchnie je z ekranu.

❌ **NIE używaj** anon key do INSERT do `app_updates` — RLS to zablokuje.

❌ **NIE rób** `assembleRelease` bez podpisywania — `signingConfig` w `build.gradle.kts` wymaga `local.properties` z `RELEASE_STORE_FILE` itp. Do testów wystarczy `assembleDebug`.

✅ **ZAWSZE** flushuj + fsync przed `onComplete(true)`.
✅ **ZAWSZE** sealed `InstallResult` z Toast feedback.
✅ **ZAWSZE** `isApkReady` polling przed pokazaniem buttona w INSTALLING.
✅ **ZAWSZE** aktualizuj **oba** call-site'y `onInstall` w `MainActivity` symetrycznie.
✅ **ZAWSZE** weryfikuj `apkFile.length() == totalSize` po pętli download.
✅ **ZAWSZE** używaj service_role key tylko z terminala — **nigdy** nie commituj go do repo.

---

## Historia incydentów

| Wersja | Symptom | Root cause | Fix |
|--------|---------|-----------|-----|
| v5.9.0 | Buttony dialogu ucięte przez krawędź ekranu | Długie release_notes z Supabase, brak maxLines | maxLines=4 + TextOverflow.Ellipsis |
| v5.9.1 → v5.9.2 | "Klikam Zainstaluj i nic się nie dzieje" | `output.close()` bez flush+sync; `installApk()` zwraca Unit i loguje błędy | `flush()+fd.sync()`, sealed `InstallResult`, retry loop + Toast |
| v5.9.3 → v5.9.5 | Mimo fix z v5.9.2 użytkownik wciąż widzi "klik bez reakcji" tuż po pobraniu | Retry działa w tle, ale UI nie pokazuje że coś się dzieje; user może kliknąć button który właśnie sprawdza plik | `isApkReady` parametr w UpdateDialog, polling w MainActivity, spinner zamiast buttona dopóki nie ready, auto-refocus po aktywacji |

---

## Quick reference: "Coś z update jest popsute"

1. **"Nie wyświetla się dialog"** → sprawdź czy w `app_updates` jest row z `version_code > local` i czy `apk_url` to public GH URL (anon key MUSI móc go pobrać bez auth).
2. **"Pobieranie failuje na 0%"** → sprawdź `apk_url` w Supabase, `gh release view v<X.Y.Z> --repo dobroslawdab/tv-epg-app`, czy asset `app-debug.apk` istnieje.
3. **"Klik Zainstaluj nic nie robi"** → sprawdź czy `isApkReady` flag działa (logcat `UpdateManager`); sprawdź FileProvider authorities w `AndroidManifest.xml` (`${applicationId}.fileprovider`).
4. **"Toast 'Instalator niedostępny'"** → ROM nie ma `PackageInstaller`. Ścieżka awaryjna: ręczny ADB sideload albo Files app.
5. **"Toast 'Plik się jeszcze zapisuje'"** → retry-loop wyczerpał 5s. Zwykle oznacza że sam download failował (size mismatch) — sprawdź logcat `UpdateManager`.
6. **"INSERT do Supabase zwraca 401/42501"** → używasz anon key zamiast service_role. To **nie** błąd RLS do naprawiania w bazie — to feature (anon ma tylko SELECT).

---

## Lesson learned

Filesystem timing + ACTION_VIEW install + Compose state flip to **trzy niezależne wyścigi** które łatwo zapomnieć przy refactorze. Każdy fix dodaje jedną warstwę obrony; **wszystkie** są potrzebne:

1. **Filesystem layer** — `flush()+fsync()` + size verification (`UpdateManager.downloadApk`)
2. **Result layer** — sealed `InstallResult` zamiast Unit (`UpdateManager.installApk`)
3. **UI layer** — `isApkReady` flag z LaunchedEffect polling, spinner zamiast buttona, retry+Toast (`UpdateDialog` + `MainActivity`)

Brak nawet jednej z tych warstw skutkował konkretnym incydentem produkcyjnym.
