import ast
import asyncio
import concurrent.futures
import contextlib
import importlib.util
import io
import json
import logging
from pathlib import Path
import shutil
import sys
import tempfile
import types
import unittest
from typing import Final

ROOT = Path(__file__).resolve().parent
CANARY = "synthetic-safe-logger-private-body"
CALL = "12345678-1234-4234-8234-123456789012"


def load_callback():
    # Offline unit boundary only; the pinned-image probe separately checks the real SDK.
    module = types.ModuleType("litellm.integrations.custom_logger")
    class CustomLogger:
        def __init__(self, **kwargs): self.turn_off_message_logging = kwargs["turn_off_message_logging"]
    module.CustomLogger = CustomLogger
    sys.modules["litellm.integrations.custom_logger"] = module
    spec = importlib.util.spec_from_file_location("fleet_safe_logger_fixture", ROOT / "fleet_safe_logger.py")
    result = importlib.util.module_from_spec(spec); spec.loader.exec_module(result)
    return result


class DangerousError(Exception):
    status_code = 503
    def __str__(self): raise AssertionError("EXCEPTION_STRINGIFICATION_FORBIDDEN")


class SafeLogging(unittest.TestCase):
    @classmethod
    def setUpClass(cls): cls.module = load_callback()

    def setUp(self):
        self.output = io.StringIO()
        self.callback = self.module.SafeTerminalLogger(stream=self.output)
        self.kwargs = {"litellm_call_id": CALL, "call_type": "acompletion", "stream": False,
                       "exception": DangerousError(CANARY), "messages": [{"content": CANARY}],
                       "response": CANARY, "headers": {"Authorization": CANARY}, "url": CANARY,
                       "standard_logging_object": {"error_information": {"error_message": CANARY, "traceback": CANARY, "error_code": "503"}}}

    def rows(self): return [json.loads(line) for line in self.output.getvalue().splitlines()]

    def test_failure_is_safe_error_without_stringifying_payload_or_exception(self):
        self.callback.log_failure_event(self.kwargs, None, 1.0, 1.5)
        row = self.rows()[0]
        self.assertEqual(row["level"], "ERROR"); self.assertEqual(row["http_status"], 503)
        self.assertEqual(row["error_type"], "ProviderError"); self.assertEqual(row["outcome"], "failed")
        self.assertEqual(row["duration_ms"], 500)
        self.assertNotIn(CANARY, self.output.getvalue()); self.assertNotIn(CALL, self.output.getvalue())

    def test_sync_async_and_proxy_failure_hooks_emit_one_terminal(self):
        self.callback.log_failure_event(self.kwargs, None, 1.0, 1.5)
        asyncio.run(self.callback.async_log_failure_event(self.kwargs, None, 1.0, 1.5))
        asyncio.run(self.callback.async_post_call_failure_hook(self.kwargs, self.kwargs["exception"], None, CANARY))
        self.assertEqual(len(self.rows()), 1)

    def test_concurrent_hook_duplicates_are_bounded_and_unique(self):
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            list(pool.map(lambda _: self.callback.log_failure_event(self.kwargs, None, 1.0, 1.5), range(32)))
        self.assertEqual(len(self.rows()), 1)

    def test_stream_and_all_native_successes_remain_unverified(self):
        for kind in ("acompletion", "pass_through_endpoint"):
            callback = self.module.SafeTerminalLogger(stream=self.output)
            kwargs = {"litellm_call_id": CALL, "call_type": kind, "stream": kind == "acompletion"}
            callback.log_success_event(kwargs, {"finish_reason": "stop", "body": CANARY}, 1.0, 1.5)
        self.assertTrue(all(row["outcome"] == "stream_closed_unverified" and row["completion_verified"] is False for row in self.rows()))
        self.assertNotIn(CANARY, self.output.getvalue())

    def test_late_failure_is_visible_without_a_second_terminal_or_success_claim(self):
        self.callback.log_success_event(self.kwargs, None, 1.0, 1.5)
        self.callback.log_failure_event(self.kwargs, None, 1.0, 1.5)
        rows = self.rows()
        self.assertEqual(sum(row["terminal"] for row in rows), 1)
        self.assertEqual(rows[-1]["event"], "litellm.request.failure_update")
        self.assertEqual(rows[-1]["level"], "ERROR")
        self.assertTrue(all(row["completion_verified"] is False for row in rows))

    def test_invalid_identity_is_not_emitted_and_unknown_types_are_fixed(self):
        self.kwargs["litellm_call_id"] = CANARY
        self.callback.log_failure_event(self.kwargs, None, None, None)
        self.assertFalse(self.rows()[0]["correlation_available"])
        self.assertFalse(self.rows()[0]["terminal"])
        self.assertNotIn(CANARY, self.output.getvalue())

    def test_auth_validation_hook_preserves_error_and_handles_untrusted_field_types(self):
        data = {"litellm_call_id": CALL, "call_type": {"body": CANARY},
                "standard_logging_object": {"error_information": {"error_class": {"body": CANARY}, "error_code": "401"}}}
        result = asyncio.run(self.callback.async_post_call_failure_hook(data, None, {"key": CANARY}, CANARY))
        self.assertIsNone(result)
        self.assertEqual(self.rows()[0]["operation"], "unknown")
        self.assertEqual(self.rows()[0]["http_status"], 401)
        self.assertNotIn(CANARY, self.output.getvalue())

    def test_output_failure_prints_only_fixed_health_event_without_recursive_traceback(self):
        class Broken:
            def write(self, value): raise OSError(CANARY)
            def flush(self): pass
        callback = self.module.SafeTerminalLogger(stream=Broken())
        error = io.StringIO()
        with contextlib.redirect_stderr(error): callback.log_failure_event(self.kwargs, None, 1.0, 1.5)
        self.assertEqual(json.loads(error.getvalue())["event"], "litellm.safe_logger.output_failed")
        self.assertNotIn(CANARY, error.getvalue())

    def test_critical_root_does_not_hide_callback_info_or_error(self):
        root = logging.getLogger(); old = root.level; root.setLevel(logging.CRITICAL)
        try:
            self.callback.log_success_event({"litellm_call_id": CALL, "stream": True}, None, 1.0, 1.5)
            other = dict(self.kwargs, litellm_call_id="22345678-1234-4234-8234-123456789012")
            self.callback.log_failure_event(other, None, 1.0, 1.5)
        finally: root.setLevel(old)
        self.assertEqual([row["level"] for row in self.rows()], ["INFO", "ERROR"])
        self.assertFalse(self.callback.logger.propagate)




if __name__ == "__main__": unittest.main()
