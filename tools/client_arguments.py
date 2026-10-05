from pathlib import Path
import json,sys,re
ROOT=Path.cwd();physical=Path(__file__).resolve().parents[1]
def quote(s):return '"'+str(s).replace('\\','\\\\').replace('"','\\"')+'"'
def main():
    mode,run=sys.argv[1:3];assert mode in ('candles','field','fireworks','lifecycle','pulses','showcase')
    assert re.fullmatch(r'run-[a-z0-9-]+',run)
    data=json.loads((ROOT/'compatibility/production-runtime/runtime.json').read_text())
    def path(s):return str(s).replace(str(physical),str(ROOT))
    args=['-Xmx8G','-Dfile.encoding=UTF-8','-Dfabric.development=false','-Djava.library.path='+path(data['natives'])]
    if 'gate-off' in sys.argv[3:]:args+=['-Dprism.disableTransitionGate=true']
    if 'gl-debug' in sys.argv[3:]:args+=['-Dframeprobe.glDiagnostics=true']
    if mode in ('candles','field','fireworks'):
        args+=['-Dprism.qaRuntime=true','-Dprism.'+{'candles':'candleTest','field':'lightCacheTest','fireworks':'fireworkTest'}[mode]+'=true']
    if mode=='lifecycle':args+=['-Dframeprobe.transitionLifecycle=true']
    if mode=='pulses':args+=['-Dframeprobe.pulses=true','-Dframeprobe.pulsePixels=true','-Dframeprobe.pulseColor=green','-Dframeprobe.pulseIsolated=true']
    if mode=='showcase':pass
    if run.endswith('-sodium') and mode in ('candles','fireworks'):args+=['-Dprism.responseRenderer=sodium']
    args+=['-cp',';'.join(path(x)for x in data['classpath']),data['main'],'--username','Player','--version','1.20.1','--gameDir',str(ROOT/run),'--assetsDir',path(data['assets']),'--assetIndex',str(data['asset_index']),'--accessToken','0','--userType','legacy','--versionType','release','--quickPlaySingleplayer','Port QA','--width','1600','--height','900']
    (ROOT/'compatibility/production-runtime/arguments.txt').write_text('\n'.join(quote(x)for x in args),encoding='utf-8')
if __name__=='__main__':main()
