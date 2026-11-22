#!/usr/bin/env python3
"""
Portable media transfer, verification, pause/drain, and rollback tooling for MeetStudent.

Supports Disk <-> S3 and S3 <-> S3 transfers, PostgreSQL + legacy public/** inventory checks,
SHA-256 verification, API maintenance pause/drain orchestration, and post-reopen reverse sync.
"""

from __future__ import annotations

import argparse
import base64
import csv
import dataclasses
import fnmatch
import hashlib
import hmac
import http.client
import io
import json
import mimetypes
import os
import re
import shlex
import ssl
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable, Iterable, Optional


TEMP_FILE_PATTERN = ".media-*.tmp"
EMPTY_SHA256 = hashlib.sha256(b"").hexdigest()


@dataclasses.dataclass(frozen=True)
class MediaRow:
    id: int
    storage_key: str
    visibility: str
    category: str
    size_bytes: Optional[int]
    content_type: str


def is_temp_filename(name: str) -> bool:
    return fnmatch.fnmatch(name, TEMP_FILE_PATTERN)


def is_valid_key(key: Optional[str]) -> bool:
    """Matches MediaStorage.isValidKey(String) in apps/api."""
    if not key or not (key.startswith("public/") or key.startswith("private/")):
        return False
    for segment in key.split("/"):
        if not segment or segment in (".", "..") or "\\" in segment or "\x00" in segment:
            return False
    return True


def guess_content_type(key: str, explicit: str = "") -> str:
    if explicit:
        return explicit
    guessed, _ = mimetypes.guess_type(key)
    return guessed or "application/octet-stream"


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


class StorageEndpoint:
    """Common storage contract implemented by Disk, Docker-volume, and S3 endpoints."""

    def list_public_keys(self) -> list[str]:
        raise NotImplementedError

    def list_all_keys(self) -> list[str]:
        raise NotImplementedError

    def stat_and_hash(self, key: str) -> Optional[tuple[int, str]]:
        raise NotImplementedError

    def batch_stat_and_hash(self, keys: Iterable[str]) -> dict[str, Optional[tuple[int, str]]]:
        return {k: self.stat_and_hash(k) for k in keys}

    def read_bytes(self, key: str) -> bytes:
        raise NotImplementedError

    def write_bytes(self, key: str, data: bytes, content_type: str = "application/octet-stream") -> None:
        raise NotImplementedError

    def delete_key(self, key: str) -> None:
        raise NotImplementedError


class DiskEndpoint(StorageEndpoint):
    """Local filesystem storage matching DiskMediaStorage key mapping."""

    def __init__(self, upload_dir: Path | str, private_dir: Path | str):
        self.public_root = Path(upload_dir).resolve() / "public"
        self.private_root = Path(private_dir).resolve()
        self.ignored_temp_files: list[str] = []
        self.ignored_symlinks: list[str] = []

    def _resolve(self, key: str) -> Optional[Path]:
        if not is_valid_key(key):
            return None
        prefix, rel = key.split("/", 1)
        base = self.public_root if prefix == "public" else self.private_root
        current = base
        for segment in rel.split("/"):
            current = current / segment
            if current.is_symlink():
                return None
        return current

    def _walk_prefix(self, base: Path, key_prefix: str) -> list[str]:
        if not base.exists() or base.is_symlink() or not base.is_dir():
            return []
        keys: list[str] = []
        for root, dirs, files in os.walk(base, followlinks=False):
            root_path = Path(root)
            rel_dir = root_path.relative_to(base)
            kept_dirs = []
            for d in sorted(dirs):
                dp = root_path / d
                rel_key = f"{key_prefix}/{(rel_dir / d).as_posix()}" if rel_dir != Path(".") else f"{key_prefix}/{d}"
                if dp.is_symlink():
                    self.ignored_symlinks.append(rel_key)
                else:
                    kept_dirs.append(d)
            dirs[:] = kept_dirs
            for f in sorted(files):
                fp = root_path / f
                rel_key = f"{key_prefix}/{(rel_dir / f).as_posix()}" if rel_dir != Path(".") else f"{key_prefix}/{f}"
                if is_temp_filename(f):
                    self.ignored_temp_files.append(rel_key)
                elif fp.is_symlink():
                    self.ignored_symlinks.append(rel_key)
                elif fp.is_file():
                    keys.append(rel_key)
        return sorted(keys)

    def list_public_keys(self) -> list[str]:
        self.ignored_temp_files = []
        self.ignored_symlinks = []
        return self._walk_prefix(self.public_root, "public")

    def list_all_keys(self) -> list[str]:
        self.ignored_temp_files = []
        self.ignored_symlinks = []
        keys = self._walk_prefix(self.public_root, "public")
        keys.extend(self._walk_prefix(self.private_root, "private"))
        return sorted(keys)

    def stat_and_hash(self, key: str) -> Optional[tuple[int, str]]:
        path = self._resolve(key)
        if path is None or not path.exists() or not path.is_file():
            return None
        h = hashlib.sha256()
        total = 0
        with path.open("rb") as fh:
            while True:
                chunk = fh.read(65536)
                if not chunk:
                    break
                total += len(chunk)
                h.update(chunk)
        return total, h.hexdigest()

    def read_bytes(self, key: str) -> bytes:
        path = self._resolve(key)
        if path is None or not path.is_file():
            raise FileNotFoundError(f"Missing disk key: {key}")
        return path.read_bytes()

    def write_bytes(self, key: str, data: bytes, content_type: str = "application/octet-stream") -> None:
        if not is_valid_key(key):
            raise ValueError(f"Invalid storage key: {key}")
        path = self._resolve(key)
        if path is None:
            raise ValueError(f"Symlink encountered for key: {key}")
        path.parent.mkdir(parents=True, exist_ok=True)
        tmp = path.with_name(f".media-{uuid.uuid4()}.tmp")
        try:
            tmp.write_bytes(data)
            os.replace(tmp, path)
        finally:
            if tmp.exists():
                tmp.unlink()

    def delete_key(self, key: str) -> None:
        path = self._resolve(key)
        if path is not None and path.is_file():
            path.unlink()


