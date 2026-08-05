#!/bin/bash
# Wgranie makiety na box przez KABEL USB (bez Play Store, bez konta Google,
# bez menedżera plików). Dla partii świeżych boxów: podepnij kabel, odpal
# skrypt, odepnij, powtórz.
#
#   ./scripts/wgraj_na_box.sh                 # użyje ostatniego zbudowanego APK
#   ./scripts/wgraj_na_box.sh ~/Desktop/x.apk # albo wskaż plik
#
# NA BOXIE (jednorazowo, przed pierwszym wgraniem):
#   1. Ustawienia → Preferencje urządzenia → Informacje
#   2. Kliknij 7× w "Kompilacja" — pojawi się "Opcje programisty"
#   3. Opcje programisty → włącz "Debugowanie USB" (i "Debugowanie ADB" jeśli jest)
#   4. Po podpięciu kabla box pokaże pytanie "Zezwolić na debugowanie?" —
#      zaznacz "Zawsze zezwalaj z tego komputera" i potwierdź PILOTEM

set -u

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
PKG="com.uxellence.tv.prod"
DEFAULT_APK="$(cd "$(dirname "$0")/.." && pwd)/app/build/outputs/apk/debug/app-debug.apk"
APK="${1:-$DEFAULT_APK}"

if [ ! -f "$APK" ]; then
    echo "❌ Nie znaleziono APK: $APK"
    exit 1
fi

echo "📦 APK: $APK"
ls -lh "$APK" | awk '{print "   rozmiar: " $5}'

# --- Czekaj na urządzenie po kablu ---
echo "🔌 Czekam na box podpięty kablem USB…"
"$ADB" wait-for-device

SERIAL=$("$ADB" devices | awk '/\tdevice$/{print $1; exit}')
if [ -z "$SERIAL" ]; then
    echo "❌ Box widoczny, ale nieautoryzowany."
    echo "   Spójrz na TV — powinno być pytanie 'Zezwolić na debugowanie?'."
    echo "   Zaznacz 'Zawsze zezwalaj' i potwierdź pilotem, potem odpal skrypt ponownie."
    exit 1
fi

MODEL=$("$ADB" -s "$SERIAL" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
echo "✅ Podłączony: $SERIAL ($MODEL)"

# --- Instalacja (-r = update z zachowaniem danych, -g = przyznaj uprawnienia) ---
echo "⬇️  Instaluję… (przy 55 MB przez USB to zwykle 1–3 min)"
OUT=$("$ADB" -s "$SERIAL" install -r -g "$APK" 2>&1)

if echo "$OUT" | grep -q "Success"; then
    VER=$("$ADB" -s "$SERIAL" shell dumpsys package "$PKG" 2>/dev/null | grep versionName | head -1 | tr -d ' \r')
    echo "✅ Zainstalowano — $VER"
elif echo "$OUT" | grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE\|signatures do not match"; then
    echo "⚠️  Na boxie jest wersja podpisana INNYM kluczem."
    echo "   Trzeba ją odinstalować (UWAGA: kasuje dane aplikacji):"
    echo "     $ADB -s $SERIAL uninstall $PKG"
    exit 1
else
    echo "❌ Instalacja nieudana:"
    echo "$OUT" | tail -3
    exit 1
fi

# --- Ustawienie jako launcher (opcjonalne, makieta zastępuje ekran główny) ---
read -r -p "Ustawić makietę jako launcher (ekran główny) na tym boxie? [t/N] " ANS
if [ "${ANS:-n}" = "t" ] || [ "${ANS:-n}" = "T" ]; then
    "$ADB" -s "$SERIAL" shell cmd role add-role-holder android.app.role.HOME "$PKG" 2>/dev/null \
        && echo "✅ Ustawiono jako launcher" \
        || echo "⚠️  Nie udało się — ustaw ręcznie w Ustawieniach"
fi

echo
echo "🔻 Możesz odpiąć kabel i podpiąć następny box."
echo "⏱️  Po pierwszym uruchomieniu apka wstaje ~2 min — NIE naciskaj pilota w tym czasie"
echo "   (wcześniejszy klawisz kończy się zamknięciem aplikacji)."
