---
name: fetch-design
description: Use when the user pastes a Claude Design URL (https://api.anthropic.com/v1/design/h/...) or says "fetch this design file". Immediately fetch and read the design assets before writing any layout code or making any layout decisions. Use this skill whenever you see that URL pattern, even if the user's main request is about implementation rather than explicitly about fetching.
---

# Fetch Design

## Trigger

URL matching: `https://api.anthropic.com/v1/design/h/<hash>[?open_file=<encoded-path>]`

The user may also write "Implement: <path>" — that's the primary file to read.

## Steps

**1. Parse the URL**
- Hash: segment after `/h/`
- Target file: URL-decode the `open_file` param (`%2F` → `/`)

**2. Download + extract**

The endpoint requires API key authentication — use curl directly:

```bash
curl -s \
  -H "x-api-key: $ANTHROPIC_API_KEY" \
  -H "anthropic-version: 2023-06-01" \
  "https://api.anthropic.com/v1/design/h/<hash>" \
  -o /tmp/design-<hash>.zip
unzip -q /tmp/design-<hash>.zip -d /tmp/design-<hash>/
```

**3. Read — in this order**
1. README at the archive root (tells you what screens exist and what each folder contains)
2. Target file from `open_file` / "Implement:" path

**4. Map to codebase before writing code**
- Colors → `zero-ui/src/.../theme/` tokens; never hardcode hex in Compose
- Spacing → match existing `dp` values in the file being implemented
- Components → check if a matching composable exists in `zero-ui/` first

## Rules

**Never write layout code before fetching** — "cards", "grid", "list" are ambiguous in prose; the file is the authoritative spec.

**README first** — it maps screen names to file paths; skipping it causes wrong-file reads.