def run_precheck(
    rows: Iterable[MediaRow],
    source: StorageEndpoint,
    _stats_cache: Optional[dict[str, Optional[tuple[int, str]]]] = None,
) -> dict:
    row_list = list(rows)
    legacy_private_keys = []
    invalid_keys = []
    visibility_mismatches = []
    missing_on_source = []
    db_size_mismatches = []
    seen_db_keys: set[str] = set()

    public_keys = source.list_public_keys()
    public_key_set = set(public_keys)

    valid_db_keys = sorted({r.storage_key for r in row_list if is_valid_key(r.storage_key)})
    all_needed_keys = sorted(set(valid_db_keys) | public_key_set)
    stats = source.batch_stat_and_hash(all_needed_keys)
    if _stats_cache is not None:
        _stats_cache.update(stats)

    for row in row_list:
        key = row.storage_key
        valid = is_valid_key(key)
        if not valid:
            invalid_keys.append({"id": row.id, "storage_key": key, "visibility": row.visibility})
        if row.visibility == "PRIVATE" and not (key or "").startswith("private/"):
            legacy_private_keys.append({"id": row.id, "storage_key": key, "visibility": row.visibility})
        elif row.visibility == "PUBLIC" and not (key or "").startswith("public/"):
            visibility_mismatches.append({"id": row.id, "storage_key": key, "visibility": row.visibility})

        if valid:
            seen_db_keys.add(key)
            stat = stats.get(key)
            if stat is None:
                missing_on_source.append({"id": row.id, "storage_key": key})
            else:
                actual_size, _ = stat
                if row.size_bytes is not None and row.size_bytes >= 0 and actual_size != row.size_bytes:
                    db_size_mismatches.append({
                        "id": row.id,
                        "storage_key": key,
                        "db_size_bytes": row.size_bytes,
                        "actual_size_bytes": actual_size,
                    })

    legacy_public_only_keys = sorted(k for k in public_key_set if k not in seen_db_keys)
    ok = not (legacy_private_keys or invalid_keys or visibility_mismatches or missing_on_source or db_size_mismatches)
    return {
        "ok": ok,
        "db_row_count": len(row_list),
        "public_object_count": len(public_keys),
        "legacy_public_only_keys": legacy_public_only_keys,
        "legacy_private_keys": legacy_private_keys,
        "invalid_keys": invalid_keys,
        "visibility_mismatches": visibility_mismatches,
        "missing_on_source": missing_on_source,
        "db_size_mismatches": db_size_mismatches,
        "ignored_temp_files": getattr(source, "ignored_temp_files", []),
        "ignored_symlinks": getattr(source, "ignored_symlinks", []),
    }


class PrecheckError(RuntimeError):
    def __init__(self, report: dict):
        super().__init__("Pre-copy check failed")
        self.report = report


def build_inventory(rows: Iterable[MediaRow], source: StorageEndpoint) -> dict:
    row_list = list(rows)
    stats: dict[str, Optional[tuple[int, str]]] = {}
    precheck = run_precheck(row_list, source, _stats_cache=stats)
    if not precheck["ok"]:
        raise PrecheckError(precheck)

    items: dict[str, dict] = {}
    for row in row_list:
        key = row.storage_key
        if key not in items:
            stat = stats.get(key)
            if stat is None:
                raise RuntimeError(f"Key vanished during inventory: {key}")
            size, digest = stat
            items[key] = {
                "key": key,
                "size": size,
                "sha256": digest,
                "content_type": guess_content_type(key, row.content_type),
                "in_db": True,
                "db_ids": [row.id],
            }
        else:
            items[key]["db_ids"].append(row.id)

    for key in sorted(k for k in stats if k.startswith("public/") and k not in items):
        stat = stats.get(key)
        if stat is None:
            continue
        size, digest = stat
        items[key] = {
            "key": key,
            "size": size,
            "sha256": digest,
            "content_type": guess_content_type(key),
            "in_db": False,
            "db_ids": [],
        }

    return {
        "precheck": precheck,
        "total_keys": len(items),
        "items": {k: items[k] for k in sorted(items)},
    }


def sync_storage(
    rows: Iterable[MediaRow],
    source: StorageEndpoint,
    target: StorageEndpoint,
    delete_extra: bool = False,
) -> dict:
    inventory = build_inventory(rows, source)
    items = inventory["items"]
    target_stats = target.batch_stat_and_hash(items.keys())

    copied = 0
    updated = 0
    skipped = 0
    deleted = 0
    copied_keys: list[str] = []
    updated_keys: list[str] = []
    deleted_keys: list[str] = []

    for key, entry in items.items():
        target_stat = target_stats.get(key)
        if target_stat is None:
            data = source.read_bytes(key)
            target.write_bytes(key, data, entry["content_type"])
            copied += 1
            copied_keys.append(key)
        elif target_stat != (entry["size"], entry["sha256"]):
            data = source.read_bytes(key)
            target.write_bytes(key, data, entry["content_type"])
            updated += 1
            updated_keys.append(key)
        else:
            skipped += 1

    if delete_extra:
        for target_key in target.list_all_keys():
            if target_key not in items:
                target.delete_key(target_key)
                deleted += 1
                deleted_keys.append(target_key)

    return {
        "ok": True,
        "total_source_keys": len(items),
        "copied": copied,
        "updated": updated,
        "skipped": skipped,
        "deleted": deleted,
        "copied_keys": copied_keys,
        "updated_keys": updated_keys,
        "deleted_keys": deleted_keys,
    }


def verify_storage(
    rows: Iterable[MediaRow],
    source: StorageEndpoint,
    target: StorageEndpoint,
    strict_extra: bool = True,
) -> dict:
    inventory = build_inventory(rows, source)
    items = inventory["items"]
    target_stats = target.batch_stat_and_hash(items.keys())

    missing_on_target: list[str] = []
    size_mismatches: list[dict] = []
    checksum_mismatches: list[dict] = []
    extra_on_target: list[str] = []
    verified_count = 0

    for key, entry in items.items():
        target_stat = target_stats.get(key)
        if target_stat is None:
            missing_on_target.append(key)
            continue
        t_size, t_sha = target_stat
        if t_size != entry["size"]:
            size_mismatches.append({
                "key": key,
                "source_size": entry["size"],
                "target_size": t_size,
            })
        elif t_sha != entry["sha256"]:
            checksum_mismatches.append({
                "key": key,
                "source_sha256": entry["sha256"],
                "target_sha256": t_sha,
            })
        else:
            verified_count += 1

    if strict_extra:
        for target_key in target.list_all_keys():
            if target_key not in items:
                extra_on_target.append(target_key)

    ok = not (missing_on_target or size_mismatches or checksum_mismatches or extra_on_target)
    return {
        "ok": ok,
        "total_source_keys": len(items),
        "verified_count": verified_count,
        "missing_on_target": missing_on_target,
        "size_mismatches": size_mismatches,
        "checksum_mismatches": checksum_mismatches,
        "extra_on_target": extra_on_target,
    }


