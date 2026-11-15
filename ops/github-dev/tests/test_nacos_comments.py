"""Nacos 模板注释完整性：每个 dataId 有文件说明，每个非注释行紧贴的上一行或行尾都有注释（块文本内容行除外）。"""
import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1] / "nacos" / "DEFAULT_GROUP"


def _without_quoted(line):
    return re.sub(r'"(?:[^"\\]|\\.)*"|\'(?:[^\']|\'\')*\'', '""', line)


def uncommented_lines(text):
    """返回缺注释的 (行号, 内容) 列表；块文本（| 或 >）里的内容行不算。"""
    problems = []
    previous = None
    block_indent = None
    for number, line in enumerate(text.splitlines(), 1):
        stripped = line.strip()
        if not stripped:
            if block_indent is None:
                previous = None  # 空行隔开的注释不算贴着下一行，文件头不能代替第一个键的说明。
            continue
        indent = len(line) - len(line.lstrip(" "))
        if block_indent is not None:
            if indent > block_indent:
                previous = line
                continue
            block_indent = None
        if stripped.startswith("#"):
            previous = line
            continue
        inline = re.search(r"\s#\s", _without_quoted(line))
        above = previous is not None and previous.strip().startswith("#")
        if not (inline or above):
            problems.append((number, stripped[:80]))
        if re.search(r":\s*[|>][-+0-9]*\s*(#.*)?$", _without_quoted(line)):
            block_indent = indent
        previous = line
    return problems


def header_problems(text):
    head = "\n".join(line for line in text.splitlines()[:8] if line.strip().startswith("#"))
    missing = [label for label in ("dataId", "用途") if label not in head]
    if "导入服务" not in head and "消费者" not in head:
        missing.append("导入服务/消费者")
    return missing


class NacosCommentTest(unittest.TestCase):
    def test_every_line_commented(self):
        problems = []
        for path in sorted(ROOT.glob("*.y*ml")):
            text = path.read_text(encoding="utf-8")
            for label in header_problems(text):
                problems.append(f"{path.name}: 文件说明缺少{label}")
            for number, content in uncommented_lines(text):
                problems.append(f"{path.name}:{number}: {content}")
        self.assertEqual([], problems, "\n" + "\n".join(problems))


if __name__ == "__main__":
    unittest.main()
