# Versões e distribuição

O código fica no Git; os executáveis ficam em **GitHub Releases → Assets**. Os arquivos automáticos “Source code (zip/tar.gz)” contêm o código-fonte, não a IDE pronta. Esse modelo segue a [documentação do GitHub Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases).

## Preparação

1. Confira versões e tags existentes no remoto. Atualize `VERSION`, o status/link no README e `docs/releases/<versão>.md`, partindo de [TEMPLATE.md](releases/TEMPLATE.md). Use SemVer: correções no patch, novos recursos no minor e incompatibilidades no major. A série 0.x permanece experimental.
2. Execute `scripts/setup.ps1`, `build.ps1`, `node scripts/smoke.mjs` e `scripts/verify-branding.ps1`.
3. Revise os arquivos, licenças, dependências e resultados. Execute `scripts/audit-source.ps1` depois de preparar o staging do Git.
4. Faça o commit de release; mantenha a árvore de fontes limpa durante o empacotamento.
5. Execute `scripts/package-windows.ps1`. Use `-Portable` se também desejar ZIP. `-SkipBuild` é somente para uma compilação já validada da mesma revisão.
6. Execute `scripts/verify-installer.ps1 -Installer <caminho-do-instalador>`; ele testa instalação e desinstalação isoladas, inicializador, atalho e helpers, preservando a sessão de desenvolvimento. Se existir uma instalação registrada, use outro usuário/VM para não sobrescrevê-la. Copie o resultado sanitizado `validation/installer.json` para `VALIDATION.json` na pasta da release. Teste também seleção de SDK, Run, Test e Debug na interface; registre como pendente o que não foi verificado. Repita em Windows x64 limpo antes de declarar estabilidade.

O script usa Inno Setup 6.7.3, baixado da distribuição oficial e validado por SHA-256. Saída padrão: pasta irmã `GoTrick-Releases/<versão>`. O staging é imutável e fica ignorado em `.runtime/packages/`.

## Publicação

- Tag anotada: `v<versão>`, apontando para o commit efetivamente empacotado.
- Título: `GoTrick <versão> — Windows e Ubuntu 24.04 (preview)` enquanto houver limitações de prévia.
- Corpo: conteúdo das notas de versão, commit, requisitos, instalação, alterações, validação e limitações conhecidas.
- Assets: `GoTrick-<versão>-windows-x64-setup.exe`, `release-manifest.json`, `RELEASE-NOTES.md`, `VALIDATION.json`, `SHA256SUMS.txt`; ZIP portátil apenas se gerado e validado.
- O empacotador acrescenta o commit ao `RELEASE-NOTES.md` gerado e ao manifesto. Use esse arquivo gerado como corpo da release; o arquivo versionado contém as notas editoriais, evitando a referência circular ao hash do próprio commit.
- Disponibilize junto da release os fontes correspondentes exigidos pelas licenças de componentes redistribuídos. Consulte `THIRD-PARTY.md`; inclua fontes do JBR da revisão usada e LSP4IJ quando publicar os binários.
- Gere novamente SHA256SUMS se adicionar assets. Verifique nome, tamanho e SHA-256 remoto após o upload.
- Crie primeiro um rascunho e publique somente após todos os assets estarem completos e verificados. Marque **pre-release** enquanto a versão for experimental. Compare o SHA-256 local com o digest retornado pelo GitHub; se o digest não estiver disponível, baixe o asset e calcule. Confirme também o commit da branch e da tag remotas.
- Nunca envie instaladores ao histórico Git e nunca substitua arquivos publicados. Uma correção recebe nova versão/tag.

Com GitHub CLI autenticado, o fluxo é `git push origin main`, `git tag -a v<versão> -m ...`, `git push origin v<versão>` e `gh release create ... --draft --prerelease --notes-file ...`, seguido de upload/verificação e publicação. Use argumentos reais da revisão e do repositório, sem tokens em comandos ou arquivos.

## Convenção de commit e push

Por orientação do mantenedor, um pedido ao agente de **commit e push** neste repositório inclui incrementar a versão e concluir a publicação dos downloads. Pedidos de commit local, envio somente de código ou **sem release** limitam o fluxo conforme solicitado. Alterar arquivos, por si só, não autoriza publicação. Os comandos Git manuais não têm hooks de publicação automática.

A skill pessoal **gotrick-release**, quando disponível, aplica este processo. As instruções versionadas neste documento e no `AGENTS.md` permitem continuar o mesmo fluxo em outras máquinas, mesmo sem a skill instalada.

Se uma etapa falhar, registre o ponto exato e mantenha a release como rascunho até validar os arquivos. Antes de retentar, inspecione o estado remoto para evitar duplicações. Nunca declare a publicação concluída apenas porque o push passou.

## Atualização e compatibilidade

As atualizações da plataforma são publicadas pelo GoTrick, sem usar o atualizador de builds do IntelliJ. Feche a IDE antes de instalar outra versão. Plugins adicionais precisam ser compatíveis com IC 252 e não depender de APIs comerciais/GoLand. Os dados do usuário ficam fora da instalação; a desinstalação os preserva.

Ubuntu 24.04 x64 possui bootstrap e empacotamento próprios. Gere o pacote em Linux para preservar permissões e links; nunca renomeie o pacote Windows. ARM64, Ubuntu 22.04 e outras distribuições não foram validados.

## Release para os dois sistemas

1. Use a mesma versão e commit limpo nos dois sistemas. Preserve alterações locais do mantenedor em outra cópia de trabalho. No Ubuntu 24.04 instale OpenJDK 21, Python 3, curl, Git, Node.js, bibliotecas gráficas e `desktop-file-utils`.
2. Execute `python3 scripts/setup-linux.py`, `bash build.sh` e `node scripts/smoke.mjs`. Os downloads de dependências são fixados por SHA-256; o SDK de build nunca integra o pacote final.
3. Após o commit, execute `python3 scripts/package-linux.py --output <pasta-de-saída>`. A saída inclui `GoTrick-<versão>-ubuntu-24.04-x64.tar.gz` e `release-manifest-ubuntu-24.04-x64.json`.
4. Execute `python3 scripts/verify-linux.py <arquivo.tar.gz>` no Ubuntu 24.04. O teste extrai o pacote, verifica inicializador/JBR, identidade e consentimentos, atalho, paths XDG, download real do SDK e os helpers empacotados em 11 verificações Go/LSP/DAP. Copie `validation/linux.json` como `VALIDATION-ubuntu-24.04-x64.json`.
5. Gere e valide também o instalador Windows conforme o fluxo acima. Na pasta combinada, nomeie seu manifesto `release-manifest-windows-x64.json` e seu resultado `VALIDATION-windows-x64.json`. Mantenha um `RELEASE-NOTES.md` com a revisão comum e resultados dos dois sistemas.
6. Inclua os fontes correspondentes JBR/LSP4IJ, regenere um único `SHA256SUMS.txt` após reunir todos os assets e confira os hashes remotos antes de publicar. A mesma tag anotada `v<versão>` identifica os dois pacotes.

Os testes automatizados no Ubuntu 24.04 em WSL2 não substituem testes visuais em uma sessão Ubuntu Desktop física. Registre essa limitação nas notas e mantenha a marca de prévia até a aceitação do desktop.
