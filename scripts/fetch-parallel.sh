#!/usr/bin/env bash
# Parallel range-chunk downloader for the slow alphacephei.com server.
set -euo pipefail
url=$1
out=$2
parts=${3:-8}

len=$(curl -sI "$url" | awk 'tolower($1)=="content-length:"{print $2}' | tr -d '\r')
[ -n "$len" ] || { echo "no content-length"; exit 1; }
echo "len=$len parts=$parts"

chunk=$((( len + parts - 1 ) / parts))
pids=()
for i in $(seq 0 $((parts-1))); do
  start=$((i*chunk))
  name=$(printf '%03d' "$i")
  part="$out.part.$name"
  if [ "$i" -eq "$((parts-1))" ]; then end=$((len-1)); else end=$((start+chunk-1)); fi
  curl -sL --fail --retry 5 --retry-all-errors -o "$part" -r "$start-$end" "$url" &
  pids+=($!)
done
fail=0
for p in "${pids[@]}"; do wait "$p" || fail=1; done
[ "$fail" -eq 0 ] || { echo "a chunk failed"; exit 1; }

got=$(cat "$out".part.* | wc -c)
if [ "$got" -ne "$len" ]; then echo "size mismatch: got $got want $len"; exit 1; fi

rm -f "$out"
cat "$out".part.* > "$out"
rm -f "$out".part.*
echo "done: $out ($(du -h "$out" | cut -f1))"
