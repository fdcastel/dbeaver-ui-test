# DBeaver UI Tests

Private, local-only SWTBot UI test harness for DBeaver Firebird improvements.

## Prerequisites

- Java 21
- Maven 3.9+
- Firebird 5 server installed and running
- Local DBeaver build (run `mvn clean verify -DskipTests` in the DBeaver repo)

## Quick Start

```powershell
# 1. Copy and edit local config
cp ui-test.local.properties.example ui-test.local.properties
# Edit ui-test.local.properties with your paths

# 2. Run the test suite
powershell -ExecutionPolicy Bypass -File .\run-ui-tests.ps1
```

## Project Structure

- `plugins/local.dbeaver.ui.test.support/` — shared SWTBot helpers
- `plugins/local.dbeaver.firebird.ui.test/` — Firebird UI test scenarios
- `fixtures/sql/` — SQL scripts for test database setup
- `scripts/` — utility scripts (DB reset, prerequisite checks)
- `artifacts/` — test run output (screenshots, logs)
