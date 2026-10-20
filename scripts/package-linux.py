#!/usr/bin/env python3
"""Create the Linux x64 distribution from a clean release commit."""
import argparse
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import tarfile
import uuid
import xml.etree.ElementTree as ET
import zipfile
import hashlib

ROOT = Path(__file__).resolve().parents[1]

def digest(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if platform.system() != 'Linux' or platform.machine() != 'x86_64':
        raise SystemExit('Package on Linux x86_64 to preserve executable permissions and symlinks.')
    version = (ROOT / 'VERSION').read_text().strip()
    assert re.fullmatch(r'\d+\.\d+\.\d+', version), 'Invalid VERSION'
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    if subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True).strip():
        raise SystemExit('Commit or preserve pending changes before packaging.')
    output = (args.output or ROOT.parent / 'GoTrick-Releases' / version).resolve()
    output.mkdir(parents=True, exist_ok=True)
    archive = output / f'GoTrick-{version}-ubuntu-24.04-x64.tar.gz'
    if archive.exists():
        raise SystemExit('Artifact already exists; use a fresh output directory.')
    tools = ROOT / '.tools'
    source = tools / 'idea'
    stage = ROOT / '.runtime/packages' / uuid.uuid4().hex / f'GoTrick-{version}'
    ide = stage / 'ide'
    # Fresh copies: branding must never modify a shared runtime or hard link.
    shutil.copytree(source, ide, symlinks=True, ignore=shutil.ignore_patterns('.gotrick-*'))
    product = json.loads((ide / 'product-info.json').read_text())
    assert product['productCode'] == 'IC' and product['buildNumber'] == '252.28539.97'
    launch = next(item for item in product['launch'] if item['os'] == 'Linux' and item['arch'] == 'amd64')
    shutil.copy2(ide / launch['launcherPath'], ide / 'bin/gotrick')
    shutil.copy2(ide / launch['vmOptionsFilePath'], ide / 'bin/gotrick.vmoptions')
    ET.register_namespace('', 'http://jetbrains.org/intellij/schema/application-info')
    identity = ET.parse(ROOT / 'branding/GoTrickApplicationInfo.xml')
    identity.find('{*}version').attrib.update(dict(zip(('major', 'minor', 'micro'), version.split('.'))))
    with zipfile.ZipFile(ide / 'lib/gotrick-branding.jar', 'w', zipfile.ZIP_DEFLATED) as brand:
        brand.writestr('idea/IdeaApplicationInfo.xml', ET.tostring(identity.getroot(), encoding='utf-8'))
        with zipfile.ZipFile(source / 'lib/app-client.jar') as upstream:
            brand.writestr('consents-GoTrick.json', upstream.read('consents.json'))
        for item in (ROOT / 'branding/assets').iterdir():
            if item.is_file():
                brand.write(item, 'gotrick/' + item.name)
    shutil.copy2(ROOT / 'branding/assets/icon.svg', ide / 'bin/gotrick.svg')
    product.update(name='GoTrick', version=version, productVendor='GoTrick', dataDirectoryName='GoTrick', svgIconPath='bin/gotrick.svg')
    launch.update(launcherPath='bin/gotrick', vmOptionsFilePath='bin/gotrick.vmoptions', startupWmClass='gotrick')
    launch['bootClassPathJarNames'].insert(0, 'gotrick-branding.jar')
    launch['additionalJvmArguments'] = [
        re.sub(r'^-Didea.vendor.name=.*', '-Didea.vendor.name=GoTrick',
               re.sub(r'^-Didea.paths.selector=.*', '-Didea.paths.selector=GoTrick', arg))
        for arg in launch['additionalJvmArguments']
    ] + ['-Dintellij.platform.load.app.info.from.resources=true', '-Dide.no.platform.update=GoTrick', '-Dsun.awt.X11.XWMClass=gotrick']
    (ide / 'product-info.json').write_text(json.dumps(product, indent=2) + '\n')
    plugin = ROOT / f'plugin/build/distributions/plugin-{version}.zip'
    with zipfile.ZipFile(plugin) as source_plugin:
        source_plugin.extractall(stage / 'plugins')
    shutil.copytree(tools / 'lsp4ij', stage / 'plugins/lsp4ij', ignore=shutil.ignore_patterns('.gotrick-*'))
    (stage / 'tools/gotools').mkdir(parents=True)
    shutil.copytree(ROOT / 'licenses', stage / 'licenses')
    for name in ('gopls', 'dlv'):
        binary = tools / 'gotools' / name
        assert binary.read_bytes()[:4] == b'\x7fELF', 'Linux helper required'
        shutil.copy2(binary, stage / 'tools/gotools' / name)
        shutil.copy2(binary.with_suffix('.json'), stage / 'tools/gotools' / (name + '.json'))
        lines = subprocess.check_output([str(tools / 'go/bin/go'), 'version', '-m', str(binary)], text=True).splitlines()
        lines[0] = name + ': ' + lines[0].split(': ', 1)[-1]
        (stage / 'licenses' / (name + '-modules.txt')).write_text('\n'.join(lines) + '\n')
    modules = tools / 'go-work/pkg/mod'
    for item in modules.rglob('*'):
        if item.is_file() and 'cache' not in item.relative_to(modules).parts and re.match(r'^(LICENSE|COPYING|NOTICE|AUTHORS|PATENTS)(\..*)?$', item.name):
            target = stage / 'licenses/go-modules' / item.relative_to(modules)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(item, target)
    shutil.copy2(tools / 'go/LICENSE', stage / 'licenses/Go-LICENSE')
    for name in ('LICENSE', 'NOTICE', 'VERSION', 'README.md'):
        shutil.copy2(ROOT / name, stage / name)
    shutil.copy2(ROOT / 'docs/THIRD-PARTY.md', stage / 'THIRD-PARTY-NOTICES.md')
    (stage / 'bin').mkdir()
    shutil.copy2(ROOT / 'packaging/linux/gotrick', stage / 'bin/gotrick')
    for name in ('install.sh', 'uninstall.sh', 'desktop.py'):
        shutil.copy2(ROOT / 'packaging/linux' / name, stage / name)
        (stage / name).chmod(0o755)
    (stage / 'bin/gotrick').chmod(0o755)
    manifest = dict(name='GoTrick', version=version, sourceCommit=commit, platform='ubuntu-24.04-x64',
                    channel='preview', goSdkBundled=False, base='IC-252.28539.97',
                    gopls='v0.24.0', delve='v1.27.2', lsp4ij='0.21.0', signed=False, pluginSha256=digest(plugin))
    (stage / 'release-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    shutil.copy2(stage / 'release-manifest.json', output / 'release-manifest-ubuntu-24.04-x64.json')
    notes = (ROOT / f'docs/releases/{version}.md').read_text() + f'\n## Revisão empacotada\n\nCommit: {commit}.\n'
    (output / 'RELEASE-NOTES.md').write_text(notes)
    # Never include a build SDK, personal configuration or build cache.
    assert not (stage / 'tools/go').exists()
    assert not list(stage.rglob('gotrick-sdk.xml'))
    print('Compressing ' + str(archive), flush=True)
    with tarfile.open(archive, 'w:gz', compresslevel=3) as bundle:
        bundle.add(stage, arcname=stage.name)
    print('Package: ' + str(archive), flush=True)
    print('Stage: ' + str(stage), flush=True)

if __name__ == '__main__':
    main()
