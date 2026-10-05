# Pou's Colored Lights

Status: APROVADO (revisão 3.0.0 do produto existente)

## Complemento autorizado em 2026-10-02

Manter 3.0.0; disparar dois lançadores equipados simultaneamente, com cores independentes, bocas separadas e trajetórias levemente abertas. Recuo para trás e para cima; braços de jogadores apontados para a frente ao equipar. Trocar os detalhes rosa da arma por arco-íris. Adicionar 16 luzes lisas de redstone com as texturas acesa/apagada das lisas. Testar Vanilla, renderizadores/desempenho e um minimodpack de dez mods invasivos disponíveis para Fabric 1.21.1; Pixelmon somente se compatível com o loader/versão. Ao concluir, abrir o showcase original com JEI, Sodium, Iris e shaders. Não iniciar ports nem alterar o mundo original durante QA.

## Visão atual documentada

O mod existente adiciona 16 prismas que emitem luz colorida sobre cenário, entidades e armaduras. Há mistura artística de cores e opção de mistura RGB. O comportamento atual está descrito em `README.md`; esta descrição ainda não representa a aprovação do usuário para a próxima versão.

## Direção apresentada pelo usuário

- Prioridade principal: a luz colorida deve parecer natural no Minecraft Vanilla. Sob sol forte a cor continua visível, porém mais discreta; em sombra pode aparecer com mais força. A luz colorida não deve escurecer o cenário nem dar aparência de noite.
- Revisar a mistura visual das cores para produzir transições coerentes. Azul com amarelo não deve gerar um verde excessivamente forte; vermelho com azul deve produzir roxo perceptível, sem a transição esbranquiçada atual. A regra exata será escolhida e validada visualmente, sem privilegiar apenas um par de cores.
- O desempenho atual parece bom ao usuário. A compatibilidade testada por ele também parece boa. Preservar essas qualidades ao melhorar a luz.
- Shaders são um bônus: manter o suporte existente quando possível, mas priorizar a qualidade no Vanilla.
- Expansão desejada: lanternas coloridas com forma e uso próximos da lanterna Vanilla; variantes dos prismas atuais controladas por redstone, com o mesmo desenho dos prismas convencionais, sem detalhes vermelhos na versão 3.0.0, aspecto escuro quando apagadas e claro quando acesas. Tochas coloridas com forma e uso próximos da tocha Vanilla também interessam; suas partículas devem acompanhar a cor da luz.
- Verificar especificamente a iluminação colorida da grama no Vanilla. O projeto existente afirma ter corrigido esse comportamento na versão 2.0.4, mas o usuário ainda não confirmou que o resultado está certo.
- Correção visual solicitada em 2026-09-30: à noite, cores intensas, com azul escuro perceptível no chão. Uma fonte azul não deve produzir um halo branco/amarelado; grama, concreto, entidades e armaduras precisam receber a mesma iluminação, respeitando a resposta de cada material.
- Ajuste seguinte na mesma data: o usuário apontou variação estranha de azul/roxo e cor excessiva na grama durante o dia com muitas fontes. Preservar a identidade das cores e transições suaves; ao sol, o efeito deve ficar bem mais discreto, sem aparência de tinta na grama.
- Depois de estabilizar a versão atual, o usuário deseja estudar ports para Minecraft 1.20.1 e 26.1.2 e, se viável, NeoForge nessas versões. Versões e viabilidade técnica serão verificadas em fase posterior; não iniciar ports agora.

## Velas, bordas e sombra parcial — aprovado em 2026-10-03

- Adicionar 16 velas de luz colorida com modelos/texturas Vanilla, agrupamento de 1 a 4, acendimento, apagamento, água e uso em bolos. Receita: uma vela Vanilla da mesma cor e um pó de glowstone produzem uma vela do mod.
- Manter a chama pequena original, recolorida conforme a vela; preservar fumaça e som. Luminância nativa 3/6/9/12 acompanha o número de velas acesas.
- Afinar para um pixel a borda externa dos prismas convencionais e de redstone, preservando as luzes lisas.
- Corrigir a cor fraca em sombra parcial durante o dia sem aumentar globalmente o efeito sob céu aberto; manter a versão 3.0.0 e os IDs existentes.

## Escopo aprovado

- CORE: iluminação Vanilla durante o dia sem escurecimento, mistura de cores convincente e verificação da grama.
- CORE: lanternas coloridas no estilo Vanilla e variantes dos prismas acionadas por redstone.
- CORE: tochas coloridas no estilo Vanilla, com partículas da cor correspondente.
- FUTURE: ports e ampliação da compatibilidade com loaders/versões.

## Critérios aceitos

- As 16 cores atuais se aplicam a lanternas, tochas e prismas de redstone.
- Lanternas e tochas devem manter comportamento de colocação e intensidade compatíveis com seus equivalentes Vanilla; o efeito RGB acompanha a luz emitida.
- Confirmar por testes visuais os critérios de luz diurna, mistura e grama antes de considerar a implementação pronta.

O usuário autorizou concluir esta revisão, inclusive desenvolvimento e validação, sem novos gates entre as etapas. Após a entrega, retomar o protocolo normal da fábrica.

## Lançador de fogos — expansão solicitada em 2026-09-30

O usuário pediu implementar neste protótipo uma arma de fogos para experimentar luz móvel: botão direito dispara um foguete de cor aleatória, que ilumina o trajeto e explode com partículas da mesma cor ao colidir ou expirar por distância/tempo. Parâmetros iniciais: alcance de 40 blocos, máximo de 3 segundos de voo e clarão breve que se apaga após a explosão. Item na aba criativa do mod, com modelo de lançador. Esta etapa implementa a experiência e seu caminho de luz móvel; não publica uma API de biblioteca nem inicia ports. Explosão visual, sem remoção de blocos.

## Versão 3.0.0 — autorizada em 2026-10-02

O usuário aprovou os testes interativos da iluminação e pediu: prismas de redstone sem detalhes vermelhos, 16 blocos luminosos totalmente lisos para construções e um recuo simples ao disparar o lançador em primeira pessoa, substituindo o movimento genérico de golpe. Blocos lisos compartilham paleta, intensidade, alcance e alternância com mão vazia dos prismas; craft: um prisma convencional da mesma cor resulta em uma luz lisa. Ordem criativa: prismas, luzes lisas, redstone, lanternas, tochas, lançador. Descrição do mod prioriza iluminação, mistura e resposta ao ambiente. Usuário autorizou versionar e preparar a distribuição local 3.0.0.
