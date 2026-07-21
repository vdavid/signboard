#!/usr/bin/env python3
"""Show what's live on each Google Play track.

    ./scripts/play-status.py

Read-only. The Play API does everything through an "edit", so this opens one, reads, and
always discards it: abandoned edits linger and block later ones.

Needs SIGNBOARD_PLAY_SA_KEY in the sops store; see AGENTS.md.

Caveat worth knowing before you trust the output: the API has no "in review" field. A
submitted release sits at status `inProgress` or `draft` until Google finishes reviewing it
and it flips to `completed`. Rejection reasons and policy warnings are Console-only.
"""
import json
import subprocess
import sys
import urllib.error
import urllib.request

PACKAGE = "com.veszelovszki.signboard"
API = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}"


def token() -> str:
    from pathlib import Path
    script = Path(__file__).with_name("play_token.py")
    return subprocess.run([sys.executable, str(script)], capture_output=True, check=True).stdout.decode().strip()


def call(method: str, path: str, bearer: str) -> dict:
    request = urllib.request.Request(f"{API}{path}", method=method)
    request.add_header("Authorization", f"Bearer {bearer}")
    with urllib.request.urlopen(request) as response:
        body = response.read()
    return json.loads(body) if body else {}


def main() -> None:
    bearer = token()
    edit = call("POST", "/edits", bearer)["id"]
    try:
        tracks = call("GET", f"/edits/{edit}/tracks", bearer).get("tracks", [])
        bundles = call("GET", f"/edits/{edit}/bundles", bearer).get("bundles", [])
    finally:
        try:
            call("DELETE", f"/edits/{edit}", bearer)
        except urllib.error.HTTPError:
            pass

    print("== tracks ==")
    for track in tracks:
        releases = track.get("releases", [])
        if not releases:
            print(f"{track['track']:<12} (no releases)")
            continue
        for release in releases:
            codes = ",".join(str(c) for c in (release.get("versionCodes") or [])) or "-"
            name = release.get("name") or "-"
            fraction = release.get("userFraction")
            rollout = f"  rollout={fraction * 100:.0f}%" if fraction else ""
            print(f"{track['track']:<12} code={codes:<6} name={name:<12} {release.get('status')}{rollout}")

    print("\n== bundles uploaded ==")
    codes = [b["versionCode"] for b in bundles]
    print(", ".join(str(c) for c in codes) if codes else "(none)")


if __name__ == "__main__":
    main()
