---
name: fetch-design
description: Use when the user pastes a Claude Design URL (https://api.anthropic.com/v1/design/h/...) to fetch and read design assets before implementing any layout.
---

# Fetch Design

## Trigger

URL matching: `https://api.anthropic.com/v1/design/h/<hash>[?open_file=<encoded-path>]`

The user may also write "Implement: <path>" — that's the primary file to read.

## Steps

**1. Parse the URL**
- Hash: segment after `/h/`
- Target file: URL-decode the `open_file` param (`%2F` → `/`)

**2. Fetch**

Use `WebFetch` on the URL as-is. If it returns file content directly, read it.

If `WebFetch` returns a binary or redirect, download and extract:
```bash
curl -s -H "x-api-key: $ANTHROPIC_API_KEY" -H "anthropic-version: 2023-06-01" \
  "https://api.anthropic.com/v1/design/h/<hash>" -o /tmp/design-<hash>.zip
unzip -q /tmp/design-<hash>.zip -d /tmp/design-<hash>/
```

**3. Read — in this order**
1. README at archive root (orientation: what screens exist, folder structure)
2. Target file from `open_file` / "Implement:" path

**4. Map to codebase before writing code**
- Colors → `zero-ui/src/.../theme/` tokens; never hardcode hex in Compose
- Spacing → match existing `dp` values in the file being implemented
- Components → check if a matching composable exists in `zero-ui/` first

## Rules

**Never write layout code before fetching** — "cards", "grid", "list" are ambiguous in prose; the file is the spec.

**README first** — it maps screen names to file paths and explains the folder structure; skipping it causes wrong-file reads.
