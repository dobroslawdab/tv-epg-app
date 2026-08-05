#!/bin/bash
# Szybka podmiana tokenu JWT kanałów live.
#
#   ./scripts/set_live_token.sh "eyJ0eXAiOiJKV1QiLCJhbGci..."
#
# Ustawia token na WSZYSTKICH podłączonych urządzeniach (box po WiFi + emulator)
# przez broadcast do LiveTokenReceiver — działa natychmiast, bez Supabase
# i bez rebuilda APK. Dodatkowo, jeśli w środowisku jest SUPABASE_SERVICE_KEY,
# wysyła token do tabeli live_config (wtedy złapią go też urządzenia offline
# przy następnym starcie / pollingu).
#
# Token dekoduje się lokalnie tylko po to, żeby pokazać godzinę wygaśnięcia.

set -u

TOKEN="${1:-}"
if [ -z "$TOKEN" ]; then
    echo "Użycie: $0 \"<JWT>\""
    exit 1
fi

if [ "$(echo -n "$TOKEN" | tr -cd '.' | wc -c | tr -d ' ')" != "2" ]; then
    echo "❌ To nie wygląda na JWT (potrzebne 2 kropki)."
    exit 1
fi

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
PKG="com.uxellence.tv.prod"
RECEIVER="$PKG/com.uxellence.tv.v3.channels.LiveTokenReceiver"

# --- Kiedy wygasa? (payload JWT to jawny base64) ---
PAYLOAD=$(echo -n "$TOKEN" | cut -d. -f2)
# base64url → base64 + padding
PAYLOAD=$(echo -n "$PAYLOAD" | tr '_-' '/+')
case $((${#PAYLOAD} % 4)) in
    2) PAYLOAD="$PAYLOAD==" ;;
    3) PAYLOAD="$PAYLOAD=" ;;
esac
EXP=$(echo -n "$PAYLOAD" | base64 -d 2>/dev/null | sed -n 's/.*"exp":\([0-9]*\).*/\1/p')
if [ -n "$EXP" ]; then
    python3 -c "
from datetime import datetime, timezone, timedelta
tz = timezone(timedelta(hours=2))
exp = datetime.fromtimestamp($EXP, tz)
now = datetime.now(tz)
left = (exp - now).total_seconds() / 3600
print(f'ℹ️  Token wygasa: {exp:%Y-%m-%d %H:%M} (za {left:.1f} h)')
" 2>/dev/null || true
fi

# --- 1) Urządzenia podłączone po adb (natychmiast) ---
DEVICES=$("$ADB" devices | awk '/\tdevice$/{print $1}')
if [ -z "$DEVICES" ]; then
    echo "⚠️  Brak urządzeń w adb (box: adb connect 192.168.31.207:5555)"
else
    for D in $DEVICES; do
        printf "→ %-24s " "$D"
        OUT=$("$ADB" -s "$D" shell am broadcast \
            -a "$PKG.SET_LIVE_TOKEN" -n "$RECEIVER" \
            --es token "$TOKEN" 2>&1 | tr -d '\r')
        case "$OUT" in
            *"result=0"*|*"Broadcast completed"*) echo "OK" ;;
            *) echo "błąd: $OUT" ;;
        esac
    done
fi

# --- 2) Supabase (opcjonalnie — wymaga service_role key w env) ---
if [ -n "${SUPABASE_SERVICE_KEY:-}" ]; then
    printf "→ %-24s " "Supabase live_config"
    CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH \
        "https://kexrkaqxoadxugnnbnjh.supabase.co/rest/v1/live_config?id=eq.1" \
        -H "apikey: $SUPABASE_SERVICE_KEY" \
        -H "Authorization: Bearer $SUPABASE_SERVICE_KEY" \
        -H "Content-Type: application/json" \
        -d "{\"jwt\":\"$TOKEN\",\"updated_at\":\"now()\"}")
    [ "$CODE" = "204" ] && echo "OK" || echo "http=$CODE"
else
    echo "ℹ️  SUPABASE_SERVICE_KEY nieustawiony — pominięto zapis do bazy."
    echo "   (urządzenia z adb mają już nowy token; do bazy: admin-panel/sql/update_live_token.sql)"
fi

echo "✅ Gotowe. Przełącz kanał na TV — token wchodzi od razu."
