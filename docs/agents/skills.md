# Skills

Project-specific skills for Claude and Gemini agents live in `skills/` and are symlinked into each agent's plugin tree to maintain a single source of truth.

## Directory structure

```
skills/
└── <skill-name>/
    └── SKILL.md

.claude/plugins/zero-project/skills/<skill-name> → ../../../../skills/<skill-name>
```

## Adding a new skill

1. Create `skills/<skill-name>/SKILL.md`.
2. Symlink into the Claude plugin:
   ```
   ln -s ../../../../skills/<skill-name> .claude/plugins/zero-project/skills/<skill-name>
   ```
3. Symlink into the Gemini plugin if applicable (same pattern).
4. Run `/reload-plugins` in Claude Code to pick it up.

## Editing and committing skill files

**Edit skill files via their real path (`skills/<name>/SKILL.md`), not through the `.claude/marketplace/` symlink** — `git add` refuses to stage paths that traverse a symlink, so staging via `.claude/marketplace/plugins/zero-project/skills/<name>/SKILL.md` will always fail with "beyond a symbolic link". Use the root-relative path instead:

```bash
git add skills/<name>/SKILL.md
```

## Description length

Descriptions are always in context — keep them under 25 words. Name the trigger phrases/contexts only; all implementation detail belongs in the skill body.

## Plugin loader trap

The Claude Code plugin loader resolves plugins by looking for a directory at `<marketplace installLocation>/plugins/<plugin-name>/`. The `source` field in `marketplace.json` and `enabledPlugins` in `settings.json` are **necessary but not sufficient** — the directory must physically exist at that path (symlink is fine).

For this project: `installLocation` = `.claude/marketplace/`, so the plugin must be reachable at `.claude/marketplace/plugins/zero-project/` (already a symlink to `.claude/plugins/zero-project/`).

If a skill isn't loading after `/reload-plugins`, run `/doctor` — the error will name exactly which plugin can't be found and in which marketplace.
