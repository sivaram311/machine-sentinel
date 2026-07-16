import json
from pathlib import Path

p = Path(r"E:\MyAgent\workflow\ports\registry.json")
data = json.loads(p.read_text(encoding="utf-8"))
key = "reservations" if "reservations" in data else "ports"
existing = {r["port"] for r in data[key]}
new = [
    {"port": 3350, "appId": "machine-sentinel", "env": "dev", "role": "api", "status": "reserved", "notes": "Grok GO 2026-07-16; Spring Boot API; observe/classify/ledger"},
    {"port": 3351, "appId": "machine-sentinel", "env": "dev", "role": "http", "status": "reserved", "notes": "UI later"},
    {"port": 3352, "appId": "machine-sentinel", "env": "dev", "role": "worker", "status": "reserved", "notes": "Watcher reserved"},
    {"port": 4350, "appId": "machine-sentinel", "env": "preprod", "role": "http", "status": "reserved", "notes": "PREPROD API"},
    {"port": 4351, "appId": "machine-sentinel", "env": "preprod", "role": "http", "status": "reserved", "notes": "PREPROD UI"},
    {"port": 4352, "appId": "machine-sentinel", "env": "preprod", "role": "worker", "status": "reserved", "notes": "PREPROD watcher"},
    {"port": 5350, "appId": "machine-sentinel", "env": "prod", "role": "http", "status": "reserved", "notes": "PROD API"},
    {"port": 5351, "appId": "machine-sentinel", "env": "prod", "role": "http", "status": "reserved", "notes": "PROD UI"},
    {"port": 5352, "appId": "machine-sentinel", "env": "prod", "role": "worker", "status": "reserved", "notes": "PROD watcher"},
]
for n in new:
    if n["port"] not in existing:
        data[key].append(n)
        print("added", n["port"])
    else:
        print("exists", n["port"])
data["updated"] = "2026-07-16"
p.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
print("ok total", len(data[key]))

# DB registry
dbp = Path(r"E:\MyAgent\workflow\db\registry.json")
db = json.loads(dbp.read_text(encoding="utf-8"))
ids = {a["appId"] for a in db["applications"]}
if "machine-sentinel" not in ids:
    db["applications"].append({
        "appId": "machine-sentinel",
        "database": "app_machine_sentinel",
        "schemas": ["dev", "preprod", "prod"],
        "roles": ["app_machine_sentinel_dev", "app_machine_sentinel_preprod", "app_machine_sentinel_prod"],
        "status": "reserved",
        "notes": "Grok GO 2026-07-16; event ledger; tiny Hikari; observe/classify only v0.1",
    })
    db["updated"] = "2026-07-16"
    dbp.write_text(json.dumps(db, indent=2) + "\n", encoding="utf-8")
    print("db app added")
else:
    print("db app exists")
