from pathlib import Path
import shutil,json,hashlib,sys
ROOT=Path(__file__).resolve().parents[1]
REFERENCE=ROOT.parents[1]
def main():
    world=ROOT/'run-candle-server-validation/world'
    assert (world/'level.dat').exists()
    artifact=ROOT/'fabric/build/libs/pous-colored-lights-3.5.0.jar'
    probe=ROOT/'tools/frame-probe/build/libs/frame-probe-1.0.0.jar'
    api=next(p for p in Path('C:/Users/jp200/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/0.92.12+1.20.1').rglob('*.jar')if not p.name.endswith('-sources.jar'))
    plans=[('run-candle-validation-vanilla',False,False),('run-candle-validation-shaders',True,False),('run-optimization-validation-fireworks-shaders',True,False),('run-optimization-validation-field-sodium',True,False),('run-frame-validation-transition-lifecycle-sodium',True,True),('run-frame-validation-pulses-green-isolated-pixels-sodium',True,True),('run-frame-validation-pulses-green-isolated-pixels-reimagined',True,True)]
    for name,mods,extra in plans:
        if len(sys.argv)>1:
            suffix=sys.argv[1];assert suffix.isalnum()
            name=name.rsplit('-',1)[0]+'-'+suffix+'-'+name.rsplit('-',1)[1]
        target=ROOT/name;assert not target.exists(),name
        (target/'mods').mkdir(parents=True)
        shutil.copytree(world,target/'saves/Port QA',ignore=shutil.ignore_patterns('session.lock'))
        shutil.copy2(artifact,target/'mods'/artifact.name);shutil.copy2(api,target/'mods'/api.name)
        if mods:
            for p in (ROOT/'compatibility/mods').glob('*.jar'):shutil.copy2(p,target/'mods'/p.name)
            shutil.copytree(REFERENCE/'run-showcase/shaderpacks',target/'shaderpacks')
            (target/'config').mkdir();(target/'config/iris.properties').write_text('enableShaders=false\nshaderPack=ComplementaryReimagined_r5.9.3.zip\n')
            if name.endswith('-reimagined'):(target/'config/iris.properties').write_text('enableShaders=true\nshaderPack=ComplementaryReimagined_r5.9.3.zip\n')
        if extra:shutil.copy2(probe,target/'mods'/probe.name)
        (target/'options.txt').write_text('version:3465\nmaxFps:120\nenableVsync:false\nrenderDistance:12\nsimulationDistance:8\npauseOnLostFocus:false\nguiScale:2\nfov:0.0\n')
        (target/'prepared.json').write_text(json.dumps({'world_source':'Fresh generated Minecraft1.20.1 dedicated-server world','jar_sha256':hashlib.sha256(artifact.read_bytes()).hexdigest(),'reference_world_copied':False},indent=2))
        print('Prepared',name)
if __name__=='__main__':main()
