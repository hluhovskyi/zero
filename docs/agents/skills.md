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

## Plugin loader trap

The Claude Code plugin loader resolves plugins by looking for a directory at `<marketplace installLocation>/plugins/<plugin-name>/`. The `source` field in `marketplace.json` and `enabledPlugins` in `settings.json` are **necessary but not sufficient** — the directory must physically exist at that path (symlink is fine).

For this project: `installLocation` = `.claude/marketplace/`, so the plugin must be reachable at `.claude/marketplace/plugins/zero-project/` (already a symlink to `.claude/plugins/zero-project/`).

If a skill isn't loading after `/reload-plugins`, run `/doctor` — the error will name exactly which plugin can't be found and in which marketplace.
