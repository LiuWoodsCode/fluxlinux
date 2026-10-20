#!/bin/sh
# Installed as /etc/profile.d/flux-user.sh. Older guests default to flux.
if [ -z "${FLUX_USER:-}" ] && [ -r /etc/fluxlinux/username ]; then
    FLUX_USER=$(cat /etc/fluxlinux/username)
fi
FLUX_USER="${FLUX_USER:-flux}"
case "$FLUX_USER" in
    ''|[!a-z_]*|*[!a-z0-9_-]*|root) echo "FluxLinux: Invalid guest username" >&2; exit 1 ;;
esac
[ "${#FLUX_USER}" -le 32 ] || { echo "FluxLinux: Username too long" >&2; exit 1; }
export FLUX_USER
