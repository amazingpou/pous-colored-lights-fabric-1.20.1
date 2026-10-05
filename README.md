# Pou's Colored Lights 3.5.0 — Fabric 1.20.1

Backport independente da base 1.21.1 `e9ee341`, mantendo a versão do mod em **3.5.0**.

Iluminação colorida em 16 cores para blocos convencionais, blocos lisos, variantes de redstone, lanternas, tochas e velas. Inclui o lançador de fogos com luz durante o trajeto e explosões coloridas. Mantém os limites de **72 fontes fixas e 8 móveis**, as receitas e a aparência da versão aprovada.

Requisitos: **Minecraft 1.20.1, Fabric Loader 0.19.5 ou superior, Fabric API 0.92.12+1.20.1 ou superior compatível com 1.20.1 e Java 17 ou superior**. Instale o JAR da pasta `dist` junto ao Fabric API na pasta `mods` da sua instalação 1.20.1. Não instale simultaneamente o JAR 1.21.1.

Use um mundo novo ou um save próprio de 1.20.1. Não abra seus mundos 1.21.1 nesta versão. As construções originais foram preservadas; os testes usam mundos novos e separados.

Consulte [COMPATIBILITY.md](COMPATIBILITY.md) e [validation/RESULTS.md](validation/RESULTS.md) para as versões e o alcance real dos testes.

Estrutura `core/common/fabric`: cálculos independentes em `core`, conteúdo e renderização em `common`, integrações Fabric em `fabric`. O bytecode é Java 17; a ferramenta de compilação usa JDK 21. Para compilar no Windows, use `./scripts/gradle-workspace.ps1 -GradleArguments @('--no-daemon','build')` no PowerShell nesta pasta, com `JAVA_HOME` apontando para JDK 21. O wrapper contorna problemas de caminhos com acentos. O JAR instalável sai em `build/libs`.
