# Decisões — Pou's Colored Lights

## Revisão 2.1.0

- Preservar os IDs dos 16 prismas e o formato da configuração (`configVersion: 3`). Novas famílias recebem IDs próprios; a variante de parede da tocha compartilha o item da tocha de chão.
- Priorizar a aparência no Vanilla. Durante o dia, adicionar componentes de cor sem reduzir os canais da iluminação do céu. A revisão visual noturna de 2026-09-30 substitui a curva anterior: o usuário apontou cores fracas, halo neutro e diferença entre chão e armadura. A grama precisa responder ao matiz durante o dia e à noite.
- Mistura artística por média circular de matiz. O modo RGB anterior continua disponível pela tecla G. Isso evita branco involuntário entre vermelho e azul e controla a transição azul/amarelo.
- Prismas de redstone usam o estado do bloco para ligar a emissão Vanilla e RGB. Lanternas e tochas usam o mesmo campo RGB dos prismas, sem renderer paralelo.
- Limitar o tamanho do volume RGB antes de alocá-lo. Fontes muito espalhadas podem exceder o limite e perder parte do efeito colorido até voltarem a caber; a iluminação Vanilla dos blocos continua.
- Manter irradiância RGB apenas em voxels atravessáveis. Preencher o voxel de uma parede opaca iluminava também sua face oposta por interpolação da textura 3D; a face voltada à luz agora recebe a contribuição do ar adjacente. Essa escolha preserva a oclusão de paredes finas sem acrescentar uma textura por face.
- Manter o alvo atual em Minecraft 1.21.1/Fabric. Ports para 1.20.1, 26.1.2 e NeoForge ficam para uma etapa posterior, após estabilização.

## Direção de compatibilidade — 2026-09-30

- O produto é um protótipo de uma futura biblioteca de iluminação; não transformar esta rodada em implementação da biblioteca. O usuário autorizou trabalhar em compatibilidade e preparação para versões futuras.
- Usar BSL, Complementary Reimagined e Complementary Unbound como os três packs iniciais: dois adaptadores de família, com QA separado dos três packs. OptiFine terá perfil independente de Sodium/Iris.
- Tratar Create/Flywheel e OptiFine como requisitos dos ports em que existem combinações oficiais de loader/Minecraft. Não publicar alegação de suporte no Fabric 1.21.1 atual por inspeção de código.
- Fixar mods auxiliares em perfis locais de QA, fora das dependências de produção. ImmediatelyFast 1.6.14 não pôde ser remapeado pelo Loom 1.14.10 fixado; selecionar 1.6.9 (Loom de origem 1.13.467) para esta rodada, sem atualizar o toolchain. Isso não estabelece incompatibilidade de runtime com 1.6.14.
- Corrigir a leitura da face opaca nos adaptadores por fragmento usando o ar do lado externo, preservando voxels sólidos vazios para não vazar pela face oposta. A noite usa domínio cromático local, incluindo resposta limitada do material e matiz da iluminação ambiente dos packs; isso aproxima a direção artística do lightmap Vanilla. Não aumentar apenas `strength`, pois isso mantinha o amarelo/branco e podia estourar a exposição.
- Medir materiais equivalentes na mesma cena, antes/depois e RGB on/off, com câmera fixa. A abertura do cliente ou o azul na armadura isoladamente não comprova iluminação correta do terreno. O mundo de revisão do usuário não recebe comandos do harness; novos testes usam o mundo separado de QA.

## Mistura e luz do dia — segunda correção de 2026-09-30

- Reduzir bastante a contribuição de cor ao sol, conforme o usuário pediu após testar muitos prismas. Manter presença forte à noite. Usar skylight local e uma transição solar que trate a manhã clara como dia, em vez de misturá-la com a curva noturna.
- Corrigir a identidade da saturação no mixer artístico (codificar ao quadrado para casar com a raiz da decodificação), preservando as cores registradas dos blocos. Duplicar a mesma cor não deve alterar seu matiz ou saturação.
- Limitar irradiância por uma escala comum aos três canais no Vanilla/Sodium; não cortar cada canal isoladamente. Manter tone mapping/color grading nativos dos packs e documentar sua variação residual de matiz.
- Separar amostragem geométrica de exposição: meio voxel externo constante nos packs. O horário altera a resposta da luz, sem deslocar sua leitura na superfície.
- Acrescentar QA com 32 fontes e transições azul/roxo em ambas as ordens, checando por raycast se o ponto medido é realmente parede/chão. Os primeiros probes interceptavam prismas/teto; essas medições foram excluídas e repetidas.

## Lançador de fogos e luz móvel — 2026-09-30

- O pedido de poderes/mobs era inicialmente conceitual; o usuário depois autorizou explicitamente implementar um lançador de fogos com iluminação no trajeto neste projeto.
- Usar entidade de projétil sincronizada pelo servidor para cor, colisão e fase de explosão. O cliente desenha luz/partículas; classes client-only permanecem fora do código comum.
- Separar luzes móveis do campo estático: posições/cores limitadas em uniforms e máscaras locais de oclusão em um atlas pequeno. Movimento não deve reconstruir o campo denso de todas as lâmpadas. Limitar fontes e reconstruções de máscaras por tick; revalidar samplers/recargas e registrar limites.

## Desempenho — 2026-10-01

- O usuário aprovou a otimização após receber a análise. Preservar cores, alcance, quantidade de fontes e qualidade de bloqueio; medir em cópias do showcase, mantendo seus arquivos e configurações intactos.
- Preferir invalidação local e descoberta por eventos. Chunks distantes não devem descartar propagações locais; a região conservadora inclui fronteiras e usa o mesmo alcance do propagador.
- Reutilizar memória e alternar duas texturas/buffers para manter snapshots completos. Aceitar capacidade retida e uma textura adicional como troca por menos alocação e realocação frequente. Atualização parcial e trabalho assíncrono ficam fora desta rodada para evitar deriva de cores e resultados parciais.
- Preservar o custo maior de fluidos com filas por distância inteira. Verificar igualdade bit a bit do campo contra a versão anterior e equivalência entre cache incremental e reconstrução completa.
- Não remover hooks/restauração OpenGL nem alterar o mixer GLSL sem profiling específico que justifique a mudança. Registrar também cenários sem ganho ou com variação desfavorável de frametime.

## 2026-10-02 — Design e distribuição 3.0.0

Usuário aprovou os testes interativos e autorizou versão 3.0.0. Remover detalhe vermelho dos prismas de redstone, adicionar família lisa nas mesmas 16 cores e suavizar disparo em primeira pessoa. Luzes lisas reutilizam PrismBlock e motor RGB, com IDs novos e receita 1:1; não alteram saves antigos. Recuo local de 0,3 s apenas na mão que disparou, sem animação de ataque e sem dependência de biblioteca de animação. Descrição destaca iluminação em vez da quantidade de itens. Backup fonte/JAR/mundo/config em backups/2026-10-02-2.1.0-aprovada-antes-3.0.
