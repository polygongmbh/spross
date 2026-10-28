#!/usr/bin/env python3
"""Fetch, or create, the provisioning profiles a release build signs with, through the App Store Connect API.

One profile per bundle id in project.yml's Release config, all on the team's distribution certificate —
the one whose identity sits in the keychain (`security find-identity`), so no development certificate is ever minted.
A profile that is invalid, expired, on another certificate or, for ad-hoc, short of a registered device is replaced.

  scripts/asc-profiles.py app-store [--export-options OUT]   install the App Store profiles
  scripts/asc-profiles.py ad-hoc [--export-options OUT]      install the ad-hoc ones, covering every registered device
  scripts/asc-profiles.py app-store --dry-run                report what exists and would change, touching nothing

Reads ASC_KEY_ID, ASC_ISSUER_ID and ASC_KEY_PATH (the .p8) from the environment.
Prints `SPROSS_PROFILE_<target>=<name>` per target, the settings project.yml's PROVISIONING_PROFILE_SPECIFIER reads;
--export-options writes scripts/ExportOptions-<kind>.plist to OUT with each bundle id's profile filled in.
Needs no package beyond Python and `openssl`.
"""
import argparse
import base64
import datetime
import hashlib
import json
import os
import plistlib
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
API = "https://api.appstoreconnect.apple.com/v1"
PROFILE_DIR = os.path.expanduser("~/Library/Developer/Xcode/UserData/Provisioning Profiles")
KINDS = {"app-store": ("IOS_APP_STORE", "App Store"), "ad-hoc": ("IOS_APP_ADHOC", "Ad Hoc")}


def log(*parts):
    print(*parts, file=sys.stderr)


def targets():
    """Target name → Release bundle id, read off project.yml."""
    spec = open(os.path.join(ROOT, "project.yml"), encoding="utf-8").read()
    prefix = re.search(r"^    Release:\n(?:      .*\n)*?      APP_ID_PREFIX: (\S+)", spec, re.M).group(1)
    found, target = {}, None
    for line in spec.split("\n"):
        if m := re.match(r"^  (\w+):$", line):
            target = m.group(1)
        elif target and (m := re.search(r'PRODUCT_BUNDLE_IDENTIFIER: "\$\(APP_ID_PREFIX\)([^"]*)"', line)):
            found[target] = prefix + m.group(1)
    return found


def der_to_raw(sig):
    """ECDSA signature from openssl's DER SEQUENCE{r, s} to the JWS r||s form."""
    def integer(at):
        assert sig[at] == 2
        size = sig[at + 1]
        return sig[at + 2:at + 2 + size].lstrip(b"\0").rjust(32, b"\0"), at + 2 + size
    r, at = integer(2 if sig[1] < 0x80 else 3)
    s, _ = integer(at)
    return r + s


def token():
    b64 = lambda raw: base64.urlsafe_b64encode(raw).rstrip(b"=")
    now = int(time.time())
    header = {"alg": "ES256", "kid": os.environ["ASC_KEY_ID"], "typ": "JWT"}
    claims = {"iss": os.environ["ASC_ISSUER_ID"], "iat": now, "exp": now + 1200, "aud": "appstoreconnect-v1"}
    signing_input = b64(json.dumps(header).encode()) + b"." + b64(json.dumps(claims).encode())
    der = subprocess.run(["openssl", "dgst", "-sha256", "-sign", os.environ["ASC_KEY_PATH"]],
                         input=signing_input, capture_output=True, check=True).stdout
    return (signing_input + b"." + b64(der_to_raw(der))).decode()


class Api:
    def __init__(self):
        self.auth = {"Authorization": "Bearer " + token(), "Content-Type": "application/json"}

    def call(self, method, url, body=None):
        url = url if url.startswith("https:") else API + url
        request = urllib.request.Request(url, method=method, headers=self.auth,
                                         data=json.dumps(body).encode() if body else None)
        try:
            with urllib.request.urlopen(request) as response:
                raw = response.read()
        except urllib.error.HTTPError as error:
            sys.exit(f"{method} {url.split('?')[0]}: HTTP {error.code} {error.read().decode()[:600]}")
        return json.loads(raw) if raw else None

    def all(self, path, **query):
        """Every page of a list, data and included."""
        url, data, included = path + "?" + urllib.parse.urlencode(query), [], []
        while url:
            page = self.call("GET", url)
            data += page["data"]
            included += page.get("included", [])
            url = page.get("links", {}).get("next")
        return data, included


