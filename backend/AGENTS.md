# Backend Agent Rules

Apply these rules to changes under `backend/`.

- Keep controllers thin; business logic belongs in services.
- Use constructor injection and existing Spring/Kotlin patterns.
- Use `BigDecimal`/domain money types for monetary calculations; never `Float` or `Double`.
- Preserve currency information and existing sign conventions for assets, savings, debts, credits, and debits.
- Compare monetary values intentionally in tests; do not depend on BigDecimal scale when scale is not a business rule.
- Database schema changes require Flyway migrations and should be non-destructive by default.
- Backend tests use JUnit 5, Spring Boot test utilities, and H2. Prefer descriptive `should...` test names.
- Financial logic changes should cover relevant positive, negative, and zero cases.
- Useful commands: `./gradlew :backend:test`, `./gradlew :backend:bootRun`.
