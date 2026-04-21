# DBeaver UI Tests

SWTBot UI test harness for validating DBeaver pre-release builds.

Currently covers:
- **Firebird** — connection wizard, connection-settings round-trip, SET TERM script execution, EXECUTE BLOCK parameter binding (positional + named), EXECUTE PROCEDURE parameter binding (regression guard for [dbeaver/dbeaver#40644](https://github.com/dbeaver/dbeaver/pull/40644)), navigator smoke tests.
- **PostgreSQL** — connection via API, regression guard for [dbeaver/dbeaver#40665](https://github.com/dbeaver/dbeaver/pull/40665) (script execution of COMMIT/ROLLBACK must not regress on databases that historically accepted them as raw SQL).

## Prerequisites

### Local execution

- Java 21
- Maven 3.9+
- Firebird 5 server installed and running (local runs cover Firebird only; PostgreSQL tests run in CI)
- Local DBeaver build (run `mvn clean verify -DskipTests` in the DBeaver repo)

### CI (GitHub Actions)

All prerequisites are handled automatically by the workflow. See [CI Workflow](#ci-workflow) below.

## Quick Start

```powershell
# 1. Copy and edit local config
cp ui-test.local.properties.example ui-test.local.properties
# Edit ui-test.local.properties with your paths

# 2. Run the test suite
powershell -ExecutionPolicy Bypass -File .\run-ui-tests.ps1
```

## CI Workflow

The **UI Tests** workflow (`.github/workflows/ui-tests.yml`) runs the full test suite on GitHub Actions against a DBeaver pre-release build from [`fdcastel/dbeaver`](https://github.com/fdcastel/dbeaver/releases).

### Triggers

| Trigger | Description |
|---------|-------------|
| `repository_dispatch` | Fired automatically by the `fdcastel/dbeaver` pre-release workflow after each successful build. |
| `workflow_dispatch` | Manual trigger from the Actions tab. Accepts an optional `release_tag` input (e.g. `v17.0.0-pre.4`); auto-detects the latest prerelease if empty. |

### What it does

1. **Resolves the DBeaver release tag** — from dispatch payload, manual input, or latest prerelease auto-detection.
2. **Downloads the P2 repository** — the `*-p2-repository.tar.gz` asset published by the DBeaver pre-release workflow.
3. **Installs Firebird 5** — silent install of Firebird on the Windows runner via the official Inno-Setup installer.
4. **Creates the Firebird test database** — using `isql.exe` and the fixture SQL scripts from `fixtures/sql/`.
5. **Starts pre-installed PostgreSQL** — uses the PostgreSQL service already present on `windows-latest`, resets the `postgres` password, creates the `ui_test` database.
6. **Runs the UI tests** — `mvn clean verify` with Tycho/SWTBot against the DBeaver P2 repository; both Firebird and PostgreSQL test plugins are exercised.
7. **Uploads artifacts** — JUnit XML reports (all plugins) and screenshots as a build artifact.
8. **Publishes a test report** — parsed JUnit results shown as a GitHub Check Run.

### Manual trigger

```bash
gh workflow run ui-tests.yml -f release_tag=v17.0.0-pre.4
```

## Project Structure

- `plugins/local.dbeaver.ui.test.support/` — shared SWTBot helpers
- `plugins/local.dbeaver.firebird.ui.test/` — Firebird UI test scenarios
- `plugins/local.dbeaver.postgresql.ui.test/` — PostgreSQL UI test scenarios
- `fixtures/sql/` — SQL scripts for Firebird test database setup
- `scripts/` — utility scripts (DB reset, prerequisite checks)
- `.github/workflows/ui-tests.yml` — CI workflow
- `artifacts/` — test run output (screenshots, logs)
