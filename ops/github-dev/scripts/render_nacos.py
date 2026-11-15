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


def dotted(path):
    """键路径的报错写法，只含键名与列表下标，不含值。"""
    text = ""
    for part in path:
        text += f"[{part}]" if isinstance(part, int) else (f".{part}" if text else str(part))
    return text


def _same(left, right):
    return json.dumps(left, sort_keys=True, ensure_ascii=False) == json.dumps(right, sort_keys=True, ensure_ascii=False)


def _expected(template, remote, path, drops, problems):
    """模板每个标量取远端同路径的值；远端多出的键只能是已声明的删除项。"""
    if isinstance(template, dict) and isinstance(remote, dict):
        for key in remote:
            if key not in template and dotted(path + [key]) not in drops:
                problems.append("远端有模板没有的键：" + dotted(path + [key]))
        return {key: _expected(value, remote[key], path + [key], drops, problems) if key in remote else value
                for key, value in template.items()}
    if isinstance(template, list) and isinstance(remote, list):
        if len(template) != len(remote):
            problems.append("列表长度不同：" + dotted(path))
            return template
        return [_expected(item, other, path + [index], drops, problems)
                for index, (item, other) in enumerate(zip(template, remote))]
    if isinstance(template, (dict, list)) or isinstance(remote, (dict, list)):
        problems.append("结构不同：" + dotted(path))
        return template
    return remote


def overlay_document(rendered, remote_text, drops=()):
    """保值渲染：保留已渲染模板的注释、键序与结构，同路径的值换成远端当前值。

    远端多出的键必须在 drops（点分键路径）中声明才会被删除；报错只给键路径，不输出值。"""
    template, remote = yaml_json(rendered), yaml_json(remote_text)
    problems = []
    expected = _expected(template, remote, [], set(drops), problems)
    if problems:
        raise ValueError("；".join(problems))
    positions = subprocess.run(["ruby", "-ryaml", "-rjson", "-e", """
      nodes = []
      visit = lambda do |node, path|
        case node
        when Psych::Nodes::Mapping
          node.children.each_slice(2) { |key, value| visit.call(value, path + [key.value]) }
        when Psych::Nodes::Sequence
          node.children.each_with_index { |child, index| visit.call(child, path + [index]) }
        when Psych::Nodes::Scalar
          nodes << [path, node.start_line, node.start_column, node.end_line, node.end_column]
        when Psych::Nodes::Alias
          raise 'alias unsupported'
        else
          (node.children || []).each { |child| visit.call(child, path) }
        end
      end
      visit.call(Psych.parse_stream(STDIN.read), [])
      puts JSON.generate(nodes)
    """], input=rendered, capture_output=True, text=True)
    if positions.returncode:
        raise ValueError("YAML 模板格式错误；原文不输出")
    offsets = [0]
    for line in rendered.splitlines(keepends=True):
        offsets.append(offsets[-1] + len(line))
    result = rendered
    for path, start_line, start_column, end_line, end_column in reversed(json.loads(positions.stdout)):
        value, current = expected, template
        for part in path:
            value, current = value[part], current[part]
        if _same(value, current):
            continue
        start, end = offsets[start_line] + start_column, offsets[end_line] + end_column
        replacement = json.dumps(value, ensure_ascii=False)
        if rendered[start:end].endswith("\n"):
            replacement += "\n"
        if start == end and not rendered[start - 1:start].isspace():
            replacement = " " + replacement
        result = result[:start] + replacement + result[end:]
    if not _same(yaml_json(result), expected):
        raise ValueError("保值渲染后与远端语义不一致；未输出配置")
    return result


def render_directory(source, output, environment, remote=None, keep_remote=(), drops=()):
    """渲染全部模板；keep_remote 中的 dataId 按 remote 目录的远端原文保值渲染。

    全部算完才写文件；输出目录只能含当前模板同名文件，避免已退役的 dataId 随发布被重新写回。"""
    names = sorted(file.name for file in source.iterdir())
    keep = set(keep_remote)
    unknown = keep - set(names)
    if unknown:
        raise ValueError("--keep-remote 不是当前模板：" + "、".join(sorted(unknown)))
    if keep and remote is None:
        raise ValueError("--keep-remote 需要 --remote 远端原文目录")
    grouped = {}
    for item in drops:
        name, separator, path = item.partition(":")
        if not separator or not path or name not in keep:
            raise ValueError("--drop 须写成 <保值 dataId>:<键路径>：" + item)
        grouped.setdefault(name, set()).add(path)
    if output.exists():
        stale = sorted(file.name for file in output.iterdir() if file.name not in names)
        if stale:
            raise ValueError("输出目录含非当前模板的文件，发布时会被一并写入：" + "、".join(stale))
    results = {}
    for name in names:
        result = render_document((source / name).read_text(), environment)
        if name in keep:
            current = remote / name
            if not current.is_file():
                raise ValueError("远端原文目录缺少：" + name)
            try:
                result = overlay_document(result, current.read_text(), grouped.get(name, ()))
            except ValueError as error:
                raise ValueError(f"{name}：{error}") from None
        results[name] = result
    output.mkdir(parents=True, exist_ok=True, mode=0o700)
    output.chmod(0o700)
    for name, result in results.items():
        destination = output / name
        destination.write_text(result)
        destination.chmod(0o600)
    return names


def outside_git(path):
    resolved = path.resolve()
    if any((parent / ".git").exists() for parent in [resolved, *resolved.parents]):
        raise ValueError("含凭据的渲染结果和远端原文必须放在 Git 仓库外")
    return path


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, default=Path.home() / ".config/kellen-github/dev.env")
    parser.add_argument("--output", type=Path, default=Path.home() / ".config/kellen-github/nacos-rendered/DEFAULT_GROUP")
    parser.add_argument("--remote", type=Path, help="Git 外的远端原文目录，文件名即 dataId；可与 sync_nacos.py 的 --previous 共用")
    parser.add_argument("--keep-remote", action="append", default=[], metavar="DATAID",
                        help="该 dataId 的值以远端为准，模板只提供注释、键序与结构；可重复")
    parser.add_argument("--drop", action="append", default=[], metavar="DATAID:键路径",
                        help="保值渲染时允许删除的远端键，例如 rag-spring.yaml:rag.engine.graph-enabled；可重复")
    args = parser.parse_args()
    outside_git(args.output)
    if args.remote:
        outside_git(args.remote)
    source = Path(__file__).resolve().parents[1] / "nacos/DEFAULT_GROUP"
    names = render_directory(source, args.output, private_environment(args.env_file), args.remote,
                             args.keep_remote, args.drop)
    print(f"已渲染 {len(names)} 个 dataId 到私有目录，其中 {len(set(args.keep_remote))} 个按远端值保值渲染；配置值不输出")
    for item in sorted(args.drop):
        print("已删除远端键 " + item)
