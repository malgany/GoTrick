# GoTrick <versão> — Windows e Ubuntu 24.04 (preview)

<Resumo curto do benefício desta versão. Substitua todos os campos entre sinais de menor/maior antes de publicar.>

## Download e instalação

Baixe **GoTrick-<versão>-windows-x64-setup.exe** nos Assets. Requer Windows x64. Feche o GoTrick antes de atualizar. O instalador funciona por usuário e preserva projetos e preferências. Esta prévia não possui assinatura digital de publicação.

Para Ubuntu 24.04 x64, baixe **GoTrick-<versão>-ubuntu-24.04-x64.tar.gz**, extraia em uma pasta permanente e execute `./install.sh`, seguido de `./bin/gotrick`. Inclua os comandos com a versão real. Arquivos com `sources` no nome são fontes de terceiros, não a aplicação Linux.

O SDK Go é separado: use **GoTrick → Configure Go SDK…** para selecionar ou baixar uma versão oficial. JBR, gopls e Delve acompanham a IDE.

## Alterações

- <Mudança e efeito percebido pelo usuário.>
- <Correção e situação em que ocorria.>

## Validação

- <Resultados reais de build, testes e verificações LSP/DAP.>
- <Resultados reais de branding, instalação e desinstalação.>
- <Verificações visuais realizadas e verificações pendentes.>

## Limitações conhecidas

Prévia experimental para Windows x64 e Ubuntu 24.04 x64. <Descrever o ambiente de validação Linux e as limitações específicas, sem afirmar validações que não ocorreram.>

## Componentes e integridade

<Versões efetivamente empacotadas de Community, JBR, LSP4IJ, gopls e Delve.>

Confira **SHA256SUMS.txt**. Os arquivos **release-manifest-<plataforma>.json** identificam a revisão empacotada, e **VALIDATION-<plataforma>.json** registra os testes. Os arquivos de fontes correspondentes do JBR e LSP4IJ acompanham a release e não são necessários para executar a IDE.

<!-- O empacotador acrescenta o hash real do commit ao RELEASE-NOTES.md gerado. -->
