#!/usr/bin/env python3
"""Validate the shipped archive on Ubuntu 24.04, with isolated user data."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import uuid

ROOT = Path(__file__).resolve().parents[1]
archive = Path(sys.argv[1]).resolve()
version = (ROOT / 'VERSION').read_text().strip()
os_release = Path('/etc/os-release').read_text()
assert 'ID=ubuntu\n' in os_release and 'VERSION_ID="24.04"' in os_release, 'Validate on Ubuntu 24.04'
sandbox = ROOT / '.runtime/linux-validation' / uuid.uuid4().hex
sandbox.mkdir(parents=True)
with tarfile.open(archive) as bundle:
    bundle.extractall(sandbox, filter='data')
app = sandbox / f'GoTrick-{version}'
env = dict(os.environ, XDG_CONFIG_HOME=str(sandbox / 'Config ação'),
           XDG_DATA_HOME=str(sandbox / 'Data ação'), XDG_CACHE_HOME=str(sandbox / 'Cache ação'))

def run(args, **kwargs):
    return subprocess.check_output([str(arg) for arg in args], env=env, text=True, timeout=180, **kwargs)

assert not (app / 'tools/go').exists()
assert not list(app.rglob('gotrick-sdk.xml'))
assert os.access(app / 'bin/gotrick', os.X_OK)
native = run([app / 'bin/gotrick', '--version'])
assert f'GoTrick {version}' in native, native
run([app / 'install.sh'])
desktop = Path(env['XDG_DATA_HOME']) / 'applications/gotrick.desktop'
run(['desktop-file-validate', desktop])
assert 'StartupWMClass=gotrick' in desktop.read_text()
properties = Path(run([app / 'bin/gotrick', '--prepare-only']).strip()).read_text()
assert 'gotrick.tools=' in properties and 'plugin.path=' in properties and '\\u00e7' in properties
run([app / 'uninstall.sh'])
assert not desktop.exists()
assert Path(env['XDG_CONFIG_HOME']).exists()
classes = sandbox / 'classes'
classes.mkdir()
classpath = f'{app}/ide/lib/*:{app}/plugins/plugin/lib/*'
run(['javac', '-cp', classpath, '-d', classes, ROOT / 'scripts/BrandingProbe.java', ROOT / 'scripts/SdkDownloadProbe.java'])
java = app / 'ide/jbr/bin/java'
brand_cp = f'{app}/ide/lib/gotrick-branding.jar:{classpath}:{classes}'
print(run([java, '-Djava.awt.headless=true', '-Didea.platform.prefix=Idea', '-Dintellij.platform.load.app.info.from.resources=true',
           '-Dide.no.platform.update=GoTrick', f'-Didea.home.path={app}/ide', '-cp', brand_cp, 'BrandingProbe']), flush=True)
print(run([java, '-cp', f'{classpath}:{classes}', 'SdkDownloadProbe', sandbox / 'sdk']), flush=True)
smoke_tools = sandbox / 'tools'
smoke_tools.mkdir()
sdk = next((sandbox / 'sdk').glob('go*'))
(smoke_tools / 'go').symlink_to(sdk, target_is_directory=True)
(smoke_tools / 'gotools').symlink_to(app / 'tools/gotools', target_is_directory=True)
env['GOTRICK_SMOKE_TOOLS'] = str(smoke_tools)
print(run(['node', ROOT / 'scripts/smoke.mjs']), flush=True)
with archive.open('rb') as source:
    sha = hashlib.file_digest(source, 'sha256').hexdigest()
environment = 'Ubuntu 24.04 on WSL2' if 'microsoft' in Path('/proc/version').read_text().lower() else 'Ubuntu 24.04 Linux'
report = dict(passed=True, version=version, platform='ubuntu-24.04-x64', environment=environment,
              archiveSha256=sha, nativeVersion=native.strip(), goSdkBundled=False, sdkDownloadVerified=True,
              protocolChecks=11, desktopEntryVerified=True, guiVerified=False)
(ROOT / 'validation').mkdir(exist_ok=True)
(ROOT / 'validation/linux.json').write_text(json.dumps(report, indent=2) + '\n')
print('Linux archive, native runtime, branding, SDK download, desktop registration and Go/LSP/DAP passed.', flush=True)
