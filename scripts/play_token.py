#!/usr/bin/env python3
"""Print an OAuth access token for the Play Developer API.

Signs a JWT assertion with the service-account key using openssl, so it needs no
third-party libraries. Reads the key from the sops store by default.
"""
import base64
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.parse
import urllib.request

SCOPE = "https://www.googleapis.com/auth/androidpublisher"


def load_key() -> dict:
    if len(sys.argv) > 1:
        return json.load(open(sys.argv[1]))
    raw = subprocess.run(["secret", "SIGNBOARD_PLAY_SA_KEY"], capture_output=True, check=True).stdout
    return json.loads(base64.b64decode(raw))


def b64(data: bytes) -> bytes:
    return base64.urlsafe_b64encode(data).rstrip(b"=")


def main() -> None:
    key = load_key()
    now = int(time.time())
    header = b64(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
    claims = b64(json.dumps({
        "iss": key["client_email"],
        "scope": SCOPE,
        "aud": "https://oauth2.googleapis.com/token",
        "iat": now,
        "exp": now + 3600,
    }).encode())
    signing_input = header + b"." + claims

    key_file = tempfile.NamedTemporaryFile(delete=False)
    key_file.write(key["private_key"].encode())
    key_file.close()
    data_file = tempfile.NamedTemporaryFile(delete=False)
    data_file.write(signing_input)
    data_file.close()
    try:
        signature = subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", key_file.name, data_file.name],
            capture_output=True, check=True,
        ).stdout
    finally:
        os.unlink(key_file.name)
        os.unlink(data_file.name)

    assertion = signing_input + b"." + b64(signature)
    body = urllib.parse.urlencode({
        "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
        "assertion": assertion.decode(),
    }).encode()
    with urllib.request.urlopen("https://oauth2.googleapis.com/token", body) as response:
        print(json.load(response)["access_token"])


if __name__ == "__main__":
    main()
