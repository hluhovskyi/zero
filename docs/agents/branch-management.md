# Branch Management

## Primary Branch Protection

The `master` branch is protected. **Never commit or push directly to `master`.**

### Workflow:
1.  **Create a feature branch** for every change (e.g., `feature/description`, `bugfix/description`, `docs/description`).
2.  **Submit a Pull Request (PR)** once the work is complete.
3.  **Merge the PR** into `master` after review and verification.

### Local Enforcement:
To prevent accidental commits and pushes, adding local git hooks is recommended.

#### Pre-commit Hook (`.git/hooks/pre-commit`)
```bash
#!/bin/bash
protected_branch="master"
current_branch=$(git rev-parse --abbrev-ref HEAD)

if [ "$current_branch" = "$protected_branch" ]; then
    echo "ERROR: Direct commit to $protected_branch is not allowed."
    exit 1
fi

exit 0
```

#### Pre-push Hook (`.git/hooks/pre-push`)
```bash
#!/bin/bash
protected_branch="master"
current_branch=$(git rev-parse --abbrev-ref HEAD)

if [ "$current_branch" = "$protected_branch" ]; then
    echo "ERROR: Direct push to $protected_branch is not allowed."
    exit 1
fi

exit 0
```

Make sure to make these hooks executable:
```bash
chmod +x .git/hooks/pre-commit .git/hooks/pre-push
```
