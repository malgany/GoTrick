# Componentes de terceiros

Este arquivo registra as principais dependências do ambiente GoTrick e as fontes consultadas em 15/10/2026. O código próprio usa Apache-2.0 (LICENSE na raiz). Os pacotes originais mantêm seus avisos, licenças e metadados; as dependências transitivas têm seus próprios termos.

A distribuição GoTrick preparada em `.runtime/distributions` personaliza os recursos de nome, ícone e splash, `product-info.json` e os recursos de ícone/versão de uma cópia do inicializador nativo (`gotrick64.exe`). O código executável do inicializador não é alterado; a instalação original em `.tools/idea` é preservada. Os avisos de autoria da plataforma permanecem nos arquivos de licença e nos metadados de copyright. O código de compatibilidade IC e a versão da API são mantidos deliberadamente. Esta distribuição modificada não é um produto oficial da JetBrains.

## Runtime e ferramentas

| Componente fixado | Uso | Licença principal e fonte oficial |
|---|---|---|
| IntelliJ IDEA Community 2025.2.6.3 / 252.28539.97 | Aplicação hospedeira e APIs IntelliJ | [Apache-2.0, código e licença da revisão](https://github.com/JetBrains/intellij-community/blob/idea/252.28539.97/LICENSE.txt). Há componentes adicionais com licenças próprias. |
| JetBrains Runtime 21.0.9+1-b1038.78, incluído no IC | JVM da aplicação hospedeira | [Projeto oficial JetBrainsRuntime](https://github.com/JetBrains/JetBrainsRuntime). GPL-2.0 com Classpath Exception nos componentes aplicáveis e avisos adicionais no pacote. |
| Eclipse Temurin JDK 21.0.12.1+1 | Compilação Kotlin/Gradle | [Release oficial](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.12.1%2B1), [licença OpenJDK](https://github.com/adoptium/jdk21u/blob/master/LICENSE). GPL-2.0 com Classpath Exception nos componentes aplicáveis. |
| Gradle 8.14.3 | Build | [Apache-2.0 e avisos incluídos](https://github.com/gradle/gradle/blob/v8.14.3/LICENSE) |
| Go 1.27.2 | SDK Go | [BSD-3-Clause](https://go.dev/LICENSE), [downloads oficiais](https://go.dev/dl/) |
| gopls v0.24.0 | Servidor LSP Go | [BSD-3-Clause e código](https://github.com/golang/tools/blob/gopls/v0.24.0/LICENSE), [documentação](https://go.dev/gopls/) |
| Delve v1.27.2 | Depurador Go / DAP | [MIT e código](https://github.com/go-delve/delve/blob/v1.27.2/LICENSE) |
| LSP4IJ 0.21.0 | Integração LSP e DAP na plataforma | [EPL-2.0](https://github.com/redhat-developer/lsp4ij/blob/0.21.0/LICENSE), [release e código](https://github.com/redhat-developer/lsp4ij/releases/tag/0.21.0) |

Os metadados locais da instalação confirmam `productCode: IC` em `.tools/idea/product-info.json` e o runtime JBR em `.tools/idea/jbr/release`. Os textos completos preservados incluem:

- `.tools/idea/LICENSE.txt`, `.tools/idea/NOTICE.txt` e `.tools/idea/license/`;
- `.tools/idea/jbr/legal/` e `.tools/jdk/legal/`, incluindo `java.base/LICENSE` e `java.base/ADDITIONAL_LICENSE_INFO`;
- `.tools/gradle/LICENSE` e `.tools/gradle/NOTICE`;
- `.tools/go/LICENSE` e os arquivos de licença nos módulos de `.tools/go-work/pkg/mod/`.

O código-fonte do LSP4IJ distribuído no ambiente está disponível no [tag 0.21.0 do projeto oficial](https://github.com/redhat-developer/lsp4ij/tree/0.21.0), sob a licença indicada. O GoTrick utiliza o plugin publicado sem modificá-lo.

## Dependências de compilação e bibliotecas

O build usa [Kotlin 2.1.20 sob Apache-2.0](https://github.com/JetBrains/kotlin/blob/v2.1.20/license/LICENSE.txt), o [IntelliJ Platform Gradle Plugin 2.9.0 sob Apache-2.0](https://github.com/JetBrains/intellij-platform-gradle-plugin/blob/2.9.0/LICENSE) e [JUnit 4.13.2 sob EPL-1.0](https://github.com/junit-team/junit4/blob/r4.13.2/LICENSE-junit.txt) para testes. LSP4IJ inclui bibliotecas adicionais, entre elas Eclipse LSP4J; o conjunto completo é descrito no [build da versão usada](https://github.com/redhat-developer/lsp4ij/tree/0.21.0).

O script de teste dos serviços usa uma instalação externa de Node.js, não incluída no bootstrap. O teste registrado usou Node.js 24.21.0; sua [licença principal é MIT, com avisos adicionais de terceiros](https://github.com/nodejs/node/blob/v24.21.0/LICENSE).

Este é um índice dos componentes principais, não um inventário exaustivo das dependências transitivas. Para identificar módulos e versões incorporados nos executáveis Go:

```powershell
.\.tools\go\bin\go.exe version -m .\.tools\gotools\gopls.exe
.\.tools\go\bin\go.exe version -m .\.tools\gotools\dlv.exe
```

## Origem e integridade

`scripts/setup.ps1` fixa URLs de download e SHA-256 publicados pelos fornecedores para os arquivos principais. Os hashes são verificados antes da extração; cada instalação grava `.gotrick-package.json` com versão, URL, fonte de verificação e hash.

Os executáveis gopls e Delve são compilados a partir de versões fixas por `go install`, com verificação de módulos em `sum.golang.org`. Seus metadados e hashes locais ficam em `.tools/gotools/gopls.json` e `.tools/gotools/dlv.json`. Downloads, SDKs e caches ficam separados do código em `.tools/`.

Os pacotes GoLand e as análises locais históricas não fazem parte deste repositório nem da distribuição.

## Conteúdo redistribuído e fontes correspondentes

O instalador inclui Community/JBR, LSP4IJ, gopls e Delve. Go SDK, Temurin e Gradle são dependências de compilação e não são incluídos. O SDK do usuário é selecionado ou baixado separadamente.

As licenças originais da plataforma permanecem em `ide/LICENSE.txt`, `ide/NOTICE.txt`, `ide/license/` e `ide/jbr/legal/`. `licenses/` inclui EPL do LSP4IJ, licença Go e avisos dos módulos usados na compilação dos helpers. Os JARs preservam seus próprios avisos em META-INF. O inventário dos módulos efetivamente incorporados está em `licenses/gopls-modules.txt` e `licenses/dlv-modules.txt`; a coleção de textos do cache pode conter módulos adicionais usados no build.

- JBR: [fonte da revisão jbr-release-21.0.9b1038.78](https://github.com/JetBrains/JetBrainsRuntime/tree/jbr-release-21.0.9b1038.78), commit c24e7acbdf28; instruções de build no mesmo projeto. Preserve também os avisos dos componentes JCEF e OpenJDK no runtime.
- LSP4IJ: [fontes 0.21.0](https://github.com/redhat-developer/lsp4ij/tree/0.21.0), sem modificações no binário publicado.
- Community: [fontes idea/252.28539.97](https://github.com/JetBrains/intellij-community/tree/idea/252.28539.97); modificações de empacotamento estão em scripts/prepare-branding.ps1, scripts/NativeBranding.cs e branding/ deste repositório.

Ao distribuir binários publicamente, forneça os arquivos de fontes correspondentes do JBR e LSP4IJ como assets junto da release, além destes links e dos avisos preservados.

## Pacote Ubuntu 24.04 x64

O pacote Linux usa o arquivo oficial `ideaIC-2025.2.6.3.tar.gz`, com JBR Linux incluído, e preserva o build IC-252.28539.97. `scripts/setup-linux.py` fixa o SHA-256 do arquivo e dos demais downloads. `scripts/package-linux.py` aplica a mesma identidade em cópias novas e inclui gopls/Delve compilados nativamente para Linux, sem SDK Go, Gradle ou JDK de desenvolvimento. Os fontes JBR da revisão 21.0.9b1038.78 e LSP4IJ 0.21.0 correspondem aos componentes dos dois pacotes. Os avisos, licenças e inventários são preservados em ambos.
