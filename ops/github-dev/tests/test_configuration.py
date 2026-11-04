"""网关迁移反例：重复前缀、缺少重写和路由优先级不能被单仓编译发现。"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from verify_configuration import check_route, route_destination


class RouteContractTests(unittest.TestCase):
    def setUp(self):
        self.routes = [
            {"id": "rag", "uri": "lb://rag", "order": 1, "predicates": ["Path=/api/rag/**"]},
            {"id": "ai", "uri": "lb://ai", "order": 2, "predicates": ["Path=/api/**"]},
            {"id": "message", "uri": "lb://message", "predicates": ["Path=/message/**"],
             "filters": ["StripPrefix=1"]},
        ]

    def test_direct_rag_and_prefixed_message_reach_their_controllers(self):
        check_route("/api/rag/documents", "rag", {"/api/rag/documents"}, self.routes)
        check_route("/message/messages/user-messages", "message", {"/messages/user-messages"}, self.routes)

    def test_old_rag_prefix_reproduces_the_migration_404(self):
        with self.assertRaisesRegex(AssertionError, "没有网关路由"):
            check_route("/rag/api/rag/documents", "rag", {"/api/rag/documents"}, self.routes)

    def test_missing_strip_prefix_cannot_reach_message_controller(self):
        self.routes[2]["filters"] = []
        with self.assertRaises(AssertionError):
            check_route("/message/messages/user-messages", "message", {"/messages/user-messages"}, self.routes)

    def test_general_ai_route_cannot_take_priority_over_rag(self):
        self.routes[1]["order"] = 0
        with self.assertRaises(AssertionError):
            check_route("/api/rag/documents", "rag", {"/api/rag/documents"}, self.routes)

    def test_path_segment_boundary_is_not_a_prefix_match(self):
        self.assertEqual(route_destination("/api/ragged/documents", self.routes),
                         ("lb://ai", "/api/ragged/documents"))

    def test_unsupported_rewrites_fail_instead_of_claiming_validation(self):
        self.routes[0]["filters"] = ["RewritePath=/api/rag/(?<segment>.*), /${segment}"]
        with self.assertRaisesRegex(AssertionError, "未支持的路径过滤器"):
            route_destination("/api/rag/documents", self.routes)


if __name__ == "__main__":
    unittest.main()
