# Working on GoTrick

## Scope and architecture

- GoTrick is an independent Kotlin plugin and branded IntelliJ Community distribution.
- Use public IntelliJ/LSP4IJ APIs, gopls for language intelligence and Delve/DAP for debugging.
- Do not import proprietary GoLand binaries, code, authentication or license mechanisms.
- Preserve upstream notices and the platform IC build identity required for compatibility.
- Windows x64 is the current release target. Ubuntu 22.04+ is planned, not validated or packaged yet.
- The end-user distribution includes JBR, gopls and Delve. Never bundle the developer Go SDK, JDK or Gradle.
- Go SDK selection/download belongs to the project settings. Never silently select the repository's build SDK.

## Build and validation

- Read README.md and docs/RELEASING.md before changing packaging.
- Bootstrap with scripts/setup.ps1; build and run tests with build.ps1.
- For LSP/DAP changes also run node scripts/smoke.mjs and inspect its result.
- For branding changes run scripts/verify-branding.ps1. It verifies consent metadata as well as identity.
- Tests and logs are local in validation/ and build directories; do not commit them.
- Never edit hard-linked runtime or packaging files. Modify source and create fresh staging instead.
- Preserve user files and active IDE sessions. Archive obsolete material outside the repository.
- Use literal paths and validate absolute targets before recursive moves or deletion.

## Version and release

- Project convention requested by the maintainer: a request to "commit and push" includes preparing and publishing the next version with downloadable Release assets. Follow docs/RELEASING.md and use the gotrick-release skill when available. A request limited to local commits, code-only pushes, or no release overrides this convention. Ordinary edits alone do not authorize publication.
- Start release notes from docs/releases/TEMPLATE.md; update the README version and download link together with VERSION.
- VERSION is the single version source (major.minor.patch). Keep release notes in docs/releases/<version>.md.
- Use annotated tags v<version>. Preview versions are GitHub prereleases until the documented acceptance criteria pass.
- Run scripts/package-windows.ps1; its default output is the sibling GoTrick-Releases/<version> folder.
- Installer: GoTrick-<version>-windows-x64-setup.exe. Optional portable: GoTrick-<version>-windows-x64.zip.
- Publish installers, SHA256SUMS.txt, release-manifest.json, release notes and required third-party source archives as Release assets, never as Git blobs.
- Build from the intended release commit, record it in release notes, and verify checksums after upload.
- Never replace a published tag or asset in place; increment VERSION for corrections.
- Do not label an untested platform supported, a preview stable, or an unsigned installer signed.

## Git and publication

- Review git status, git diff --cached and git diff --cached --check before every commit.
- Run scripts/audit-source.ps1 on staged files; manually review any finding and all staged paths.
- Never commit .tools/, .runtime/, caches, .idea/, validation/, secrets, personal paths or local reports.
- Do not print credentials, copy browser tokens, or store credentials in files/remotes.
- Use the user's authenticated Git/GitHub flow. Request sign-in only when authentication is required.
- Push and publish only when authorized in the conversation. An authorized release does not require another confirmation.
- Never force-push, rewrite published history or delete remote content without explicit authorization.
- A release is complete only when the remote commit/tag and uploaded assets are verified. Report any blocked step accurately.
