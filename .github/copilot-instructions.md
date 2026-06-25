# GitHub Copilot Instructions

This repository is the `utils` Java library published as `com:utils`. Before suggesting or changing code, read `AGENTS.md` and `docs/ai-coding/README.md`.

Follow these project rules:

- Follow `docs/ai-coding/AI_DIRECTORY_STRUCTURE_GUIDE.md` before adding, moving, or deleting directories.
- Keep Java code under `src/main/java/com/kellen`; tests belong under `src/test/java/com/kellen`.
- Only multi-project reusable capabilities belong here; service-specific logic must remain in consumer services.
- Keep business Dubbo RPC interfaces and DTOs in sibling `rpc-api`; `utils` may only provide cross-cutting Dubbo support such as context propagation.
- Do not change existing SDK credentials, Maven repository credentials, secrets, or production configuration values. Report file paths and line numbers only.
- Public API, package, annotation, AOP, authentication, tenant, response, or MyBatis changes require consumer impact checks.
