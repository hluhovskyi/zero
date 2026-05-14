#!/bin/bash
# Assigns an unclaimed running emulator to this worktree.
# Scans all active worktrees for .emulator-serial to build the claimed set,
# then picks the first running emulator not in that set.
# Writes the assigned serial to .emulator-serial in the current directory.
#
# If all running emulators are claimed, auto-invokes start-emulator.sh to add
# capacity, then claims the new instance.
# Pass --no-auto-start to disable auto-start and fail instead.
#
# RACE SAFETY: concurrent invocations from different worktrees are serialised
# by a mkdir-based mutex in /tmp.  mkdir(2) is atomic on all Unix filesystems,
# so only one process can own the lock at a time.  A stale-lock guard (PID
# liveness check + age limit) prevents a crashed holder from blocking forever.

AUTO_START=true
for arg in "$@"; do
  [[ "$arg" == "--no-auto-start" ]] && AUTO_START=false
done

# ---------------------------------------------------------------------------
# Mutex helpers
# ---------------------------------------------------------------------------
REPO_SLUG=$(git rev-parse --show-toplevel 2>/dev/null | tr '/' '_')
LOCK_DIR="/tmp/zero-emulator-claim${REPO_SLUG}.lock"
LOCK_TIMEOUT=300   # seconds to wait for the lock before giving up
LOCK_MAX_AGE=300   # seconds: locks older than this are presumed stale (must exceed emulator boot time)

_lock_acquire() {
    local deadline=$(( $(date +%s) + LOCK_TIMEOUT ))
    while true; do
        # Atomic create: succeeds only if directory does not yet exist.
        if mkdir "$LOCK_DIR" 2>/dev/null; then
            echo $$ > "$LOCK_DIR/pid"
            return 0
        fi

        # Stale-lock recovery 1: owning PID is no longer alive.
        local pid_file="$LOCK_DIR/pid"
        if [ -f "$pid_file" ]; then
            local lock_pid
            lock_pid=$(cat "$pid_file" 2>/dev/null)
            if [ -n "$lock_pid" ] && ! kill -0 "$lock_pid" 2>/dev/null; then
                rm -rf "$LOCK_DIR"
                continue
            fi
        fi

        # Stale-lock recovery 2: lock dir is older than LOCK_MAX_AGE (belt+suspenders).
        if [ -d "$LOCK_DIR" ]; then
            local mtime
            mtime=$(stat -f %m "$LOCK_DIR" 2>/dev/null || stat -c %Y "$LOCK_DIR" 2>/dev/null || echo 0)
            local age=$(( $(date +%s) - mtime ))
            if [ "$age" -gt "$LOCK_MAX_AGE" ]; then
                rm -rf "$LOCK_DIR"
                continue
            fi
        fi

        if [ "$(date +%s)" -ge "$deadline" ]; then
            echo "ERROR: timed out waiting for emulator claim lock after ${LOCK_TIMEOUT}s" >&2
            exit 1
        fi
        sleep 0.2
    done
}

_lock_release() {
    rm -rf "$LOCK_DIR"
}

# ---------------------------------------------------------------------------
# Claim logic (must run inside the mutex)
# ---------------------------------------------------------------------------
claim_unused() {
    # Get all running emulators first — needed for stale-claim pruning below.
    local RUNNING=()
    while IFS= read -r line; do
        local SERIAL
        SERIAL=$(echo "$line" | awk '{print $1}')
        [[ "$SERIAL" == emulator-* ]] && RUNNING+=("$SERIAL")
    done < <(adb devices | tail -n +2)

    if [ ${#RUNNING[@]} -eq 0 ]; then
        echo "__NONE_RUNNING__"
        return 1
    fi

    # Collect claimed serials from all active worktrees.
    # Prune any claim whose emulator is no longer running — these are
    # left-over .emulator-serial files from previous sessions where the
    # emulator has since been shut down.
    local CLAIMED=()
    while IFS= read -r WORKTREE_PATH; do
        local SERIAL_FILE="$WORKTREE_PATH/.emulator-serial"
        [ -f "$SERIAL_FILE" ] || continue
        local SERIAL
        SERIAL=$(cat "$SERIAL_FILE" 2>/dev/null | tr -d '[:space:]')
        [ -n "$SERIAL" ] || continue

        local IS_RUNNING=false
        for R in "${RUNNING[@]}"; do
            [[ "$R" == "$SERIAL" ]] && IS_RUNNING=true && break
        done

        if $IS_RUNNING; then
            CLAIMED+=("$SERIAL")
        else
            # Emulator is gone — the claim is stale; remove it automatically.
            rm -f "$SERIAL_FILE"
            echo "  Pruned stale claim: $SERIAL from $(basename "$WORKTREE_PATH") (emulator not running)" >&2
        fi
    done < <(git worktree list --porcelain | grep "^worktree " | sed 's/^worktree //')

    for SERIAL in "${RUNNING[@]}"; do
        local CLAIMED_FLAG=false
        for C in "${CLAIMED[@]}"; do
            [[ "$C" == "$SERIAL" ]] && CLAIMED_FLAG=true && break
        done
        if ! $CLAIMED_FLAG; then
            echo "$SERIAL" > .emulator-serial
            echo "Assigned $SERIAL to this worktree"
            return 0
        fi
    done

    echo "__ALL_CLAIMED__ ${RUNNING[*]}"
    return 1
}

# ---------------------------------------------------------------------------
# Phase 1: try to claim an already-running emulator.
# ---------------------------------------------------------------------------
_lock_acquire
trap _lock_release EXIT INT TERM

OUTPUT=$(claim_unused)
RC=$?

if [ $RC -eq 0 ]; then
    # Claim written inside claim_unused while lock was held.
    _lock_release
    trap - EXIT INT TERM
    echo "$OUTPUT"
    exit 0
fi

# ---------------------------------------------------------------------------
# Phase 2: no unclaimed emulator — start a fresh one, then claim it.
# The lock is held throughout start-emulator.sh so concurrent sessions
# cannot race to pick the same port.  Sessions 2 and 3 will block in
# _lock_acquire until session 1 has both started the emulator AND written
# the claim, at which point they enter claim_unused and find it available.
# ---------------------------------------------------------------------------
if $AUTO_START; then
    echo "No free running emulator — starting a new one (use --no-auto-start to disable)..."
    if "$(dirname "$0")/start-emulator.sh" > /tmp/start-emulator.out 2>&1; then
        SERIAL=$(tail -1 /tmp/start-emulator.out)
        if [[ "$SERIAL" == emulator-* ]]; then
            echo "$SERIAL" > .emulator-serial
            _lock_release
            trap - EXIT INT TERM
            echo "Started and assigned $SERIAL to this worktree"
            exit 0
        fi
    fi
    echo "start-emulator.sh failed. Output:"
    cat /tmp/start-emulator.out
    exit 1
fi

# Auto-start disabled — print the diagnostic and suggested fix.
case "$OUTPUT" in
    __NONE_RUNNING__*)
        echo "No running emulators. Run: ./scripts/start-emulator.sh"
        ;;
    __ALL_CLAIMED__*)
        echo "All running emulators are claimed by other worktrees."
        echo "Run: ./scripts/start-emulator.sh"
        ;;
esac
exit 1
