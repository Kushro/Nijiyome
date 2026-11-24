#!/usr/bin/env bash
# Corre un comando pesado (Gradle, CMake, un spike) sin poder congelar la máquina:
# dentro de un scope de systemd con tope de memoria y sin swap, con prioridad baja de CPU y de disco.
# Si el comando se pasa del tope, el kernel mata ese proceso; el resto del escritorio sigue respondiendo.
#
#   bash scripts/acotado.sh bash gradlew testDebugUnitTest :app:assembleDebug --no-daemon --max-workers=4
#
# Variables: ACOTADO_MEM (por defecto 12G), ACOTADO_CPU (peso relativo, por defecto 40 sobre 100).
# Regla de uso: un solo trabajo pesado a la vez. Dos builds en paralelo, cada uno con su tope, igual suman.
#
# Códigos propios: 2 = sin comando; 69 = no hay systemd de usuario (no se corre sin tope);
# 75 = ya hay un trabajo pesado en curso. Cualquier otro código es el del comando (137 = lo mató el tope).
set -euo pipefail

if [ "$#" -eq 0 ]; then
  sed -n '2,12p' "$0" >&2
  exit 2
fi

mem="${ACOTADO_MEM:-12G}"
cpu="${ACOTADO_CPU:-40}"

if ! command -v systemd-run >/dev/null 2>&1 || ! systemctl --user show-environment >/dev/null 2>&1; then
  echo "acotado.sh: no hay systemd de usuario en esta sesión; no corro el comando sin tope." >&2
  exit 69
fi

# Un solo archivo de lock por usuario, siempre el mismo.
lockdir="/run/user/$(id -u)"
[ -d "$lockdir" ] || lockdir="/tmp"
lock="$lockdir/nijiyome-acotado-$(id -u).lock"
exec 9>"$lock"
if ! flock -n 9; then
  echo "acotado.sh: ya hay un trabajo pesado en curso (lock $lock). Esperá a que termine." >&2
  exit 75
fi
# Un scope anterior que siga vivo también cuenta (por ejemplo si mataron este script con SIGKILL).
if systemctl --user list-units --type=scope --state=running --no-legend 'nijiyome-acotado-*' 2>/dev/null | grep -q .; then
  echo "acotado.sh: queda un trabajo pesado anterior corriendo (systemctl --user list-units 'nijiyome-acotado-*')." >&2
  exit 75
fi

unit="nijiyome-acotado-$$"
# Al salir, por la razón que sea, no queda nada vivo del scope: ni el comando ni los daemons que haya lanzado.
cleanup() {
  systemctl --user kill --signal=SIGKILL "$unit.scope" >/dev/null 2>&1 || true
  systemctl --user reset-failed "$unit.scope" >/dev/null 2>&1 || true
}
trap cleanup EXIT
trap 'exit 143' TERM
trap 'exit 130' INT
trap 'exit 129' HUP

# El lock lo sostiene solo este script (9>&-), no los procesos del comando.
systemd-run --user --scope --quiet --unit="$unit" \
  -p MemoryMax="$mem" -p MemorySwapMax=0 -p CPUWeight="$cpu" -p IOWeight=20 \
  -- nice -n 10 ionice -c 3 -- "$@" 9>&- &
child=$!
code=0
wait "$child" || code=$?
exit "$code"
