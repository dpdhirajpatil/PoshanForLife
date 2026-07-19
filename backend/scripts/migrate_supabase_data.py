#!/usr/bin/env python3
"""One-time / re-runnable migration: old Next.js app data (Supabase, pfl_*
tables, read via the PostgREST data API with the service-role key) into the
rebuild's local Postgres schema.

Usage:
    set -a; source backend/.env; set +a
    python3 backend/scripts/migrate_supabase_data.py [--purge] [--dry-run]

  --purge    delete existing rows from the target tables first (keeps the
             dev seed admin login) — the clean path for a full resync
  --dry-run  print the generated SQL instead of applying it

Re-runnable by design:
  * every old string id (e.g. pfl_usr_pat_002) maps to a deterministic
    UUIDv5, so re-runs upsert the same rows and FKs stay consistent
  * users whose email already exists locally reuse the existing row's id
  * everything runs in a single transaction (ON_ERROR_STOP)

Mapping notes (fields the new schema doesn't have yet are dropped; re-run the
script after those feature prompts extend the schema):
  * placeholder password hashes are replaced with bcrypt("Poshan@123") —
    every migrated user signs in with that until reset
  * old health_records carry full InBody data; only weight/body-fat/recorded_at
    fit today's minimal table
  * old sessions have no sub-category — their `mode` (video/...) becomes type
  * old challenges have no serviceCode — CHL-<n> is derived from the old id
  * invoice_counters is bumped to max(imported invoice numbers)+1 per month so
    newly generated invoices can never collide
"""

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
import uuid

TEMP_PASSWORD_HASH = "$2y$10$GaeNelNEgJnpZAJMXfLSuuIEtJ9rBV2kRg8IuykHhZI9ehyGMcp/u"  # Poshan@123
BCRYPT_RE = re.compile(r"^\$2[abxy]\$\d\d\$[./A-Za-z0-9]{53}$")
SEED_ADMIN_EMAIL = "admin@poshanforlife.com"

CATALOGUE_STATUSES = {"DRAFT", "PUBLISHED", "ARCHIVED"}
PP_STATUSES = {"ACTIVE", "COMPLETED", "CANCELLED"}
ORDER_STATUSES = {"ACTIVE", "COMPLETED", "DEACTIVATED"}
PAYMENT_STATUSES = {"PAID", "UNPAID", "PENDING"}
PAYMENT_TYPES = {"OFFLINE", "ONLINE", "CREDIT"}
TX_TYPES = {"ACTIVATION", "DEACTIVATION", "REFUND"}


def die(msg):
    print(f"error: {msg}", file=sys.stderr)
    sys.exit(1)


def supabase_base():
    url = os.environ.get("SUPABASE_URL") or os.environ.get("SUPABASE_STORAGE_URL", "")
    url = url.replace("/storage/v1", "").rstrip("/")
    if not url:
        die("SUPABASE_URL or SUPABASE_STORAGE_URL must be set (source backend/.env)")
    return url + "/rest/v1"


def fetch(table):
    """Read a table via PostgREST. Uses curl — the system Python on macOS
    often has no CA bundle for urllib."""
    base, key = supabase_base(), os.environ.get("SUPABASE_SERVICE_KEY", "")
    if not key:
        die("SUPABASE_SERVICE_KEY must be set (source backend/.env)")
    rows, offset, page = [], 0, 1000
    while True:
        out = subprocess.run(
            ["curl", "-sf", f"{base}/{table}?limit={page}&offset={offset}",
             "-H", f"apikey: {key}", "-H", f"Authorization: Bearer {key}"],
            capture_output=True, text=True, check=True).stdout
        batch = json.loads(out)
        rows.extend(batch)
        if len(batch) < page:
            return rows
        offset += page


def uid(old_id):
    """Deterministic UUID for an old string id — stable across re-runs."""
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "poshanforlife.migration/" + old_id))


def lit(value):
    """SQL literal."""
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        return repr(value)
    return "'" + str(value).replace("'", "''") + "'"


def enum(value, allowed, fallback):
    v = (value or "").strip().upper()
    return v if v in allowed else fallback


def date_only(value):
    return value[:10] if value else None


def upsert(table, cols, rows, conflict="id"):
    if not rows:
        return f"-- {table}: nothing to import\n"
    updates = ", ".join(f"{c} = excluded.{c}" for c in cols if c not in conflict.split(", "))
    values = ",\n".join("  (" + ", ".join(lit(r[c]) for c in cols) + ")" for r in rows)
    return (
        f"INSERT INTO {table} ({', '.join(cols)}) VALUES\n{values}\n"
        f"ON CONFLICT ({conflict}) DO UPDATE SET {updates};\n"
    )


