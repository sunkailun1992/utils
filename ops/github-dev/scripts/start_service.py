#!/usr/bin/env python3
"""使用私有配置启动 GitHub dev 服务，不执行 env 文件中的 shell 代码。"""
import argparse
import os
from pathlib import Path
import shlex
import subprocess

SERVICES = ("gateway", "user", "message", "report", "ai", "ai-agent", "ai-memory", "rag", "mcp")


def private_environment(path):
    if path.is_symlink() or not path.is_file() or path.stat().st_mode & 0o077:
        raise ValueError("私有环境文件必须是 mode 0600 的普通文件")
    values = {}
    for line in path.read_text().splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        key, value = line.split("=", 1)
        if not key.startswith("GITHUB_") or not key.replace("_", "").isalnum():
            raise ValueError("环境文件只接受 GITHUB_* 键")
        tokens = shlex.split(value)
        if len(tokens) > 1:
            raise ValueError("环境值必须是单个带引号或不带引号的字符串")
        values[key] = tokens[0] if tokens else ""
    return values


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("service", choices=SERVICES)
    parser.add_argument("--env-file", type=Path, default=Path.home() / ".config/kellen-github/dev.env")
    parser.add_argument("--repos-root", type=Path, default=Path(__file__).resolve().parents[4])
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    values = private_environment(args.env_file)
    if values.get("GITHUB_NACOS_DEV_NAMESPACE", "github-dev") != "github-dev":
        raise ValueError("个人 dev 启动入口只允许 github-dev namespace")
    if not values.get("GITHUB_NACOS_SERVER_ADDR"):
        raise ValueError("缺少 GITHUB_NACOS_SERVER_ADDR")
    values.update(APP_ENV="dev", GITHUB_NACOS_DEV_NAMESPACE="github-dev")
    repo = args.repos_root / args.service
    jars = sorted(p for p in (repo / "build/libs").glob("*.jar") if not p.name.endswith("-plain.jar"))
    if len(jars) != 1:
        raise ValueError("请先完成对应服务 bootJar，build/libs 必须只有一个可部署 jar")
    command = ["java", "-jar", str(jars[0])]
    print(f"{args.service}: profile=dev, namespace=github-dev; 私有值不输出", flush=True)
    if not args.dry_run:
        raise SystemExit(subprocess.call(command, cwd=repo, env={**os.environ, **values}))
