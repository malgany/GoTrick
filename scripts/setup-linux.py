#!/usr/bin/env python3
"""Pinned Linux x64 development dependencies; never changes the global PATH."""
import hashlib
import json
import os
import platform
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.tools'
PACKAGES = [
    ('idea', 'https://download.jetbrains.com/idea/ideaIC-2025.2.6.3.tar.gz', 'f9bb0c6e7de99becbd738937dd521864ee2226965331222d51fb3d921575fb00'),
    ('go', 'https://go.dev/dl/go1.27.2.linux-amd64.tar.gz', 'ecbadb99091a3f46e31f5f934b068b1864eafa7995211b39eaddf76996045fe5'),
    ('gradle', 'https://services.gradle.org/distributions/gradle-8.14.3-bin.zip', 'bd71102213493060956ec229d946beee57158dbd89d0e62b91bca0fa2c5f3531'),
    ('lsp4ij', 'https://github.com/redhat-developer/lsp4ij/releases/download/0.21.0/lsp4ij-0.21.0.zip', 'dd3d0024a3f160e6bb28164314f5a5af80700fa2d491d853c64d309bd42b8d43'),
]

def sha256(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()

def install(name, url, digest):
    destination = TOOLS / name
    marker = destination / '.gotrick-package.json'
    if marker.exists() and json.loads(marker.read_text())['sha256'] == digest:
        print(f'{name}: ready', flush=True)
        return
    if destination.exists():
        raise RuntimeError(f'Preserve or move the existing unmanaged dependency before retrying: {destination}')
    archive = TOOLS / 'downloads' / url.rsplit('/', 1)[1]
    archive.parent.mkdir(parents=True, exist_ok=True)
    if not archive.exists():
        partial = archive.with_name(archive.name + '.partial')
        print(f'Downloading {name}', flush=True)
        subprocess.run(['curl', '-fL', '--retry', '3', '--silent', '--show-error', '-o', str(partial), url], check=True)
        if sha256(partial) != digest:
            raise RuntimeError(f'Checksum mismatch: {name}')
        partial.rename(archive)
    if sha256(archive) != digest:
        raise RuntimeError(f'Cached checksum mismatch: {name}')
    print(f'Extracting {name}', flush=True)
    with tempfile.TemporaryDirectory(prefix='.extract-', dir=TOOLS) as temporary:
        stage = Path(temporary)
        if archive.name.endswith('.tar.gz'):
            with tarfile.open(archive) as source:
                source.extractall(stage, filter='data')
        else:
            with zipfile.ZipFile(archive) as source:
                source.extractall(stage)
        children = list(stage.iterdir())
        payload = children[0] if len(children) == 1 and children[0].is_dir() else stage
        shutil.move(str(payload), destination)
    marker.write_text(json.dumps({'url': url, 'sha256': digest}) + '\n')
    if name == 'gradle':
        (destination / 'bin/gradle').chmod(0o755)

def main():
    if platform.system() != 'Linux' or platform.machine() != 'x86_64':
        raise SystemExit('This bootstrap targets Linux x86_64.')
    for package in PACKAGES:
        install(*package)
    env = dict(os.environ, GOTOOLCHAIN='local', CGO_ENABLED='0',
               GOPATH=str(TOOLS / 'go-work'), GOCACHE=str(TOOLS / 'go-cache'),
               GOBIN=str(TOOLS / 'gotools'), PATH=str(TOOLS / 'go/bin') + os.pathsep + os.environ['PATH'])
    for binary, module in [('gopls', 'golang.org/x/tools/gopls@v0.24.0'), ('dlv', 'github.com/go-delve/delve/cmd/dlv@v1.27.2')]:
        metadata = TOOLS / 'gotools' / (binary + '.json')
        if metadata.exists() and (TOOLS / 'gotools' / binary).exists() and json.loads(metadata.read_text()).get('module') == module:
            continue
        subprocess.run([str(TOOLS / 'go/bin/go'), 'install', module], env=env, check=True)
        metadata.write_text(json.dumps({'module': module, 'sha256': sha256(TOOLS / 'gotools' / binary)}) + '\n')
    print('Linux dependencies ready. Build requires OpenJDK 21; protocol checks require Node.js.', flush=True)

if __name__ == '__main__':
    main()
