# Tensura Jade

A **NeoForge 1.21.1** addon that serves **both** [Tensura: Reincarnated](https://www.curseforge.com/minecraft/mc-mods/tensura-reincarnated) and [Jade 🔍](https://www.curseforge.com/minecraft/mc-mods/jade): it moves Tensura's **analysis window** into Jade's **look-up tooltip**.

Aim at a mob and Tensura's race, alignment, HP, SHP, armor, aura, magicule, EP and abilities appear in the Jade tooltip you already read for everything else. Aim at a block and its hardness, resistance, light, harvest check, age and EP appear the same way. Tensura's own floating overlay — the same information, drawn twice — is hidden while this addon is doing that.

---

## Why it exists

Tensura ships a genuinely good analysis system: gated behind an `analysis_level` attribute, a distance attribute and an analysis mode, it tells you a target's race, stats and abilities, or a block's properties.

It draws that as a **separate floating panel** over the HUD — its own position, its own scale, its own opacity — sitting alongside Jade's window, which is where you already look for the name of whatever you're pointing at.

Tensura contains **no reference to Jade or Waila at all** (every one of its classes was scanned), and Jade knows nothing about Tensura. So the two were never going to find each other on their own.

This addon is the bridge, and it belongs in both mod lists: without Tensura there is no analysis to show, and without Jade there is no window to show it in.

---

## What it adds

### Entities

| Line | Content |
| --- | --- |
| *Race* | The target's Tensura race, drawn in the target's **alignment colour** — Tensura tints this line the same way |
| `Alignment:` | The target's alignment name, localised through Tensura's own keys |
| `HP:` | Health, whole points |
| `SHP:` | Spiritual health, rounded up |
| `Armor:` | Armor value |
| `AP:` | Aura, shortened (`12.3k`) exactly as Tensura shortens it |
| `MP:` | Magicule, shortened |
| `EP:` | Existence points, shortened — with Tensura's blurring, see below |
| *abilities* | Every qualifying ability, one per line |

Label wording is **Tensura's own translation keys**, not a transcription of its English text — so the panel reads, and localises, identically to the overlay it replaces. `Alignment:` is the only label this mod contributes itself.

### Blocks

| Line | Content |
| --- | --- |
| `Hardness:` | Destroy time; negative hardness reads as *Unbreakable* |
| `Explosion Resistance:` | Blast resistance |
| `Light:` | Max of block and sky light at the position |
| `Correct Tool:` | Yes / No, from the same check Tensura performs |
| `Age:` | Crop/vine age, when the block has one |
| `Redstone Strength:` | Signal strength, when the block has one |
| `EP:` | For Tensura's Charybdis core, which carries EP of its own |

Two calls here are deprecated in vanilla's mappings and are kept **on purpose**: `Block#getExplosionResistance()` and `Player#hasCorrectToolForDrops(BlockState)` are exactly the methods Tensura's own renderer uses. Swapping in their successors would risk computing numbers that differ from the overlay being replaced.

---

## Details worth knowing

### Abilities are listed, not cycled

Tensura cycles a **single ability at a time on a timer** — an animation for a HUD panel, and unreadable in a static tooltip. Every qualifying ability gets its own line instead.

What *qualifies* matches the overlay exactly:

- slots that are filled, and whose ability has actually been learned;
- **not** mastered — Tensura skips mastered abilities, because they are already known, and so does this;
- drawn in the ability's own colour, falling back to **red** when it has none, exactly as Tensura does.

If a target has abilities but none qualify, the line reads `None` — Tensura's own wording.

### EP blurring is preserved

While gating is on (the default), an EP beyond the viewer's grasp is shown as **`12k+`** rather than the real number, reproducing the capped readout Tensura draws. That cap exists so analysis cannot be used to measure the unmeasurable — this addon does not remove it.

### Nothing is read through reflection

Tensura draws its panel from `private static` methods inside `OverlayHandler`, straight onto the HUD — they cannot be called into a tooltip, and reaching into them reflectively would break on every Tensura update.

Instead the addon **re-reads the same public APIs** Tensura reads (ManasCore's existence, race, skill and ability storage, Tensura's own attributes and helpers) and rebuilds each line. Content parity, not renderer reuse: same data, same keys, same formatting rules.

The one thing reused directly is the **target**, from Jade's own raytrace.

---

## Overlay suppression

A client mixin cancels Tensura's `pre_renderAnalysis()` and `renderAnalysis()` — **both**, because Tensura's panel is not one method: the backdrop is drawn by the first, and the second is reached from it through the `ANALYSIS_LAYER` renderer (a method reference, which is why no bytecode anywhere says `invokestatic renderAnalysis`). Suppressing only one would leave a backdrop floating with nothing under it, or content with nothing behind it.

Both are analysis-only code paths that touch no other HUD layer, so cancelling either has no side effects.

Suppression is gated on **all three** of:

- the addon's own toggle (default **on**),
- Jade being installed,
- Tensura being installed,

and is declared in `neoforge.mods.toml` as `requiredMods=["tensura","jade"]`, with a mixin plugin that independently re-checks `ModList`. It can therefore never hide the overlay without leaving the same information in a tooltip — the case the addon exists to prevent.

While suppressed, **Tensura's HUD editor still works**: opening *Edit Analysis Position/Scale* suspends suppression, so the preview the player is trying to place stays visible.

If the toggle cannot be read at all, suppression **fails off** rather than on — deleting information with nothing in its place would be the one genuinely harmful failure.

---

## Configuration

Every toggle lives in **Jade's settings screen**, under a **Tensura Jade** category, next to the tooltip it controls:

| Key | Default | Effect |
| --- | --- | --- |
| `tensura_jade.entity_analysis` | `true` | Append entity analysis to Jade's tooltip |
| `tensura_jade.block_analysis` | `true` | Append block analysis to Jade's tooltip |
| `tensura_jade.show_abilities` | `true` | Include the ability list |
| `tensura_jade.hide_tensura_overlay` | `true` | Suppress Tensura's floating overlay |
| `tensura_jade.respect_analysis_gating` | `true` | Honour Tensura's analysis rules (below) |

### Analysis gating

With **Respect analysis gating** on — the default — the addon applies exactly the conditions Tensura applies to its own overlay:

- the viewer's `analysis_level` **and** `analysis_distance` attributes must both be positive (with neither, Tensura's panel draws at zero opacity, and so does this);
- the analysis **mode** decides what appears: `0` analyses whatever is aimed at, mobs taking priority over blocks; `1` mobs only; `2` blocks only;
- the target must be inside the viewer's analysis range;
- EP is blurred as described above.

