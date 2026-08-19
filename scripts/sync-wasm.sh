#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="${1:-$ROOT/../wellfriend-perception/target/wellfriend-wasm}"
python3 - "$ROOT" "$SOURCE" <<'PY'
import hashlib,json,os,shutil,sys
root,source=sys.argv[1:]
with open(os.path.join(source,'manifest.json')) as f: manifest=json.load(f)
with open(os.path.join(source,'checksums.json')) as f: checksums=json.load(f)
if manifest.get('schema_version') != 1 or manifest.get('artifact_kind') != 'wellfriend-wasm-package' or len(manifest.get('source_sha','')) != 40: raise SystemExit('invalid WASM artifact manifest')
records={r['path']:r for r in manifest.get('files',[])}; sums={r['path']:r['sha256'] for r in checksums.get('files',[])}
dest=os.path.join(root,'web','public','wasm'); os.makedirs(dest,exist_ok=True)
for rel in ('wellfriend_perception_bg.wasm','wellfriend_perception.js','wellfriend_perception.d.ts','package.json'):
 p=os.path.join(source,rel); actual=hashlib.sha256(open(p,'rb').read()).hexdigest()
 if rel not in records or actual != records[rel]['sha256'] or actual != sums.get(rel): raise SystemExit('invalid artifact '+rel)
 shutil.copy2(p,os.path.join(dest,rel))
for name in ('manifest.json','checksums.json'): shutil.copy2(os.path.join(source,name),os.path.join(dest,name))
print('Synced verified browser WASM artifact from '+manifest['source_sha'])
PY
