from pathlib import Path
import urllib.request,json,hashlib,zipfile,shutil
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'compatibility/java17';OUT.mkdir(exist_ok=True)
req=urllib.request.Request('https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jre&os=windows&vendor=eclipse',headers={'User-Agent':'PousColoredLights-PortQA'})
release=json.load(urllib.request.urlopen(req))[0];package=release['binary']['package'];target=OUT/package['name']
if not target.exists():
    with urllib.request.urlopen(package['link'],timeout=90)as src,target.open('wb')as dst:shutil.copyfileobj(src,dst)
assert hashlib.sha256(target.read_bytes()).hexdigest()==package['checksum']
with zipfile.ZipFile(target)as z:
    for name in z.namelist():
        resolved=(OUT/name).resolve();assert resolved.is_relative_to(OUT.resolve()),name
    z.extractall(OUT)
java=next(OUT.glob('*/bin/java.exe'))
(OUT/'runtime.json').write_text(json.dumps({'java':str(java),'release':release['release_name'],'sha256':package['checksum'],'source':package['link']},indent=2))
print('Verified Java17 runtime',release['release_name'])