def psql(args, sql_input=None, capture=False):
    db = os.environ.get("MIGRATE_DB", "poshan_api")
    user = os.environ.get("MIGRATE_DB_USER", os.environ.get("USER", "postgres"))
    cmd = ["psql", "-h", "localhost", "-U", user, "-d", db, "-v", "ON_ERROR_STOP=1"] + args
    return subprocess.run(cmd, input=sql_input, text=True, check=True,
                          capture_output=capture)


def existing_users_by_email():
    out = psql(["-tA", "-c", "select email, id, password_hash from users"],
               capture=True).stdout
    return {email: (uid_, hash_) for email, uid_, hash_ in
            (line.split("|") for line in out.strip().splitlines() if "|" in line)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--purge", action="store_true")
    ap.add_argument("--dry-run", action="store_true")
    opts = ap.parse_args()

    print("Fetching old data from Supabase …")
    src = {t: fetch("pfl_" + t) for t in [
        "users", "patient_profiles", "doctor_patients", "programmes", "sessions",
        "challenges", "patient_programmes", "orders", "transactions",
        "health_records", "notifications",
    ]}
    for t, rows in src.items():
        print(f"  pfl_{t}: {len(rows)} rows")

    # -- user id map: reuse local ids for emails that already exist ----------
    local_by_email = {} if opts.dry_run else existing_users_by_email()
    user_id = {}
    for u in src["users"]:
        email = u["email"].strip().lower()
        user_id[u["id"]] = local_by_email.get(email, (uid(u["id"]), None))[0]

    def password_for(old_user):
        """Old real bcrypt hashes carry over; placeholders get the temp
        password. The dev seed admin keeps its local password (Admin@123)
        so the login in active use never silently changes."""
        email = old_user["email"].strip().lower()
        if email == SEED_ADMIN_EMAIL and email in local_by_email:
            return local_by_email[email][1]
        old_hash = old_user.get("password_hash") or ""
        return old_hash if BCRYPT_RE.match(old_hash) else TEMP_PASSWORD_HASH

    dob_by_user = {p["user_id"]: date_only(p.get("date_of_birth"))
                   for p in src["patient_profiles"]}
    profile_user = {p["id"]: p["user_id"] for p in src["patient_profiles"]}

    users = [{
        "id": user_id[u["id"]],
        "name": u["name"],
        "email": u["email"].strip().lower(),
        "password_hash": password_for(u),
        "role": u["role"],
        "phone": u.get("phone"),
        "avatar_url": u.get("avatar_url"),
        "date_of_birth": dob_by_user.get(u["id"]),
        "is_active": u.get("is_active", True),
        "created_at": u["created_at"],
        "updated_at": u["updated_at"],
    } for u in src["users"]]
    temp_pw_users = [u["email"] for o, u in zip(src["users"], users)
                     if u["password_hash"] == TEMP_PASSWORD_HASH]

    profiles = [{
        "id": uid(p["id"]),
        "user_id": user_id[p["user_id"]],
        "gender": p.get("gender"),
        "blood_group": p.get("blood_group"),
        "height_cm": p.get("height_cm"),
        "emergency_contact": p.get("emergency_contact"),
        "medical_history": p.get("medical_history"),
        "doctor_notes": p.get("doctor_notes"),
        "created_at": p["created_at"],
        "updated_at": p["updated_at"],
    } for p in src["patient_profiles"]]

    links = [{
        "id": uid(dp["id"]),
        "doctor_id": user_id[dp["doctor_id"]],
        "patient_id": user_id[dp["patient_id"]],
        "created_at": dp.get("assigned_at"),
        "updated_at": dp.get("assigned_at"),
    } for dp in src["doctor_patients"]]

    dropped_covers = []

    def usable_cover(row):
        """Only real http(s) URLs that fit the column — the old app has at
        least one inline data:-URI test image, which we drop."""
        url = row.get("coverImageUrl")
        if url and (not url.startswith("http") or len(url) > 1000):
            dropped_covers.append(f"{row['name']} ({row['id']})")
            return None
        return url

    def catalogue_row(row, extra):
        return {
            "id": uid(row["id"]),
            "name": row["name"],
            "service_code": row["serviceCode"],
            "type": row["type"],
            "price_inr": row.get("priceInr") or 0,
            "description": row.get("description"),
            "cover_image_url": usable_cover(row),
            "status": enum(row.get("status"), CATALOGUE_STATUSES, "DRAFT"),
            "created_by": user_id[row["createdById"]],
            "created_at": row["createdAt"],
            "updated_at": row["updatedAt"],
            **extra,
        }

    programmes = [catalogue_row(r, {"duration_weeks": r["durationWeeks"]})
                  for r in src["programmes"]]
    sessions = [catalogue_row({**r, "type": r.get("mode") or "general"},
                              {"duration_minutes": r["durationMinutes"]})
                for r in src["sessions"]]
    challenges = [catalogue_row(
        {**r,
         "serviceCode": "CHL-" + ((re.findall(r"\d+", r["id"]) or [uid(r["id"])[:6]])[-1]),
         "type": r.get("category") or "general"},
        {"duration_days": r["durationDays"], "goal_description": r.get("goalDescription") or ""})
        for r in src["challenges"]]

    codes = [{"id": uid("code/" + row["id"]), "code": row["service_code"],
              "item_type": item_type, "item_id": row["id"],
              "created_at": row["created_at"], "updated_at": row["updated_at"]}
             for item_type, rows in
             [("PROGRAMME", programmes), ("SESSION", sessions), ("CHALLENGE", challenges)]
             for row in rows]

    pp_new_id_by_old_order = {p["orderId"]: uid(p["id"])
                              for p in src["patient_programmes"] if p.get("orderId")}

    pps = [{
        "id": uid(p["id"]),
        "patient_id": user_id[p["patientId"]],
        "service_type": enum(p["serviceType"], {"PROGRAMME", "SESSION", "CHALLENGE"}, "PROGRAMME"),
        "programme_id": uid(p["programmeId"]) if p.get("programmeId") else None,
        "session_id": uid(p["sessionId"]) if p.get("sessionId") else None,
        "challenge_id": uid(p["challengeId"]) if p.get("challengeId") else None,
        "start_date": date_only(p.get("startDate")),
        "end_date": date_only(p.get("endDate")),
        "price_inr": p.get("priceInr") or 0,
        "status": enum(p.get("status"), PP_STATUSES, "ACTIVE"),
        "notes": p.get("notes"),
        "assigned_by": user_id.get(p.get("assignedById")),
        "assigned_doctor_id": user_id.get(p.get("doctorId")),
        "created_at": p["createdAt"],
        "updated_at": p["updatedAt"],
    } for p in src["patient_programmes"]]

    orders = [{
        "id": uid(o["id"]),
        "patient_id": user_id[o["patientId"]],
        "patient_programme_id": pp_new_id_by_old_order.get(o["id"]),
        "amount_inr": o.get("amountInr", o.get("total_amount_inr")) or 0,
        "status": enum(o.get("status"), ORDER_STATUSES, "ACTIVE"),
        "payment_status": enum(o.get("paymentStatus"), PAYMENT_STATUSES, "PENDING"),
        "notes": o.get("notes"),
        "created_by": user_id[o["createdById"]],
        "created_at": o["createdAt"],
        "updated_at": o["updatedAt"],
    } for o in src["orders"]]

    txs = [{
        "id": uid(t["id"]),
        "transaction_id": t["transaction_id"],
        "invoice_number": t["invoice_number"],
        "transaction_type": enum(t.get("transaction_type"), TX_TYPES, "ACTIVATION"),
        "payment_type": enum(t.get("payment_type"), PAYMENT_TYPES, "OFFLINE"),
        "price_inr": t.get("price_inr") or 0,
        "discount_inr": t.get("discount_inr") or 0,
        "amount_inr": t.get("amount_inr") or 0,
        "credit_charged": t.get("credit_charged") or 0,
        "source": t.get("source") or "admin",
        "order_id": uid(t["order_id"]),
        "patient_id": user_id[t["patient_id"]],
        "created_by": user_id[t["created_by_id"]],
        "created_at": t.get("transaction_time") or t["created_at"],
        "updated_at": t.get("updated_at") or t["created_at"],
    } for t in src["transactions"]]

    records = [{
        "id": uid(h["id"]),
        "patient_id": user_id[profile_user[h["profile_id"]]],
        "weight_kg": h.get("weight_kg"),
        "body_fat_pct": h.get("pbf_pct"),
        "recorded_at": h["recorded_at"],
        "created_at": h.get("created_at") or h["recorded_at"],
        "updated_at": h.get("updated_at") or h["recorded_at"],
    } for h in src["health_records"] if h.get("profile_id") in profile_user]

    notes = [{
        "id": uid(n["id"]),
        "user_id": user_id[n["user_id"]],
        "type": n.get("type") or "info",
        "message": (n["title"] + ": " + n["message"]) if n.get("title") else n["message"],
        "is_read": n.get("is_read", False),
        "created_at": n["created_at"],
        "updated_at": n["created_at"],
    } for n in src["notifications"] if n.get("user_id") in user_id]

    # per-month invoice counter: max(NNNN)+1, never lowered
    months = {}
    for t in txs:
        m = re.match(r"INV-(\d{6})-(\d{4})$", t["invoice_number"] or "")
        if m:
            months[m.group(1)] = max(months.get(m.group(1), 0), int(m.group(2)))
    counter_sql = "".join(
        f"INSERT INTO invoice_counters (month_key, next_value) VALUES ('{k}', {v + 1})\n"
        f"ON CONFLICT (month_key) DO UPDATE SET next_value ="
        f" GREATEST(invoice_counters.next_value, {v + 1});\n"
        for k, v in sorted(months.items()))

    purge_sql = f"""
-- purge existing rows (dev seed admin login is kept)
DELETE FROM notifications; DELETE FROM transactions; DELETE FROM orders;
DELETE FROM patient_programmes; DELETE FROM catalogue_service_codes;
DELETE FROM programmes; DELETE FROM sessions; DELETE FROM challenges;
DELETE FROM health_records; DELETE FROM doctor_patients; DELETE FROM patient_profiles;
DELETE FROM refresh_tokens;
DELETE FROM users WHERE email <> {lit(SEED_ADMIN_EMAIL)};
""" if opts.purge else ""

    sql = "\n".join([
        "BEGIN;",
        "SET LOCAL timezone TO 'UTC';",  # old naive timestamps are UTC
        purge_sql,
        upsert("users",
               ["id", "name", "email", "password_hash", "role", "phone", "avatar_url",
                "date_of_birth", "is_active", "created_at", "updated_at"], users),
        upsert("patient_profiles",
               ["id", "user_id", "gender", "blood_group", "height_cm", "emergency_contact",
                "medical_history", "doctor_notes", "created_at", "updated_at"],
               profiles, conflict="user_id"),
        upsert("doctor_patients",
               ["id", "doctor_id", "patient_id", "created_at", "updated_at"],
               links, conflict="doctor_id, patient_id"),
        upsert("programmes",
               ["id", "name", "service_code", "type", "price_inr", "description",
                "cover_image_url", "status", "duration_weeks", "created_by",
                "created_at", "updated_at"], programmes),
        upsert("sessions",
               ["id", "name", "service_code", "type", "price_inr", "description",
                "cover_image_url", "status", "duration_minutes", "created_by",
                "created_at", "updated_at"], sessions),
        upsert("challenges",
               ["id", "name", "service_code", "type", "price_inr", "description",
                "cover_image_url", "status", "duration_days", "goal_description",
                "created_by", "created_at", "updated_at"], challenges),
        upsert("catalogue_service_codes",
               ["id", "code", "item_type", "item_id", "created_at", "updated_at"],
               codes, conflict="item_type, item_id"),
        upsert("patient_programmes",
               ["id", "patient_id", "service_type", "programme_id", "session_id",
                "challenge_id", "start_date", "end_date", "price_inr", "status", "notes",
                "assigned_by", "assigned_doctor_id", "created_at", "updated_at"], pps),
        upsert("orders",
               ["id", "patient_id", "patient_programme_id", "amount_inr", "status",
                "payment_status", "notes", "created_by", "created_at", "updated_at"], orders),
        upsert("transactions",
               ["id", "transaction_id", "invoice_number", "transaction_type", "payment_type",
                "price_inr", "discount_inr", "amount_inr", "credit_charged", "source",
                "order_id", "patient_id", "created_by", "created_at", "updated_at"], txs),
        counter_sql,
        upsert("health_records",
               ["id", "patient_id", "weight_kg", "body_fat_pct", "recorded_at",
                "created_at", "updated_at"], records),
        upsert("notifications",
               ["id", "user_id", "type", "message", "is_read", "created_at", "updated_at"],
               notes),
        "COMMIT;",
    ])

    if opts.dry_run:
        print(sql)
        return

    with tempfile.NamedTemporaryFile("w", suffix=".sql", delete=False) as f:
        f.write(sql)
        path = f.name
    print(f"Applying ({'purge + ' if opts.purge else ''}upsert) …")
    psql(["-q", "-f", path])
    os.unlink(path)

    print("\nDone. Imported:")
    for label, rows in [("users", users), ("patient profiles", profiles),
                        ("doctor-patient links", links), ("programmes", programmes),
                        ("sessions", sessions), ("challenges", challenges),
                        ("service assignments", pps), ("orders", orders),
                        ("transactions", txs), ("health records", records),
                        ("notifications", notes)]:
        print(f"  {label}: {len(rows)}")
    if dropped_covers:
        print("\nDropped non-URL cover images (re-upload via the catalogue UI):")
        for item in dropped_covers:
            print(f"  {item}")
    if temp_pw_users:
        print("\nUsers with placeholder hashes got the temp password 'Poshan@123'"
              " (reset via the Users page):")
        for email in temp_pw_users:
            print(f"  {email}")


if __name__ == "__main__":
    main()