def _xml_local_name(tag: str) -> str:
    return tag.split("}", 1)[-1]


def parse_list_objects_v2_xml(xml_bytes: bytes) -> tuple[list[str], Optional[str]]:
    root = ET.fromstring(xml_bytes)
    keys: list[str] = []
    is_truncated = False
    next_token: Optional[str] = None
    for child in root:
        name = _xml_local_name(child.tag)
        if name == "IsTruncated" and (child.text or "").strip().lower() == "true":
            is_truncated = True
        elif name == "NextContinuationToken" and child.text:
            next_token = child.text.strip()
        elif name == "Contents":
            for item in child:
                if _xml_local_name(item.tag) == "Key" and item.text:
                    key = item.text
                    if key.endswith("/") or is_temp_filename(key.rsplit("/", 1)[-1]):
                        continue
                    keys.append(key)
    return keys, (next_token if is_truncated else None)


class S3RequestError(RuntimeError):
    def __init__(self, status: int, operation: str, detail: str = ""):
        super().__init__(f"S3 {operation} failed with HTTP {status}: {detail}")
        self.status = status


class S3Endpoint(StorageEndpoint):
    """S3-compatible endpoint using pure-stdlib AWS SigV4 (AWS S3, Cloudflare R2, Hetzner, MinIO)."""

    def __init__(
        self,
        bucket: str,
        region: str,
        endpoint: str = "",
        path_style: bool = False,
        access_key: str = "",
        secret_key: str = "",
        session_token: str = "",
        timeout: int = 30,
    ):
        if not bucket or not region or not access_key or not secret_key:
            raise ValueError("S3 bucket, region, access_key, and secret_key are required")
        self.bucket = bucket
        self.region = region
        self.path_style = path_style
        self.access_key = access_key
        self.secret_key = secret_key
        self.session_token = session_token
        self.timeout = timeout

        if endpoint:
            parsed = urllib.parse.urlparse(endpoint.rstrip("/"))
            if parsed.scheme not in ("http", "https") or not parsed.hostname:
                raise ValueError(f"Invalid S3 endpoint URL: {endpoint}")
            self.scheme = parsed.scheme
            self.base_host = parsed.hostname
            self.port = parsed.port
        else:
            self.scheme = "https"
            self.base_host = f"s3.{region}.amazonaws.com"
            self.port = None

    def _effective_host_and_port(self) -> tuple[str, Optional[int], str]:
        host = self.base_host if self.path_style else f"{self.bucket}.{self.base_host}"
        default_port = 443 if self.scheme == "https" else 80
        host_header = host if self.port in (None, default_port) else f"{host}:{self.port}"
        return host, self.port, host_header

    def _canonical_uri(self, key: Optional[str]) -> str:
        encoded_bucket = urllib.parse.quote(self.bucket, safe="~")
        if self.path_style:
            if not key:
                return f"/{encoded_bucket}"
            return f"/{encoded_bucket}/{urllib.parse.quote(key, safe='/~')}"
        if not key:
            return "/"
        return f"/{urllib.parse.quote(key, safe='/~')}"

    def build_signed_request(
        self,
        method: str,
        key: Optional[str] = None,
        query_params: Optional[dict[str, str]] = None,
        body: bytes = b"",
        content_type: Optional[str] = None,
        now: Optional[datetime] = None,
    ) -> dict:
        now = now or datetime.now(timezone.utc)
        amz_date = now.strftime("%Y%m%dT%H%M%SZ")
        date_stamp = now.strftime("%Y%m%d")

        host, port, host_header = self._effective_host_and_port()
        canonical_uri = self._canonical_uri(key)

        params = query_params or {}
        canonical_qs = "&".join(
            f"{urllib.parse.quote(k, safe='~')}={urllib.parse.quote(v, safe='~')}"
            for k, v in sorted(params.items())
        )
        request_target = f"{canonical_uri}?{canonical_qs}" if canonical_qs else canonical_uri

        payload_hash = sha256_bytes(body) if body else EMPTY_SHA256
        headers_lower = {
            "host": host_header,
            "x-amz-content-sha256": payload_hash,
            "x-amz-date": amz_date,
        }
        if content_type:
            headers_lower["content-type"] = content_type
        if self.session_token:
            headers_lower["x-amz-security-token"] = self.session_token

        sorted_header_names = sorted(headers_lower.keys())
        canonical_headers = "".join(f"{name}:{headers_lower[name].strip()}\n" for name in sorted_header_names)
        signed_headers = ";".join(sorted_header_names)

        canonical_request = "\n".join([
            method.upper(),
            canonical_uri,
            canonical_qs,
            canonical_headers,
            signed_headers,
            payload_hash,
        ])

        scope = f"{date_stamp}/{self.region}/s3/aws4_request"
        string_to_sign = "\n".join([
            "AWS4-HMAC-SHA256",
            amz_date,
            scope,
            sha256_bytes(canonical_request.encode("utf-8")),
        ])

        def _sign(k: bytes, msg: str) -> bytes:
            return hmac.new(k, msg.encode("utf-8"), hashlib.sha256).digest()

        k_date = _sign(("AWS4" + self.secret_key).encode("utf-8"), date_stamp)
        k_region = _sign(k_date, self.region)
        k_service = _sign(k_region, "s3")
        k_signing = _sign(k_service, "aws4_request")
        signature = hmac.new(k_signing, string_to_sign.encode("utf-8"), hashlib.sha256).hexdigest()

        out_headers = {
            "Host": host_header,
            "x-amz-date": amz_date,
            "x-amz-content-sha256": payload_hash,
            "Authorization": (
                f"AWS4-HMAC-SHA256 Credential={self.access_key}/{scope}, "
                f"SignedHeaders={signed_headers}, Signature={signature}"
            ),
        }
        if content_type:
            out_headers["Content-Type"] = content_type
        if self.session_token:
            out_headers["x-amz-security-token"] = self.session_token
        if body or method.upper() == "PUT":
            out_headers["Content-Length"] = str(len(body))

        return {
            "method": method.upper(),
            "connect_host": host,
            "connect_port": port,
            "path": request_target,
            "headers": out_headers,
        }

    def _connect(self, host: str, port: Optional[int]) -> http.client.HTTPConnection:
        if self.scheme == "https":
            return http.client.HTTPSConnection(host, port=port, timeout=self.timeout, context=ssl.create_default_context())
        return http.client.HTTPConnection(host, port=port, timeout=self.timeout)

    def _open_response(
        self,
        method: str,
        key: Optional[str] = None,
        query_params: Optional[dict[str, str]] = None,
        body: bytes = b"",
        content_type: Optional[str] = None,
    ) -> tuple[http.client.HTTPConnection, http.client.HTTPResponse]:
        signed = self.build_signed_request(method, key=key, query_params=query_params, body=body, content_type=content_type)
        conn = self._connect(signed["connect_host"], signed["connect_port"])
        conn.request(signed["method"], signed["path"], body=body if body else None, headers=signed["headers"])
        resp = conn.getresponse()
        return conn, resp

    def _list_prefix(self, prefix: str) -> list[str]:
        keys: list[str] = []
        token: Optional[str] = None
        while True:
            params = {"list-type": "2", "prefix": prefix}
            if token:
                params["continuation-token"] = token
            conn, resp = self._open_response("GET", key=None, query_params=params)
            try:
                raw = resp.read()
                if resp.status != 200:
                    raise S3RequestError(resp.status, f"list({prefix})", raw.decode("utf-8", errors="replace"))
            finally:
                conn.close()
            page_keys, token = parse_list_objects_v2_xml(raw)
            keys.extend(page_keys)
            if not token:
                break
        return sorted(keys)

    def list_public_keys(self) -> list[str]:
        return self._list_prefix("public/")

    def list_all_keys(self) -> list[str]:
        keys = self._list_prefix("public/")
        keys.extend(self._list_prefix("private/"))
        return sorted(keys)

    def stat_and_hash(self, key: str) -> Optional[tuple[int, str]]:
        if not is_valid_key(key):
            return None
        conn, resp = self._open_response("GET", key=key)
        try:
            if resp.status == 404:
                resp.read()
                return None
            if resp.status != 200:
                detail = resp.read().decode("utf-8", errors="replace")
                raise S3RequestError(resp.status, f"get({key})", detail)
            h = hashlib.sha256()
            total = 0
            while True:
                chunk = resp.read(65536)
                if not chunk:
                    break
                total += len(chunk)
                h.update(chunk)
            return total, h.hexdigest()
        finally:
            conn.close()

    def read_bytes(self, key: str) -> bytes:
        conn, resp = self._open_response("GET", key=key)
        try:
            data = resp.read()
            if resp.status == 404:
                raise FileNotFoundError(f"Missing S3 key: {key}")
            if resp.status != 200:
                raise S3RequestError(resp.status, f"get({key})", data.decode("utf-8", errors="replace"))
            return data
        finally:
            conn.close()

    def write_bytes(self, key: str, data: bytes, content_type: str = "application/octet-stream") -> None:
        if not is_valid_key(key):
            raise ValueError(f"Invalid storage key: {key}")
        conn, resp = self._open_response("PUT", key=key, body=data, content_type=content_type)
        try:
            raw = resp.read()
            if resp.status not in (200, 201, 204):
                raise S3RequestError(resp.status, f"put({key})", raw.decode("utf-8", errors="replace"))
        finally:
            conn.close()

    def delete_key(self, key: str) -> None:
        if not is_valid_key(key):
            return
        conn, resp = self._open_response("DELETE", key=key)
        try:
            raw = resp.read()
            if resp.status not in (200, 204, 404):
                raise S3RequestError(resp.status, f"delete({key})", raw.decode("utf-8", errors="replace"))
        finally:
            conn.close()


