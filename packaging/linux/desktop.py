#!/usr/bin/env python3
"""Register this extracted installation without root privileges or copying projects."""
import os
from pathlib import Path
import shutil
import subprocess
import sys

root = Path(__file__).resolve().parent
applications = Path(os.environ.get('XDG_DATA_HOME', Path.home() / '.local/share')) / 'applications'
desktop = applications / 'gotrick.desktop'
if any(c in str(root) for c in '\n\r'):
    raise SystemExit('Install GoTrick in a folder without newlines in its name.')

def quoted(value):
    return '"' + str(value).replace('\\', '\\\\').replace('"', '\\"').replace('`', '\\`').replace('$', '\\$').replace('%', '%%') + '"'

entry = '\n'.join([
    '[Desktop Entry]', 'Type=Application', 'Name=GoTrick', 'Comment=Go development environment',
    'Exec=' + quoted(root / 'bin/gotrick') + ' %f',
    'Icon=' + str(root / 'ide/bin/gotrick.svg').replace('\\', '\\\\'),
    'Terminal=false', 'Categories=Development;IDE;', 'StartupWMClass=gotrick', '',
])
if sys.argv[1:] == ['install']:
    applications.mkdir(parents=True, exist_ok=True)
    if desktop.exists() and 'Name=GoTrick\n' not in desktop.read_text():
        raise SystemExit('An unrelated desktop entry already exists at ' + str(desktop))
    desktop.write_text(entry)
    print('GoTrick registered in the applications menu. Keep this folder: ' + str(root))
elif sys.argv[1:] == ['uninstall']:
    if desktop.exists() and desktop.read_text() == entry:
        desktop.unlink()
    print('Shortcut removed if it belonged to this installation. Application files, projects and preferences preserved.')
else:
    raise SystemExit('Expected install or uninstall')
if shutil.which('update-desktop-database') and applications.exists():
    subprocess.run(['update-desktop-database', str(applications)], check=True)
