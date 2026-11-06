"""从私有目标清单生成 Prometheus 配置；不读取或回显运维令牌。"""
import argparse
import ipaddress
import json
from pathlib import Path
import re

SERVICES = ("gateway", "user", "message", "ai", "ai-agent", "ai-memory", "rag", "report", "mcp")


def address(value):
    """只允许host:port，拒绝凭据、URL、路径及未替换的示例目标。"""
    if not isinstance(value, str) or "PRIVATE_HOST" in value:
        raise ValueError("invalid target")
    match = re.fullmatch(r"(\[[0-9a-fA-F:]+\]|[a-zA-Z0-9][a-zA-Z0-9.-]*):([0-9]{1,5})", value)
    if not match or not 1 <= int(match[2]) <= 65535:
        raise ValueError("invalid target")
    if match[1].startswith("["):
        ipaddress.IPv6Address(match[1][1:-1])
    return value


def render(inventory):
    """完整9服务和Engine各有一个目标，静态标签与URL分离。"""
    if set(inventory) != {"environment", "services", "engine"}:
        raise ValueError("inventory keys do not match schema")
    if inventory["environment"] != "github-dev" or set(inventory["services"]) != set(SERVICES):
        raise ValueError("inventory must contain all nine personal services")
    jobs = []
    targets = [address(inventory["services"][service]) for service in SERVICES]
    if len(set(targets)) != len(targets):
        raise ValueError("different services cannot share the same metrics endpoint")
    for service, target in zip(SERVICES, targets):
        jobs.append({
            "job_name": f"fleet-{service}", "metrics_path": "/actuator/prometheus",
            "scrape_timeout": "3s", "sample_limit": 20000, "label_limit": 30,
            "label_value_length_limit": 256,
            "http_headers": {"actuatorReq": {"files": ["/run/secrets/actuator_token"]}},
            "static_configs": [{"targets": [target], "labels": {
                "application": service, "environment": "github-dev"}}],
        })
    jobs.append({
        "job_name": "fleet-rag-engine", "metrics_path": "/metrics", "scrape_timeout": "3s",
        "authorization": {"type": "Bearer", "credentials_file": "/run/secrets/engine_token"},
        "static_configs": [{"targets": [address(inventory["engine"])], "labels": {
            "application": "rag-engine", "environment": "github-dev"}}],
        "sample_limit": 10000, "label_limit": 30,
    })
    jobs.extend([
        {"job_name": "collector", "static_configs": [{"targets": ["collector:8888"]}]},
        {"job_name": "prometheus", "static_configs": [{"targets": ["localhost:9090"]}]},
    ])
    return {"global": {"scrape_interval": "15s", "evaluation_interval": "15s"},
            "rule_files": ["/etc/prometheus/alerts.yaml"], "scrape_configs": jobs}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inventory", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        config = render(json.loads(args.inventory.read_text()))
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(config, indent=2, ensure_ascii=False) + "\n")
    except (ValueError, TypeError, KeyError, OSError):
        parser.exit(2, "inventory invalid or output unavailable; no target or credential values logged\n")
    print("rendered 9 Java targets + 1 Engine target; secret file references only")


if __name__ == "__main__":
    main()
