#!/usr/bin/env python3
"""维护个人 Nacos 描述；默认预演，--apply 才写，--retire 另行选择旧项清理。

接口依据：https://nacos.io/en/docs/v3.2/manual/admin/admin-api/（3.1、3.3、3.25）
登录依据：https://nacos.io/en/docs/v3.2/manual/user/auth/
"""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import stat
import tempfile
import urllib.error
import urllib.parse
import urllib.request

from start_service import private_environment

NAMESPACE = "github-dev"
GROUP = "DEFAULT_GROUP"
CATALOG_ROOT = Path(__file__).resolve().parents[1] / "nacos"
MAX_BYTES = 16 * 1024 * 1024
DATA_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,127}\.(?:yaml|yml)")


class MaintenanceError(Exception):
    """仅承载固定诊断和已验证 dataId，不包含远端正文或连接凭据。"""


def require(condition, message):
    if not condition:
        raise MaintenanceError(message)


def outside_git(path):
    path = Path(path).expanduser().absolute()
    for candidate in (path, path.resolve()):
        require(not any((parent / ".git").exists() for parent in [candidate, *candidate.parents]),
                "私有路径必须位于所有 Git 仓库之外")
    return path


def private_directory(path):
    path = outside_git(path)
    require(not path.is_symlink() and path.is_dir(), "私有配置目录必须是普通目录")
    require(path.stat().st_uid == os.getuid() and not path.stat().st_mode & 0o077,
            "私有配置目录必须归当前用户所有，权限为 0700")
    return path


def private_text(path):
    path = outside_git(path)
    descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(descriptor, "r", encoding="utf-8", newline="") as stream:
        info = os.fstat(stream.fileno())
        require(stat.S_ISREG(info.st_mode) and info.st_uid == os.getuid()
                and not info.st_mode & 0o077 and info.st_size <= MAX_BYTES,
                "私有配置必须是当前用户的 0600 普通文件且不超过 16 MiB")
        return stream.read()


def valid_dataid(value):
    require(isinstance(value, str) and DATA_ID.fullmatch(value), "清单中的 dataId 不合法")
    return value


def validate_manifests(catalog, retirement):
    require(isinstance(catalog, dict) and isinstance(retirement, dict), "配置清单格式不合法")
    for manifest in (catalog, retirement):
        require(manifest.get("namespace") == NAMESPACE and manifest.get("group") == GROUP,
                "仅允许 github-dev/DEFAULT_GROUP")
    entries, retired = catalog.get("entries"), retirement.get("retired")
    require(isinstance(entries, list) and entries and isinstance(retired, list), "配置清单缺少条目")
    descriptions, replacements = {}, {}
    for entry in entries:
        require(isinstance(entry, dict), "配置清单条目不合法")
        name = valid_dataid(entry.get("dataId"))
        description = entry.get("description")
        require(name not in descriptions and isinstance(description, str) and description.strip()
                and len(description) <= 4096 and not re.search(r"[\x00-\x1f\x7f]", description),
                "配置清单描述缺失、重复或不合法")
        descriptions[name] = description
    for entry in retired:
        require(isinstance(entry, dict), "清理清单条目不合法")
        name = valid_dataid(entry.get("dataId"))
        replacement = entry.get("replacement")
        require(name not in replacements and name not in descriptions, "清理清单重复或包含当前配置")
        require(replacement is None or replacement in descriptions, "清理项缺少有效替代配置")
        replacements[name] = replacement
    return descriptions, replacements


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, newurl):
        # 登录表单和认证头不转发到重定向目标。
        return None


