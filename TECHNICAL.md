# Target e arquitetura

Minecraft 1.20.1 / Fabric Loader 0.19.5 / Fabric API 0.92.12+1.20.1 / Yarn 1.20.1+build.10. `core`: cálculo independente; `common`: conteúdo e render; `fabric`: hooks e entrypoints. Bytecode Java 17; Gradle 9.2 / Loom 1.14.10 usa JDK 21 para compilar.

As integrações foram adaptadas ao namespace `me.jellysquid.mods.sodium` do Sodium 0.5.13 e ao Iris 1.7.6. As operações de câmera e matrizes usam as assinaturas 1.20.1. Fogos usam NBT `Explosion/Colors`, preservando a mesma cor do projétil; DataTracker e callbacks de interação usam as APIs anteriores aos data components.

No Iris 1.7, `GlProgram.bind` do Sodium é redirecionado pelo Iris para `_glUseProgram` do Minecraft. O hook global de uniformes executa antes do tail do Sodium. A invalidação de locations de IDs reutilizados foi movida para o head do primeiro bind, usando o handle do próprio programa. O callback OpenGL síncrono de uma ferramenta externa confirmou uploads errados no hook global antes dessa correção. Sem checagens de erro ou callbacks extras no JAR de produção.

Datapacks usam `recipes`, `advancements`, `loot_tables`, `functions`, `tags/blocks` e `tags/items`. Resultados de receita usam `item`; a função de demonstração usa `minecraft:grass`, nome anterior a `short_grass`. IDs do mod, texturas, modelos e receitas equivalentes são comparados automaticamente com a referência.

A sincronização mantém a emissão nativa bloqueada até a publicação da cor, mas libera somente o motor de luz `BLOCK`. Reprocessar o provider combinado em 1.20.1 alterava o skylight recebido do servidor sob folhas (11 virava 15). O controle com a barreira desligada isolou a causa; a correção preserva o skylight e mantém a barreira ativa.

O código de servidor não importa classes exclusivas do cliente. A única exceção nominal verificada é `net.minecraft.client.item.TooltipContext`, classe comum do próprio Minecraft 1.20.1 apesar do namespace. Não há dependências Fabric em `common` nem Minecraft em `core`.

QA nativo usa Java 17, runtime oficial e cópias de um mundo novo 1.20.1. Os testes de render usam o JAR de produção. Probes de framebuffer são instrumentação de correção, não benchmarks de FPS. No ambiente offline de QA, falha de autenticação é esperada. O registro da entidade de fogo em 1.20.1 também gera o diagnóstico vanilla `No data fixer registered`, sem impedir spawn, NBT ou salvamento.
