"""Summarize completed native QA; failed/control runs never count as final passes."""
from pathlib import Path
import csv, hashlib, json, zipfile

ROOT = Path(__file__).resolve().parents[1]
FILES = {
    'vanilla': ('client-candles-accessbind-vanilla.log', 'Candle revision QA COMPLETE'),
    'field': ('client-field-accessbind-sodium.log', 'Differential light field QA COMPLETE'),
    'lifecycle': ('client-lifecycle-blockonly-sodium.log', 'TRANSITION LIFECYCLE COMPLETE'),
    'pulses_sodium': ('client-pulses-accessbind-sodium.log', 'PULSE PROBE COMPLETE'),
    'candles_shaders': ('client-candles-accessbind-shaders.log', 'Candle revision QA COMPLETE'),
    'pulses_reimagined': ('client-pulses-accessbind-reimagined.log', 'PULSE PROBE COMPLETE'),
    'fireworks_shaders': ('client-fireworks-accessbind-shaders.log', 'Firework rendered probe COMPLETE'),
}
BAD = ('java.lang.AssertionError', 'Encountered an unexpected exception', 'Shader compilation failed',
       'Failed to compile shader', 'Iris pipeline observation unavailable', 'Renderer mesh completion bridge unavailable')
BAD += ('GL_INVALID_OPERATION', 'GL_INVALID_ENUM', 'GL_INVALID_VALUE')

def rows(path):
    with path.open(encoding='utf-8') as stream:
        return list(csv.DictReader(stream))

