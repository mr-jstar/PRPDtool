#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
VENV_DIR="$SCRIPT_DIR/.venv"

find_python() {
    if command -v python3 >/dev/null 2>&1; then
        printf '%s\n' "python3"
        return 0
    fi
    if command -v python >/dev/null 2>&1; then
        printf '%s\n' "python"
        return 0
    fi
    return 1
}

PYTHON=$(find_python) || {
    echo "Python 3 was not found. Install Python 3 and try again." >&2
    exit 1
}

"$PYTHON" -m venv "$VENV_DIR"
"$VENV_DIR/bin/python" -m pip install --upgrade pip
"$VENV_DIR/bin/python" -m pip install -r "$SCRIPT_DIR/requirements.txt"

echo "Run GUI with:"
echo "  $VENV_DIR/bin/python $SCRIPT_DIR/rp_prpd_receive_to_bin.py"
