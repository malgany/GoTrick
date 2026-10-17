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
- Título: `GoTrick <versão> — Windows preview` enquanto houver limitações de prévia.
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

Ubuntu 22.04+ é um alvo futuro: exige bootstrap e empacotamento Linux, binários nativos, testes de SDK/LSP/DAP, desktop entry e validação em ambiente limpo. Não publique um pacote Linux apenas renomeando os arquivos Windows.
