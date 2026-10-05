# Port 1.21.1 → 1.20.1

Base: 3.5.0 / `e9ee341`. Projeto separado em `ports/fabric-1.20.1`, com `core/common/fabric`, sem alterações no projeto 1.21.1.

IDs, assets, paleta, receitas equivalentes e limites 72/8 foram preservados. A comparação do arquivo instalável com a referência verifica os 630 arquivos visuais e os 376 arquivos de dados. Todo bytecode do JAR é compatível com Java 17; a compilação usa JDK 21.

Adaptações: data components para NBT; inicialização antiga de DataTracker; assinaturas de blocos, partículas, entidades e item tooltip; callbacks de câmera/matrizes; datapacks com diretórios plurais; namespace Sodium 0.5; Iris 1.7; nomes anteriores de blocos vanilla nas funções auxiliares.

Duas correções específicas de integração foram necessárias: liberar somente a luz BLOCK para preservar SKY do servidor na sombra; invalidar os uniformes antes do primeiro bind do Sodium, pois o Iris antigo redireciona a ativação pelo hook global do Minecraft. O handle é acessado na classe que o declara por uma interface opcional, sem reflexão por frame.

Testes usam mundos novos 1.20.1. Não faça downgrade dos saves 1.21.1. Falhas e controles estão preservados, separados dos resultados aprovados. Veja `validation/RESULTS.md` para versões, números, alcance e limitações.

Esta pasta não foi commitada ou enviada automaticamente ao GitHub. O histórico aprovado da referência permanece no repositório atual.
