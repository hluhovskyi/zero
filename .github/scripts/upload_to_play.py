#!/usr/bin/env python3
import json
import os
import sys

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

AAB_PATH = sys.argv[1]
PACKAGE_NAME = sys.argv[2]

credentials = service_account.Credentials.from_service_account_info(
    json.loads(os.environ["PLAY_SERVICE_ACCOUNT_JSON"]),
    scopes=["https://www.googleapis.com/auth/androidpublisher"],
)
service = build("androidpublisher", "v3", credentials=credentials)
edits = service.edits()

edit = edits.insert(packageName=PACKAGE_NAME, body={}).execute()
edit_id = edit["id"]
print(f"Edit created: {edit_id}")

bundle = edits.bundles().upload(
    packageName=PACKAGE_NAME,
    editId=edit_id,
    media_body=MediaFileUpload(AAB_PATH, mimetype="application/octet-stream"),
).execute()
version_code = bundle["versionCode"]
print(f"AAB uploaded, versionCode: {version_code}")

edits.tracks().update(
    packageName=PACKAGE_NAME,
    editId=edit_id,
    track="internal",
    body={"releases": [{"versionCodes": [version_code], "status": "completed"}]},
).execute()
print("Track assigned: internal")

result = edits.commit(packageName=PACKAGE_NAME, editId=edit_id).execute()
print(f"Edit committed: {result['id']}")
