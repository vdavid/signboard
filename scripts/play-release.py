#!/usr/bin/env python3
"""Upload a release bundle to Google Play and roll it out on every track.

    ./scripts/play-release.py path/to/Signboard-release.aab "Release notes, en-US"

Uploads the bundle, points production and internal testing at its versionCode, validates,
and commits. Committing sends the release to Google's review; nothing here can undo that.

Every track gets the new build on purpose: Play's target-API check counts all tracks, so a
stale internal-testing build keeps a policy issue open. See AGENTS.md.

Needs SIGNBOARD_PLAY_SA_KEY in the sops store. Use the signed bundle from the GitHub
Release, so both channels ship the same build.
"""
import json
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

PACKAGE = "com.veszelovszki.signboard"
HOST = "https://androidpublisher.googleapis.com"
API = f"{HOST}/androidpublisher/v3/applications/{PACKAGE}"
UPLOAD = f"{HOST}/upload/androidpublisher/v3/applications/{PACKAGE}"
TRACKS = ["production", "internal"]


def token() -> str:
    script = Path(__file__).with_name("play_token.py")
    return subprocess.run([sys.executable, str(script)], capture_output=True, check=True).stdout.decode().strip()


def call(method: str, url: str, bearer: str, body: bytes | None = None, content_type: str = "application/json") -> dict:
    request = urllib.request.Request(url, method=method, data=body)
    request.add_header("Authorization", f"Bearer {bearer}")
    if body is not None:
        request.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(request) as response:
            raw = response.read()
    except urllib.error.HTTPError as error:
        sys.exit(f"{method} {url} failed: {error.code}\n{error.read().decode()}")
    return json.loads(raw) if raw else {}


def main() -> None:
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    bundle, notes = Path(sys.argv[1]).read_bytes(), sys.argv[2]
    bearer = token()
    edit = call("POST", f"{API}/edits", bearer)["id"]
    uploaded = call(
        "POST", f"{UPLOAD}/edits/{edit}/bundles?uploadType=media", bearer, bundle, "application/octet-stream"
    )
    code = uploaded["versionCode"]
    print(f"uploaded versionCode {code}, sha256 {uploaded.get('sha256')}")
    release = {
        "versionCodes": [str(code)],
        "status": "completed",
        "releaseNotes": [{"language": "en-US", "text": notes}],
    }
    for track in TRACKS:
        body = json.dumps({"track": track, "releases": [release]}).encode()
        call("PUT", f"{API}/edits/{edit}/tracks/{track}", bearer, body)
        print(f"{track}: set to {code}")
    call("POST", f"{API}/edits/{edit}:validate", bearer)
    print("validated")
    call("POST", f"{API}/edits/{edit}:commit", bearer)
    print("committed: sent to review")


if __name__ == "__main__":
    main()
