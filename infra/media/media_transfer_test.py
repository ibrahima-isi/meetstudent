import hashlib
import io
import json
import os
import tempfile
import unittest
import urllib.parse
from pathlib import Path
from typing import Optional
from unittest import mock

import media_transfer
from media_transfer import (
    ApiClient,
    DiskEndpoint,
    MediaRow,
    S3Endpoint,
    _verify_api_against_storage,
    build_inventory,
    check_backup_independence,
    is_valid_key,
    main as media_transfer_main,
    parse_list_objects_v2_xml,
    qualify_bucket,
    run_cutover,
    run_precheck,
    sha256_bytes,
    sync_storage,
    verify_storage,
)


class MediaTransferSlice1Test(unittest.TestCase):

    def test_is_valid_key_matches_api_contract(self):
        self.assertTrue(is_valid_key("public/logo.png"))
        self.assertTrue(is_valid_key("public/legacy/old logo.png"))
        self.assertTrue(is_valid_key("private/1234-uuid.pdf"))

        for bad in [
            None,
            "",
            "users/old.pdf",
            "public/",
            "private/",
            "public//double.png",
            "public/../secret.pdf",
            "private/./a.pdf",
            "public/a\\b.png",
            "public/a\x00b.png",
        ]:
            self.assertFalse(is_valid_key(bad), f"Expected invalid key: {bad!r}")

    def test_disk_endpoint_ignores_temp_files_and_symlinks(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            upload_dir = root / "uploads"
            private_dir = root / "private"
            (upload_dir / "public" / "legacy").mkdir(parents=True)
            private_dir.mkdir(parents=True)

            legacy_file = upload_dir / "public" / "legacy" / "old logo.png"
            legacy_file.write_bytes(b"png-bytes")
            (upload_dir / "public" / ".media-temp123.tmp").write_bytes(b"tmp")
            (upload_dir / "stray-outside-public.png").write_bytes(b"stray")

            priv_file = private_dir / "doc.pdf"
            priv_file.write_bytes(b"%PDF-1.4")
            (private_dir / ".media-abc.tmp").write_bytes(b"tmp")

            symlink_target = root / "secret.txt"
            symlink_target.write_text("secret")
            os.symlink(symlink_target, upload_dir / "public" / "link.txt")

            endpoint = DiskEndpoint(upload_dir, private_dir)
            public_keys = endpoint.list_public_keys()
            self.assertEqual(public_keys, ["public/legacy/old logo.png"])
            self.assertEqual(endpoint.ignored_temp_files, [
                "public/.media-temp123.tmp",
            ])
            self.assertEqual(endpoint.ignored_symlinks, ["public/link.txt"])

            all_keys = endpoint.list_all_keys()
            self.assertEqual(all_keys, ["private/doc.pdf", "public/legacy/old logo.png"])

            stat = endpoint.stat_and_hash("public/legacy/old logo.png")
            self.assertEqual(stat, (9, hashlib.sha256(b"png-bytes").hexdigest()))
            self.assertIsNone(endpoint.stat_and_hash("public/link.txt"))

    def test_precheck_flags_legacy_private_invalid_keys_and_missing_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            upload_dir = root / "uploads"
            private_dir = root / "private"
            (upload_dir / "public").mkdir(parents=True)
            private_dir.mkdir(parents=True)

            (upload_dir / "public" / "logo.png").write_bytes(b"logo")
            (upload_dir / "public" / "legacy-only.png").write_bytes(b"legacy")
            (private_dir / "valid.pdf").write_bytes(b"%PDF")

            endpoint = DiskEndpoint(upload_dir, private_dir)
            bad_rows = [
                MediaRow(1, "public/logo.png", "PUBLIC", "SCHOOL_LOGO", 4, "image/png"),
                MediaRow(2, "users/old.pdf", "PRIVATE", "DIPLOMA", 10, "application/pdf"),
                MediaRow(3, "private/../escape.pdf", "PRIVATE", "DIPLOMA", 10, "application/pdf"),
                MediaRow(4, "private/missing.pdf", "PRIVATE", "DIPLOMA", 10, "application/pdf"),
                MediaRow(5, "private/valid.pdf", "PRIVATE", "DIPLOMA", 999, "application/pdf"),
            ]

            report = run_precheck(bad_rows, endpoint)
            self.assertFalse(report["ok"])
            self.assertEqual([r["id"] for r in report["legacy_private_keys"]], [2])
            self.assertEqual([r["id"] for r in report["invalid_keys"]], [2, 3])
            self.assertEqual([r["id"] for r in report["missing_on_source"]], [4])
            self.assertEqual([r["id"] for r in report["db_size_mismatches"]], [5])

            good_rows = [
                MediaRow(1, "public/logo.png", "PUBLIC", "SCHOOL_LOGO", 4, "image/png"),
                MediaRow(5, "private/valid.pdf", "PRIVATE", "DIPLOMA", 4, "application/pdf"),
            ]
            clean_report = run_precheck(good_rows, endpoint)
            self.assertTrue(clean_report["ok"])
            self.assertEqual(clean_report["legacy_public_only_keys"], ["public/legacy-only.png"])

    def test_repeatable_sync_verify_and_reverse_reconciliation(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            src_u, src_p = root / "src_u", root / "src_p"
            dst_u, dst_p = root / "dst_u", root / "dst_p"
            (src_u / "public" / "legacy").mkdir(parents=True)
            src_p.mkdir(parents=True)
            dst_u.mkdir(parents=True)
            dst_p.mkdir(parents=True)

            (src_u / "public" / "logo.png").write_bytes(b"logo-v1")
            (src_u / "public" / "legacy" / "old logo.png").write_bytes(b"legacy-v1")
            (src_u / "public" / ".media-ignored.tmp").write_bytes(b"ignored")
            (src_p / "doc1.pdf").write_bytes(b"%PDF-doc1")
            (src_p / "doc2.pdf").write_bytes(b"%PDF-doc2")

            src = DiskEndpoint(src_u, src_p)
            dst = DiskEndpoint(dst_u, dst_p)
            rows = [
                MediaRow(1, "public/logo.png", "PUBLIC", "SCHOOL_LOGO", 7, "image/png"),
                MediaRow(2, "private/doc1.pdf", "PRIVATE", "DIPLOMA", 9, "application/pdf"),
                MediaRow(3, "private/doc2.pdf", "PRIVATE", "DIPLOMA", 9, "application/pdf"),
            ]

            inv = build_inventory(rows, src)
            self.assertEqual(
                sorted(inv["items"].keys()),
                ["private/doc1.pdf", "private/doc2.pdf", "public/legacy/old logo.png", "public/logo.png"],
            )

            first = sync_storage(rows, src, dst, delete_extra=False)
            self.assertEqual((first["copied"], first["updated"], first["skipped"], first["deleted"]), (4, 0, 0, 0))
            self.assertFalse((dst_u / "public" / ".media-ignored.tmp").exists())

            second = sync_storage(rows, src, dst, delete_extra=False)
            self.assertEqual((second["copied"], second["updated"], second["skipped"], second["deleted"]), (0, 0, 4, 0))
            self.assertTrue(verify_storage(rows, src, dst)["ok"])

            # Mutate destination (simulate target writes reopened: add doc3, modify legacy, delete doc2).
            (dst_p / "doc3.pdf").write_bytes(b"%PDF-doc3")
            (dst_u / "public" / "legacy" / "old logo.png").write_bytes(b"legacy-v2-changed")
            (dst_p / "doc2.pdf").unlink()
            rows_after_reopen = [
                MediaRow(1, "public/logo.png", "PUBLIC", "SCHOOL_LOGO", 7, "image/png"),
                MediaRow(2, "private/doc1.pdf", "PRIVATE", "DIPLOMA", 9, "application/pdf"),
                MediaRow(4, "private/doc3.pdf", "PRIVATE", "DIPLOMA", 9, "application/pdf"),
            ]

            # Reverse sync dst -> src reconciles additions, modifications, and deletions!
            rev = sync_storage(rows_after_reopen, dst, src, delete_extra=True)
            self.assertEqual((rev["copied"], rev["updated"], rev["skipped"], rev["deleted"]), (1, 1, 2, 1))
            self.assertTrue(verify_storage(rows_after_reopen, dst, src)["ok"])
            self.assertFalse((src_p / "doc2.pdf").exists())
            self.assertEqual((src_u / "public" / "legacy" / "old logo.png").read_bytes(), b"legacy-v2-changed")
            self.assertEqual((src_p / "doc3.pdf").read_bytes(), b"%PDF-doc3")

    def test_verify_explicitly_reports_missing_size_checksum_and_extra_divergences(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            src = DiskEndpoint(root / "su", root / "sp")
            dst = DiskEndpoint(root / "du", root / "dp")
            src.write_bytes("public/a.png", b"1234", "image/png")
            src.write_bytes("public/b.png", b"abcd", "image/png")
            src.write_bytes("private/c.pdf", b"missing", "application/pdf")

            dst.write_bytes("public/a.png", b"12", "image/png")       # size mismatch
            dst.write_bytes("public/b.png", b"abce", "image/png")     # same size (4), checksum mismatch
            dst.write_bytes("private/extra.pdf", b"stray", "application/pdf")

            rows = [
                MediaRow(1, "public/a.png", "PUBLIC", "SCHOOL_LOGO", 4, "image/png"),
                MediaRow(2, "public/b.png", "PUBLIC", "SCHOOL_LOGO", 4, "image/png"),
                MediaRow(3, "private/c.pdf", "PRIVATE", "DIPLOMA", 7, "application/pdf"),
            ]
            report = verify_storage(rows, src, dst)
            self.assertFalse(report["ok"])
            self.assertEqual(report["missing_on_target"], ["private/c.pdf"])
            self.assertEqual([x["key"] for x in report["size_mismatches"]], ["public/a.png"])
            self.assertEqual([x["key"] for x in report["checksum_mismatches"]], ["public/b.png"])
            self.assertEqual(report["extra_on_target"], ["private/extra.pdf"])

    def test_s3_sigv4_signing_and_xml_pagination_helpers(self):
        s3_path = S3Endpoint(
            bucket="my-bucket",
            region="auto",
            endpoint="https://acct.eu.r2.cloudflarestorage.com",
            path_style=True,
            access_key="ak",
            secret_key="sk",
            session_token="tok",
        )
        req = s3_path.build_signed_request(
            "GET",
            key="public/legacy/old logo.png",
            query_params={},
            body=b"",
        )
        self.assertEqual(req["path"], "/my-bucket/public/legacy/old%20logo.png")
        self.assertEqual(req["headers"]["Host"], "acct.eu.r2.cloudflarestorage.com")
        self.assertEqual(req["headers"]["x-amz-security-token"], "tok")
        self.assertIn("Credential=ak/", req["headers"]["Authorization"])
        self.assertIn("/auto/s3/aws4_request", req["headers"]["Authorization"])

        s3_vhost = S3Endpoint(
            bucket="my-bucket",
            region="eu-west-1",
            endpoint="",
            path_style=False,
            access_key="ak",
            secret_key="sk",
        )
        req_list = s3_vhost.build_signed_request(
            "GET",
            key=None,
            query_params={"list-type": "2", "prefix": "public/"},
            body=b"",
        )
        self.assertEqual(req_list["headers"]["Host"], "my-bucket.s3.eu-west-1.amazonaws.com")
        self.assertEqual(req_list["path"], "/?list-type=2&prefix=public%2F")

        xml_body = (
            '<?xml version="1.0" encoding="UTF-8"?>'
            '<ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">'
            '<IsTruncated>true</IsTruncated>'
            '<NextContinuationToken>tok-2</NextContinuationToken>'
            '<Contents><Key>public/a.png</Key><Size>4</Size></Contents>'
            '<Contents><Key>public/.media-123.tmp</Key><Size>2</Size></Contents>'
            '</ListBucketResult>'
        ).encode("utf-8")
        keys, next_tok = parse_list_objects_v2_xml(xml_body)
        self.assertEqual(keys, ["public/a.png"])
        self.assertEqual(next_tok, "tok-2")

    def test_cutover_orchestrator_commits_and_aborts_cleanly_reopening_writes(self):
        class FakeDb:
            def __init__(self, rows, active_tx_counts=None):
                self._rows = rows
                self._tx = list(active_tx_counts or [0])

            def fetch_media_rows(self):
                return list(self._rows)

            def count_active_write_transactions(self):
                if len(self._tx) > 1:
                    return self._tx.pop(0)
                return self._tx[0]

        class FakeApi:
            def __init__(self):
                self.paused = False
                self.pause_calls = 0
                self.resume_calls = 0

            def maintenance_status(self):
                return {"writesPaused": self.paused, "inFlightWrites": 0, "pendingDeferredDeletions": 0, "drained": self.paused}

            def pause(self, wait_seconds=0):
                self.paused = True
                self.pause_calls += 1
                return self.maintenance_status()

            def resume(self):
                self.paused = False
                self.resume_calls += 1
                return self.maintenance_status()

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            src = DiskEndpoint(root / "su", root / "sp")
            dst = DiskEndpoint(root / "du", root / "dp")
            src.write_bytes("public/logo.png", b"logo", "image/png")
            rows = [MediaRow(1, "public/logo.png", "PUBLIC", "SCHOOL_LOGO", 4, "image/png")]
            db = FakeDb(rows)
            api = FakeApi()

            # 1. Happy path commits and reopens writes after activation.
            activated = []
            res = run_cutover(
                db=db,
                source=src,
                target=dst,
                api=api,
                max_pause_seconds=5.0,
                drain_timeout_seconds=2.0,
                initial_sync=True,
                on_activate=lambda: activated.append("yes"),
            )
            self.assertEqual(res["status"], "COMMITTED")
            self.assertEqual(activated, ["yes"])
            self.assertFalse(api.paused)
            self.assertEqual(api.resume_calls, 1)

            # 2. Divergence between final sync and verify aborts and reopens writes on source.
            res_div = run_cutover(
                db=db,
                source=src,
                target=dst,
                api=api,
                max_pause_seconds=5.0,
                drain_timeout_seconds=2.0,
                initial_sync=False,
                after_final_sync_hook=lambda: dst.write_bytes("public/logo.png", b"corrupt", "image/png"),
            )
            self.assertEqual(res_div["status"], "ABORTED")
            self.assertEqual(res_div["abort_reason"], "INVENTORY_DIVERGENCE")
            self.assertFalse(api.paused)

            # 3. Stuck Postgres transaction exceeding max_pause_seconds aborts and reopens writes.
            ticks = iter([0.0, 0.1, 0.3, 0.6, 1.2])
            stuck_db = FakeDb(rows, active_tx_counts=[1, 1, 1, 1])
            res_timeout = run_cutover(
                db=stuck_db,
                source=src,
                target=dst,
                api=api,
                max_pause_seconds=0.5,
                drain_timeout_seconds=0.5,
                initial_sync=False,
                clock=lambda: next(ticks, 2.0),
                poll_interval=0.0,
            )
            self.assertEqual(res_timeout["status"], "ABORTED")
            self.assertIn(res_timeout["abort_reason"], ("WINDOW_EXCEEDED", "DRAIN_TIMEOUT"))
            self.assertFalse(api.paused)

    def test_api_client_verify_reads_checks_public_private_auth_and_pause_state(self):
        class StubApiClient(ApiClient):
            def __init__(self):
                super().__init__("http://api.test", admin_token="adm")

            def _request(self, method, path, token="", json_body=None):
                if path == "/uploads/public/legacy/old%20logo.png":
                    return 200, {}, b"png-bytes"
                if path == "/api/v1/media/42":
                    if not token:
                        return 401, {}, b""
                    if token == "other":
                        return 403, {}, b""
                    if token in ("owner", "adm"):
                        return 200, {}, b"%PDF-42"
                if path == "/api/v1/admin/media/maintenance" and token == "adm":
                    return 200, {}, b'{"writesPaused":true,"drained":true}'
                return 404, {}, b""

        client = StubApiClient()
        ok = client.verify_reads(
            public_expected={"public/legacy/old logo.png": sha256_bytes(b"png-bytes")},
            private_expected={42: sha256_bytes(b"%PDF-42")},
            owner_token="owner",
            other_token="other",
            expect_writes_paused=True,
        )
        self.assertTrue(ok["ok"], ok)

        bad = client.verify_reads(
            public_expected={"public/legacy/old logo.png": "0" * 64},
            private_expected={42: sha256_bytes(b"%PDF-42")},
            owner_token="owner",
            other_token="other",
            expect_writes_paused=False,
        )
        self.assertFalse(bad["ok"])
        self.assertEqual(len(bad["errors"]), 2)


class _MemoryResponse:
    def __init__(self, status: int, headers: dict[str, str], body: bytes):
        self.status = status
        self._headers = headers
        self._stream = io.BytesIO(body)

    def read(self, amt=None):
        return self._stream.read() if amt is None else self._stream.read(amt)

    def getheaders(self):
        return list(self._headers.items())


class _MemoryS3Connection:
    def __init__(self, server, host: str, port):
        self.server = server
        self.host = host
        self.port = port
        self._resp = None

    def request(self, method: str, path: str, body=None, headers=None):
        self._resp = self.server.handle(self.host, self.port, method, path, body or b"", headers or {})

    def getresponse(self):
        return self._resp

    def close(self):
        pass


class _InMemoryS3Server:
    """Minimal S3-compatible protocol router verifying host/path addressing, SigV4 presence, Range 206, and public/private ACLs."""

    def __init__(self, bucket: str, path_style: bool = True, public_anonymous: bool = False, deny_missing_with_403: bool = False, ignore_range: bool = False):
        self.bucket = bucket
        self.path_style = path_style
        self.public_anonymous = public_anonymous
        self.deny_missing_with_403 = deny_missing_with_403
        self.ignore_range = ignore_range
        self.objects: dict[str, tuple[bytes, str]] = {}
        self.requests: list[tuple[str, str, str, dict[str, str]]] = []

    def bind(self, s3: S3Endpoint) -> S3Endpoint:
        s3._connect = lambda host, port: _MemoryS3Connection(self, host, port)
        return s3

    def _extract_key(self, path: str, headers: dict[str, str]) -> tuple[Optional[str], dict[str, str]]:
        parsed = urllib.parse.urlsplit(path)
        qs = dict(urllib.parse.parse_qsl(parsed.query, keep_blank_values=True))
        raw_path = urllib.parse.unquote(parsed.path)
        if self.path_style:
            prefix = f"/{self.bucket}"
            if raw_path == prefix or raw_path == prefix + "/":
                return None, qs
            if raw_path.startswith(prefix + "/"):
                return raw_path[len(prefix) + 1:], qs
            return "__WRONG_BUCKET__", qs
        host_hdr = headers.get("Host", "")
        if not host_hdr.startswith(f"{self.bucket}."):
            return "__WRONG_BUCKET__", qs
        key = raw_path.lstrip("/")
        return (key or None), qs

    def handle(self, host: str, port, method: str, path: str, body: bytes, headers: dict[str, str]) -> _MemoryResponse:
        self.requests.append((method, host, path, dict(headers)))
        key, qs = self._extract_key(path, headers)
        if key == "__WRONG_BUCKET__":
            return _MemoryResponse(404, {}, b"<Error><Code>NoSuchBucket</Code></Error>")

        is_signed = bool(headers.get("Authorization"))
        if not is_signed:
            if self.public_anonymous and key is not None and key.startswith("public/") and key in self.objects:
                data, ctype = self.objects[key]
                return _MemoryResponse(200, {"Content-Type": ctype, "Content-Length": str(len(data))}, data)
            return _MemoryResponse(403, {}, b"<Error><Code>AccessDenied</Code></Error>")

        if method == "PUT" and key is not None:
            ctype = headers.get("Content-Type", "application/octet-stream")
            self.objects[key] = (body, ctype)
            return _MemoryResponse(200, {}, b"")

        if method == "DELETE" and key is not None:
            self.objects.pop(key, None)
            return _MemoryResponse(204, {}, b"")

        if method == "HEAD" and key is not None:
            if key not in self.objects:
                return _MemoryResponse(403 if self.deny_missing_with_403 else 404, {}, b"")
            data, ctype = self.objects[key]
            return _MemoryResponse(200, {"Content-Length": str(len(data)), "Content-Type": ctype}, b"")

        if method == "GET" and key is None and qs.get("list-type") == "2":
            prefix = qs.get("prefix", "")
            matched = sorted(k for k in self.objects if k.startswith(prefix))
            items_xml = "".join(
                f"<Contents><Key>{k}</Key><Size>{len(self.objects[k][0])}</Size></Contents>"
                for k in matched
            )
            xml = (
                '<?xml version="1.0" encoding="UTF-8"?>'
                '<ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">'
                '<IsTruncated>false</IsTruncated>'
                f'{items_xml}</ListBucketResult>'
            ).encode("utf-8")
            return _MemoryResponse(200, {"Content-Type": "application/xml"}, xml)

        if method == "GET" and key is not None:
            if key not in self.objects:
                return _MemoryResponse(403 if self.deny_missing_with_403 else 404, {}, b"")
            data, ctype = self.objects[key]
            rng = headers.get("Range")
            if rng and not self.ignore_range and rng.startswith("bytes="):
                start_s, end_s = rng[6:].split("-", 1)
                start, end = int(start_s), int(end_s)
                part = data[start : end + 1]
                return _MemoryResponse(
                    206,
                    {
                        "Content-Type": ctype,
                        "Content-Range": f"bytes {start}-{end}/{len(data)}",
                        "Content-Length": str(len(part)),
                    },
                    part,
                )
            return _MemoryResponse(200, {"Content-Type": ctype, "Content-Length": str(len(data))}, data)

        return _MemoryResponse(400, {}, b"")


class MediaTransferSlice2S3EndpointTest(unittest.TestCase):

    def test_s3_endpoint_rejects_malformed_endpoints_with_userinfo_query_or_fragment(self):
        for bad_ep in [
            "ftp://s3.example.test",
            "https://user:secret@s3.example.test",
            "https://s3.example.test?foo=bar",
            "https://s3.example.test#frag",
            "not-a-url",
        ]:
            with self.assertRaises(ValueError, msg=f"Expected ValueError for {bad_ep}"):
                S3Endpoint(
                    bucket="b",
                    region="eu-west-1",
                    endpoint=bad_ep,
                    path_style=True,
                    access_key="ak",
                    secret_key="sk",
                )

    def test_s3_endpoint_signed_range_head_and_anonymous_probe_for_path_and_virtual_hosted(self):
        payload = b"0123456789abcdefghij"
        for path_style, endpoint in [(True, "https://s3.a.test"), (False, "https://s3.b.test")]:
            srv = _InMemoryS3Server("my-bucket", path_style=path_style)
            srv.objects["public/present.txt"] = (payload, "text/plain")
            ep = srv.bind(S3Endpoint(
                bucket="my-bucket",
                region="eu-west-1",
                endpoint=endpoint,
                path_style=path_style,
                access_key="ak",
                secret_key="sk",
            ))

            self.assertEqual(ep.head_object_status("public/present.txt"), 200)
            self.assertEqual(ep.head_object_status("public/missing.txt"), 404)

            status, hdrs, body = ep.read_range("public/present.txt", 2, 4)
            self.assertEqual(status, 206)
            self.assertEqual(hdrs.get("content-range"), f"bytes 2-5/{len(payload)}")
            self.assertEqual(body, b"2345")
            last_get = [r for r in srv.requests if r[0] == "GET"][-1]
            self.assertIn("range", last_get[3].get("Authorization", ""))
            expected_host = "s3.a.test" if path_style else "my-bucket.s3.b.test"
            self.assertEqual(last_get[1], expected_host)

            anon_status = ep.anonymous_get("public/present.txt")
            self.assertEqual(anon_status, 403)
            anon_req = srv.requests[-1]
            self.assertNotIn("Authorization", anon_req[3])
            self.assertNotIn("x-amz-date", {k.lower() for k in anon_req[3]})


class MediaTransferSlice3QualificationTest(unittest.TestCase):

    def test_qualify_bucket_passes_across_distinct_endpoints_and_addressing_modes_and_cleans_up(self):
        configs = [
            ("bucket-a", "auto", "https://acct.eu.r2.cloudflarestorage.com", True, "path-style"),
            ("bucket-b", "eu-west-1", "https://s3.eu-west-1.custom.test", False, "virtual-hosted"),
        ]
        for bucket, region, endpoint, path_style, expected_mode in configs:
            srv = _InMemoryS3Server(bucket, path_style=path_style)
            ep = srv.bind(S3Endpoint(
                bucket=bucket,
                region=region,
                endpoint=endpoint,
                path_style=path_style,
                access_key="ak",
                secret_key="sk",
            ))
            report = qualify_bucket(ep, require_https=True)
            self.assertTrue(report["ok"], report)
            self.assertEqual(report["addressing_mode"], expected_mode)
            self.assertEqual(report["region"], region)
            self.assertEqual(report["checks"]["missing_object_404"], True)
            self.assertEqual(report["checks"]["anonymous_bucket_list_denied"], True)
            for prefix in ("public", "private"):
                p = report["checks"][prefix]
                self.assertTrue(all([p["put"], p["get"], p["range_206"], p["list"], p["anonymous_denied"], p["delete"]]), p)
            # Cleanup guarantees zero probe objects remain in bucket.
            self.assertEqual(srv.objects, {})

    def test_qualify_bucket_fails_on_http_when_https_required_public_bucket_missing_403_or_broken_range(self):
        # 1. HTTP refused when require_https=True
        srv1 = _InMemoryS3Server("b", path_style=True)
        ep_http = srv1.bind(S3Endpoint("b", "us-east-1", "http://s3.test:9000", True, "ak", "sk"))
        rep_http = qualify_bucket(ep_http, require_https=True)
        self.assertFalse(rep_http["ok"])
        self.assertTrue(any("https" in e.lower() for e in rep_http["errors"]), rep_http)

        # 2. Publicly readable public/ prefix must fail and still clean up probe objects
        srv2 = _InMemoryS3Server("b", path_style=True, public_anonymous=True)
        ep_pub = srv2.bind(S3Endpoint("b", "us-east-1", "https://s3.test", True, "ak", "sk"))
        rep_pub = qualify_bucket(ep_pub, require_https=True)
        self.assertFalse(rep_pub["ok"])
        self.assertFalse(rep_pub["checks"]["public"]["anonymous_denied"])
        self.assertTrue(rep_pub["checks"]["private"]["anonymous_denied"])
        self.assertEqual(srv2.objects, {})

        # 3. Missing key returning 403 (missing s3:ListBucket) must fail
        srv3 = _InMemoryS3Server("b", path_style=True, deny_missing_with_403=True)
        ep_403 = srv3.bind(S3Endpoint("b", "us-east-1", "https://s3.test", True, "ak", "sk"))
        rep_403 = qualify_bucket(ep_403, require_https=True)
        self.assertFalse(rep_403["ok"])
        self.assertFalse(rep_403["checks"]["missing_object_404"])

        # 4. Broken range support (returns 200 instead of 206) must fail and clean up
        srv4 = _InMemoryS3Server("b", path_style=True, ignore_range=True)
        ep_rng = srv4.bind(S3Endpoint("b", "us-east-1", "https://s3.test", True, "ak", "sk"))
        rep_rng = qualify_bucket(ep_rng, require_https=True)
        self.assertFalse(rep_rng["ok"])
        self.assertFalse(rep_rng["checks"]["public"]["range_206"])
        self.assertEqual(srv4.objects, {})

    def test_backup_provider_independence_and_eu_jurisdiction_validation(self):
        # Reject Hetzner as backup (same provider as VPS)
        bad_hetzner = check_backup_independence(
            media_endpoint="https://acct.eu.r2.cloudflarestorage.com",
            backup_endpoint="https://fsn1.your-objectstorage.com",
            backup_region="fsn1",
        )
        self.assertFalse(bad_hetzner["ok"])

        # Reject non-EU backup
        bad_non_eu = check_backup_independence(
            media_endpoint="https://fsn1.your-objectstorage.com",
            backup_endpoint="https://acct.r2.cloudflarestorage.com",
            backup_region="auto",
        )
        self.assertFalse(bad_non_eu["ok"])
        bad_us_aws = check_backup_independence(
            media_endpoint="https://acct.eu.r2.cloudflarestorage.com",
            backup_endpoint="",
            backup_region="us-east-1",
            backup_provider="AWS",
        )
        self.assertFalse(bad_us_aws["ok"])

        # Reject non-EU European regions (eu-west-2 London/UK, eu-central-2 Zurich/CH) across ALL endpoints
        for non_eu_ep, non_eu_reg in [
            ("", "eu-west-2"),
            ("", "eu-central-2"),
            ("https://s3.eu-west-2.custom.test", "eu-west-2"),
            ("https://s3.eu-central-2.custom.test", "eu-central-2"),
            ("https://s3.eu-west-2.custom.test", "eu-west-1"),
            ("https://s3.eu.custom.test", "eu-west-2"),
            ("https://s3.eu.custom.test", "eu-central-2"),
            ("https://us-east.example.com/.eu./", "custom"),
        ]:
            bad_non_eu_europe = check_backup_independence(
                media_endpoint="https://acct.eu.r2.cloudflarestorage.com",
                backup_endpoint=non_eu_ep,
                backup_region=non_eu_reg,
            )
            self.assertFalse(
                bad_non_eu_europe["ok"],
                f"Expected non-EU rejection for endpoint={non_eu_ep!r}, region={non_eu_reg!r}: {bad_non_eu_europe}",
            )

        # Reject same provider as active media (R2 + R2, AWS + AWS, same custom endpoint)
        same_r2 = check_backup_independence(
            media_endpoint="https://media-acct.eu.r2.cloudflarestorage.com",
            backup_endpoint="https://backup-acct.eu.r2.cloudflarestorage.com",
            backup_region="auto",
        )
        self.assertFalse(same_r2["ok"])
        same_custom = check_backup_independence(
            media_endpoint="http://127.0.0.1:59006/",
            backup_endpoint="http://127.0.0.1:59006",
            backup_region="eu-west-1",
        )
        self.assertFalse(same_custom["ok"])

        # Accept independent EU backup destinations
        ok_r2_to_scw = check_backup_independence(
            media_endpoint="https://acct.eu.r2.cloudflarestorage.com",
            backup_endpoint="https://s3.fr-par.scw.cloud",
            backup_region="fr-par",
        )
        self.assertTrue(ok_r2_to_scw["ok"], ok_r2_to_scw)

        ok_r2_to_aws_eu = check_backup_independence(
            media_endpoint="https://acct.eu.r2.cloudflarestorage.com",
            backup_endpoint="",
            backup_region="eu-west-1",
            backup_provider="AWS",
        )
        self.assertTrue(ok_r2_to_aws_eu["ok"], ok_r2_to_aws_eu)

        ok_hetzner_to_r2 = check_backup_independence(
            media_endpoint="https://fsn1.your-objectstorage.com",
            backup_endpoint="https://acct.eu.r2.cloudflarestorage.com",
            backup_region="auto",
        )
        self.assertTrue(ok_hetzner_to_r2["ok"], ok_hetzner_to_r2)

        # Integrated inside qualify_bucket when backup_config is passed
        srv = _InMemoryS3Server("media-b", path_style=True)
        ep = srv.bind(S3Endpoint("media-b", "auto", "https://acct.eu.r2.cloudflarestorage.com", True, "ak", "sk"))
        rep_bad = qualify_bucket(ep, backup_config={"endpoint": "https://other.eu.r2.cloudflarestorage.com", "region": "auto"})
        self.assertFalse(rep_bad["ok"])
        rep_ok = qualify_bucket(ep, backup_config={"endpoint": "https://s3.fr-par.scw.cloud", "region": "fr-par"})
        self.assertTrue(rep_ok["ok"], rep_ok)

    def test_qualify_bucket_cli_uses_aws_regional_backup_without_r2_fallback_during_overlap(self):
        srv = _InMemoryS3Server("media-r2", path_style=True)

        def _fake_connect(s3_self, host, port):
            return _MemoryS3Connection(srv, host, port)

        base_argv = [
            "qualify-bucket",
            "--s3-bucket", "media-r2",
            "--s3-region", "auto",
            "--s3-endpoint", "https://media-acct.eu.r2.cloudflarestorage.com",
            "--s3-path-style",
            "--s3-access-key", "ak",
            "--s3-secret-key", "sk",
            "--require-https",
            "--check-backup-independence",
        ]

        # 1. AWS regional backup (empty BACKUP_S3_ENDPOINT + region=eu-west-1) while R2_ENDPOINT is kept for 30-day overlap.
        stdout_buf = io.StringIO()
        with (
            mock.patch.object(S3Endpoint, "_connect", _fake_connect),
            mock.patch.dict(os.environ, {}, clear=True),
            mock.patch("sys.stdout", stdout_buf),
        ):
            rc = media_transfer_main([
                *base_argv,
                "--backup-s3-endpoint", "",
                "--backup-s3-region", "eu-west-1",
                "--r2-endpoint", "https://old-backup.eu.r2.cloudflarestorage.com",
            ])
        rep = json.loads(stdout_buf.getvalue())
        self.assertEqual(rc, 0, rep)
        self.assertTrue(rep["ok"], rep)
        self.assertEqual(rep["backup_independence"]["backup_provider_identity"], "aws")

        # 2. Same via environment variables (BACKUP_S3_BUCKET + BACKUP_S3_REGION=eu-west-1, empty BACKUP_S3_ENDPOINT, R2_ENDPOINT set).
        stdout_buf2 = io.StringIO()
        env = {
            "BACKUP_S3_BUCKET": "bkp-aws",
            "BACKUP_S3_ENDPOINT": "",
            "BACKUP_S3_REGION": "eu-west-1",
            "R2_ENDPOINT": "https://old-backup.eu.r2.cloudflarestorage.com",
        }
        with (
            mock.patch.object(S3Endpoint, "_connect", _fake_connect),
            mock.patch.dict(os.environ, env, clear=True),
            mock.patch("sys.stdout", stdout_buf2),
        ):
            rc2 = media_transfer_main(base_argv)
        rep2 = json.loads(stdout_buf2.getvalue())
        self.assertEqual(rc2, 0, rep2)
        self.assertTrue(rep2["ok"], rep2)
        self.assertEqual(rep2["backup_independence"]["backup_provider_identity"], "aws")

        # 3. When BACKUP_REMOTE explicitly selects r2crypt:, evaluate R2_ENDPOINT (and fail independence against R2 media).
        stdout_buf3 = io.StringIO()
        with (
            mock.patch.object(S3Endpoint, "_connect", _fake_connect),
            mock.patch.dict(os.environ, {**env, "BACKUP_REMOTE": "r2crypt:"}, clear=True),
            mock.patch("sys.stdout", stdout_buf3),
        ):
            rc3 = media_transfer_main(base_argv)
        rep3 = json.loads(stdout_buf3.getvalue())
        self.assertEqual(rc3, 7, rep3)
        self.assertFalse(rep3["ok"])
        self.assertEqual(rep3["backup_independence"]["backup_provider_identity"], "cloudflare-r2")


class MediaTransferSlice4PreReopenVerifyTest(unittest.TestCase):

    def test_verify_reads_checks_admin_read_range_paused_503_and_s3_anonymous_denial(self):
        pub_bytes = b"\x89PNG\r\n\x1a\nPUBLIC"
        priv_bytes = b"%PDF-1.4\nPRIVATE"

        class FullStubApi(ApiClient):
            def __init__(self, admin_blocked=False, unpaused_delete=False, missing_retry_after=False):
                super().__init__("http://api.test", admin_token="adm")
                self.admin_blocked = admin_blocked
                self.unpaused_delete = unpaused_delete
                self.missing_retry_after = missing_retry_after
                self.calls = []

            def _request(self, method, path, token="", json_body=None, extra_headers=None):
                self.calls.append((method, path, token, dict(extra_headers or {})))
                if path == "/uploads/public/logo.png":
                    if extra_headers and extra_headers.get("Range") == "bytes=0-3":
                        return 206, {"content-range": f"bytes 0-3/{len(pub_bytes)}"}, pub_bytes[:4]
                    return 200, {}, pub_bytes
                if path == "/api/v1/media/77":
                    if not token:
                        return 401, {}, b""
                    if token == "other":
                        return 403, {}, b""
                    if token == "owner":
                        return 200, {}, priv_bytes
                    if token == "adm":
                        return (403, {}, b"") if self.admin_blocked else (200, {}, priv_bytes)
                if method == "DELETE" and path == "/api/v1/media/2147483647" and token == "adm":
                    if self.unpaused_delete:
                        return 404, {}, b""
                    hdrs = {} if self.missing_retry_after else {"retry-after": "60"}
                    return 503, hdrs, b'{"status":503}'
                if path == "/api/v1/admin/media/maintenance" and token == "adm":
                    return 200, {}, b'{"writesPaused":true,"drained":true}'
                return 404, {}, b""

        s3_srv = _InMemoryS3Server("priv-bucket", path_style=True)
        s3_srv.objects["public/logo.png"] = (pub_bytes, "image/png")
        s3_srv.objects["private/doc.pdf"] = (priv_bytes, "application/pdf")
        s3_ep = s3_srv.bind(S3Endpoint("priv-bucket", "eu-west-1", "https://s3.test", True, "ak", "sk"))

        ok = FullStubApi().verify_reads(
            public_expected={"public/logo.png": sha256_bytes(pub_bytes)},
            private_expected={77: sha256_bytes(priv_bytes)},
            owner_token="owner",
            other_token="other",
            expect_writes_paused=True,
            verify_range=True,
            verify_paused_write_503=True,
            s3_anonymous_check=s3_ep,
            s3_anonymous_keys=["public/logo.png", "private/doc.pdf"],
        )
        self.assertTrue(ok["ok"], ok)

        # Admin private read refusal must fail
        fail_admin = FullStubApi(admin_blocked=True).verify_reads(
            public_expected={"public/logo.png": sha256_bytes(pub_bytes)},
            private_expected={77: sha256_bytes(priv_bytes)},
            owner_token="owner",
            other_token="other",
            expect_writes_paused=True,
            verify_range=True,
            verify_paused_write_503=True,
        )
        self.assertFalse(fail_admin["ok"])
        self.assertTrue(any("admin" in e.lower() for e in fail_admin["errors"]), fail_admin)

        # Unpaused mutation (404 instead of 503) or missing Retry-After must fail
        fail_unpaused = FullStubApi(unpaused_delete=True).verify_reads(
            public_expected={"public/logo.png": sha256_bytes(pub_bytes)},
            private_expected={77: sha256_bytes(priv_bytes)},
            owner_token="owner",
            other_token="other",
            expect_writes_paused=True,
            verify_paused_write_503=True,
        )
        self.assertFalse(fail_unpaused["ok"])
        self.assertTrue(any("503" in e for e in fail_unpaused["errors"]), fail_unpaused)

        fail_retry_hdr = FullStubApi(missing_retry_after=True).verify_reads(
            public_expected={"public/logo.png": sha256_bytes(pub_bytes)},
            private_expected={77: sha256_bytes(priv_bytes)},
            owner_token="owner",
            other_token="other",
            expect_writes_paused=True,
            verify_paused_write_503=True,
        )
        self.assertFalse(fail_retry_hdr["ok"])
        self.assertTrue(any("retry-after" in e.lower() for e in fail_retry_hdr["errors"]), fail_retry_hdr)

        # Publicly readable S3 bucket must fail pre-reopen check
        s3_pub_srv = _InMemoryS3Server("pub-bucket", path_style=True, public_anonymous=True)
        s3_pub_srv.objects["public/logo.png"] = (pub_bytes, "image/png")
        s3_pub_srv.objects["private/doc.pdf"] = (priv_bytes, "application/pdf")
        s3_pub_ep = s3_pub_srv.bind(S3Endpoint("pub-bucket", "eu-west-1", "https://s3.test", True, "ak", "sk"))
        fail_s3_anon = FullStubApi().verify_reads(
            public_expected={"public/logo.png": sha256_bytes(pub_bytes)},
            private_expected={77: sha256_bytes(priv_bytes)},
            owner_token="owner",
            other_token="other",
            expect_writes_paused=True,
            s3_anonymous_check=s3_pub_ep,
            s3_anonymous_keys=["public/logo.png", "private/doc.pdf"],
        )
        self.assertFalse(fail_s3_anon["ok"])
        self.assertTrue(any("anonymous s3" in e.lower() for e in fail_s3_anon["errors"]), fail_s3_anon)

    def test_cutover_aborts_and_invokes_on_abort_when_pre_reopen_verify_reads_fails(self):
        class FakeDb:
            def fetch_media_rows(self):
                return [MediaRow(1, "public/logo.png", "PUBLIC", "SCHOOL_LOGO", 4, "image/png")]

            def count_active_write_transactions(self):
                return 0

        class FakeApi:
            def __init__(self):
                self.paused = False

            def maintenance_status(self):
                return {"writesPaused": self.paused, "drained": self.paused}

            def pause(self, wait_seconds=0):
                self.paused = True
                return self.maintenance_status()

            def resume(self):
                self.paused = False
                return self.maintenance_status()

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            src = DiskEndpoint(root / "su", root / "sp")
            dst = DiskEndpoint(root / "du", root / "dp")
            src.write_bytes("public/logo.png", b"logo", "image/png")
            api = FakeApi()
            aborted = []
            res = run_cutover(
                db=FakeDb(),
                source=src,
                target=dst,
                api=api,
                on_activate=lambda: None,
                on_abort=lambda: aborted.append("rolled_back"),
                verify_reads_fn=lambda: {"ok": False, "errors": ["admin private read failed"]},
            )
            self.assertEqual(res["status"], "ABORTED")
            self.assertEqual(res["abort_reason"], "READ_VERIFICATION_FAILED")
            self.assertEqual(res["activation_state"], "post_switch")
            self.assertEqual(aborted, ["rolled_back"])
            self.assertFalse(api.paused)

    def test_verify_api_against_storage_handles_multi_owner_private_media(self):
        import base64

        def make_jwt(user_id: int, roles: list[str]) -> str:
            hdr = base64.urlsafe_b64encode(b'{"alg":"HS256","typ":"JWT"}').decode().rstrip("=")
            payload = base64.urlsafe_b64encode(
                json.dumps({"sub": str(user_id), "roles": roles}).encode()
            ).decode().rstrip("=")
            return f"{hdr}.{payload}.sig"

        u1_tok = make_jwt(101, ["ROLE_STUDENT"])
        u2_tok = make_jwt(202, ["ROLE_STUDENT"])
        u3_tok = make_jwt(303, ["ROLE_STUDENT"])
        adm_tok = make_jwt(999, ["ROLE_ADMIN"])

        pub_bytes = b"\x89PNG\r\n\x1a\nPUB"
        u1_bytes = b"%PDF-U1-PRIVATE"
        u2_bytes = b"%PDF-U2-PRIVATE"

        class MultiOwnerStubDb:
            def __init__(self, rows):
                self._rows = rows

            def fetch_media_rows(self):
                return list(self._rows)

        class MultiOwnerStubApi(ApiClient):
            def __init__(self, tok_users: dict[str, tuple[int, bool]], leak_to_user101: bool = False):
                super().__init__("http://api.test", admin_token=adm_tok)
                self.tok_users = tok_users
                self.leak_to_user101 = leak_to_user101
                self.files = {
                    10: (101, u1_bytes),
                    20: (202, u2_bytes),
                }

            def _request(self, method, path, token="", json_body=None, extra_headers=None, timeout=None):
                if path == "/uploads/public/logo.png":
                    if extra_headers and extra_headers.get("Range") == "bytes=0-3":
                        return 206, {"content-range": f"bytes 0-3/{len(pub_bytes)}"}, pub_bytes[:4]
                    return 200, {}, pub_bytes
                if path == "/api/v1/users/me":
                    if token not in self.tok_users:
                        return 401, {}, b""
                    uid, is_adm = self.tok_users[token]
                    return 200, {}, json.dumps({"id": uid, "roles": ["ROLE_ADMIN"] if is_adm else ["ROLE_STUDENT"]}).encode()
                if path == "/api/v1/admin/media/maintenance":
                    return 200, {}, b'{"writesPaused":true,"drained":true}'
                if method == "DELETE" and path == "/api/v1/media/2147483647":
                    return 503, {"retry-after": "60"}, b"{}"
                if path.startswith("/api/v1/media/"):
                    mid = int(path.rsplit("/", 1)[-1])
                    if mid not in self.files:
                        return 404, {}, b""
                    if not token or token not in self.tok_users:
                        return 401, {}, b""
                    uid, is_adm = self.tok_users[token]
                    owner_id, content = self.files[mid]
                    if is_adm or uid == owner_id or (self.leak_to_user101 and uid == 101 and mid == 20):
                        return 200, {}, content
                    return 403, {}, b""
                return 404, {}, b""

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            ep = DiskEndpoint(root / "u", root / "p")
            ep.write_bytes("public/logo.png", pub_bytes, "image/png")
            ep.write_bytes("private/u1.pdf", u1_bytes, "application/pdf")
            ep.write_bytes("private/u2.pdf", u2_bytes, "application/pdf")

            rows = [
                MediaRow(1, "public/logo.png", "PUBLIC", "SCHOOL_LOGO", len(pub_bytes), "image/png"),
                MediaRow(10, "private/u1.pdf", "PRIVATE", "DIPLOMA", len(u1_bytes), "application/pdf", owner_id=101),
                MediaRow(20, "private/u2.pdf", "PRIVATE", "DIPLOMA", len(u2_bytes), "application/pdf", owner_id=202),
            ]
            db = MultiOwnerStubDb(rows)
            tok_map = {
                u1_tok: (101, False),
                u2_tok: (202, False),
                u3_tok: (303, False),
                adm_tok: (999, True),
                "opaque-owner": (101, False),
                "opaque-other": (303, False),
            }

            # 1. JWT owner_token=u1 (owns 10, does NOT own 20), other_token=u3 (owns neither).
            res_u3 = _verify_api_against_storage(
                db, ep, MultiOwnerStubApi(tok_map), owner_token=u1_tok, other_token=u3_tok, expect_writes_paused=True
            )
            self.assertTrue(res_u3["ok"], res_u3)

            # 2. JWT owner_token=u1, other_token=u2 (owns 20, does NOT own 10).
            res_u2 = _verify_api_against_storage(
                db, ep, MultiOwnerStubApi(tok_map), owner_token=u1_tok, other_token=u2_tok, expect_writes_paused=True
            )
            self.assertTrue(res_u2["ok"], res_u2)

            # 3. Opaque tokens resolved via /api/v1/users/me.
            res_opaque = _verify_api_against_storage(
                db, ep, MultiOwnerStubApi(tok_map), owner_token="opaque-owner", other_token="opaque-other", expect_writes_paused=True
            )
            self.assertTrue(res_opaque["ok"], res_opaque)

            # 4. Security leak: user 101 can read user 202's private media (200 instead of 403) -> MUST fail!
            res_leak = _verify_api_against_storage(
                db, ep, MultiOwnerStubApi(tok_map, leak_to_user101=True), owner_token=u1_tok, other_token=u3_tok, expect_writes_paused=True
            )
            self.assertFalse(res_leak["ok"])
            self.assertTrue(any("403" in e and "/api/v1/media/20" in e for e in res_leak["errors"]), res_leak)

    def test_api_client_pause_scales_http_timeout_beyond_wait_seconds(self):
        captured_timeouts = []

        class _FakeUrlopenResp:
            status = 200
            headers = {"Content-Type": "application/json"}

            def read(self):
                return b'{"writesPaused":true,"drained":true}'

            def __enter__(self):
                return self

            def __exit__(self, *args):
                return False

        def _fake_urlopen(req, timeout=None):
            captured_timeouts.append(timeout)
            return _FakeUrlopenResp()

        client = ApiClient("http://api.test", admin_token="adm", timeout=15)
        with mock.patch("urllib.request.urlopen", side_effect=_fake_urlopen):
            client.pause(wait_seconds=30)
            client.pause(wait_seconds=2)

        self.assertEqual(captured_timeouts, [40, 15])

    def test_cutover_invokes_on_abort_and_reopens_writes_when_on_activate_raises(self):
        class FakeDb:
            def fetch_media_rows(self):
                return [MediaRow(1, "public/logo.png", "PUBLIC", "SCHOOL_LOGO", 4, "image/png")]

            def count_active_write_transactions(self):
                return 0

        class FakeApi:
            def __init__(self):
                self.paused = False
                self.resume_calls = 0

            def maintenance_status(self):
                return {"writesPaused": self.paused, "drained": self.paused}

            def pause(self, wait_seconds=0):
                self.paused = True
                return self.maintenance_status()

            def resume(self):
                self.paused = False
                self.resume_calls += 1
                return self.maintenance_status()

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            src = DiskEndpoint(root / "su", root / "sp")
            dst = DiskEndpoint(root / "du", root / "dp")
            src.write_bytes("public/logo.png", b"logo", "image/png")

            api = FakeApi()
            aborted = []

            def failing_activate():
                raise RuntimeError("redeploy failed mid-switch")

            res = run_cutover(
                db=FakeDb(),
                source=src,
                target=dst,
                api=api,
                on_activate=failing_activate,
                on_abort=lambda: aborted.append("rolled_back"),
            )
            self.assertEqual(res["status"], "ABORTED")
            self.assertEqual(aborted, ["rolled_back"])
            self.assertFalse(api.paused)
            self.assertEqual(api.resume_calls, 1)
            self.assertEqual(res["activation_state"], "mid_switch")

            # Pre-switch abort must NOT invoke on_abort and reports pre_switch.
            pre_aborted = []
            res_pre = run_cutover(
                db=FakeDb(),
                source=src,
                target=dst,
                api=api,
                initial_sync=False,
                after_final_sync_hook=lambda: dst.write_bytes("public/logo.png", b"corrupt", "image/png"),
                on_activate=lambda: None,
                on_abort=lambda: pre_aborted.append("should_not_run"),
            )
            self.assertEqual(res_pre["status"], "ABORTED")
            self.assertEqual(pre_aborted, [])
            self.assertEqual(res_pre["activation_state"], "pre_switch")


if __name__ == "__main__":
    unittest.main()