class DockerVolumeEndpoint(StorageEndpoint):
    """Storage endpoint operating directly on Docker named volumes via a lightweight helper image."""

    def __init__(self, uploads_volume: str, private_volume: str, image: str = "postgres:18-alpine"):
        self.uploads_volume = uploads_volume
        self.private_volume = private_volume
        self.image = image
        self.ignored_temp_files: list[str] = []
        self.ignored_symlinks: list[str] = []

    def _run(self, script: str, args: list[str] = (), input_bytes: Optional[bytes] = None) -> bytes:
        cmd = [
            "docker", "run", "--rm", "-i",
            "-v", f"{self.uploads_volume}:/data/uploads",
            "-v", f"{self.private_volume}:/data/private",
            "--entrypoint", "sh",
            self.image,
            "-c", script, "_", *args,
        ]
        res = subprocess.run(cmd, input=input_bytes, capture_output=True)
        if res.returncode != 0:
            raise RuntimeError(f"Docker volume op failed ({res.returncode}): {res.stderr.decode('utf-8', errors='replace')}")
        return res.stdout

    @staticmethod
    def _disk_rel_path(key: str) -> str:
        prefix, rel = key.split("/", 1)
        return f"/data/uploads/public/{rel}" if prefix == "public" else f"/data/private/{rel}"

    def _list_tree(self, root: str, key_prefix: str) -> list[str]:
        script = (
            'ROOT="$1"; PREFIX="$2"; [ -d "$ROOT" ] || exit 0; '
            'cd "$ROOT" && find . -mindepth 1 \\( -type l -o -type f \\) -exec sh -c \''
            'PREFIX="$1"; shift; '
            'for p; do '
            '  rel="${p#./}"; '
            '  base="$(basename "$rel")"; '
            '  if [ -L "$p" ]; then printf "L\\t%s/%s\\0" "$PREFIX" "$rel"; '
            '  elif case "$base" in .media-*.tmp) true;; *) false;; esac; then printf "T\\t%s/%s\\0" "$PREFIX" "$rel"; '
            '  else printf "F\\t%s/%s\\0" "$PREFIX" "$rel"; fi; '
            'done\' _ "$PREFIX" {} +'
        )
        raw = self._run(script, [root, key_prefix])
        keys: list[str] = []
        for entry in raw.split(b"\x00"):
            if not entry:
                continue
            kind, key = entry.decode("utf-8").split("\t", 1)
            if kind == "T":
                self.ignored_temp_files.append(key)
            elif kind == "L":
                self.ignored_symlinks.append(key)
            elif kind == "F":
                keys.append(key)
        return sorted(keys)

    def list_public_keys(self) -> list[str]:
        self.ignored_temp_files = []
        self.ignored_symlinks = []
        return self._list_tree("/data/uploads/public", "public")

    def list_all_keys(self) -> list[str]:
        self.ignored_temp_files = []
        self.ignored_symlinks = []
        keys = self._list_tree("/data/uploads/public", "public")
        keys.extend(self._list_tree("/data/private", "private"))
        return sorted(keys)

    def stat_and_hash(self, key: str) -> Optional[tuple[int, str]]:
        return self.batch_stat_and_hash([key]).get(key)

    def batch_stat_and_hash(self, keys: Iterable[str]) -> dict[str, Optional[tuple[int, str]]]:
        key_list = list(keys)
        if not key_list:
            return {}
        lines = []
        for k in key_list:
            if is_valid_key(k):
                lines.append(f"{k}\n")
        if not lines:
            return {k: None for k in key_list}
        script = (
            'while IFS= read -r k; do '
            '  prefix="${k%%/*}"; rel="${k#*/}"; '
            '  [ "$prefix" = "public" ] && base="/data/uploads/public" || base="/data/private"; '
            '  p="$base/$rel"; '
            '  if [ -f "$p" ] && [ ! -L "$p" ]; then '
            '    rb=$(readlink -f "$base"); rp=$(readlink -f "$p"); '
            '    case "$rp" in '
            '      "$rb"/*) '
            '        sz=$(wc -c < "$p" | tr -d " "); '
            '        sha=$(sha256sum < "$p" | cut -d" " -f1); '
            '        printf "OK\\t%s\\t%s\\t%s\\n" "$k" "$sz" "$sha"; '
            '        ;; '
            '      *) printf "MISSING\\t%s\\n" "$k" ;; '
            '    esac; '
            '  else '
            '    printf "MISSING\\t%s\\n" "$k"; '
            '  fi; '
            'done'
        )
        out = self._run(script, input_bytes="".join(lines).encode("utf-8")).decode("utf-8")
        result: dict[str, Optional[tuple[int, str]]] = {k: None for k in key_list}
        for line in out.splitlines():
            parts = line.split("\t")
            if parts[0] == "OK" and len(parts) == 4:
                result[parts[1]] = (int(parts[2]), parts[3])
        return result

    def read_bytes(self, key: str) -> bytes:
        if not is_valid_key(key):
            raise FileNotFoundError(f"Invalid key: {key}")
        prefix, rel = key.split("/", 1)
        base = "/data/uploads/public" if prefix == "public" else "/data/private"
        path = f"{base}/{rel}"
        script = (
            'B="$1"; P="$2"; '
            '[ -f "$P" ] && [ ! -L "$P" ] || exit 44; '
            'case "$(readlink -f "$P")" in "$(readlink -f "$B")"/*) cat "$P" ;; *) exit 44 ;; esac'
        )
        try:
            return self._run(script, [base, path])
        except RuntimeError as exc:
            if "failed (44)" in str(exc):
                raise FileNotFoundError(f"Missing volume key: {key}") from exc
            raise

    def write_bytes(self, key: str, data: bytes, content_type: str = "application/octet-stream") -> None:
        if not is_valid_key(key):
            raise ValueError(f"Invalid storage key: {key}")
        path = self._disk_rel_path(key)
        tmp_name = f".media-{uuid.uuid4()}.tmp"
        script = (
            'P="$1"; TMP="$2"; DIR="$(dirname "$P")"; '
            'mkdir -p "$DIR"; '
            'cat > "$DIR/$TMP" && mv -f "$DIR/$TMP" "$P"'
        )
        self._run(script, [path, tmp_name], input_bytes=data)

    def delete_key(self, key: str) -> None:
        if not is_valid_key(key):
            return
        path = self._disk_rel_path(key)
        self._run('P="$1"; [ -L "$P" ] || rm -f "$P"', [path])


