"""Exercise a running local API against its real database. Creates labeled QA data.

Usage: python scripts/verify-api.py --base-url http://127.0.0.1:8080
"""
import argparse
import base64
from concurrent.futures import ThreadPoolExecutor
from decimal import Decimal
import json
from pathlib import Path
import re
import urllib.error
import urllib.request
import uuid


def request(base, path, token=None, method="GET", payload=None, raw=None, content_type=None):
    headers = {}
    if token:
        headers["Authorization"] = "Bearer " + token
    body = raw
    if payload is not None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if content_type:
        headers["Content-Type"] = content_type
    req = urllib.request.Request(base + "/api" + path, body, headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=25) as response:
            status, data = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, data = error.code, error.read()
    try:
        result = json.loads(data)
    except (ValueError, UnicodeDecodeError):
        result = {"data": data}
    return status, result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    args = parser.parse_args()
    base = args.base_url.rstrip("/")
    checks = []

    def check(condition, name):
        if not condition:
            raise AssertionError(name)
        checks.append(name)

    def call(path, token=None, method="GET", payload=None, expected=200):
        status, result = request(base, path, token, method, payload)
        if status != expected:
            raise AssertionError(f"{method} {path}: expected {expected}, got {status}: {result.get('message', '')}")
        return result.get("data")

    def login(username):
        captcha = call("/auth/captcha")
        svg = base64.b64decode(captcha["svg"].split(",", 1)[1]).decode("utf-8")
        code = re.search(r">(\d{4})</text>", svg).group(1)
        result = call("/auth/login", method="POST", payload={
            "username": username, "password": "Demo@2026",
            "captchaId": captcha["captchaId"], "captchaCode": code,
        })
        return result["token"], result["user"]

    admin, admin_user = login("admin")
    doctor, doctor_user = login("doctor")
    reviewer, reviewer_user = login("reviewer")
    check([admin_user["role"], doctor_user["role"], reviewer_user["role"]] == ["ADMIN", "DOCTOR", "REVIEWER"], "three-role login")
    call("/auth/me", expected=401)
    check(True, "anonymous API rejected")
    call("/admin/users", doctor, expected=403)
    call("/plans", doctor, "POST", {}, expected=403)
    check(True, "doctor cannot manage users or plans")

    suffix = uuid.uuid4().hex[:10]
    plan = call("/plans", reviewer, "POST", {
        "code": "QA" + suffix, "name": "联调验证保险", "rateA": .8,
        "rateB": .7, "rateC": .6, "enabled": True, "description": "自动化联调数据",
    })
    surgery = call("/surgeries", reviewer, "POST", {
        "code": "QA" + suffix, "name": "联调验证手术", "category": "一级手术",
        "price": 125.30, "description": "自动化联调数据", "enabled": True,
    })
    patient = call("/patients", reviewer, "POST", {
        "name": "联调验证参保人", "gender": "女", "birthDate": "1990-01-01",
        "phone": "13800000000", "idNumber": "QA-ID-" + suffix,
        "cardNo": "QA-CARD-" + suffix, "address": "演示数据", "planId": plan["id"], "enabled": True,
        "balance": 999999,
    })
    pid = patient["id"]
    check(Decimal(str(patient["balance"])) == 0, "patient creation cannot inject balance")
    for amount in [-1, 50000.01, .001]:
        call(f"/patients/{pid}/recharges", admin, "POST", {"amount": amount, "requestId": str(uuid.uuid4())}, expected=400)
    check(True, "invalid recharge amounts rejected")
    recharge = {"amount": 100, "requestId": str(uuid.uuid4()), "note": "联调验证"}
    first = call(f"/patients/{pid}/recharges", admin, "POST", recharge)
    second = call(f"/patients/{pid}/recharges", admin, "POST", recharge)
    check(first["id"] == second["id"], "recharge replay returns original ledger")
    call(f"/patients/{pid}/recharges", admin, "POST", {**recharge, "amount": 99}, expected=409)
    check(True, "same requestId with different amount rejected")

    visit_input = {"patientId": pid, "outpatientType": "A", "diagnosisFee": 60.45,
                   "diagnosis": "联调验证：费用、审核、部分补缴", "items": [{"surgeryId": surgery["id"], "quantity": 2}]}
    quote = call("/visits/quote", doctor, "POST", visit_input)
    check(Decimal(str(quote["totalAmount"])) == Decimal("311.05") and Decimal(str(quote["claimAmount"])) == Decimal("248.84") and Decimal(str(quote["selfPayAmount"])) == Decimal("62.21"), "server pricing and cent rounding")
    visit_input["requestId"] = str(uuid.uuid4())
    visit = call("/visits", doctor, "POST", visit_input)
    replay = call("/visits", doctor, "POST", visit_input)
    check(visit["id"] == replay["id"] and Decimal(str(call(f"/patients/{pid}", doctor)["balance"])) == Decimal("37.79"), "visit replay deducts only once")
    vid = visit["id"]
    plan["rateA"] = .5
    call(f"/plans/{plan['id']}", reviewer, "PUT", plan)
    snapshot = call(f"/visits/{vid}", reviewer)
    check(Decimal(str(snapshot["reimbursementRate"])) == Decimal("0.8") and Decimal(str(snapshot["totalAmount"])) == Decimal("311.05"), "historical rate and price snapshots retained")
    call(f"/visits/{vid}/review", admin, "POST", {"decision": "APPROVE", "requestId": str(uuid.uuid4())}, expected=403)
    check(True, "administrator cannot bypass reviewer permission")

    def reject(_):
        return request(base, f"/visits/{vid}/review", reviewer, "POST", {
            "decision": "REJECT", "reason": "联调验证拒绝报销", "requestId": str(uuid.uuid4())})

    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(reject, range(2)))
    check(sorted(status for status, _ in results) == [200, 409], "concurrent review has exactly one winner")
    rejected = call(f"/visits/{vid}", reviewer)
    check(Decimal(str(rejected["outstandingAmount"])) == Decimal("211.05") and rejected["paymentStatus"] == "PENDING_TOPUP" and len(rejected["auditRecords"]) == 1, "rejection records exact arrears and single audit")
    check(Decimal(str(call(f"/patients/{pid}", admin)["balance"])) == 0, "rejection never makes balance negative")
    call("/visits", doctor, "POST", {**visit_input, "requestId": str(uuid.uuid4())}, expected=409)
    check(True, "outstanding arrears block new visit")
    call(f"/patients/{pid}/recharges", admin, "POST", {"amount": 200, "requestId": str(uuid.uuid4())})
    partial = call(f"/visits/{vid}/settle-arrears", admin, "POST", {"requestId": str(uuid.uuid4())})
    check(Decimal(str(partial["outstandingAmount"])) == Decimal("11.05"), "partial arrears payment")
    call(f"/patients/{pid}/recharges", admin, "POST", {"amount": 11.05, "requestId": str(uuid.uuid4())})
    settled = call(f"/visits/{vid}/settle-arrears", admin, "POST", {"requestId": str(uuid.uuid4())})
    check(settled["paymentStatus"] == "PAID_IN_FULL" and Decimal(str(settled["outstandingAmount"])) == 0, "arrears fully settled")
    ledger = call(f"/patients/{pid}/transactions", admin)
    check(sum((Decimal(str(row["amount"])) for row in ledger), Decimal(0)) == 0 and all(Decimal(str(row["balanceAfter"])) >= 0 for row in ledger), "ledger reconciles to final balance")

    # Approval records hospital settlement without a second patient deduction.
    call(f"/patients/{pid}/recharges", admin, "POST", {"amount": 100, "requestId": str(uuid.uuid4())})
    approved_visit = call("/visits", doctor, "POST", {**visit_input, "diagnosisFee": 100, "items": [], "requestId": str(uuid.uuid4())})
    before = call(f"/patients/{pid}", admin)["balance"]
    approved = call(f"/visits/{approved_visit['id']}/review", reviewer, "POST", {"decision": "APPROVE", "requestId": str(uuid.uuid4())})
    after = call(f"/patients/{pid}", admin)["balance"]
    check(approved["paymentStatus"] == "SETTLED" and before == after, "approval does not deduct patient twice")

    org = call("/admin/orgs", admin)[0]
    other_name = "qa_" + suffix
    call("/admin/users", admin, "POST", {"username": other_name, "displayName": "联调另一位医生",
         "password": "Demo@2026", "role": "DOCTOR", "orgId": org["id"], "enabled": True})
    other, _ = login(other_name)
    call(f"/visits/{vid}", other, expected=403)
    check(not call("/visits", other) and not call("/dashboard", other)["recentVisits"], "doctor visit detail list and dashboard scope")
    for path in ["/admin/roles", "/admin/permissions", "/admin/dicts", "/admin/menus", "/admin/logs", "/menus", "/files", "/transactions"]:
        call(path, admin)
    check(True, "base management endpoints respond")
    boundary = "anlan" + uuid.uuid4().hex
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"qa-notes.txt\"\r\nContent-Type: text/plain\r\n\r\nQA portfolio attachment\r\n--{boundary}--\r\n").encode()
    status, uploaded = request(base, "/files", admin, "POST", raw=body, content_type="multipart/form-data; boundary=" + boundary)
    check(status == 200, "authenticated file upload")
    fid = uploaded["data"]["id"]
    call(f"/files/{fid}/download", other, expected=403)
    downloaded_status, downloaded = request(base, f"/files/{fid}/download", admin)
    check(downloaded_status == 200 and b"QA portfolio attachment" in downloaded["data"], "file owner download and cross-user isolation")
    call(f"/files/{fid}", admin, "DELETE")
    call("/auth/logout", other, "POST")
    call("/auth/me", other, expected=401)
    check(True, "logout invalidates session")
    result = {"baseUrl": base, "passed": len(checks), "checks": checks, "qaVisitIds": [vid, approved_visit["id"]]}
    destination = Path(__file__).resolve().parents[1] / "docs" / "verification-api.json"
    destination.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"passed": len(checks), "report": str(destination)}, ensure_ascii=True))


if __name__ == "__main__":
    main()