def main():
    logs = {}
    for key, (filename, marker) in FILES.items():
        text = (ROOT / 'validation' / filename).read_text(encoding='utf-8', errors='replace')
        assert marker in text, filename
        assert not any(item in text for item in BAD), filename
        logs[key] = text
    assert logs['field'].count('Fresh field equality PASS:') == 19
    assert '113 mod crafting recipes indexed PASS' in logs['candles_shaders']
    assert 'Firework lifecycle tests PASS:' in logs['fireworks_shaders']
    data = {'native_clients_completed': len(FILES), 'fresh_field_scenarios': 19, 'core_tests': 46, 'jei_recipes': 113, 'pulse_probes': {}}
    pulse_table = []
    for renderer in ('sodium', 'reimagined'):
        folder = ROOT / ('run-frame-validation-pulses-green-isolated-pixels-accessbind-' + renderer)
        pixels = rows(folder / 'pulse-native.csv')
        summary = rows(folder / 'pulse-summary.csv')
        assert len(summary) == 10 and len(pixels) > 1000
        # Same classifier and isolated green/concrete point as the 1.21.1 study.
        bright_neutral = sum(float(p['r']) > 100 and float(p['g']) - float(p['r']) < 35 for p in pixels)
        green = sum(float(p['g']) > 100 and float(p['g']) - float(p['r']) >= 35 for p in pixels)
        assert green > 50, 'Fixture did not render green light'
        assert bright_neutral == 0, (renderer, bright_neutral)
        data['pulse_probes'][renderer] = {'sampled_frames': len(pixels), 'green_frames': green, 'bright_neutral_frames': bright_neutral, 'phases': summary}
        for phase in summary:
            pulse_table.append(f"| {renderer} | {phase['phase']} | {phase['reached']}/{phase['changes']} | {phase['missed']} |")
    candle_rows = rows(ROOT / 'run-candle-validation-accessbind-vanilla/candle-pixels.csv') + rows(ROOT / 'run-candle-validation-accessbind-shaders/candle-pixels.csv')
    assert len(candle_rows) == 160
    assert len({p['renderer'] for p in candle_rows}) == 5
    data['candle_pixel_samples'] = len(candle_rows)
    fireworks = rows(ROOT / 'run-optimization-validation-fireworks-accessbind-shaders/firework-colors.csv')
    assert len({p['renderer'] for p in fireworks}) == 4
    assert len({p['mode'] for p in fireworks}) == 7
    assert all(int(p['mobileCount']) <= 8 for p in fireworks)
    assert any(int(p['mobileCount']) == 8 for p in fireworks)
    data['firework_pixel_samples'] = len(fireworks)
    # The final resource-path correction must not silently change tested code/assets.
    tested = ROOT / 'run-candle-validation-blockonly-vanilla/mods/pous-colored-lights-3.5.0.jar'
    final = ROOT / 'fabric/build/libs/pous-colored-lights-3.5.0.jar'
    with zipfile.ZipFile(tested) as before, zipfile.ZipFile(final) as after:
        for name in before.namelist():
            if name in ('dev/prismlights/CandleServerValidation.class', 'dev/prismlights/client/mixin/SodiumProgramMixin.class'):
                continue  # Function QA and Sodium hook rerun on the final JAR.
            if name.endswith('.class') or name.startswith('assets/'):
                assert before.read(name) == after.read(name), name
    data['tested_sha256'] = hashlib.sha256(tested.read_bytes()).hexdigest()
    data['final_sha256'] = hashlib.sha256(final.read_bytes()).hexdigest()
    for folder in ('run-candle-validation-accessbind-vanilla', 'run-optimization-validation-field-accessbind-sodium', 'run-frame-validation-pulses-green-isolated-pixels-accessbind-sodium', 'run-candle-validation-accessbind-shaders', 'run-frame-validation-pulses-green-isolated-pixels-accessbind-reimagined', 'run-optimization-validation-fireworks-accessbind-shaders'):
        assert hashlib.sha256((ROOT / folder / 'mods/pous-colored-lights-3.5.0.jar').read_bytes()).hexdigest() == data['final_sha256'], folder
    server = (ROOT / 'validation/server-candles-final.log').read_text(encoding='utf-8', errors='replace')
    assert 'demo/showcase parsed by native command dispatcher PASS' in server
    assert 'save/restart PASS' in server
    assert 'Firework lifecycle tests PASS:' in (ROOT / 'validation/server-fireworks-final.log').read_text(encoding='utf-8', errors='replace')
    server_candidate = ROOT / 'run-optimization-validation-field-headbind-sodium/mods/pous-colored-lights-3.5.0.jar'
    with zipfile.ZipFile(server_candidate) as before, zipfile.ZipFile(final) as after:
        for name in before.namelist():
            if name.endswith('.class') and '/client/' not in name:
                assert before.read(name) == after.read(name), 'Server implementation changed: ' + name
    (ROOT / 'validation/native-results.json').write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
    report = f"""# Validação Fabric 1.20.1 / 3.5.0

Base aprovada 1.21.1: `e9ee341`. Cliente de produção Java 17; mundos novos 1.20.1, nenhum save do usuário convertido. Gate de transição ativo nos sete cenários-clientes aprovados, executados sequencialmente. Lifecycle retido de blockonly; Vanilla/campo/pixels/candles-shaders/fogos repetidos no JAR final accessbind. Servidores de desenvolvimento separados em JDK 21 verificam save/restart, funções e lifecycle de fogos.

- 46 testes do núcleo aprovados; build, arquitetura, recursos e arquivo instalável verificados.
- 19 comparações exatas do campo RGB/sky com reconstrução fresca: liga/desliga, fonte removida, paredes, água, tocha, chunks, seleção, limite 72 e mistura.
- GPU: texels exatos, bindings/samplers/PBO/unpack restaurados, buffer duplo, publicação cooperativa e cancelamento.
- 16 cores de velas: interação nativa, grupos 1–4, níveis 3/6/9/12, ignição, extinção, água, loot, bolo; save/restart em servidor separado.
- Vanilla, Sodium sem shader, BSL 10.1.8, Complementary Unbound 5.9.3 e Reimagined 5.9.3: 160 amostras de piso em 40 modos dia/noite/sombra/fallback/efeito desligado. Skylight 11/9 sob folhas e 15 aberto preservado.
- JEI: 113 receitas indexadas no runtime real.
- Luz móvel: 4 perfis × 7 modos, {len(fireworks)} amostras, voo azul/roxo, explosão, cap 8, efeito desligado e launcher. Atlas 8 máscaras com readback exato, limpeza após o último tiro, colisão bloco/entidade, distância/tempo, NBT e explosão sem dano.
- Lifecycle: fontes fora da câmera, disable/re-enable, remoção e aumento de grupo de velas com emissão retida até a cor estar publicada.

## Piscadas: resultados e limite

| Renderizador | Fase | Estados publicados/observados | Substituídos antes da publicação |
|---|---|---|---|
{chr(10).join(pulse_table)}

As sequências de 1–2 ticks com 16 fontes podem substituir estados antes da publicação. Isto preserva a estratégia da base aprovada; não equivale a exibir todas as piscadas instantaneamente. Os probes de pixels examinam um ponto isolado de concreto sob luz verde e usam média 5×5 por frame. Sodium: {data['pulse_probes']['sodium']['sampled_frames']} frames; Reimagined: {data['pulse_probes']['reimagined']['sampled_frames']} frames. Nenhum frame classificado como clarão neutro brilhante nesse ponto (R>100 e G−R<35). É evidência dessa cena, não prova de ausência em qualquer mapa/ângulo/shader. Readback de pixels não é benchmark de FPS.

## Correções específicas do port

Tags e datapacks foram convertidos aos diretórios 1.20.1; callbacks/NBT/DataTracker e render hooks usam APIs antigas. Liberação da barreira atualiza somente BLOCK, preservando SKY recebido do servidor. O controle gate-off e os candidatos falhos estão preservados nos logs, mas não contam como passes finais.

Os candidatos anteriores tinham erros de uniformes OpenGL somente em trocas de shader. Callback síncrono externo localizou a chamada: Iris redireciona o bind do Sodium para GlStateManager; nossa invalidação no tail acontecia após o hook global. A correção move essa invalidação para o head, com o handle do programa novo. As rodadas finais do campo/pixels e dos quatro perfis de shader não têm GL_INVALID_OPERATION/ENUM/VALUE nos logs, nem falha de compilação shader/ponte de renderer. Não há callbacks ou instrumentação adicionais de diagnóstico no JAR entregue.

As funções auxiliares foram movidas `function`→`functions` e `short_grass`→`grass`; acrescentou-se uma asserção no QA de servidor para verificar o parsing nativo das duas funções. O relatório verifica identidade byte a byte das classes do candidato blockonly e do JAR final, exceto o QA de funções e o hook Sodium corrigido/retestado. Todos os assets são idênticos. O build final verifica recursos/receitas equivalentes. Os servidores finais verificam carregamento das funções.

SHA256 testado: `{data['tested_sha256']}`

SHA256 final: `{data['final_sha256']}`

## Alcance

Testes no Windows 11 / RTX 5050 Laptop com os pins de COMPATIBILITY.md. Não houve teste de desempenho A/B entre versões de Minecraft, nem multiplayer com dois clientes, nem validação geral de todos os modpacks. OptiFine/Forge/NeoForge não estão validados neste port. DH/Spectrum não foram testados, conforme orientação do usuário. Mundos 1.21.1 não são saves para downgrade.

QA offline gera falha de autenticação esperada; o registro de entidade 1.20.1 gera `No data fixer registered`. Nenhum desses diagnósticos impediu os testes concluídos. Candidatos falhos, scripts, CSVs e screenshots estão na pasta local do port.
"""
    (ROOT / 'validation/RESULTS.md').write_text(report, encoding='utf-8')
    print(json.dumps({k: v for k, v in data.items() if k != 'pulse_probes'}, indent=2))

if __name__ == '__main__':
    main()
