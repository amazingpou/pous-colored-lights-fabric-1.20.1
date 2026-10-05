"""Package only our source/artifact; never include saves, credentials or third-party runtimes."""
from pathlib import Path
import hashlib, shutil, zipfile

ROOT = Path(__file__).resolve().parents[1]

def main():
    out = ROOT / 'dist'
    out.mkdir(exist_ok=True)
    artifact = ROOT / 'fabric/build/libs/pous-colored-lights-3.5.0.jar'
    jar = out / 'pous-colored-lights-fabric-1.20.1-3.5.0.jar'
    shutil.copy2(artifact, jar)
    source = out / 'pous-colored-lights-fabric-1.20.1-3.5.0-source.zip'
    directories = ('core', 'common', 'fabric', 'gradle', 'scripts', 'tools', 'referencias', 'validation')
    excluded = {'.git', '.gradle', 'build', '__pycache__'}
    selected = {p for p in ROOT.iterdir() if p.is_file() and (p.suffix in ('.md', '.gradle', '.properties') or p.name in ('LICENSE', '.gitignore', 'gradlew', 'gradlew.bat'))}
    for directory in directories:
        for path in (ROOT / directory).rglob('*'):
            if not path.is_file() or excluded.intersection(path.relative_to(ROOT).parts):
                continue
            if directory == 'validation' and path.suffix != '.md':
                continue
            if directory == 'referencias' and path.name not in ('README.md', 'mod-icon.png'):
                continue
            if path.suffix in ('.log', '.pyc', '.csv'):
                continue
            selected.add(path)
    with zipfile.ZipFile(source, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(selected):
            archive.write(path, 'fabric-1.20.1/' + path.relative_to(ROOT).as_posix())
    with zipfile.ZipFile(source) as archive:
        assert archive.testzip() is None
        assert 'fabric-1.20.1/gradle/wrapper/gradle-wrapper.jar' in archive.namelist()
        assert not any('/saves/' in name or '/compatibility/' in name for name in archive.namelist())
    (out / 'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name + '\n' for p in (jar, source)), encoding='utf-8')
    print(f'JAR {jar.name}: {jar.stat().st_size} bytes')
    print(f'SOURCE {source.name}: {source.stat().st_size} bytes, {len(selected)} files')

if __name__ == '__main__':
    main()
