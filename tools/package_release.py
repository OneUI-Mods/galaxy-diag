#!/usr/bin/env python3
"""Build, sign and verify the release; keep credentials outside source and CI."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ.get('ANDROID_HOME', Path.home() / 'Library/Android/sdk'))
TOOLS = SDK / 'build-tools/37.0.0'
PACKAGE = 'dev.ryuya.galaxydiag'


def run(*args, capture=False):
    return subprocess.run([str(x) for x in args], cwd=ROOT, check=True, capture_output=capture, text=True)


def validate(apk):
    badging = run(TOOLS / 'aapt2', 'dump', 'badging', apk, capture=True).stdout
    assert f"name='{PACKAGE}'" in badging and "targetSdkVersion:'37'" in badging
    assert 'application-debuggable' not in badging
    manifest = run(TOOLS / 'aapt2', 'dump', 'xmltree', apk, '--file', 'AndroidManifest.xml', capture=True).stdout.lower()
    assert not any(name in manifest for name in ['testactivity', 'probeactivity', 'fixtureactivity', 'instrumentation', 'android.permission.internet', 'android.permission.foreground_service'])
    with zipfile.ZipFile(apk) as archive:
        assert not any('.signing' in n or n.endswith('.keystore') for n in archive.namelist())
        assert 'assets/legal/libraries.json' in archive.namelist()
    run(TOOLS / 'zipalign', '-c', '-P', '16', '4', apk)
    return badging


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check-release-apk', type=Path)
    parser.add_argument('--source-ref', default='HEAD')
    args = parser.parse_args()
    if args.check_release_apk:
        validate(args.check_release_apk.resolve())
        print('Release structure verified.')
        return
    run('git', 'diff', '--exit-code', args.source_ref, '--', '.')
    assert not run('git', 'ls-files', '--others', '--exclude-standard', capture=True).stdout, 'Commit release sources first'
    run(ROOT / 'gradlew', ':app:assembleRelease', ':app:lintRelease', ':app:testDebugUnitTest', ':app:writeRuntimeDependencies')
    run('python3', ROOT / 'tools/check_license_catalog.py')
    output = ROOT / 'app/build/outputs/apk/release'
    metadata = json.loads((output / 'output-metadata.json').read_text())
    assert metadata['variantName'] == 'release' and len(metadata['elements']) == 1
    item = metadata['elements'][0]
    version = item['versionName']
    unsigned = output / item['outputFile']
    validate(unsigned)
    dist = ROOT / 'dist'; dist.mkdir(exist_ok=True)
    apk = dist / f'GalaxyDiag-{version}-release.apk'
    with tempfile.TemporaryDirectory() as tmp:
        aligned = Path(tmp) / 'aligned.apk'
        run(TOOLS / 'zipalign', '-P', '16', '-f', '4', unsigned, aligned)
        run(TOOLS / 'apksigner', 'sign', '--ks', ROOT / '.signing/release.keystore', '--ks-key-alias', 'galaxy-diag', '--ks-pass', 'file:' + str(ROOT / '.signing/password'), '--debuggable-apk-permitted', 'false', '--out', apk, aligned)
    signature = run(TOOLS / 'apksigner', 'verify', '--verbose', '--print-certs', apk, capture=True).stdout
    assert re.findall(r'certificate SHA-256 digest: ([0-9a-f]+)', signature) == [(ROOT / 'signing-certificate.sha256').read_text().strip()]
    badging = validate(apk)
    report = dist / f'GalaxyDiag-{version}-signature.txt'; report.write_text(signature)
    source = dist / f'GalaxyDiag-{version}-source.zip'
    run('git', 'archive', '--format=zip', '--prefix=GalaxyDiag-' + version + '/', '--output=' + str(source), args.source_ref)
    public = dist / 'release-metadata.json'
    public.write_text(json.dumps({'schema': 1, 'packageName': PACKAGE, 'versionCode': item['versionCode'], 'versionName': version, 'apk': apk.name, 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest()}, indent=2) + '\n')
    files = [apk, source, report, public]
    (dist / f'SHA256SUMS-{version}.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name + '\n' for p in files))
    print('Signed release, matching sources, public metadata and hashes verified.')


if __name__ == '__main__':
    main()