Missing player data means no reading, as it does in Tensura.

Turn it off and the panel shows **everything to everyone** — useful for testing and screenshots, and a deliberate balance choice to hand back to the server.

---

## Requirements

- **Minecraft** 1.21.1
- **NeoForge** 21.1.233 or later
- **Java** 21
- **Tensura: Reincarnated** and **Jade** — both, at runtime

Without both installed the mod still loads, and does nothing: there is no tooltip to contribute to without Jade, and no analysis to contribute without Tensura. The mixin is not applied at all unless both are present.

---

## Building

```
gradlew build
```

The output jar is written to `build/libs/`.

### The `libs/` folder

A fresh clone will **not** build until you drop six third-party jars into a `libs/` folder. They are gitignored — this mod does not redistribute them — and the build compiles directly against them:

| Files in `libs/` | Needed for |
| --- | --- |
| `tensura-neoforge-2.0.1.3.jar` | the analysis content, and the mixin's target class |
| `manascore-neoforge-4.0.0.2.jar` | ManasCore's storage APIs (this jar JiJ-embeds the three below at runtime) |
| `manascore-race-neoforge-4.0.0.2.jar` | reading a target's race |
| `manascore-skill-neoforge-4.0.0.2.jar` | reading a target's abilities |
| `manascore-storage-neoforge-4.0.0.2.jar` | existence, aura, magicule, EP |
| `Jade-1.21.1-NeoForge-15.10.6.jar` | the plugin and tooltip API |

Take them from the matching projects' own downloads. Missing any produces a Gradle compile failure, not a silent partial build.

**These pins are deliberate.** The suppression mixin names Tensura's methods — `pre_renderAnalysis`, `renderAnalysis` — and resolves them without a refmap, so compiling against a different Tensura than the one being run is the failure mode that matters. Compile against the version you ship with.

Jade is not referenced by Tensura, and Tensura is not referenced by Jade; nothing in either jar had to be modified to make this work.

---

## Credits

- **Tensura: Reincarnated** by ManasMods — the analysis content, its translation keys, and every capability the addon reads.
- **Jade 🔍** by Snownee — the tooltip window, the plugin API, and the settings screen hosting the configuration.
- **ManasCore** by ManasMods — the existence, race, skill and ability storage APIs.
- Built on **NeoForge** and **ModDevGradle**.

This mod embeds no third-party assets, textures, models or code.

## License

MIT — see [`LICENSE`](LICENSE).
