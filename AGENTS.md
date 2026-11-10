# Project rules

- In Java code, import classes instead of using fully qualified names in expressions, declarations, or constructor calls. Prefer importing the class and calling `UrlUtils.isWebUrl(...)`; use static imports only when they improve readability. Fully qualified names are acceptable only when necessary to resolve a name collision.
- Do not commit or push unless the user explicitly requests it.
- Do not test the running app manually (launching Spark, clicking through it, taking screenshots). The user tests changes in the app; run the automated tests instead.
- Keep `next_steps.md` (including `docs/next_steps.md`) out of commits.
- Write new project documentation, code comments, and user-facing application text in English. The local, uncommitted roadmap may remain in Russian.

## Java code style

- Optimize for human readability: use descriptive names, short methods, and blank lines between logical steps.
- Always use braces for `if`, `else`, and loop bodies. Put each statement and variable declaration on its own line.
- Put annotations on separate lines above declarations, including Lombok annotations.
- Prefer `var` for local variables when type inference preserves the intended type. Use Java 21 syntax and APIs where they improve clarity.
- Prefer Lombok for boilerplate getters, setters, and constructors. Keep methods with validation, persistence, or lifecycle behavior explicit.
- Keep classes focused and small; aim for about 130–150 lines. Split by responsibility, not arbitrary file length. Follow SOLID and favor composition.
- Extract repeated logic into named methods or focused classes. Keep constructors and event handlers short; avoid deeply nested lambdas.
- Code must remain correct after IntelliJ formatting. Do not rely on compact layout or formatter suppression for readability.
- Preserve behavior during refactoring. Build and run relevant tests before reporting completion.
