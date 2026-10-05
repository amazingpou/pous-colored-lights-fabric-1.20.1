# Validação Fabric 1.20.1 / 3.5.0

Base aprovada 1.21.1: `e9ee341`. Cliente de produção Java 17; mundos novos 1.20.1, nenhum save do usuário convertido. Gate de transição ativo nos sete cenários-clientes aprovados, executados sequencialmente. Lifecycle retido de blockonly; Vanilla/campo/pixels/candles-shaders/fogos repetidos no JAR final accessbind. Servidores de desenvolvimento separados em JDK 21 verificam save/restart, funções e lifecycle de fogos.

- 46 testes do núcleo aprovados; build, arquitetura, recursos e arquivo instalável verificados.
- 19 comparações exatas do campo RGB/sky com reconstrução fresca: liga/desliga, fonte removida, paredes, água, tocha, chunks, seleção, limite 72 e mistura.
- GPU: texels exatos, bindings/samplers/PBO/unpack restaurados, buffer duplo, publicação cooperativa e cancelamento.
- 16 cores de velas: interação nativa, grupos 1–4, níveis 3/6/9/12, ignição, extinção, água, loot, bolo; save/restart em servidor separado.
- Vanilla, Sodium sem shader, BSL 10.1.8, Complementary Unbound 5.9.3 e Reimagined 5.9.3: 160 amostras de piso em 40 modos dia/noite/sombra/fallback/efeito desligado. Skylight 11/9 sob folhas e 15 aberto preservado.
- JEI: 113 receitas indexadas no runtime real.
- Luz móvel: 4 perfis × 7 modos, 56 amostras, voo azul/roxo, explosão, cap 8, efeito desligado e launcher. Atlas 8 máscaras com readback exato, limpeza após o último tiro, colisão bloco/entidade, distância/tempo, NBT e explosão sem dano.
- Lifecycle: fontes fora da câmera, disable/re-enable, remoção e aumento de grupo de velas com emissão retida até a cor estar publicada.

## Piscadas: resultados e limite

| Renderizador | Fase | Estados publicados/observados | Substituídos antes da publicação |
|---|---|---|---|
| sodium | single-1t | 95/118 | 23 |
| sodium | single-2t | 60/60 | 0 |
| sodium | single-4t | 30/30 | 0 |
| sodium | single-8t | 15/15 | 0 |
| sodium | single-16t | 7/7 | 0 |
| sodium | alternating16-1t | 0/120 | 120 |
| sodium | alternating16-2t | 59/60 | 1 |
| sodium | alternating16-4t | 30/30 | 0 |
| sodium | alternating16-8t | 15/15 | 0 |
| sodium | alternating16-16t | 7/7 | 0 |
| reimagined | single-1t | 111/120 | 9 |
| reimagined | single-2t | 60/60 | 0 |
| reimagined | single-4t | 30/30 | 0 |
| reimagined | single-8t | 15/15 | 0 |
| reimagined | single-16t | 7/7 | 0 |
| reimagined | alternating16-1t | 102/120 | 18 |
| reimagined | alternating16-2t | 60/60 | 0 |
| reimagined | alternating16-4t | 25/25 | 0 |
| reimagined | alternating16-8t | 15/15 | 0 |
| reimagined | alternating16-16t | 6/6 | 0 |

As sequências de 1–2 ticks com 16 fontes podem substituir estados antes da publicação. Isto preserva a estratégia da base aprovada; não equivale a exibir todas as piscadas instantaneamente. Os probes de pixels examinam um ponto isolado de concreto sob luz verde e usam média 5×5 por frame. Sodium: 7100 frames; Reimagined: 6819 frames. Nenhum frame classificado como clarão neutro brilhante nesse ponto (R>100 e G−R<35). É evidência dessa cena, não prova de ausência em qualquer mapa/ângulo/shader. Readback de pixels não é benchmark de FPS.

## Correções específicas do port

Tags e datapacks foram convertidos aos diretórios 1.20.1; callbacks/NBT/DataTracker e render hooks usam APIs antigas. Liberação da barreira atualiza somente BLOCK, preservando SKY recebido do servidor. O controle gate-off e os candidatos falhos estão preservados nos logs, mas não contam como passes finais.

Os candidatos anteriores tinham erros de uniformes OpenGL somente em trocas de shader. Callback síncrono externo localizou a chamada: Iris redireciona o bind do Sodium para GlStateManager; nossa invalidação no tail acontecia após o hook global. A correção move essa invalidação para o head, com o handle do programa novo. As rodadas finais do campo/pixels e dos quatro perfis de shader não têm GL_INVALID_OPERATION/ENUM/VALUE nos logs, nem falha de compilação shader/ponte de renderer. Não há callbacks ou instrumentação adicionais de diagnóstico no JAR entregue.

As funções auxiliares foram movidas `function`→`functions` e `short_grass`→`grass`; acrescentou-se uma asserção no QA de servidor para verificar o parsing nativo das duas funções. O relatório verifica identidade byte a byte das classes do candidato blockonly e do JAR final, exceto o QA de funções e o hook Sodium corrigido/retestado. Todos os assets são idênticos. O build final verifica recursos/receitas equivalentes. Os servidores finais verificam carregamento das funções.

SHA256 testado: `5c3af85611d72b8613ad38a1fc8596b352bdbef85d26fb250134723339bc5770`

SHA256 final: `b517d691516a75ad2e92ee7dad70b62f409fc502b0a8199dd571c9729d8a46ba`

## Alcance

Testes no Windows 11 / RTX 5050 Laptop com os pins de COMPATIBILITY.md. Não houve teste de desempenho A/B entre versões de Minecraft, nem multiplayer com dois clientes, nem validação geral de todos os modpacks. OptiFine/Forge/NeoForge não estão validados neste port. DH/Spectrum não foram testados, conforme orientação do usuário. Mundos 1.21.1 não são saves para downgrade.

QA offline gera falha de autenticação esperada; o registro de entidade 1.20.1 gera `No data fixer registered`. Nenhum desses diagnósticos impediu os testes concluídos. Candidatos falhos, scripts, CSVs e screenshots estão na pasta local do port.
