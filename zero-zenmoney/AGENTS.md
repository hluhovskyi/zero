# zero-zenmoney — Agent Guide

Import feature for ZenMoney CSV files.

## Rules

1. **Isolated module** — depends on `zero-api` for domain types. Does not depend on `zero-core`.
2. **Import-only** — parses CSV data and produces domain `ImportTransaction` objects.

## What Lives Here

- `ZenMoneyImportComponent` — Dagger component for the import flow
- CSV parsing logic for ZenMoney export format
