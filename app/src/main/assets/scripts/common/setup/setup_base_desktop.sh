#!/bin/sh
[ ! -r /etc/profile.d/flux-user.sh ] || . /etc/profile.d/flux-user.sh
FLUX_USER="${FLUX_USER:-flux}"
# First-install defaults only. Desktop/shell theming is an optional component.
set -eu

USER_HOME=/home/${FLUX_USER}
XFCONF_DIR="$USER_HOME/.config/xfce4/xfconf/xfce-perchannel-xml"
WALLPAPER_PATH=/usr/share/backgrounds/KasaneTeto.PNG

[ -s "$WALLPAPER_PATH" ] || { echo "FluxLinux: Default wallpaper missing" >&2; exit 1; }
mkdir -p "$XFCONF_DIR" /etc/fluxlinux

# Seed once; future launches and retries keep the user's desktop settings.
if [ ! -f /etc/fluxlinux/base-desktop ]; then
    {
        cat <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xfce4-desktop" version="1.0">
  <property name="backdrop" type="empty">
    <property name="single-workspace-mode" type="bool" value="true"/>
    <property name="single-workspace-number" type="int" value="0"/>
    <property name="screen0" type="empty">
EOF
        # Xlorie uses builtin; include XFCE's indexed and VNC output names too.
        for monitor in monitor0 monitor1 monitorbuiltin monitorscreen monitorVNC-0 monitorHDMI-A-0 monitorVirtual-0 monitorVirtual1; do
            cat <<EOF
      <property name="$monitor" type="empty">
        <property name="workspace0" type="empty">
          <property name="last-image" type="string" value="$WALLPAPER_PATH"/>
          <!-- XFCE Zoomed (5): preserve aspect ratio and crop to fill. -->
          <property name="image-style" type="int" value="5"/>
        </property>
      </property>
EOF
        done
        cat <<'EOF'
    </property>
  </property>
</channel>
EOF
    } > "$XFCONF_DIR/xfce4-desktop.xml"

    # Keep the existing GPU compatibility default without applying a WM theme.
    if [ ! -f "$XFCONF_DIR/xfwm4.xml" ]; then
        cat > "$XFCONF_DIR/xfwm4.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xfwm4" version="1.0">
  <property name="general" type="empty">
    <property name="use_compositing" type="bool" value="false"/>
  </property>
</channel>
EOF
    fi
fi

# PRoot homes belong to the Android app UID; chroot homes belong to guest flux.
# Match the home rather than forcing guest UID 1000 onto a PRoot config.
home_uid=$(stat -c %u "$USER_HOME" 2>/dev/null || stat -f %u "$USER_HOME")
home_gid=$(stat -c %g "$USER_HOME" 2>/dev/null || stat -f %g "$USER_HOME")
chown -R "$home_uid:$home_gid" "$USER_HOME/.config/xfce4" 2>/dev/null || true
chmod -R u+rwX "$USER_HOME/.config/xfce4"

# Session repair must not reintroduce Flux shell customizations on base installs.
touch /etc/fluxlinux/base-desktop
echo "FluxLinux: Base desktop ready (Kasane Teto wallpaper, fill)"