class NacosClient:
    def __init__(self, environment, timeout=10):
        require(1 <= timeout <= 60, "HTTP 超时必须为 1–60 秒")
        for key in ("GITHUB_NACOS_DEV_NAMESPACE", "GITHUB_NACOS_NAMESPACE"):
            require(environment.get(key, NAMESPACE) == NAMESPACE, "仅允许 github-dev namespace")
        require(environment.get("GITHUB_NACOS_GROUP", GROUP) == GROUP, "仅允许 DEFAULT_GROUP")
        address = environment.get("GITHUB_NACOS_DEV_SERVER_ADDR") or environment.get("GITHUB_NACOS_SERVER_ADDR", "")
        if address and "://" not in address:
            address = "http://" + address
        parsed = urllib.parse.urlsplit(address)
        require(parsed.scheme in ("http", "https") and parsed.hostname and not parsed.username
                and not parsed.password and not parsed.query and not parsed.fragment
                and parsed.path.rstrip("/") in ("", "/nacos") and "," not in parsed.netloc,
                "Nacos 地址必须是单个 HTTP(S) SDK 地址，不含凭据或额外路径")
        self.base = f"{parsed.scheme}://{parsed.netloc}/nacos"
        self.timeout = timeout
        self.token = None
        # 开发网直接连接，不向环境代理发送私有配置或登录凭据。
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
        username, password = environment.get("GITHUB_NACOS_USERNAME"), environment.get("GITHUB_NACOS_PASSWORD")
        require(bool(username) and bool(password), "私有环境缺少 Nacos 登录凭据")
        login = self._request("POST", "/v3/auth/user/login", form={"username": username, "password": password})
        require(isinstance(login, dict) and isinstance(login.get("accessToken"), str)
                and login["accessToken"] and not re.search(r"[\r\n]", login["accessToken"]), "Nacos 登录响应无效")
        self.token = login["accessToken"]

    def _request(self, method, path, query=None, form=None, missing_ok=False):
        url = self.base + path
        if query:
            url += "?" + urllib.parse.urlencode(query)
        headers = {"Accept": "application/json"}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        body = None
        if form is not None:
            headers["Content-Type"] = "application/x-www-form-urlencoded; charset=UTF-8"
            body = urllib.parse.urlencode(form).encode("utf-8")
        request = urllib.request.Request(url, data=body, headers=headers, method=method)
        try:
            with self.opener.open(request, timeout=self.timeout) as response:
                raw = response.read(MAX_BYTES + 1)
            require(len(raw) <= MAX_BYTES, "Nacos 响应超过限制")
            value = json.loads(raw)
            require(isinstance(value, dict), "Nacos 响应格式无效")
            return value
        except urllib.error.HTTPError as error:
            status_code = error.code
            error.close()
            if missing_ok and status_code == 404:
                return None
            raise MaintenanceError(f"Nacos HTTP 请求失败（状态 {status_code}）；响应原文不输出") from None
        except MaintenanceError:
            raise
        except Exception:
            # 包括截断响应等 HTTP 库异常，禁止异常 repr 带出 URL 或已读取的私有字节。
            raise MaintenanceError("Nacos 连接或响应解析失败；连接信息和响应原文不输出") from None

    @staticmethod
    def scope(name):
        return {"namespaceId": NAMESPACE, "groupName": GROUP, "dataId": valid_dataid(name)}

    @staticmethod
    def data(response):
        require(isinstance(response, dict) and type(response.get("code")) is int
                and response["code"] == 0 and "data" in response, "Nacos API 未返回成功结果")
        return response["data"]

    def get(self, name):
        result = self._request("GET", "/v3/admin/cs/config", query=self.scope(name), missing_ok=True)
        if result is None:
            return None
        value = self.data(result)
        if value is None:
            return None
        require(isinstance(value, dict) and value.get("namespaceId") == NAMESPACE
                and value.get("groupName") == GROUP and value.get("dataId") == name,
                name + ": 远端配置身份不匹配")
        require(isinstance(value.get("content"), str) and isinstance(value.get("md5"), str)
                and re.fullmatch(r"[a-fA-F0-9]{32}", value["md5"]), name + ": 远端内容或 MD5 无效")
        require(value.get("configTags") is None or isinstance(value["configTags"], str), name + ": 标签格式无效")
        require(value.get("desc") is None or isinstance(value["desc"], str), name + ": 描述格式无效")
        return value

    def describe(self, name, description, tags):
        form = {**self.scope(name), "desc": description}
        if tags is not None:
            form["configTags"] = tags
        require(self.data(self._request("PUT", "/v3/admin/cs/config/metadata", form=form)) is True,
                name + ": 描述更新未成功")

    def delete(self, name):
        require(self.data(self._request("DELETE", "/v3/admin/cs/config", query=self.scope(name))) is True,
                name + ": 删除未成功")


