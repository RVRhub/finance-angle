# Dashboard Agent Rules

Apply these rules to changes under `dashboard-app/`.

- Keep financial/business calculations in the backend when that logic already exists there.
- Treat the dashboard as presentation and interaction code unless the feature explicitly requires otherwise.
- Reuse existing ECharts setup, theme, chart lifecycle, and responsive patterns before adding new abstractions.
- Reuse existing chart instances instead of creating duplicate instances for alternate views such as fullscreen.
- Preserve accessibility attributes and keyboard behavior for interactive controls.
- Keep layout responsive on desktop and mobile.
- Avoid adding frontend dependencies unless the PR explains why the existing stack is insufficient.
- Manually verify visual/interaction changes when possible and state clearly when browser verification was not performed.