class PostgresClient:
    """Queries PostgreSQL media rows and active write transactions via psql."""

    def __init__(
        self,
        psql_cmd: Optional[list[str]] = None,
        host: str = "",
        port: int = 5432,
        database: str = "meetstudent",
        user: str = "meetstudent",
        password: str = "",
    ):
        self.psql_cmd = psql_cmd
        self.host = host
        self.port = port
        self.database = database
        self.user = user
        self.password = password

    def _query_csv(self, sql: str) -> list[dict[str, str]]:
        if self.psql_cmd:
            cmd = [*self.psql_cmd, "-v", "ON_ERROR_STOP=1", "--csv", "-c", sql]
            env = os.environ.copy()
        else:
            cmd = [
                "psql", "-h", self.host, "-p", str(self.port),
                "-U", self.user, "-d", self.database,
                "-v", "ON_ERROR_STOP=1", "--csv", "-c", sql,
            ]
            env = os.environ.copy()
            if self.password:
                env["PGPASSWORD"] = self.password
        res = subprocess.run(cmd, env=env, capture_output=True, text=True)
        if res.returncode != 0:
            raise RuntimeError(f"psql failed ({res.returncode}): {res.stderr.strip()}")
        reader = csv.DictReader(io.StringIO(res.stdout))
        return list(reader)

    def fetch_media_rows(self) -> list[MediaRow]:
        rows = self._query_csv(
            "SELECT id, storage_key, visibility, category, "
            "COALESCE(size_bytes, -1) AS size_bytes, COALESCE(content_type, '') AS content_type "
            "FROM media ORDER BY id"
        )
        out: list[MediaRow] = []
        for r in rows:
            size_val = int(r["size_bytes"])
            out.append(MediaRow(
                id=int(r["id"]),
                storage_key=r["storage_key"],
                visibility=r["visibility"],
                category=r["category"],
                size_bytes=None if size_val < 0 else size_val,
                content_type=r["content_type"],
            ))
        return out

    def count_active_write_transactions(self) -> int:
        rows = self._query_csv(
            "SELECT count(*) AS cnt FROM pg_stat_activity "
            "WHERE datname = current_database() "
            "  AND pid <> pg_backend_pid() "
            "  AND backend_type = 'client backend' "
            "  AND state <> 'idle' "
            "  AND (backend_xid IS NOT NULL "
            "       OR query ~* '\\m(media|schools|programs|courses|users)\\M')"
        )
        return int(rows[0]["cnt"]) if rows else 0


