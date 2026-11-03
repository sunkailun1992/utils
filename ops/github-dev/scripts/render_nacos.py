#!/usr/bin/env python3
"""把可提交的环境变量模板渲染到私有目录，不输出任何配置值。"""
import argparse
import json
from pathlib import Path
import re
import subprocess
from start_service import private_environment


def yaml_json(text):
    result = subprocess.run(["ruby", "-ryaml", "-rjson", "-e",
                             "puts JSON.generate(YAML.safe_load(STDIN.read, aliases: true))"],
                            input=text, capture_output=True, text=True)
    if result.returncode:
        raise ValueError("YAML 模板格式错误；原文不输出")
    return json.loads(result.stdout)


def render(value, environment):
    if isinstance(value, dict):
        return {key: render(item, environment) for key, item in value.items()}
    if isinstance(value, list):
        return [render(item, environment) for item in value]
    if not isinstance(value, str):
        return value
    def replace(match):
        key, separator, default = match.group(1).partition(":")
        if key in environment:
            return environment[key]
        if separator:
            return default
        raise ValueError("缺少私有配置键：" + key)
    return re.sub(r"\$\{(GITHUB_[^{}]+)\}", replace, value)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, default=Path.home() / ".config/kellen-github/dev.env")
    parser.add_argument("--output", type=Path, default=Path.home() / ".config/kellen-github/nacos-rendered/DEFAULT_GROUP")
    args = parser.parse_args()
    if any((parent / ".git").exists() for parent in [args.output.resolve(), *args.output.resolve().parents]):
        raise ValueError("含凭据的渲染结果必须放在 Git 仓库外")
    environment = private_environment(args.env_file)
    source = Path(__file__).resolve().parents[1] / "nacos/DEFAULT_GROUP"
    args.output.mkdir(parents=True, exist_ok=True, mode=0o700)
    args.output.chmod(0o700)
    for file in sorted(source.iterdir()):
        value = render(yaml_json(file.read_text()), environment)
        result = subprocess.run(["ruby", "-rjson", "-ryaml", "-e",
                                 "puts YAML.dump(JSON.parse(STDIN.read))"],
                                input=json.dumps(value, ensure_ascii=False), capture_output=True, text=True)
        if result.returncode:
            raise ValueError("私有配置序列化失败；原文不输出")
        destination = args.output / file.name
        destination.write_text(result.stdout)
        destination.chmod(0o600)
    print(f"已渲染 {len(list(source.iterdir()))} 个 dataId 到私有目录；配置值不输出")
