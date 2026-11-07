"""Verify the downloaded trial plugins against the reviewed manifest."""
import hashlib
import json
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
manifest = json.loads((root / "config/trial-artifacts.json").read_text())
failed = False
for artifact in manifest["plugins"]:
    path = root / "trial-data/plugins" / artifact["file"]
    if not path.is_file():
        print(f"MISSING {artifact['file']}")
        failed = True
        continue
    actual = hashlib.sha256(path.read_bytes()).hexdigest()
    valid = actual == artifact["sha256"]
    print(f"{'OK' if valid else 'MISMATCH'} {artifact['file']}")
    failed |= not valid
sys.exit(1 if failed else 0)
