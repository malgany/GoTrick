# GoTrick

![GoTrick](branding/assets/icon-128.png)

IDE independente e de código aberto para desenvolvimento Go, com interface IntelliJ, integração em Kotlin, inteligência de código do **gopls** e depuração com **Delve**.

**Status: 0.1.1 preview · Windows x64.** Ubuntu 22.04+ está planejado; ainda não há versão Linux disponível. O projeto não é afiliado à JetBrains e não inclui componentes proprietários do GoLand.

**[Baixar GoTrick para Windows](https://github.com/malgany/GoTrick/releases/tag/v0.1.1)** · [Todas as versões](https://github.com/malgany/GoTrick/releases)

## Baixar e instalar

Abra [Releases](https://github.com/malgany/GoTrick/releases) e escolha a versão desejada. Em **Assets**, baixe `GoTrick-<versão>-windows-x64-setup.exe`. Os arquivos automáticos **Source code** contêm o código para desenvolvimento, não o instalador.

O instalador cria um atalho no menu Iniciar e instala por usuário em `%LOCALAPPDATA%\Programs\GoTrick`, sem exigir administrador. A distribuição inclui a plataforma, sua JVM, gopls e Delve. O instalador desta prévia ainda não possui assinatura digital de publicação.

**O SDK Go é separado.** Após abrir um projeto, use **GoTrick → Configure Go SDK…** para detectar um SDK instalado, selecionar sua pasta ou baixar uma versão oficial. O download verifica SHA-256 antes de instalar. Você também encontra a configuração em **Settings → Tools → GoTrick → Go SDK**. A análise aguarda um SDK válido e reinicia automaticamente quando a seleção do projeto é alterada.

A IDE pode solicitar a revisão de termos e preferências iniciais da plataforma. O GoTrick não configura essas escolhas em seu lugar.

## O que funciona

| Área | Implementação atual |
|---|---|
| Editor | Arquivos Go, go.mod e go.work; realce, comentários, delimitadores e dobramento |
| Inteligência de código | Sugestões, diagnósticos, navegação, referências, renomeação e formatação via gopls/LSP4IJ |
| Projetos | Abrir pastas/módulos/workspaces e criar um projeto Go pelo menu GoTrick |
| Execução | Run, Build, Test, Vet, Tidy e configurações de execução |
| Depuração | Pacotes e testes locais, breakpoints, pilha, variáveis e avaliação via Delve/DAP |
| Plataforma | Pesquisa, edição, Git, terminal e extensões compatíveis com IntelliJ Community 252 |

A plataforma executa o plugin Kotlin dentro da JVM; gopls e Delve são processos locais incluídos na instalação e administrados pela integração. Esse conjunto aparece como uma única aplicação GoTrick, embora internamente continue modular.

**Não há equivalência completa ao GoLand.** O parser próprio fornece estrutura básica, enquanto a semântica vem do gopls. Plugins que exigem APIs GoLand ou módulos comerciais não se tornam compatíveis automaticamente. Resultados de testes aparecem no console, sem árvore estruturada; attach e depuração remota ainda não foram implementados.

## Primeiro projeto

Abra a pasta que contém `go.mod` ou `go.work`, ou use **GoTrick → New Go Project…**. Escolha o SDK e execute **Run Package**. Para depurar, marque um breakpoint numa instrução e use **Debug Package**. O painel Debug deve mostrar a linha suspensa, a pilha e **Locals**; a mensagem `DAP server listening` sozinha não comprova a conexão.

O exemplo em `examples/hello-go` imprime `Hello, GoTrick!` e inclui um teste. Build/Test/Vet abrangem `./...`; workspaces percorrem os módulos declarados. Tidy atua no módulo selecionado e pode modificar `go.mod` e `go.sum`.

## Compilar a partir do código

Pré-requisitos: Windows x64, PowerShell, Git, conexão para a preparação inicial e vários GB livres para dependências e caches. Node.js é necessário apenas para o teste adicional dos protocolos.

```powershell
.\scripts\setup.ps1
.\build.ps1
node .\scripts\smoke.mjs
.\scripts\launch.ps1 -PrepareOnly -SkipSetup
.\scripts\launch.ps1 -Project 'C:\Projetos\meu-projeto'
```

O bootstrap baixa dependências com versões e hashes fixos e compila gopls/Delve, sem mudar o PATH global. O SDK Go em `.tools/go` serve ao desenvolvimento do GoTrick e não é selecionado automaticamente para projetos do usuário. `build.ps1` compila, executa testes e verifica a estrutura/configuração do plugin. O plugin compilado fica em `plugin/build/distributions/`.

Para empacotar o instalador:

```powershell
.\scripts\package-windows.ps1
```

A saída fica na pasta irmã `GoTrick-Releases/<versão>`. Veja [o processo de release](docs/RELEASING.md) e [as instruções para agentes](AGENTS.md). `VERSION` centraliza a versão do plugin, identidade e instalador.

Neste projeto, pedidos ao agente de **commit e push** incluem a próxima versão e a publicação dos downloads, conforme o [fluxo de publicação](docs/RELEASING.md#convenção-de-commit-e-push). Para enviar somente o código, peça explicitamente **sem release**. O [modelo de notas](docs/releases/TEMPLATE.md) padroniza as próximas publicações. Isso é uma instrução de trabalho para o agente; comandos Git executados manualmente não disparam publicação automática.

## Estrutura e dados locais

| Caminho | Finalidade |
|---|---|
| `plugin/src/` | Código Kotlin e testes |
| `branding/` | Identidade visual e origem dos assets |
| `packaging/windows/` | Instalador e inicializador da distribuição |
| `scripts/` | Preparação, validação e empacotamento |
| `docs/` | Licenças, releases e decisões de distribuição |
| `.tools/`, `.runtime/`, `validation/` | Dependências, ambiente e resultados locais, fora do Git |

Na instalação final, preferências/plugins ficam em `%APPDATA%\GoTrick`; caches/logs em `%LOCALAPPDATA%\GoTrick`. A desinstalação preserva esses dados e os projetos. Na execução pelo código-fonte, os dados ficam isolados em `.runtime/`.

A base é IntelliJ IDEA Community **2025.2.6.3 / IC-252.28539.97**. A identidade é aplicada por sobreposição de recursos e metadados; não é uma compilação integral da plataforma. As atualizações da plataforma são entregues pelo GoTrick para manter a compatibilidade. Atualizações de plugins continuam disponíveis. Feche a IDE antes de instalar uma nova versão.

## Validação e contribuição

A suíte inclui 59 testes automatizados e verificações de estrutura/configuração do plugin. Os testes cobrem linguagem, integração real com gopls/Delve, encerramento da depuração, seleção/download de SDK e recuperação da análise após selecionar um SDK. Os cenários automatizados não equivalem a uma verificação de todos os fluxos visuais; a validação em uma máquina limpa continua necessária antes de tratar a versão como estável.

Contribuições devem incluir descrição do problema, comportamento esperado e validação pertinente. Não envie caches, dados pessoais, credenciais ou executáveis ao Git. Relatórios locais ficam ignorados; publique somente resumos sanitizados.

## Licença

Código próprio sob [Apache-2.0](LICENSE). Dependências preservam suas licenças e avisos; veja [NOTICE](NOTICE) e [componentes de terceiros](docs/THIRD-PARTY.md). GoTrick, GoLand e IntelliJ têm identidades distintas; não há associação oficial com a JetBrains.
