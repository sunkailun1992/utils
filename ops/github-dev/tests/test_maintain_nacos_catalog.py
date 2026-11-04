"""用本机 HTTP 假服务覆盖 Nacos 维护协议和失败边界，不连接真实配置中心。"""
import contextlib
from copy import deepcopy
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import io
import json
import os
from pathlib import Path
import secrets
import stat
import sys
import tempfile
import threading
import unittest
from unittest.mock import patch
from urllib.parse import parse_qs, urlsplit

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import maintain_nacos_catalog as maintenance


def config(name, content, tags=None):
    return {"dataId": name, "namespaceId": "github-dev", "groupName": "DEFAULT_GROUP",
            "content": content, "md5": hashlib.md5(content.encode()).hexdigest(),
            "desc": "旧说明", "configTags": tags, "modifyTime": 1}


def private_file(path, value):
    path.write_text(value)
    path.chmod(0o600)


class CatalogMaintenanceTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.candidate = self.root / "candidate"
        self.previous = self.root / "previous"
        self.backup = self.root / "backups"
        self.candidate.mkdir(mode=0o700)
        self.previous.mkdir(mode=0o700)
        self.descriptions = {"active.yaml": "新的服务说明", "secondary.yaml": "次要配置说明"}
        self.replacements = {"old.yaml": "active.yaml", "empty.yaml": None}
        self.configs = {"active.yaml": config("active.yaml", "service:\n  active: true\n", "team,stable"),
                        "secondary.yaml": config("secondary.yaml", "value: 2\n"),
                        "old.yaml": config("old.yaml", "legacy: true\r\n", "old"),
                        "empty.yaml": config("empty.yaml", "{}\n"),
                        "dubbo-runtime": config("dubbo-runtime", "unmanaged")}
        for name in self.descriptions:
            private_file(self.candidate / name, self.configs[name]["content"])
        for name in self.replacements:
            private_file(self.previous / name, self.configs[name]["content"])
        self.calls, self.get_counts = [], {}
        self.token = secrets.token_urlsafe(24)
        self.before_get = None
        self.after_put = None
        self.ignore_delete = False
        self.missing_as_404 = False
        self.redirect = False
        fixture = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_args):
                pass

            def reply(self, value, status=200):
                payload = json.dumps(value).encode()
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)

            def do_POST(self):
                body = parse_qs(self.rfile.read(int(self.headers.get("Content-Length", 0))).decode())
                fixture.calls.append(("POST", self.path, body, dict(self.headers)))
                if fixture.redirect:
                    self.send_response(302)
                    self.send_header("Location", "/must-not-follow")
                    self.end_headers()
                    return
                self.reply({"accessToken": fixture.token})

            def do_GET(self):
                self.handle_config("GET")

            def do_PUT(self):
                self.handle_config("PUT")

            def do_DELETE(self):
                self.handle_config("DELETE")

            def handle_config(self, method):
                parsed = urlsplit(self.path)
                parameters = parse_qs(parsed.query, keep_blank_values=True)
                if method == "PUT":
                    parameters = parse_qs(self.rfile.read(int(self.headers.get("Content-Length", 0))).decode(),
                                          keep_blank_values=True)
                fixture.calls.append((method, parsed.path, parameters, dict(self.headers)))
                if self.headers.get("Authorization") != "Bearer " + fixture.token:
                    self.reply({"code": 401, "message": "private response must not be printed"}, 401)
                    return
                if parameters.get("namespaceId") != ["github-dev"] or parameters.get("groupName") != ["DEFAULT_GROUP"]:
                    self.reply({"code": 403}, 403)
                    return
                name = parameters["dataId"][0]
                if method == "GET":
                    fixture.get_counts[name] = fixture.get_counts.get(name, 0) + 1
                    if fixture.before_get:
                        fixture.before_get(name, fixture.get_counts[name])
                    if name not in fixture.configs and fixture.missing_as_404:
                        self.reply({"code": 404}, 404)
                    else:
                        self.reply({"code": 0, "data": fixture.configs.get(name)})
                elif method == "PUT":
                    fixture.configs[name]["desc"] = parameters["desc"][0]
                    fixture.configs[name]["configTags"] = parameters.get("configTags", [None])[0]
                    fixture.configs[name]["modifyTime"] += 1
                    if fixture.after_put:
                        fixture.after_put(name)
                    self.reply({"code": 0, "data": True})
                elif method == "DELETE":
                    if not fixture.ignore_delete:
                        fixture.configs.pop(name, None)
                    self.reply({"code": 0, "data": True})

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.environment = {"GITHUB_NACOS_DEV_SERVER_ADDR": f"http://127.0.0.1:{self.server.server_port}",
                            "GITHUB_NACOS_USERNAME": "unit-user", "GITHUB_NACOS_PASSWORD": "unit-password"}

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()
        self.temporary.cleanup()

    def run_maintenance(self, **options):
        client = maintenance.NacosClient(self.environment)
        with contextlib.redirect_stdout(io.StringIO()) as output:
            result = maintenance.maintain(client, self.descriptions, self.replacements,
                                          self.candidate, self.backup, previous=self.previous, **options)
        self.assertNotIn(self.token, output.getvalue())
        self.assertNotIn("legacy:", output.getvalue())
        self.assertNotIn("新的服务说明", output.getvalue())
        return result

    def writes(self):
        return [call for call in self.calls if call[0] in ("PUT", "DELETE")]

    def test_dry_run_backs_up_metadata_and_content_without_mutating(self):
        original = deepcopy(self.configs)
        result = self.run_maintenance(retire=True)
        self.assertEqual(result["mode"], "dry-run")
        self.assertEqual(result["descriptionsPlanned"], 2)
        self.assertEqual(result["retirePlanned"], 2)
        self.assertEqual(self.writes(), [])
        self.assertEqual(self.configs, original)
        snapshot = next(self.backup.iterdir())
        self.assertEqual(stat.S_IMODE(snapshot.stat().st_mode), 0o700)
        self.assertEqual(snapshot.stat().st_uid, os.getuid())
        self.assertEqual(len(list(snapshot.iterdir())), 4)
        for file in snapshot.iterdir():
            self.assertEqual(stat.S_IMODE(file.stat().st_mode), 0o600)
            self.assertEqual(json.loads(file.read_text()), original[file.name.removesuffix(".json")])

    def test_apply_updates_only_description_and_preserves_null_or_nonempty_tags(self):
        original = deepcopy(self.configs)
        result = self.run_maintenance(apply=True)
        self.assertEqual(result["descriptionsChanged"], 2)
        self.assertEqual(len(self.writes()), 2)
        for method, path, params, headers in self.writes():
            self.assertEqual(method, "PUT")
            self.assertEqual(path, "/nacos/v3/admin/cs/config/metadata")
            self.assertNotIn("content", params)
            self.assertNotIn("accessToken", params)
            self.assertEqual(headers["Authorization"], "Bearer " + self.token)
        for name in self.descriptions:
            for key in ("content", "md5", "configTags"):
                self.assertEqual(self.configs[name][key], original[name][key])
            self.assertEqual(self.configs[name]["desc"], self.descriptions[name])
        self.assertIn("old.yaml", self.configs)
        again = self.run_maintenance(apply=True)
        self.assertEqual(again["descriptionsChanged"], 0)

    def test_retire_requires_both_flags_and_checks_delete_readback(self):
        self.missing_as_404 = True
        result = self.run_maintenance(apply=True, retire=True)
        self.assertEqual(result["deleted"], 2)
        self.assertIn("dubbo-runtime", self.configs)
        self.assertNotIn("old.yaml", self.configs)
        self.assertNotIn("empty.yaml", self.configs)
        self.assertEqual(sum(call[0] == "DELETE" for call in self.writes()), 2)
        again = self.run_maintenance(apply=True, retire=True)
        self.assertEqual(again["deleted"], 0)
        self.assertEqual(again["alreadyAbsent"], 2)

    def test_preflight_content_drift_prevents_all_writes(self):
        self.configs["secondary.yaml"]["content"] = "changed: true\n"
        with self.assertRaisesRegex(maintenance.MaintenanceError, "候选不一致"):
            self.run_maintenance(apply=True, retire=True)
        self.assertEqual(self.writes(), [])

    def test_preflight_old_snapshot_drift_prevents_metadata_and_delete(self):
        self.configs["old.yaml"]["content"] = "changed: true\n"
        with self.assertRaisesRegex(maintenance.MaintenanceError, "旧配置已漂移"):
            self.run_maintenance(apply=True, retire=True)
        self.assertEqual(self.writes(), [])

    def test_second_read_catches_concurrent_description_or_tag_edits(self):
        def change(name, count):
            if name == "active.yaml" and count == 2:
                self.configs[name]["configTags"] = "concurrent"
        self.before_get = change
        with self.assertRaisesRegex(maintenance.MaintenanceError, "并发漂移"):
            self.run_maintenance(apply=True)
        self.assertEqual(self.writes(), [])

    def test_metadata_readback_detects_unexpected_content_or_md5_change(self):
        def corrupt(name):
            self.configs[name]["md5"] = "f" * 32
        self.after_put = corrupt
        with self.assertRaisesRegex(maintenance.MaintenanceError, "读回不一致"):
            self.run_maintenance(apply=True)
        self.assertEqual(len(self.writes()), 1)

    def test_replacement_drift_prevents_retirement(self):
        for name, description in self.descriptions.items():
            self.configs[name]["desc"] = description
        def change(name, count):
            if name == "active.yaml" and count == 2:
                self.configs[name]["content"] = "changed: true\n"
        self.before_get = change
        with self.assertRaisesRegex(maintenance.MaintenanceError, "替代配置已漂移"):
            self.run_maintenance(apply=True, retire=True)
        self.assertEqual(self.writes(), [])

    def test_delete_success_boolean_is_not_enough_without_absent_readback(self):
        self.ignore_delete = True
        with self.assertRaisesRegex(maintenance.MaintenanceError, "仍能读到配置"):
            self.run_maintenance(apply=True, retire=True)
        self.assertIn("old.yaml", self.configs)

    def test_login_uses_v3_form_and_does_not_follow_redirects(self):
        self.redirect = True
        with self.assertRaises(maintenance.MaintenanceError):
            maintenance.NacosClient(self.environment)
        self.assertEqual(len(self.calls), 1)
        self.assertEqual(self.calls[0][1], "/nacos/v3/auth/user/login")
        self.assertEqual(self.calls[0][2]["username"], ["unit-user"])
        self.assertNotIn("unit-password", self.calls[0][1])

    def test_scope_mismatch_fails_before_any_login(self):
        with self.assertRaises(maintenance.MaintenanceError):
            maintenance.NacosClient({**self.environment, "GITHUB_NACOS_DEV_NAMESPACE": "company"})
        with self.assertRaises(maintenance.MaintenanceError):
            maintenance.NacosClient({**self.environment, "GITHUB_NACOS_GROUP": "OTHER"})
        self.assertEqual(self.calls, [])

    def test_null_missing_response_and_empty_tags_are_preserved(self):
        self.configs["secondary.yaml"]["configTags"] = ""
        result = self.run_maintenance(apply=True)
        self.assertEqual(result["descriptionsChanged"], 2)
        self.assertEqual(self.configs["secondary.yaml"]["configTags"], "")
        self.assertIsNone(maintenance.NacosClient(self.environment).get("missing.yaml"))

    def test_concurrent_old_content_edit_stops_delete(self):
        def change(name, count):
            if name == "old.yaml" and count == 2:
                self.configs[name]["content"] = "concurrent: true\n"
        self.before_get = change
        with self.assertRaisesRegex(maintenance.MaintenanceError, "删除前旧配置已漂移"):
            self.run_maintenance(apply=True, retire=True)
        self.assertFalse(any(call[0] == "DELETE" for call in self.writes()))

    def test_cli_defaults_to_dry_run_with_private_environment(self):
        manifests = self.root / "manifests"
        manifests.mkdir()
        (manifests / "catalog.json").write_text(json.dumps({
            "namespace": "github-dev", "group": "DEFAULT_GROUP",
            "entries": [{"dataId": name, "description": description}
                        for name, description in self.descriptions.items()]}))
        (manifests / "retired-dataids.json").write_text(json.dumps({
            "namespace": "github-dev", "group": "DEFAULT_GROUP",
            "retired": [{"dataId": name, "replacement": replacement}
                        for name, replacement in self.replacements.items()]}))
        environment = self.root / "dev.env"
        private_file(environment, "".join(f"{key}={value}\n" for key, value in self.environment.items()))
        with patch.object(maintenance, "CATALOG_ROOT", manifests), contextlib.redirect_stdout(io.StringIO()) as output:
            result = maintenance.main(["--env-file", str(environment), "--directory", str(self.candidate),
                                       "--backup-directory", str(self.backup)])
        self.assertEqual(result, 0)
        self.assertIn('"mode": "dry-run"', output.getvalue())
        self.assertEqual(self.writes(), [])
        for secret in (str(self.root), self.token, "unit-password", "旧说明"):
            self.assertNotIn(secret, output.getvalue())

    def test_remote_identity_mismatch_stops_before_mutation(self):
        self.configs["active.yaml"]["namespaceId"] = "company"
        with self.assertRaisesRegex(maintenance.MaintenanceError, "身份不匹配"):
            self.run_maintenance(apply=True)
        self.assertEqual(self.writes(), [])

    def test_extra_files_and_missing_previous_are_rejected(self):
        private_file(self.candidate / "old.yaml", "unexpected")
        with self.assertRaisesRegex(maintenance.MaintenanceError, "额外文件"):
            self.run_maintenance(apply=True)
        (self.candidate / "old.yaml").unlink()
        client = maintenance.NacosClient(self.environment)
        with self.assertRaisesRegex(maintenance.MaintenanceError, "--previous"):
            maintenance.maintain(client, self.descriptions, self.replacements, self.candidate,
                                 self.backup, apply=True, retire=True)
        self.assertEqual(self.writes(), [])

    def test_git_paths_symlinks_and_world_readable_private_files_are_rejected(self):
        repository = self.root / "repository"
        repository.mkdir()
        (repository / ".git").write_text("gitdir: elsewhere")
        with self.assertRaisesRegex(maintenance.MaintenanceError, "Git"):
            maintenance.outside_git(repository / "private")
        link = self.root / "link"
        link.symlink_to(repository, target_is_directory=True)
        with self.assertRaisesRegex(maintenance.MaintenanceError, "Git"):
            maintenance.outside_git(link / "private")
        file = self.candidate / "active.yaml"
        file.chmod(0o644)
        with self.assertRaisesRegex(maintenance.MaintenanceError, "0600"):
            self.run_maintenance(apply=True)
        file.unlink()
        file.symlink_to(self.previous / "old.yaml")
        with self.assertRaises(OSError):
            self.run_maintenance(apply=True)
        self.assertEqual(self.writes(), [])

    def test_manifests_reject_path_traversal_and_unknown_replacements(self):
        catalog = {"namespace": "github-dev", "group": "DEFAULT_GROUP",
                   "entries": [{"dataId": "active.yaml", "description": "说明"}]}
        retired = {"namespace": "github-dev", "group": "DEFAULT_GROUP",
                   "retired": [{"dataId": "old.yaml", "replacement": "unknown.yaml"}]}
        with self.assertRaises(maintenance.MaintenanceError):
            maintenance.validate_manifests(catalog, retired)
        retired["retired"] = []
        catalog["entries"][0]["dataId"] = "../private.yaml"
        with self.assertRaises(maintenance.MaintenanceError):
            maintenance.validate_manifests(catalog, retired)

    def test_http_error_does_not_echo_token_url_or_response_body(self):
        client = maintenance.NacosClient(self.environment)
        client.token = "wrong-unit-token"
        with self.assertRaises(maintenance.MaintenanceError) as caught:
            client.get("active.yaml")
        message = str(caught.exception)
        self.assertIn("401", message)
        for secret in (client.token, self.token, "private response", "127.0.0.1", "unit-password"):
            self.assertNotIn(secret, message)


if __name__ == "__main__":
    unittest.main()
