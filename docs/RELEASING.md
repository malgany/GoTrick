# Versões e distribuição

O código fica no Git; os executáveis ficam em **GitHub Releases → Assets**. Os arquivos automáticos “Source code (zip/tar.gz)” contêm o código-fonte, não a IDE pronta. Esse modelo segue a [documentação do GitHub Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases).

## Preparação

1. Atualize `VERSION` e `docs/releases/<versão>.md`. Use SemVer: correções no patch, novos recursos no minor e incompatibilidades no major. A série 0.x permanece experimental.
2. Execute `scripts/setup.ps1`, `build.ps1`, `node scripts/smoke.mjs` e `scripts/verify-branding.ps1`.
3. Revise os arquivos, licenças, dependências e resultados. Execute `scripts/audit-source.ps1` depois de preparar o staging do Git.
4. Faça o commit de release; mantenha a árvore de fontes limpa durante o empacotamento.
5. Execute `scripts/package-windows.ps1`. Use `-Portable` se também desejar ZIP. `-SkipBuild` é somente para uma compilação já validada da mesma revisão.
6. Teste o instalador, atalho, inicialização, seleção de SDK, Run, Test, Debug e desinstalação em Windows x64. Idealmente repita em uma VM limpa; testes na máquina de desenvolvimento não substituem isso.

O script usa Inno Setup 6.7.3, baixado da distribuição oficial e validado por SHA-256. Saída padrão: pasta irmã `GoTrick-Releases/<versão>`. O staging é imutável e fica ignorado em `.runtime/packages/`.

## Publicação

- Tag anotada: `v<versão>`, apontando para o commit efetivamente empacotado.
- Título: `GoTrick <versão> — Windows preview` enquanto houver limitações de prévia.
- Corpo: conteúdo das notas de versão, commit, requisitos, instalação, alterações, validação e limitações conhecidas.
- Assets: `GoTrick-<versão>-windows-x64-setup.exe`, `release-manifest.json`, `RELEASE-NOTES.md`, `SHA256SUMS.txt`; ZIP portátil apenas se gerado e validado.
- Disponibilize junto da release os fontes correspondentes exigidos pelas licenças de componentes redistribuídos. Consulte `THIRD-PARTY.md`; inclua fontes do JBR da revisão usada e LSP4IJ quando publicar os binários.
- Gere novamente SHA256SUMS se adicionar assets. Verifique nome, tamanho e SHA-256 remoto após o upload.
- Crie primeiro um rascunho e publique somente após todos os assets estarem completos. Marque **pre-release** para 0.1.0.
- Nunca envie instaladores ao histórico Git e nunca substitua arquivos publicados. Uma correção recebe nova versão/tag.

Com GitHub CLI autenticado, o fluxo é `git push origin main`, `git tag -a v<versão> -m ...`, `git push origin v<versão>` e `gh release create ... --draft --prerelease --notes-file ...`, seguido de upload/verificação e publicação. Use argumentos reais da revisão e do repositório, sem tokens em comandos ou arquivos.

## Atualização e compatibilidade

As atualizações da plataforma são publicadas pelo GoTrick, sem usar o atualizador de builds do IntelliJ. Feche a IDE antes de instalar outra versão. Plugins adicionais precisam ser compatíveis com IC 252 e não depender de APIs comerciais/GoLand. Os dados do usuário ficam fora da instalação; a desinstalação os preserva.

Ubuntu 22.04+ é um alvo futuro: exige bootstrap e empacotamento Linux, binários nativos, testes de SDK/LSP/DAP, desktop entry e validação em ambiente limpo. Não publique um pacote Linux apenas renomeando os arquivos Windows.
