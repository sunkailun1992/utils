#!/usr/bin/env python3
"""把可提交的环境变量模板渲染到私有目录，不输出任何配置值。"""
import argparse
import json
from pathlib import Path
import re
import subprocess
from start_service import private_environment


def yaml_json(text):
    documents = yaml_documents(text)
    if len(documents) != 1:
        raise ValueError("Nacos YAML 必须恰好包含一个文档")
    return documents[0]


def yaml_documents(text):
    """保留 Spring profile 的 YAML 文档顺序，拒绝任一文档中的重复键。"""
    result = subprocess.run(["ruby", "-ryaml", "-rjson", "-e",
                             """
      source = STDIN.read
      visit = lambda do |node|
        if node.is_a?(Psych::Nodes::Mapping)
          keys = []
          node.children.each_slice(2) do |key, _value|
            raise 'unsupported or duplicate YAML key' unless key.is_a?(Psych::Nodes::Scalar)
            raise 'unsupported or duplicate YAML key' if keys.include?(key.value)
            keys << key.value
          end
        end
        (node.children || []).each { |child| visit.call(child) }
      end
      stream = Psych.parse_stream(source)
      visit.call(stream)
      documents = stream.children.map do |document|
        single = Psych::Nodes::Stream.new
        single.children << document
        YAML.safe_load(single.to_yaml, aliases: true)
      end
      puts JSON.generate(documents)
    """],
                            input=text, capture_output=True, text=True)
    if result.returncode:
        raise ValueError("YAML 格式错误或存在重复键；原文不输出")
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


def render_document(source, environment):
    """只替换 YAML 标量中的私有占位符，保留注释、分组和未改动的文本。"""
    # Psych 已用于 YAML 校验；复用其标量位置，避免正则误改注释或把密钥拼成 YAML。
    positions = subprocess.run(["ruby", "-ryaml", "-rjson", "-e", """
      nodes = []
      visit = lambda do |node|
        if node.is_a?(Psych::Nodes::Scalar) && node.value.include?('${GITHUB_')
          nodes << [node.start_line, node.start_column, node.end_line, node.end_column, node.value]
        end
        (node.children || []).each { |child| visit.call(child) }
      end
      visit.call(Psych.parse_stream(STDIN.read))
      puts JSON.generate(nodes)
    """], input=source, capture_output=True, text=True)
    if positions.returncode:
        raise ValueError("YAML 模板格式错误；原文不输出")
    lines = source.splitlines(keepends=True)
    offsets = [0]
    for line in lines:
        offsets.append(offsets[-1] + len(line))
    result = source
    for start_line, start_column, end_line, end_column, value in reversed(json.loads(positions.stdout)):
        start = offsets[start_line] + start_column
        end = offsets[end_line] + end_column
        replacement = json.dumps(render(value, environment), ensure_ascii=False)
        # 块标量的结束位置可能包含换行；保留下一个键的行边界。
        if source[start:end].endswith("\n"):
            replacement += "\n"
        result = result[:start] + replacement + result[end:]
    # JSON 序列化保留 bool/int/string 等类型差异，不能依赖 Python 的 True == 1。
    if json.dumps(yaml_json(result), sort_keys=True, ensure_ascii=False) != json.dumps(
            render(yaml_json(source), environment), sort_keys=True, ensure_ascii=False):
        raise ValueError("渲染前后 YAML 语义不一致；未输出配置")
    return result


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
        result = render_document(file.read_text(), environment)
        destination = args.output / file.name
        destination.write_text(result)
        destination.chmod(0o600)
    print(f"已渲染 {len(list(source.iterdir()))} 个 dataId 到私有目录；配置值不输出")
