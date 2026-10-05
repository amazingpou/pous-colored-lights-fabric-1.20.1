"""Verify the installable port against the approved reference, without launching it."""
from pathlib import Path
import hashlib, json, struct, zipfile
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[1]
REFERENCE = ROOT.parents[1]

def verify():
    artifact = ROOT / 'fabric/build/libs/pous-colored-lights-3.5.0.jar'
    counts = {'identical_assets': 0, 'equivalent_data_files': 0, 'java_classes': 0}
    with zipfile.ZipFile(artifact) as jar:
        names = jar.namelist()
        assert len(names) == len(set(names)), 'Duplicate entries'
        for name in names:
            assert not any(name.startswith('data/prism_lights/' + old + '/') for old in ('recipe', 'advancement', 'loot_table', 'function')), name
        metadata = json.loads(jar.read('fabric.mod.json'))
        assert metadata['version'] == '3.5.0'
        assert metadata['depends']['minecraft'] == '1.20.1'
        assert metadata['depends']['java'] == '>=17'
        assert metadata['description'] == 'Colored lights for minecraft'
        for name in names:
            if name.endswith('.class'):
                raw = jar.read(name)
                assert raw[:4] == b'\xca\xfe\xba\xbe'
                assert struct.unpack('>H', raw[6:8])[0] <= 61, name
                counts['java_classes'] += 1
        resources = REFERENCE / 'common/src/main/resources'
        for source in resources.rglob('*'):
            if not source.is_file():
                continue
            name = source.relative_to(resources).as_posix()
            if name.startswith('assets/'):
                assert source.read_bytes() == jar.read(name), name
                counts['identical_assets'] += 1
            elif name.startswith('data/'):
                parts = name.split('/')
                replacements = {'recipe': 'recipes', 'advancement': 'advancements', 'loot_table': 'loot_tables', 'function': 'functions'}
                parts[2] = replacements.get(parts[2], parts[2])
                if parts[2] == 'tags':
                    parts[3] = {'block': 'blocks', 'item': 'items'}.get(parts[3], parts[3])
                target = '/'.join(parts)
                if source.suffix != '.json':
                    expected = source.read_text(encoding='utf-8').replace('minecraft:short_grass', 'minecraft:grass')
                    assert expected == jar.read(target).decode('utf-8'), target
                    counts['equivalent_data_files'] += 1
                    continue
                old = json.loads(source.read_text(encoding='utf-8'))
                if parts[2] == 'recipes' and 'id' in old.get('result', {}):
                    old['result']['item'] = old['result'].pop('id')
                assert old == json.loads(jar.read(target)), target
                counts['equivalent_data_files'] += 1
        assert sum(n.startswith('data/prism_lights/recipes/') and n.endswith('.json') for n in names) == 113
    tests = failures = errors = 0
    for path in (ROOT / 'core/build/test-results/test').glob('TEST-*.xml'):
        result = ElementTree.parse(path).getroot()
        tests += int(result.attrib['tests'])
        failures += int(result.attrib['failures'])
        errors += int(result.attrib['errors'])
    assert tests == 46 and failures == 0 and errors == 0
    result = {'version': '3.5.0', 'minecraft': '1.20.1', 'loader': 'Fabric', 'sha256': hashlib.sha256(artifact.read_bytes()).hexdigest(), 'core_tests': tests, **counts}
    (ROOT / 'validation/artifact-verification.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, indent=2))

if __name__ == '__main__':
    verify()
