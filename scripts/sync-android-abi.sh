#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="${1:-$ROOT/../wellfriend-perception/target/wellfriend-android}"
python3 - "$ROOT" "$SOURCE" <<'PY'
import hashlib,json,os,shutil,sys
root,source=sys.argv[1:]
with open(os.path.join(source,'manifest.json')) as f: manifest=json.load(f)
with open(os.path.join(source,'checksums.json')) as f: checksums=json.load(f)
if manifest.get('schema_version') != 1 or manifest.get('artifact_kind') != 'wellfriend-android-abi' or len(manifest.get('source_sha','')) != 40: raise SystemExit('invalid Android artifact manifest')
records={r['file']:r for r in manifest.get('libraries',[])}; sums={r['path']:r['sha256'] for r in checksums.get('files',[])}
for rel in ('arm64-v8a/libwellfriend_perception.so','arm64-v8a/libwellfriend_perception_jni.so','x86_64/libwellfriend_perception.so','x86_64/libwellfriend_perception_jni.so'):
 p=os.path.join(source,rel); actual=hashlib.sha256(open(p,'rb').read()).hexdigest()
 if rel not in records or actual != records[rel]['sha256'] or actual != sums.get(rel): raise SystemExit('invalid artifact '+rel)
 dest=os.path.join(root,'android','app','src','main','jniLibs',rel); os.makedirs(os.path.dirname(dest),exist_ok=True); shutil.copy2(p,dest)
asset=os.path.join(root,'android','app','src','main','assets','wellfriend-runtime','android'); os.makedirs(asset,exist_ok=True)
for name in ('manifest.json','checksums.json'): shutil.copy2(os.path.join(source,name),os.path.join(asset,name))
print('Synced verified Android ABI artifact from '+manifest['source_sha'])
PY
