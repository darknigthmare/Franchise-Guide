#!/usr/bin/env python3
"""Release evidence gate. Status must only be changed after adding actual evidence."""
import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
data=json.loads((root/'docs/RELEASE_GATES.json').read_text())
blocked=[x for x in data['gates'] if x.get('status')!='verified']
print(f"FranchiseGuide {data['release']} : {'NO-GO' if blocked else 'Evidence gates closed; publisher decision required'}")
for x in blocked:
    print(f"[{x['status']}] {x['id']}: {x['why']}\n  Required: {x['required']}")
raise SystemExit(2 if blocked else 0)
