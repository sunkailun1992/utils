"""Official CustomLogger extension: fixed safe metadata, never provider text or payloads."""
import collections
from datetime import datetime, timezone
import hashlib
import json
import logging
import math
import re
import sys
import threading
import uuid

from litellm.integrations.custom_logger import CustomLogger

try:
    from opentelemetry import trace as _trace
except ImportError:
    _trace = None

ERROR_TYPES = frozenset({"HTTPException", "ServiceUnavailableError", "InternalServerError", "RateLimitError",
                        "BadRequestError", "AuthenticationError", "PermissionDeniedError", "APIConnectionError",
                        "APITimeoutError", "TimeoutError", "ContextWindowExceededError", "ContentPolicyViolationError"})
OPERATIONS = {"completion": "chat", "acompletion": "chat", "text_completion": "chat",
              "embedding": "embedding", "aembedding": "embedding", "rerank": "rerank", "arerank": "rerank",
              "pass_through_endpoint": "passthrough", "pass_through": "passthrough"}
UUID = re.compile(r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\Z")


class SafeStreamHandler(logging.StreamHandler):
    def handleError(self, record):
        # No recursive logging and no logging module traceback/record dump on sink failure.
        try:
            sys.stderr.write('{"event":"litellm.safe_logger.output_failed","level":"ERROR","terminal":false}\n')
        except Exception:
            pass


def _mapping(value):
    return value if isinstance(value, dict) else {}


def _call_hash(data):
    standard = _mapping(data.get("standard_logging_object"))
    params = _mapping(data.get("litellm_params"))
    values = [data.get("litellm_call_id"), standard.get("litellm_call_id"), params.get("litellm_call_id")]
    try:
        values.append(getattr(data.get("litellm_logging_obj"), "litellm_call_id", None))
    except Exception:
        pass
    for value in values:
        if isinstance(value, str) and UUID.fullmatch(value):
            canonical = uuid.UUID(value)
            if canonical.int:
                return hashlib.sha256(str(canonical).encode()).hexdigest()
    return None


def _error_fields(data, exception):
    info = _mapping(_mapping(data.get("standard_logging_object")).get("error_information"))
    error = exception if isinstance(exception, BaseException) else data.get("exception")
    kind = type(error).__name__ if isinstance(error, BaseException) else info.get("error_class")
    result = {"error_type": kind if isinstance(kind, str) and kind in ERROR_TYPES else "ProviderError"}
    candidates = [info.get("error_code")]
    for key in ("status_code", "code"):
        try:
            candidates.insert(0, getattr(error, key, None))
        except Exception:
            pass
    for value in candidates:
        if type(value) is int and 100 <= value <= 599:
            result["http_status"] = value
            break
        if isinstance(value, str) and re.fullmatch(r"[1-5][0-9]{2}", value):
            result["http_status"] = int(value)
            break
    return result


def _duration(start, end):
    try:
        if isinstance(start, datetime) and isinstance(end, datetime):
            seconds = (end - start).total_seconds()
        elif type(start) in (int, float) and type(end) in (int, float):
            seconds = end - start
        else:
            return None
        return round(seconds * 1000) if math.isfinite(seconds) and 0 <= seconds <= 86400 else None
    except Exception:
        return None


class SafeTerminalLogger(CustomLogger):
    def __init__(self, stream=None, capacity=4096):
        super().__init__(turn_off_message_logging=True)
        self.logger = logging.Logger("fleet.litellm.safe", level=logging.INFO)
        self.logger.propagate = False
        handler = SafeStreamHandler(stream if stream is not None else sys.stdout)
        handler.setLevel(logging.INFO)
        handler.setFormatter(logging.Formatter("%(message)s"))
        self.logger.addHandler(handler)
        self._seen = collections.OrderedDict()
        self._capacity = capacity
        self._lock = threading.Lock()

    def _emit(self, kwargs, failed, start=None, end=None, exception=None):
        data = _mapping(kwargs)
        call = _call_hash(data)
        event, terminal = "litellm.request.terminal", call is not None
        with self._lock:
            if call in self._seen:
                previous_failed = self._seen[call]
                if previous_failed or not failed:
                    return
                event, terminal = "litellm.request.failure_update", False
            if call is not None:
                self._seen[call] = failed
                self._seen.move_to_end(call)
                while len(self._seen) > self._capacity:
                    self._seen.popitem(last=False)
        call_type = data.get("call_type")
        operation = OPERATIONS.get(call_type, "unknown") if isinstance(call_type, str) else "unknown"
        stream = data.get("stream") is True or _mapping(data.get("standard_logging_object")).get("stream") is True
        # Native passthrough bills partial streams through success callbacks. Never infer completion.
        unverified_stream = stream or operation == "passthrough"
        outcome = "failed" if failed else "stream_closed_unverified" if unverified_stream else "response_returned"
        record = {"event": event, "summary": "Model request failed" if failed else "Model stream closed; completion unverified" if unverified_stream else "Model response returned",
                  "timestamp": datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z"),
                  "level": "ERROR" if failed else "INFO", "service.name": "orb-litellm", "service.version": "1.108.4",
                  "deployment.environment.name": "github-dev", "operation": operation, "outcome": outcome,
                  "terminal": terminal, "completion_verified": False, "correlation_available": call is not None}
        if call is not None:
            record["call_id_sha256"] = call
        duration = _duration(start, end)
        if duration is not None:
            record["duration_ms"] = duration
        if failed:
            record.update(_error_fields(data, exception))
        # Use only an actual current OTel context; never take identity from headers/metadata.
        if _trace is not None:
            try:
                context = _trace.get_current_span().get_span_context()
                if context.is_valid:
                    record["trace_id"] = format(context.trace_id, "032x")
                    record["span_id"] = format(context.span_id, "016x")
            except Exception:
                pass
        self.logger.log(logging.ERROR if failed else logging.INFO, json.dumps(record, separators=(",", ":"), ensure_ascii=True))

    def log_success_event(self, kwargs, response_obj, start_time, end_time):
        self._emit(kwargs, False, start_time, end_time)

    async def async_log_success_event(self, kwargs, response_obj, start_time, end_time):
        self._emit(kwargs, False, start_time, end_time)

    def log_failure_event(self, kwargs, response_obj, start_time, end_time):
        self._emit(kwargs, True, start_time, end_time)

    async def async_log_failure_event(self, kwargs, response_obj, start_time, end_time):
        self._emit(kwargs, True, start_time, end_time)

    async def async_post_call_failure_hook(self, request_data, original_exception, user_api_key_dict, traceback_str=None):
        self._emit(request_data, True, exception=original_exception)
        return None  # Logging only; preserve the original error response.


safe_logger = SafeTerminalLogger()
