"""Official Windows Fabric 1.20.1 runtime, isolated from the reference project."""
from pathlib import Path
import concurrent.futures,hashlib,json,shutil,urllib.request,zipfile
ROOT=Path(__file__).resolve().parents[1]
CACHE=Path('C:/Users/jp200/.gradle/caches')
OUT=ROOT/'compatibility/production-runtime'
def download(url,target,sha=None):
    if target.exists() and (not sha or hashlib.sha1(target.read_bytes()).hexdigest()==sha):return target
    target.parent.mkdir(parents=True,exist_ok=True)
    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'PousColoredLights-1.20.1-QA'}),timeout=90) as src,target.open('wb')as dst:shutil.copyfileobj(src,dst)
    if sha:assert hashlib.sha1(target.read_bytes()).hexdigest()==sha,target
    return target
def allowed(lib):
    if 'rules'not in lib:return True
    enabled=False
    for rule in lib['rules']:
        os=rule.get('os',{})
        if os.get('name','windows')!='windows' or os.get('arch','amd64')not in ('amd64','x86_64'):continue
        if rule.get('features'):continue
        enabled=rule['action']=='allow'
    return enabled
def resolve(a):
    target=OUT/'libraries'/a['path'];sha=a.get('sha1')
    if target.exists() and (not sha or hashlib.sha1(target.read_bytes()).hexdigest()==sha):return target
    for p in (CACHE/'modules-2/files-2.1').rglob(target.name):
        if p.is_file() and (not sha or hashlib.sha1(p.read_bytes()).hexdigest()==sha):
            target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,target);return target
    return download(a['url'],target,sha)
def main():
    OUT.mkdir(parents=True,exist_ok=True)
    game=json.loads((CACHE/'fabric-loom/1.20.1/mojang_minecraft_info.json').read_text())
    f=download('https://meta.fabricmc.net/v2/versions/loader/1.20.1/0.19.5/profile/json',OUT/'fabric-profile.json')
    profile=json.loads(f.read_text());artifacts=[x['downloads']['artifact']for x in game['libraries']if allowed(x)]
    for lib in profile['libraries']:
        group,name,version=lib['name'].split(':');path=f'{group.replace(".","/")}/{name}/{version}/{name}-{version}.jar'
        artifacts.append({'path':path,'url':lib['url']+path,'sha1':lib.get('sha1')})
    with concurrent.futures.ThreadPoolExecutor(max_workers=5)as pool:paths=list(pool.map(resolve,artifacts))
    natives=OUT/'natives';natives.mkdir(exist_ok=True)
    for p in paths:
        if 'natives-windows'in p.name:
            with zipfile.ZipFile(p)as z:
                for name in z.namelist():
                    if name.endswith('.dll'):(natives/Path(name).name).write_bytes(z.read(name))
    mc=CACHE/'fabric-loom/1.20.1/minecraft-client.jar'
    assert hashlib.sha1(mc.read_bytes()).hexdigest()==game['downloads']['client']['sha1']
    paths.append(mc)
    assets=OUT/'assets';index=download(game['assetIndex']['url'],assets/'indexes'/f"{game['assetIndex']['id']}.json",game['assetIndex']['sha1'])
    objects=json.loads(index.read_text())['objects']
    def asset(obj):
        sha=obj['hash'];rel=Path('objects')/sha[:2]/sha;dest=assets/rel;cached=CACHE/'fabric-loom/assets'/rel
        if not dest.exists()and cached.exists()and hashlib.sha1(cached.read_bytes()).hexdigest()==sha:
            dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(cached,dest)
        download(f'https://resources.download.minecraft.net/{sha[:2]}/{sha}',dest,sha)
    with concurrent.futures.ThreadPoolExecutor(max_workers=5)as pool:list(pool.map(asset,objects.values()))
    record={'main':profile['mainClass'],'classpath':[str(x)for x in paths],'assets':str(assets),'asset_index':game['assetIndex']['id'],'natives':str(natives)}
    (OUT/'runtime.json').write_text(json.dumps(record,indent=2))
    print('Official runtime ready; MC SHA1 verified; libraries',len(paths),'assets',len(objects))
if __name__=='__main__':main()
