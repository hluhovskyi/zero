# Branch Management

## Primary Branch Protection

The `master` branch is protected. **Never commit or push directly to `master`.**

### Workflow:
1.  **Create a feature branch** for every change (e.g., `feature/description`, `bugfix/description`, `docs/description`).
2.  **Submit a Pull Request (PR)** once the work is complete.
3.  **Merge the PR** into `master` after review and verification.

### Local Enforcement:
To prevent accidental commits and pushes, this project includes a tracked `.githooks/` directory.

#### Setup:
Run the following script to configure your local environment to use these hooks:
```bash
./setup-hooks.sh
```

This will set your `core.hooksPath` to `.githooks/`, making the `pre-commit` and `pre-push` checks active.
