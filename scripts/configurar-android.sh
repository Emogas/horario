#!/usr/bin/env bash
set -e
MANIFEST=android/app/src/main/AndroidManifest.xml
RES=android/app/src/main/res

# Permissões necessárias para alarmes fiáveis
for P in POST_NOTIFICATIONS SCHEDULE_EXACT_ALARM USE_EXACT_ALARM RECEIVE_BOOT_COMPLETED VIBRATE WAKE_LOCK USE_FULL_SCREEN_INTENT; do
  grep -q "android.permission.$P" "$MANIFEST" || \
    sed -i "s#<application#<uses-permission android:name=\"android.permission.$P\" />\n    <application#" "$MANIFEST"
done

# Som do alarme
mkdir -p "$RES/raw"
cp resources/alarme.wav "$RES/raw/alarme.wav"

# Ícone de estado (barra de notificações)
mkdir -p "$RES/drawable"
cat > "$RES/drawable/ic_stat_alarm.xml" <<'XML'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFF"
        android:pathData="M12,2C6.5,2 2,6.5 2,12s4.5,10 10,10 10,-4.5 10,-10S17.5,2 12,2zM12,20c-4.4,0 -8,-3.6 -8,-8s3.6,-8 8,-8 8,3.6 8,8 -3.6,8 -8,8zM12.5,7H11v6l5.2,3.2 0.8,-1.3 -4.5,-2.7V7z"/>
</vector>
XML

# Ícones da aplicação
python3 scripts/gerar_icones.py "$RES" || echo "Aviso: ícones por defeito mantidos"
