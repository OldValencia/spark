# Project rules

- In Java code, import classes instead of using fully qualified names in expressions, declarations, or constructor calls. Prefer importing the class and calling `UrlUtils.isWebUrl(...)`; use static imports only when they improve readability. Fully qualified names are acceptable only when necessary to resolve a name collision.
- Do not commit or push unless the user explicitly requests it.
- Keep `next_steps.md` (including `docs/next_steps.md`) out of commits.
- Write new project documentation, code comments, and user-facing application text in English. The local, uncommitted roadmap may remain in Russian.
