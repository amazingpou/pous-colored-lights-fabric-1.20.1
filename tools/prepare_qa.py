"""Fresh QA worlds only; never downgrade the user's 1.21.1 saves."""
from pathlib import Path
import shutil,json
ROOT=Path(__file__).resolve().parents[1]
def main():
    for name in ('run-candle-server-validation','run-firework-server-validation'):
        p=ROOT/name;p.mkdir(exist_ok=True)
        (p/'eula.txt').write_text('eula=true\n')
        (p/'server.properties').write_text('online-mode=false\nserver-port=0\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains","structures":{}}\ngenerate-structures=false\nview-distance=6\nsimulation-distance=4\nspawn-protection=0\nmax-tick-time=120000\n')
    (ROOT/'validation').mkdir(exist_ok=True)
    print('Isolated dedicated-server folders ready')
if __name__=='__main__':main()