def keychain_sha1s():
    out = subprocess.run(["security", "find-identity", "-v", "-p", "codesigning"],
                         capture_output=True, text=True, check=True).stdout
    return set(re.findall(r'\) ([0-9A-F]{40}) "(?:Apple|iPhone) Distribution', out))


def certificate(api):
    certs, _ = api.all("/certificates", **{"filter[certificateType]": "DISTRIBUTION,IOS_DISTRIBUTION", "limit": 200})
    local = keychain_sha1s()
    for cert in certs:
        der = base64.b64decode(cert["attributes"]["certificateContent"])
        if hashlib.sha1(der).hexdigest().upper() in local:
            return cert
    sys.exit(f"none of the team's {len(certs)} distribution certificates is in the keychain")


def stale(api, profile, cert_id, devices):
    """Why a profile cannot sign the build, or None."""
    attrs = profile["attributes"]
    if attrs["profileState"] != "ACTIVE":
        return attrs["profileState"].lower()
    if attrs["expirationDate"] < datetime.datetime.now(datetime.timezone.utc).isoformat():
        return "expired"
    if cert_id not in {c["id"] for c in profile["relationships"]["certificates"]["data"]}:
        return "on another certificate"
    if devices is not None:
        listed, _ = api.all(f"/profiles/{profile['id']}/devices", limit=200)
        if {d["id"] for d in listed} != devices:
            return "device list differs from the registered devices"
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("kind", choices=KINDS)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--export-options", metavar="OUT")
    args = parser.parse_args()
    profile_type, label = KINDS[args.kind]

    api = Api()
    wanted = targets()
    bundles, _ = api.all("/bundleIds", **{"filter[identifier]": ",".join(wanted.values()), "limit": 200})
    bundle_ids = {b["attributes"]["identifier"]: b["id"] for b in bundles}
    if missing := set(wanted.values()) - set(bundle_ids):
        sys.exit(f"bundle ids not registered: {', '.join(sorted(missing))}")
    cert = certificate(api)
    log(f"certificate {cert['id']}: {cert['attributes']['name']}, expires {cert['attributes']['expirationDate'][:10]}")
    devices = None
    if args.kind == "ad-hoc":
        registered, _ = api.all("/devices", **{"filter[status]": "ENABLED", "filter[platform]": "IOS", "limit": 200})
        devices = {d["id"] for d in registered}
        log(f"{len(devices)} registered devices")

    names = {}
    for target, bundle in wanted.items():
        name = f"Spross {label} {bundle}"
        found, _ = api.all("/profiles", **{"filter[name]": name, "filter[profileType]": profile_type,
                                           "include": "certificates", "limit": 200})
        found = [p for p in found if p["attributes"]["name"] == name]
        problems = [(p, stale(api, p, cert["id"], devices)) for p in found]
        usable = next((p for p, why in problems if why is None), None)
        state = "ok" if usable else "; ".join(why for _, why in problems) or "missing"
        log(f"{target}: {bundle} → '{name}': {state}" + ("" if usable or not args.dry_run else ", would create"))
        names[target] = name
        if args.dry_run:
            continue
        if not usable:
            for profile, _ in problems:
                api.call("DELETE", f"/profiles/{profile['id']}")
            relations = {"bundleId": {"data": {"type": "bundleIds", "id": bundle_ids[bundle]}},
                         "certificates": {"data": [{"type": "certificates", "id": cert["id"]}]}}
            if devices is not None:
                relations["devices"] = {"data": [{"type": "devices", "id": d} for d in sorted(devices)]}
            usable = api.call("POST", "/profiles", {"data": {
                "type": "profiles", "attributes": {"name": name, "profileType": profile_type},
                "relationships": relations}})["data"]
            log(f"  created {usable['id']}")
        os.makedirs(PROFILE_DIR, exist_ok=True)
        with open(os.path.join(PROFILE_DIR, usable["attributes"]["uuid"] + ".mobileprovision"), "wb") as out:
            out.write(base64.b64decode(usable["attributes"]["profileContent"]))

    for target, name in names.items():
        print(f"SPROSS_PROFILE_{target}={name}")
    if args.export_options:
        with open(os.path.join(ROOT, f"scripts/ExportOptions-{args.kind}.plist"), "rb") as template:
            options = plistlib.load(template)
        options["provisioningProfiles"] = {wanted[t]: n for t, n in names.items()}
        with open(args.export_options, "wb") as out:
            plistlib.dump(options, out)


if __name__ == "__main__":
    main()
