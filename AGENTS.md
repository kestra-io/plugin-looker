# Kestra Looker Plugin

## What

- Provides Looker plugin components under `io.kestra.plugin.looker`.
- Includes tasks and triggers for Looker API 4.0:
  - `io.kestra.plugin.looker.queries.Run`
  - `io.kestra.plugin.looker.queries.SqlRun`
  - `io.kestra.plugin.looker.looks.Run`
  - `io.kestra.plugin.looker.looks.List`
  - `io.kestra.plugin.looker.looks.Trigger`
  - `io.kestra.plugin.looker.dashboards.List`
  - `io.kestra.plugin.looker.dashboards.Render`
  - `io.kestra.plugin.looker.schedules.RunOnce`
  - `io.kestra.plugin.looker.projects.Deploy`

## Why

- What user problem does this solve? Teams need to automate Looker business intelligence workflows within Kestra, including running queries, rendering dashboards for automated distribution, executing scheduled reports, triggering flows on Look data changes, and automating LookML deployments.
- Why would a team adopt this plugin in a workflow? It enables end-to-end data pipeline orchestration connecting upstream data transformations directly to downstream BI assets, reporting, and alerting.
- What operational/business outcome does it enable? Reliable automation of BI reports, continuous delivery for LookML projects, and event-driven alerting on business metrics.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin.looker`:

- `queries` - Run inline queries and SQL Runner queries
- `looks` - Run saved looks, list looks, and poll looks as triggers
- `dashboards` - List dashboards and render them to PDF/image
- `schedules` - Execute scheduled plans once
- `projects` - Deploy LookML projects to production

### Key Plugin Classes

- `io.kestra.plugin.looker.queries.Run`
- `io.kestra.plugin.looker.queries.SqlRun`
- `io.kestra.plugin.looker.looks.Run`
- `io.kestra.plugin.looker.looks.List`
- `io.kestra.plugin.looker.looks.Trigger`
- `io.kestra.plugin.looker.dashboards.List`
- `io.kestra.plugin.looker.dashboards.Render`
- `io.kestra.plugin.looker.schedules.RunOnce`
- `io.kestra.plugin.looker.projects.Deploy`

### Project Structure

```
plugin-looker/
├── src/main/java/io/kestra/plugin/looker/
├── src/test/java/io/kestra/plugin/looker/
├── build.gradle
└── README.md
```

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
