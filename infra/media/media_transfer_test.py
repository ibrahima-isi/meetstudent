import hashlib
import os
import tempfile
import unittest
from pathlib import Path

from media_transfer import (
    ApiClient,
    DiskEndpoint,
    MediaRow,
    S3Endpoint,
    build_inventory,
    is_valid_key,
    parse_list_objects_v2_xml,
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


if __name__ == "__main__":
    unittest.main()
