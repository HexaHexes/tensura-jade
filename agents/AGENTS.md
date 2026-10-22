# Agent rules and conventions — Tensura Jade

**Repo:** `HexaHexes/tensura-jade` · **Mod ID:** `tensura_jade` · **Branch:** `main`
**Platform:** Minecraft 1.21.1 / NeoForge 21.1.233 / JDK 21 · **Jar:** `build/libs/tensura_jade-<version>.jar`

A NeoForge addon that moves Tensura's analysis window into Jade's look-up tooltip.
Aim at a mob or block and its Tensura stats appear in the Jade tooltip you already
read; Tensura's own floating overlay is hidden so nothing is shown twice.

**Dependencies** (all optional at runtime — the addon no-ops if any is missing)
- Tensura (`tensura`), Jade (`jade`), ManasCore (`manascore`).
- The suppression mixin is declared `requiredMods=["tensura","jade"]` and re-checked
  by `TensuraJadeMixinPlugin`, so it never applies unless both are present.
- ManasCore is present whenever Tensura is; it provides the storage APIs.

**Test:** `./gradlew runGameTestServer` — `AnalysisGameTests` + `ConfigGameTests`
(**18** tests). No `expectedTests` guard in `build.gradle`; verify from the log line
`All 18 required tests passed :)`.

**Gotchas**
- The suppression mixin names Tensura's `pre_renderAnalysis` / `renderAnalysis` and
  resolves them **without a refmap** — compile against the exact Tensura jar you ship
  with (`libs/`, pinned). Version drift there is the failure mode that matters.
- Client-only classes (Jade's `Screen`, Tensura's `RenderHelper`) must never load on a
  dedicated server. The number-shortening helper is **transcribed**, not called, for
  this reason; the config tests use a hand-written stub, not a `java.lang.reflect.Proxy`.
- `TensuraJadeConfig.register` must **not** declare `entity_analysis` / `block_analysis`
  — Jade creates those from the providers' `getUid()`. Re-adding them throws
  `Duplicate config key` and aborts the flush, leaving later toggles unregistered (a
  real shipped crash). See the javadoc on that method.
- `libs/` (six pinned jars: Tensura, ManasCore ×4, Jade) is **gitignored**. Never
  commit them.

---

## Branch sync (do this first)

Before starting any new development in this repo:

1. `git status` — confirm what uncommitted work exists. Never disturb or sweep it.
2. `git pull --ff-only` — sync with the remote. A fast-forward is the normal case,
   because we push straight to the shared branch.
3. If the pull would **not** fast-forward (the branch has diverged), or if it would
   collide with local uncommitted work, **stop and ask** rather than merging, rebasing
   or stashing on your own.

## Scope doctrine

- **One mod + bundled datapack.** No KubeJS scripts.
- **Data-driven over code** — prefer JSON/datapack content to Java when both work.
- **Public API only, no mixins** in the colony-gear-style addon mods. Sanctioned
  exceptions: `ds-tensura-fix` (tensura-dragon-skin-rebalance) and this repo
  (tensura-jade), both mixin-based by design.

## Verification

- Verify with the in-repo GameTest run (`./gradlew runGameTestServer`). Repos without
  GameTests verify with `./gradlew build`. Never verify against a live CurseForge
  instance.
- Keep the GameTest count in this file in sync with the `@GameTest` methods.
- **Client-render features cannot be proven headless.** The honest scope is
  "build green + the user's in-game check" - say so rather than overclaiming.

## Build environment

- JDK 21 (`JAVA_HOME` -> JDK 21); the toolchain targets Java 21.
- The Gradle wrapper is committed. `./gradlew build` compiles,
  `./gradlew runGameTestServer` runs the GameTest server.
- `libs/` (compile-only pinned jars) and `run/` are **gitignored**. Never commit
  third-party jars.
- On `run/` file-lock errors, kill any stale `java` process and retry.

## PowerShell quirks (this org is developed on Windows)

- A `git push` "error" in PowerShell is usually stderr passthrough — success is the
  `hash..hash  branch -> branch` line, not the absence of red text.
- `New-Object Type(args)` breaks on arithmetic — use `::new()` or a parenthesized
  argument.
- **Never write files with `Set-Content -Encoding UTF8`** — PowerShell 5.1 writes a
  UTF-8 BOM that breaks Gradle's parser and YAML. Use
  `[System.IO.File]::WriteAllText($path, $text)` (no BOM). Re-check for BOMs after
  any script-written edit.

## Git & release

- Remote is SSH: `git@github.com:HexaHexes/tensura-jade.git`.
- Push only after tests are green **and** the user has given the go.
- Never sweep the user's uncommitted work into a commit — commit only what is intended.
- Commit subjects: concise, imperative.
- **Version bump on finish:** always bump the version when a new feature or bugfix
  is finished.
- **One feature or bugfix per branch:** develop each on its own feature branch and
  merge it into main/master when finished.

## House style

- **No icons** unless the user explicitly asks.
- READMEs are **player-facing only**: pitch, mechanics in plain terms, in-game usage,
  required/optional dependencies, credits, license. No build internals, no class names.
- This repo is **LGPL v2.1**, copyright Bitwise Madness. `NOTICE` / attribution files
  are mandatory and must never be deleted.
- Deploy to the Tensura RPG instance **only** when the user asks.

## Naming

- **Repo and folder:** lowercase, hyphenated (`tensura-jade`).
- **`mod_id`:** lowercase with underscores (`tensura_jade`). Minecraft requires
  `[a-z][a-z0-9_]{1,63}`.
- **Jar / `archivesName`:** follows `mod_id` (`tensura_jade-<version>.jar`).
- **Java base package:** `org.bitwisemadness.<project-name>` - repo name lowercased,
  separators stripped (e.g. `ds-tensura-fix` -> `org.bitwisemadness.dstensurafix`).
  The Maven `group` in `build.gradle` matches (`org.bitwisemadness`).
- Keep repo name, folder name, `mod_id`, and the Java package aligned; the rename
  checklist in the template's `README.md` walks the whole set.
- **The template (`neoforge-mod-template`) is canonical for org-wide rules.** When a
  rule is added or changed in any repo's `agents/AGENTS.md`, mirror the change into
  the template so new repos inherit it.

## CI & release model

- `ci.yml` currently enforces the **version-bump guard only**: a beta or stable bump must
  change the version file and nothing else; an `-alpha` bump may bundle work; a PR labelled
  `breaking` forces the next clean bump to raise the major or minor version.
- **Build and GameTest jobs are deferred.** Six pinned `libs/` jars are not resolvable
  from a clean checkout via CurseMaven alone, so compiling in CI would fail every run.
  They land once a jar-fetch step exists. Until then, run `./gradlew build` and
  `runGameTestServer` locally before pushing.
- `version-bump.yml` opens an isolated version-bump PR (only the version file changes).
- CurseForge publishing (`curseforge.yml`) is **deferred** until the mod has a CurseForge
  listing and the `CURSEFORGE_PROJECT_ID` / `CURSEFORGE_API_KEY` secrets. Do not add it
  speculatively.