def save_snapshot(directory, name, value):
    file = directory / (valid_dataid(name) + ".json")
    descriptor = os.open(file, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
        stream.flush()
        os.fsync(stream.fileno())


def same_revision(before, after):
    # API 没有条件写；写前再核对已知版本，写后仍须读回，不声称原子 CAS。
    return after is not None and all(before.get(key) == after.get(key) for key in
                                    ("content", "md5", "desc", "configTags", "modifyTime"))


def maintain(client, descriptions, replacements, directory, backup_directory,
             previous=None, apply=False, retire=False):
    directory = private_directory(directory)
    require({file.name for file in directory.iterdir()} == set(descriptions),
            "候选目录必须只包含 catalog 的当前配置，不允许旧项或额外文件")
    candidate = {name: private_text(directory / name) for name in descriptions}
    require(not retire or previous is not None, "--retire 必须提供 --previous 旧内容快照")
    previous_content = {}
    if retire:
        previous = private_directory(previous)
        previous_content = {name: private_text(previous / name) for name in replacements}
    backup_directory = outside_git(backup_directory)
    require(not backup_directory.is_symlink(), "备份目录不能是符号链接")
    backup_directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    private_directory(backup_directory)
    backup = Path(tempfile.mkdtemp(prefix=datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ-"), dir=backup_directory))
    originals, retired_originals = {}, {}

    # 全批内容核对和备份先于任何更新，防止后面的错误造成可避免的部分执行。
    for name in descriptions:
        value = client.get(name)
        save_snapshot(backup, name, value)
        require(value is not None and value["content"] == candidate[name], name + ": 远端内容与候选不一致，停止")
        originals[name] = value
    if retire:
        for name, replacement in replacements.items():
            value = client.get(name)
            save_snapshot(backup, name, value)
            if value is not None:
                require(value["content"] == previous_content[name], name + ": 旧配置已漂移，停止")
                if replacement:
                    require(originals[replacement]["content"] == candidate[replacement], name + ": 替代配置未就绪")
            retired_originals[name] = value

    summary = {"mode": "apply" if apply else "dry-run", "checked": len(originals),
               "descriptionsChanged": 0, "descriptionsPlanned": 0, "deleted": 0,
               "retirePlanned": 0, "alreadyAbsent": 0}
    for name, description in descriptions.items():
        original = originals[name]
        if original.get("desc") == description:
            continue
        summary["descriptionsPlanned"] += 1
        if not apply:
            print("describe " + name)
            continue
        current = client.get(name)
        require(same_revision(original, current), name + ": 描述更新前检测到并发漂移，停止")
        client.describe(name, description, current.get("configTags"))
        actual = client.get(name)
        require(actual is not None and actual.get("desc") == description
                and actual["content"] == original["content"] and actual["md5"] == original["md5"]
                and actual.get("configTags") == original.get("configTags"), name + ": 描述更新读回不一致，停止")
        summary["descriptionsChanged"] += 1
        print("described " + name)

    if retire:
        for name, replacement in replacements.items():
            original = retired_originals[name]
            if original is None:
                summary["alreadyAbsent"] += 1
                continue
            summary["retirePlanned"] += 1
            if not apply:
                print("retire " + name)
                continue
            if replacement:
                actual = client.get(replacement)
                require(actual is not None and actual["content"] == candidate[replacement],
                        name + ": 删除前替代配置已漂移，停止")
            current = client.get(name)
            require(same_revision(original, current) and current["content"] == previous_content[name],
                    name + ": 删除前旧配置已漂移，停止")
            client.delete(name)
            require(client.get(name) is None, name + ": 删除后仍能读到配置，停止")
            summary["deleted"] += 1
            print("retired " + name)
    return summary


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, epilog="删除不是回收站操作；恢复旧配置需要使用 Git 外快照重新发布。接口不提供原子 CAS，维护期间避免其他写入。")
    parser.add_argument("--env-file", type=Path, default=Path.home() / ".config/kellen-github/dev.env")
    parser.add_argument("--directory", type=Path, required=True, help="已发布并读回的 27 项私有候选目录")
    parser.add_argument("--backup-directory", type=Path, required=True, help="Git 外 0700 备份根目录，每次新建独立快照子目录")
    parser.add_argument("--previous", type=Path, help="旧 dataId 原始内容快照目录，文件名与 dataId 相同")
    parser.add_argument("--apply", action="store_true", help="实际更新描述；未指定时只预演并备份")
    parser.add_argument("--retire", action="store_true", help="同时处理明确列出的旧项；必须配 --previous，配 --apply 才删除")
    parser.add_argument("--timeout", type=float, default=10, help="每次 HTTP 请求超时秒数，范围 1–60")
    args = parser.parse_args(argv)
    try:
        catalog = json.loads((CATALOG_ROOT / "catalog.json").read_text())
        retirement = json.loads((CATALOG_ROOT / "retired-dataids.json").read_text())
        descriptions, replacements = validate_manifests(catalog, retirement)
        private_text(args.env_file)
        environment = private_environment(args.env_file)
        # 私有路径先验证，避免发现仓库内候选时才拒绝已发出的登录。
        private_directory(args.directory)
        outside_git(args.backup_directory)
        if args.previous:
            private_directory(args.previous)
        require(not args.retire or args.previous is not None, "--retire 必须提供 --previous")
        client = NacosClient(environment, args.timeout)
        summary = maintain(client, descriptions, replacements, args.directory, args.backup_directory,
                           args.previous, args.apply, args.retire)
        print(json.dumps(summary, ensure_ascii=False))
        return 0
    except MaintenanceError as error:
        print("维护停止：" + str(error))
    except (OSError, ValueError, TypeError, KeyError):
        print("维护停止：本地输入或配置格式无效；路径、凭据和内容不输出")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
