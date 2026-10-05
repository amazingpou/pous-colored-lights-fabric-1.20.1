# Compatibilidade do backport

Target: Minecraft **1.20.1**, **Fabric**, mod **3.5.0**. Testes em Java17 no Windows11; build em JDK21.

| Componente | Versão fixada no QA | Escopo |
|---|---|---|
| Fabric Loader | 0.19.5 | Cliente/servidor |
| Fabric API | 0.92.12+1.20.1 | Dependência obrigatória |
| Sodium | 0.5.13+mc1.20.1 | Campo, GPU, ciclos e pixels |
| Iris | 1.7.6+mc1.20.1 | Pipeline e troca de shader |
| JEI | 15.62.0.219 | 113 receitas no runtime |
| MezzConfig | 0.6.8 | Dependência do JEI |
| Lithium | 0.11.4 | Carregado junto à pilha de QA |
| FerriteCore | 6.0.1 | Carregado junto à pilha de QA |
| ImmediatelyFast | 1.5.5+1.20.4 | Release do autor cobre 1.20–1.20.4, incluindo 1.20.1 |
| BSL | 10.1.8 | Iluminação fixa, velas e móveis |
| Complementary Unbound | 5.9.3 | Iluminação fixa, velas e móveis |
| Complementary Reimagined | 5.9.3 | Iluminação fixa, velas, móveis e pixels de piscadas |

Para detalhes, resultados e limitações, consulte [validation/RESULTS.md](validation/RESULTS.md). Os mods foram carregados juntos; isto não demonstra compatibilidade com todas as versões/configurações desses projetos ou com qualquer modpack.

Sem testes de Distant Horizons/Spectrum, conforme orientação do usuário. OptiFine e loaders Forge/NeoForge não foram validados e não estão anunciados como suportados. Multiplayer com dois clientes não foi medido nesta rodada.

O limite continua 72 fontes fixas/8 móveis. Alterações mais rápidas do que a publicação de cor podem ser substituídas, especialmente grupos de 16 fontes em 1–2 ticks; a barreira retém a luz nativa para evitar o clarão branco. Esta limitação da base não foi removida por um backport.

Mantenha mundos 1.21.1 na instalação 1.21.1. Os testes 1.20.1 foram feitos em mundos novos, sem converter os saves do usuário.
