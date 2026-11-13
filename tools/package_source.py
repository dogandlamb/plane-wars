"""Export a clean, complete source tree and its one intentional signed APK."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def collect():
    files = {}
    for folder in ('app/src', 'shared/src', 'server/src', 'gradle/wrapper'):
        for path in (ROOT / folder).rglob('*'):
            if path.is_file():
                files[path.relative_to(ROOT).as_posix()] = path.read_bytes()
    for name in ('.gitattributes', 'README.md', 'BUILDING.md', 'build.gradle', 'settings.gradle',
                 'gradle.properties', 'gradlew', 'gradlew.bat', 'app/build.gradle',
                 'app/proguard-rules.pro', 'shared/build.gradle', 'server/build.gradle',
                 'server/Dockerfile', 'server/README.md', 'server/build-server.ps1',
                 'tools/verify.ps1', 'tools/synthesize_audio.py', 'tools/import_radio.py',
                 'tools/package_source.py', 'tools/github.gitignore',
                 'docs/implementation-contract.md', 'docs/refactor-review.md',
                 'docs/acceptance-report.md', 'docs/music-sources.md',
                 'release/SkySquad-2.1.apk', 'release/SHA256SUMS.txt'):
        files[name] = (ROOT / name).read_bytes()
    # Do not change the user's existing staged .gitignore in the working checkout.
    files['.gitignore'] = (ROOT / 'tools/github.gitignore').read_bytes()
    for name, data in files.items():
        path = Path(name)
        if any(p in {'.git', '.artifacts', 'build', '.gradle', '__pycache__'} for p in path.parts):
            raise ValueError('Unintended generated path: ' + name)
        if path.name in {'local.properties', '.env'} or path.suffix.lower() in {'.jks', '.keystore', '.p12', '.pem'}:
            raise ValueError('Private file: ' + name)
        if path.suffix.lower() in {'.java', '.gradle', '.md', '.properties', '.py', '.ps1', '.txt'}:
            text = data.decode('utf-8', errors='replace')
            if re.search(r'(?:storePassword|keyPassword)\s*[=:]?\s*[\x22\x27]', text):
                raise ValueError('Literal signing secret: ' + name)
    apk_hash = sha(files['release/SkySquad-2.1.apk'])
    if apk_hash not in files['release/SHA256SUMS.txt'].decode():
        raise ValueError('APK checksum is stale')
    if apk_hash not in files['docs/acceptance-report.md'].decode('utf-8'):
        raise ValueError('Report does not identify this APK')
    return files


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stage', type=Path, help='Export a directory for independent rebuild verification')
    parser.add_argument('--output', type=Path, help='Create the final ZIP')
    args = parser.parse_args()
    if not args.stage and not args.output:
        parser.error('Specify --stage or --output')
    files = collect()
    git = lambda *a: subprocess.check_output(['git', *a], cwd=ROOT)
    try:
        anchor = git('rev-parse', 'HEAD').decode().strip()
        diff_hash = sha(git('diff', '--binary', 'HEAD'))
    except subprocess.CalledProcessError:
        anchor, diff_hash = None, None
    manifest = {'version': '2.1', 'commit_anchor': anchor, 'working_diff_sha256': diff_hash,
                'identity': 'These exported file hashes identify source, including uncommitted work.',
                'files': {name: sha(data) for name, data in sorted(files.items())}}
    files['SOURCE-MANIFEST.json'] = json.dumps(manifest, ensure_ascii=False, indent=2).encode('utf-8')
    if args.stage:
        destination = args.stage.resolve()
        destination.mkdir(parents=True, exist_ok=True)
        if any(destination.iterdir()):
            raise ValueError('Stage directory must be empty')
        for name, data in files.items():
            target = destination / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        print('Exported ' + str(destination))
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(args.output, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as bundle:
            for name, data in sorted(files.items()):
                entry = zipfile.ZipInfo('SkySquad/' + name, (2026, 11, 13, 0, 0, 0))
                entry.external_attr = (0o100755 if name == 'gradlew' else 0o100644) << 16
                entry.compress_type = zipfile.ZIP_DEFLATED
                bundle.writestr(entry, data)
        with zipfile.ZipFile(args.output) as bundle:
            if bundle.testzip():
                raise ValueError('ZIP CRC check failed')
            for name, data in files.items():
                if bundle.read('SkySquad/' + name) != data:
                    raise ValueError('ZIP content mismatch: ' + name)
        print(json.dumps({'file': str(args.output.resolve()), 'files': len(files),
                          'bytes': args.output.stat().st_size,
                          'sha256': sha(args.output.read_bytes())}, indent=2))


if __name__ == '__main__':
    main()
