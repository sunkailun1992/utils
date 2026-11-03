#!/usr/bin/env python3
"""复用仓库锁定的官方 Nacos SDK；默认只读，发布须显式 --publish。"""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile
from start_service import private_environment

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, default=Path.home() / ".config/kellen-github/dev.env")
    parser.add_argument("--directory", type=Path,
                        default=Path.home() / ".config/kellen-github/nacos-rendered/DEFAULT_GROUP")
    parser.add_argument("--publish", action="store_true")
    parser.add_argument("--previous", type=Path)
    args = parser.parse_args()
    env = {**os.environ, **private_environment(args.env_file)}
    scripts = Path(__file__).resolve().parent
    gateway = scripts.parents[3] / "gateway"
    with tempfile.TemporaryDirectory(prefix="github-nacos-") as temporary:
        init = Path(temporary) / "classpath.gradle"
        init.write_text("allprojects { tasks.register('githubNacosClasspath') { "
                        "doLast { println 'GITHUB_CP=' + configurations.runtimeClasspath.asPath } } }")
        build = subprocess.run(["./gradlew", "-q", "-I", str(init), "githubNacosClasspath", "--no-daemon"],
                               cwd=gateway, env=env, capture_output=True, text=True)
        paths = [line.removeprefix("GITHUB_CP=") for line in build.stdout.splitlines()
                 if line.startswith("GITHUB_CP=")]
        if build.returncode or not paths:
            raise SystemExit("无法解析已有 Nacos SDK classpath；未发布配置")
        command = ["java", "--class-path", paths[-1], str(scripts / "GithubNacosConfigSync.java"),
                   str(args.directory)]
        if args.publish:
            command.append("--publish")
        if args.previous:
            command += ["--previous", str(args.previous)]
        result = subprocess.run(command, env=env, capture_output=True, text=True)
    log = args.env_file.parent / "last-nacos-sync.log"
    log.write_text(result.stdout + result.stderr)
    log.chmod(0o600)
    count = sum(line.startswith("verified ") for line in result.stdout.splitlines())
    print(f"github-dev: {count} 个 dataId 已逐一读回核对；日志仅留私有目录")
    if result.returncode:
        print("校验或 CAS 发布未完成；请检查私有日志，不要把完整日志贴进仓库")
    raise SystemExit(result.returncode)
