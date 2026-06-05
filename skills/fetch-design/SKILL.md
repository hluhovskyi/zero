---
name: fetch-design
description: Use when the user pastes a Claude Design URL (https://api.anthropic.com/v1/design/h/...). Fetch and parse design assets before writing any layout code.
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

Use the helper script (pre-approved, no permission prompt):

```bash
./scripts/fetch-design.sh <hash>
```

This downloads to `/tmp/design-<hash>.tar.gz` and extracts to `/tmp/design-<hash>/`.

**3. Read — in this order**
1. README at the archive root (tells you what screens exist and what each folder contains)
2. Target file from `open_file` / "Implement:" path

**4. Map to codebase before writing code**
- Colors → `zero-ui/src/.../theme/` tokens; never hardcode hex in Compose
- Spacing → match existing `dp` values in the file being implemented
- Components → check if a matching composable exists in `zero-ui/` first

## Rules

**Never write layout code before fetching** — "cards", "grid", "list" are ambiguous in prose; the file is the authoritative spec.

**Check the exploration `.jsx`, not just the named file** — `index.html` inlines simplified placeholders; a component's real spec lives in its sibling exploration file. Read both.

**README first** — it maps screen names to file paths; skipping it causes wrong-file reads.

**Skip `chats/` entirely** — these are design iteration logs, not layout specs; reading one will overflow context before implementation starts.
