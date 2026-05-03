# Branch Management

## Primary Branch Protection

The `master` branch is protected. **Never commit or push directly to `master`.**

### Workflow:
1.  **Create a feature branch** for every change (e.g., `feature/description`, `bugfix/description`, `docs/description`).
2.  **Submit a Pull Request (PR)** once the work is complete.
3.  **Merge the PR** into `master` after review and verification.

## PR Description Format

Keep PR descriptions short — two sections only:

**Background** (2–3 sentences): why this change exists — the problem or goal, not what the code does.

**Changes** (2–3 sentences): what the PR contains, with the most important highlights. Skip obvious details the diff already shows.

Example:
```
## Background
Category Detail is a new screen showing per-category spending stats and the filtered transaction list.
It required extending TransactionComponent to support a ForCategory filter and embedding it as a sub-component via AttachWithView.

## Changes
Adds CategoryDetailComponent, ViewModel, ViewProvider, and navigation wiring.
Extends TransactionComponent with TransactionFilter and DisplayConfig for reuse across contexts.
Also ships the scaffold-feature skill that generates structural stubs for future features.
```

### Local Enforcement:
To prevent accidental commits and pushes, this project includes a tracked `.githooks/` directory.

#### Setup:
Run the following script to configure your local environment to use these hooks:
```bash
./setup-hooks.sh
```

This will set your `core.hooksPath` to `.githooks/`, making the `pre-commit` and `pre-push` checks active.
