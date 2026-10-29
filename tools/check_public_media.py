#!/usr/bin/env python3
"""Release gate: only explicitly approved public media belongs in distributed source."""
from pathlib import Path
import hashlib,json,os
root=Path(__file__).resolve().parents[1]
approved=json.loads((root/'tools/public_media_allowlist.json').read_text())
extensions={'.png','.gif','.jpg','.jpeg','.webp','.bmp','.avif','.heic','.svg','.ico','.tif','.tiff','.mp4','.webm','.mp3','.wav','.ogg'}
seen={}
for folder,dirs,names in os.walk(root):
 dirs[:]=[d for d in dirs if d not in {'.git','.gradle','.cxx','.kotlin','.idea','build','__pycache__'}]
 if Path(folder)==root:
  # Project-local evidence, private data and toolchains are never publication inputs.
  dirs[:]=[d for d in dirs if d not in {'.tools','.codex-attachments','Backups','Deliverables','Design','Evidence','Maintenance','Private','Work','work'}]
 for name in names:
  path=Path(folder)/name
  if path.suffix.lower() in extensions:
   if path.is_symlink():raise SystemExit('Refusing a media symlink in public source')
   seen[path.relative_to(root).as_posix()]=hashlib.sha256(path.read_bytes()).hexdigest()
if any(approved.get(name)!=digest for name,digest in seen.items()):raise SystemExit('Unapproved media in source: publication blocked. Personal imports must remain private.')
print('PASS: public-source media matches explicitly approved asset hashes; runtime imports excluded.')
