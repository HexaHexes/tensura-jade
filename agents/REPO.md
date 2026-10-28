# Repo rules — Tensura Jade

Repo-specific rules and notes. The org-wide `AGENTS.md` beside this file is
maintained in [`neoforge-mod-template`](https://github.com/HexaHexes/neoforge-mod-template)
and pulled in verbatim - **never hand-edit it.** Everything in this file is this
repo's own and is edited freely; keep it aligned with the org rules rather than
restating them.

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

**Remote:** `github.com/HexaHexes/tensura-jade` (public, default branch `main`).

---

## Gotchas (do not regress them)

- The suppression mixin names Tensura's `pre_renderAnalysis` / `renderAnalysis` and
  resolves them **without a refmap** - compile against the exact pinned CurseMaven
  coordinate of the Tensura jar you ship with. Version drift there is the failure
  mode that matters; bump pins deliberately, never implicitly.
- Client-only classes (Jade's `Screen`, Tensura's `RenderHelper`) must never load on a
  dedicated server. The number-shortening helper is **transcribed**, not called, for
  this reason; the config tests use a hand-written stub, not a `java.lang.reflect.Proxy`.
- `TensuraJadeConfig.register` must **not** declare `entity_analysis` / `block_analysis`
  — Jade creates those from the providers' `getUid()`. Re-adding them throws
  `Duplicate config key` and aborts the flush, leaving later toggles unregistered (a
  real shipped crash). See the javadoc on that method.
- `libs/` (six pinned jars: Tensura, ManasCore ×4, Jade) is **gitignored**. Never
  commit them. **All six now resolve as pinned CurseMaven coordinates** - see the
  dependency rule in the build environment section; the folder is retired.

## Scope doctrine - repo additions

- **Mixins are sanctioned here:** the suppression mixin is the entire point of the
  addon, guarded by `requiredMods` and `TensuraJadeMixinPlugin`. No other mixin
  without a decision; the org rule "public API only, no mixins" still applies to
  new addon repos.

## Naming - this repo

- Repo and folder: `tensura-jade`; `mod_id`: `tensura_jade`; Java base package
  `org.bitwisemadness.tensurajade`, with the Maven `group` in `build.gradle`
  matching.

## CI & release model - this repo

- `ci.yml` runs the template's three checks (junit, gametest, version-bump).
- `release.yml` cuts the GitHub release for `v<mod_version>` on a push to `main`
  (pre-releases only when the repo variable `ALPHA_RELEASES` is true), and
  `publish.yml` publishes the Maven artifact to the org's GitHub Packages
  registry when that release is published - see the org rulebook for the
  consumer-side auth details (`PACKAGE_READ_TOKEN`).