class ApiClient:
    """HTTP client for MeetStudent maintenance pause/drain and read/auth verification."""

    def __init__(
        self,
        base_url: str,
        admin_token: str = "",
        admin_email: str = "",
        admin_password: str = "",
        timeout: int = 15,
    ):
        self.base_url = base_url.rstrip("/")
        self.admin_token = admin_token
        self.admin_email = admin_email
        self.admin_password = admin_password
        self.timeout = timeout

    def _request(
        self,
        method: str,
        path: str,
        token: str = "",
        json_body: Optional[dict] = None,
    ) -> tuple[int, dict[str, str], bytes]:
        url = f"{self.base_url}{path}"
        headers: dict[str, str] = {}
        data = None
        if token:
            headers["Authorization"] = f"Bearer {token}"
        if json_body is not None:
            data = json.dumps(json_body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                return resp.status, {k.lower(): v for k, v in resp.headers.items()}, resp.read()
        except urllib.error.HTTPError as exc:
            return exc.code, {k.lower(): v for k, v in exc.headers.items()}, exc.read()

    def sign_in(self, email: str, password: str) -> str:
        status, _, body = self._request("POST", "/api/v1/auth", json_body={"username": email, "password": password})
        if status != 200:
            raise RuntimeError(f"Sign-in failed for {email}: HTTP {status} {body.decode('utf-8', errors='replace')}")
        return json.loads(body.decode("utf-8"))["accessToken"]

    def _ensure_admin_token(self) -> str:
        if not self.admin_token:
            if not (self.admin_email and self.admin_password):
                raise ValueError("Either --api-token or (--api-admin-email and --api-admin-password) is required")
            self.admin_token = self.sign_in(self.admin_email, self.admin_password)
        return self.admin_token

    def maintenance_status(self) -> dict:
        token = self._ensure_admin_token()
        status, _, body = self._request("GET", "/api/v1/admin/media/maintenance", token=token)
        if status != 200:
            raise RuntimeError(f"GET maintenance status failed: HTTP {status}")
        return json.loads(body.decode("utf-8"))

    def pause(self, wait_seconds: int = 0) -> dict:
        token = self._ensure_admin_token()
        status, _, body = self._request(
            "POST", f"/api/v1/admin/media/maintenance/pause?waitSeconds={int(wait_seconds)}", token=token
        )
        if status != 200:
            raise RuntimeError(f"POST pause failed: HTTP {status} {body.decode('utf-8', errors='replace')}")
        return json.loads(body.decode("utf-8"))

    def resume(self) -> dict:
        token = self._ensure_admin_token()
        status, _, body = self._request("POST", "/api/v1/admin/media/maintenance/resume", token=token)
        if status != 200:
            raise RuntimeError(f"POST resume failed: HTTP {status} {body.decode('utf-8', errors='replace')}")
        return json.loads(body.decode("utf-8"))

    def verify_reads(
        self,
        public_expected: dict[str, str],
        private_expected: dict[int, str],
        owner_token: str = "",
        other_token: str = "",
        expect_writes_paused: Optional[bool] = None,
    ) -> dict:
        errors: list[str] = []
        for key, expected_sha in public_expected.items():
            encoded = "/uploads/" + urllib.parse.quote(key, safe="/~")
            status, _, body = self._request("GET", encoded)
            if status != 200:
                errors.append(f"Public GET {encoded} returned {status}")
            elif sha256_bytes(body) != expected_sha:
                errors.append(f"Public GET {encoded} SHA-256 mismatch")

        for media_id, expected_sha in private_expected.items():
            path = f"/api/v1/media/{media_id}"
            anon_status, _, _ = self._request("GET", path)
            if anon_status != 401:
                errors.append(f"Anonymous GET {path} expected 401, got {anon_status}")
            if other_token:
                other_status, _, _ = self._request("GET", path, token=other_token)
                if other_status != 403:
                    errors.append(f"Non-owner GET {path} expected 403, got {other_status}")
            read_token = owner_token or self._ensure_admin_token()
            ok_status, _, body = self._request("GET", path, token=read_token)
            if ok_status != 200:
                errors.append(f"Authorized GET {path} expected 200, got {ok_status}")
            elif sha256_bytes(body) != expected_sha:
                errors.append(f"Authorized GET {path} SHA-256 mismatch")

        if expect_writes_paused is not None:
            st = self.maintenance_status()
            if bool(st.get("writesPaused")) != expect_writes_paused:
                errors.append(f"Expected writesPaused={expect_writes_paused}, got {st.get('writesPaused')}")

        return {"ok": not errors, "errors": errors}


def run_cutover(
    db: PostgresClient,
    source: StorageEndpoint,
    target: StorageEndpoint,
    api: ApiClient,
    max_pause_seconds: float = 30.0,
    drain_timeout_seconds: float = 15.0,
    initial_sync: bool = True,
    on_activate: Optional[Callable[[], None]] = None,
    on_abort: Optional[Callable[[], None]] = None,
    after_final_sync_hook: Optional[Callable[[], None]] = None,
    verify_reads_fn: Optional[Callable[[], dict]] = None,
    clock: Callable[[], float] = time.monotonic,
    poll_interval: float = 0.2,
) -> dict:
    rows = db.fetch_media_rows()
    precheck = run_precheck(rows, source)
    if not precheck["ok"]:
        return {"status": "ABORTED", "abort_reason": "PRECHECK_FAILED", "precheck": precheck, "pause_duration_ms": 0.0}

    initial_report = sync_storage(rows, source, target, delete_extra=False) if initial_sync else None

    pause_start = clock()
    activated = False

    def _abort(reason: str, extra: Optional[dict] = None) -> dict:
        if activated and on_abort is not None:
            on_abort()
        api.resume()
        elapsed_ms = round(max(0.0, clock() - pause_start) * 1000.0, 2)
        payload = {
            "status": "ABORTED",
            "abort_reason": reason,
            "pause_duration_ms": elapsed_ms,
            "max_pause_ms": round(max_pause_seconds * 1000.0, 2),
        }
        if extra:
            payload.update(extra)
        return payload

    try:
        wait_arg = max(0, min(120, int(min(drain_timeout_seconds, max_pause_seconds))))
        api_status = api.pause(wait_seconds=wait_arg)

        while True:
            api_status = api.maintenance_status()
            active_pg = db.count_active_write_transactions()
            if api_status.get("drained") and active_pg == 0:
                break
            elapsed = clock() - pause_start
            if elapsed > max_pause_seconds:
                return _abort("WINDOW_EXCEEDED", {"api_status": api_status, "active_pg_writes": active_pg})
            if elapsed > drain_timeout_seconds:
                return _abort("DRAIN_TIMEOUT", {"api_status": api_status, "active_pg_writes": active_pg})
            if poll_interval > 0:
                time.sleep(poll_interval)

        if (clock() - pause_start) > max_pause_seconds:
            return _abort("WINDOW_EXCEEDED")

        final_rows = db.fetch_media_rows()
        final_sync = sync_storage(final_rows, source, target, delete_extra=True)
        if after_final_sync_hook is not None:
            after_final_sync_hook()

        if (clock() - pause_start) > max_pause_seconds:
            return _abort("WINDOW_EXCEEDED", {"final_sync": final_sync})

        verification = verify_storage(final_rows, source, target, strict_extra=True)
        if not verification["ok"]:
            return _abort("INVENTORY_DIVERGENCE", {"final_sync": final_sync, "verification": verification})

        if (clock() - pause_start) > max_pause_seconds:
            return _abort("WINDOW_EXCEEDED", {"final_sync": final_sync, "verification": verification})

        if on_activate is not None:
            on_activate()
            activated = True

        if (clock() - pause_start) > max_pause_seconds:
            return _abort("WINDOW_EXCEEDED", {"final_sync": final_sync, "verification": verification})

        if verify_reads_fn is not None:
            reads = verify_reads_fn()
            if not reads.get("ok"):
                return _abort("READ_VERIFICATION_FAILED", {"reads": reads, "verification": verification})

        if (clock() - pause_start) > max_pause_seconds:
            return _abort("WINDOW_EXCEEDED", {"final_sync": final_sync, "verification": verification})

        api.resume()
        elapsed_ms = round(max(0.0, clock() - pause_start) * 1000.0, 2)
        return {
            "status": "COMMITTED",
            "pause_duration_ms": elapsed_ms,
            "max_pause_ms": round(max_pause_seconds * 1000.0, 2),
            "initial_sync": initial_report,
            "final_sync": final_sync,
            "verification": verification,
        }
    except Exception as exc:
        return _abort("ERROR", {"error": str(exc)})


def _endpoint_from_args(args: argparse.Namespace, role: str) -> StorageEndpoint:
    kind = getattr(args, f"{role}_type")
    if kind == "disk":
        return DiskEndpoint(getattr(args, f"{role}_upload_dir"), getattr(args, f"{role}_private_dir"))
    if kind == "docker-volume":
        return DockerVolumeEndpoint(
            getattr(args, f"{role}_upload_volume"),
            getattr(args, f"{role}_private_volume"),
            image=args.docker_helper_image,
        )
    if kind == "s3":
        return S3Endpoint(
            bucket=getattr(args, f"{role}_s3_bucket"),
            region=getattr(args, f"{role}_s3_region"),
            endpoint=getattr(args, f"{role}_s3_endpoint"),
            path_style=getattr(args, f"{role}_s3_path_style"),
            access_key=getattr(args, f"{role}_s3_access_key"),
            secret_key=getattr(args, f"{role}_s3_secret_key"),
            session_token=getattr(args, f"{role}_s3_session_token"),
        )
    raise ValueError(f"Unsupported {role} storage type: {kind}")


def _db_from_args(args: argparse.Namespace) -> PostgresClient:
    psql_cmd = shlex.split(args.psql_cmd) if args.psql_cmd else None
    return PostgresClient(
        psql_cmd=psql_cmd,
        host=args.pg_host,
        port=args.pg_port,
        database=args.pg_db,
        user=args.pg_user,
        password=args.pg_password,
    )


def _api_from_args(args: argparse.Namespace) -> ApiClient:
    return ApiClient(
        base_url=args.api_url,
        admin_token=args.api_token,
        admin_email=args.api_admin_email,
        admin_password=args.api_admin_password,
    )


def _add_storage_flags(parser: argparse.ArgumentParser, role: str, required: bool = False) -> None:
    parser.add_argument(f"--{role}-type", choices=["disk", "docker-volume", "s3"], required=required)
    parser.add_argument(f"--{role}-upload-dir", default="uploads")
    parser.add_argument(f"--{role}-private-dir", default="storage/private")
    parser.add_argument(f"--{role}-upload-volume", default="")
    parser.add_argument(f"--{role}-private-volume", default="")
    parser.add_argument(f"--{role}-s3-bucket", default="")
    parser.add_argument(f"--{role}-s3-region", default="us-east-1")
    parser.add_argument(f"--{role}-s3-endpoint", default="")
    parser.add_argument(f"--{role}-s3-path-style", action="store_true", default=False)
    parser.add_argument(f"--{role}-s3-access-key", default="")
    parser.add_argument(f"--{role}-s3-secret-key", default="")
    parser.add_argument(f"--{role}-s3-session-token", default="")


def _add_db_flags(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--psql-cmd", default=os.environ.get("MEDIA_TRANSFER_PSQL_CMD", ""))
    parser.add_argument("--pg-host", default=os.environ.get("PGHOST", "127.0.0.1"))
    parser.add_argument("--pg-port", type=int, default=int(os.environ.get("PGPORT", "5432")))
    parser.add_argument("--pg-db", default=os.environ.get("PGDATABASE", "meetstudent"))
    parser.add_argument("--pg-user", default=os.environ.get("PGUSER", "meetstudent"))
    parser.add_argument("--pg-password", default=os.environ.get("PGPASSWORD", ""))


def _add_api_flags(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--api-url", default="http://127.0.0.1:8080")
    parser.add_argument("--api-token", default="")
    parser.add_argument("--api-admin-email", default="")
    parser.add_argument("--api-admin-password", default="")


def main(argv: Optional[list[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="MeetStudent portable media transfer & rollback CLI")
    parser.add_argument("--docker-helper-image", default="postgres:18-alpine")
    sub = parser.add_subparsers(dest="command", required=True)

    p_pre = sub.add_parser("precheck")
    _add_storage_flags(p_pre, "source", required=True)
    _add_db_flags(p_pre)

    p_inv = sub.add_parser("inventory")
    _add_storage_flags(p_inv, "source", required=True)
    _add_db_flags(p_inv)

    p_sync = sub.add_parser("sync")
    _add_storage_flags(p_sync, "source", required=True)
    _add_storage_flags(p_sync, "target", required=True)
    _add_db_flags(p_sync)
    p_sync.add_argument("--delete", action="store_true", default=False)

    p_ver = sub.add_parser("verify")
    _add_storage_flags(p_ver, "source", required=True)
    _add_storage_flags(p_ver, "target", required=True)
    _add_db_flags(p_ver)
    p_ver.add_argument("--no-strict-extra", action="store_true", default=False)

    p_pause = sub.add_parser("pause-drain")
    _add_db_flags(p_pause)
    _add_api_flags(p_pause)
    p_pause.add_argument("--drain-timeout-seconds", type=float, default=15.0)
    p_pause.add_argument("--max-pause-seconds", type=float, default=30.0)

    p_resume = sub.add_parser("resume")
    _add_api_flags(p_resume)

    p_cut = sub.add_parser("cutover")
    _add_storage_flags(p_cut, "source", required=True)
    _add_storage_flags(p_cut, "target", required=True)
    _add_db_flags(p_cut)
    _add_api_flags(p_cut)
    p_cut.add_argument("--max-pause-seconds", type=float, default=30.0)
    p_cut.add_argument("--drain-timeout-seconds", type=float, default=15.0)
    p_cut.add_argument("--skip-initial-sync", action="store_true", default=False)
    p_cut.add_argument("--activate-cmd", default="")
    p_cut.add_argument("--abort-cmd", default="")
    p_cut.add_argument("--after-sync-cmd", default="")

    p_vapi = sub.add_parser("verify-api")
    _add_storage_flags(p_vapi, "source", required=True)
    _add_db_flags(p_vapi)
    _add_api_flags(p_vapi)
    p_vapi.add_argument("--owner-token", default="")
    p_vapi.add_argument("--other-token", default="")
    p_vapi.add_argument("--expect-writes-paused", action="store_true", default=False)

    args = parser.parse_args(argv)

    if args.command == "precheck":
        db = _db_from_args(args)
        source = _endpoint_from_args(args, "source")
        report = run_precheck(db.fetch_media_rows(), source)
        print(json.dumps(report, indent=2))
        return 0 if report["ok"] else 2

    if args.command == "inventory":
        db = _db_from_args(args)
        source = _endpoint_from_args(args, "source")
        try:
            inv = build_inventory(db.fetch_media_rows(), source)
            print(json.dumps(inv, indent=2))
            return 0
        except PrecheckError as exc:
            print(json.dumps(exc.report, indent=2))
            return 2

    if args.command == "sync":
        db = _db_from_args(args)
        source = _endpoint_from_args(args, "source")
        target = _endpoint_from_args(args, "target")
        try:
            report = sync_storage(db.fetch_media_rows(), source, target, delete_extra=args.delete)
            print(json.dumps(report, indent=2))
            return 0
        except PrecheckError as exc:
            print(json.dumps(exc.report, indent=2))
            return 2

    if args.command == "verify":
        db = _db_from_args(args)
        source = _endpoint_from_args(args, "source")
        target = _endpoint_from_args(args, "target")
        try:
            report = verify_storage(db.fetch_media_rows(), source, target, strict_extra=not args.no_strict_extra)
            print(json.dumps(report, indent=2))
            return 0 if report["ok"] else 3
        except PrecheckError as exc:
            print(json.dumps(exc.report, indent=2))
            return 2

    if args.command == "pause-drain":
        db = _db_from_args(args)
        api = _api_from_args(args)
        start = time.monotonic()
        api.pause(wait_seconds=max(0, min(120, int(min(args.drain_timeout_seconds, args.max_pause_seconds)))))
        while True:
            st = api.maintenance_status()
            pg_cnt = db.count_active_write_transactions()
            if st.get("drained") and pg_cnt == 0:
                print(json.dumps({
                    "ok": True,
                    "api": st,
                    "active_pg_writes": 0,
                    "elapsed_ms": round((time.monotonic() - start) * 1000.0, 2),
                }, indent=2))
                return 0
            if (time.monotonic() - start) > min(args.drain_timeout_seconds, args.max_pause_seconds):
                api.resume()
                print(json.dumps({
                    "ok": False,
                    "reason": "DRAIN_TIMEOUT",
                    "api": st,
                    "active_pg_writes": pg_cnt,
                    "reopened_writes": True,
                }, indent=2))
                return 4
            time.sleep(0.2)

    if args.command == "resume":
        api = _api_from_args(args)
        print(json.dumps(api.resume(), indent=2))
        return 0

    if args.command == "cutover":
        db = _db_from_args(args)
        source = _endpoint_from_args(args, "source")
        target = _endpoint_from_args(args, "target")
        api = _api_from_args(args)

        def _shell(cmd: str) -> None:
            subprocess.run(["sh", "-c", cmd], check=True)

        report = run_cutover(
            db=db,
            source=source,
            target=target,
            api=api,
            max_pause_seconds=args.max_pause_seconds,
            drain_timeout_seconds=args.drain_timeout_seconds,
            initial_sync=not args.skip_initial_sync,
            on_activate=(lambda: _shell(args.activate_cmd)) if args.activate_cmd else None,
            on_abort=(lambda: _shell(args.abort_cmd)) if args.abort_cmd else None,
            after_final_sync_hook=(lambda: _shell(args.after_sync_cmd)) if args.after_sync_cmd else None,
        )
        print(json.dumps(report, indent=2))
        return 0 if report["status"] == "COMMITTED" else 5

    if args.command == "verify-api":
        db = _db_from_args(args)
        source = _endpoint_from_args(args, "source")
        api = _api_from_args(args)
        rows = db.fetch_media_rows()
        inv = build_inventory(rows, source)
        public_expected = {
            k: v["sha256"] for k, v in inv["items"].items() if k.startswith("public/")
        }
        private_expected = {
            r.id: inv["items"][r.storage_key]["sha256"]
            for r in rows
            if r.visibility == "PRIVATE" and r.storage_key in inv["items"]
        }
        report = api.verify_reads(
            public_expected=public_expected,
            private_expected=private_expected,
            owner_token=args.owner_token,
            other_token=args.other_token,
            expect_writes_paused=True if args.expect_writes_paused else None,
        )
        print(json.dumps(report, indent=2))
        return 0 if report["ok"] else 6

    return 2


if __name__ == "__main__":
    sys.exit(main())
