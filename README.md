# Tensura Jade

Tensura Jade puts Tensura: Reincarnated's analysis window inside Jade's look-up
tooltip, so the information you already read for every block and mob now includes
Tensura's stats. One tooltip, drawn once, instead of a second floating panel.

Aim at a mob and its race, alignment, HP, SHP, armor, aura, magicule, EP and
abilities appear in the Jade tooltip. Aim at a block and its hardness,
resistance, light, harvest check, age and EP appear the same way. Tensura's own
overlay, which was showing all of that a second time, stays hidden while this
addon is doing the work.

## What it does

- **Entities show Tensura's stats.** Race (in the target's alignment colour),
  alignment, HP, SHP, armor, aura, magicule, EP, and every ability the target has
  actually learned. Abilities are listed one per line instead of cycling one at a
  time, so you can read them all at once.
- **Mob names show their variant.** A mob that has one is titled the way it
  actually reads, *Red Fang Direwolf* or *Emerald Fire Dragon*, rather than the
  plain species name. This covers Tensura, Ice and Fire, Mobs of Mythology and
  Mysticism mobs. The line needs the addon installed on the server too; without
  it, names stay vanilla.
- **Blocks show Tensura's analysis.** Hardness (negative reads as
  *Unbreakable*), blast resistance, light, whether the correct tool is equipped,
  crop/vine age, redstone strength, and EP for the Charybdis core.
- **No duplicate overlay.** Tensura's floating analysis panel is hidden so the
  same information isn't drawn twice. You can still move and scale that panel from
  Tensura's own HUD editor; suppression lifts while the editor is open, so you
  can see what you're placing.
- **Same rules as Tensura.** The addon honours Tensura's analysis gating by
  default. If your analysis level, analysis distance or analysis mode say you
  shouldn't see something, it stays hidden here too, and high EP is blurred the
  same way Tensura blurs it. The addon never becomes a way around Tensura's own
  balance.

## Settings

With Tensura and Jade installed there is nothing to configure first: aim at
something as you normally would with Jade, and the Tensura lines appear. Every
toggle lives in **Jade's settings screen**, under a **Tensura Jade** group, right
next to the tooltip it controls:

| Setting | Default | What it does |
|---|---|---|
| Entity analysis | On | Add Tensura's stats to mob tooltips |
| Block analysis | On | Add Tensura's analysis to block tooltips |
| Show abilities | On | Include the target's ability list |
| Variant titles | On | Show mob variants in the entity name |
| Hide Tensura overlay | On | Suppress Tensura's floating panel |
| Respect analysis gating | On | Honour Tensura's analysis rules |

Turn **Respect analysis gating** off and the panel shows everything to everyone.
It is handy for testing or screenshots, but it is a balance choice to leave to
your server.

## Dependencies

The mod loads and does nothing without these; install all four:

| | Mod | Why |
|---|---|---|
| Required | [Tensura: Reincarnated](https://www.curseforge.com/minecraft/mc-mods/tensura-reincarnated) | The analysis content being shown |
| Required | [Jade](https://www.curseforge.com/minecraft/mc-mods/jade) | The tooltip it appears in |
| Required | [ManasCore](https://www.curseforge.com/minecraft/mc-mods/manascore) | Tensura's core library |
| Required | Mob Info Common | Shared variant-name and analysis-gating logic (ships with this addon's packs) |

Made for the **Tensura RPG** pack (Minecraft 1.21.1, NeoForge).

## Credits

- [Tensura: Reincarnated](https://www.curseforge.com/minecraft/mc-mods/tensura-reincarnated) by ManasMods — the analysis content and the data it reads.
- [Jade](https://www.curseforge.com/minecraft/mc-mods/jade) by Snownee — the tooltip window and the settings screen.
- [ManasCore](https://www.curseforge.com/minecraft/mc-mods/manascore) by ManasMods — the storage APIs.

This addon embeds no third-party assets, textures, models or code.

## License

GNU Lesser General Public License v2.1 — see [`LICENSE`](LICENSE).
Copyright (c) 2026 Bitwise Madness.

## AI disclosure

Parts of this project were written with the assistance of AI coding tools.
AI-assisted code, data and documentation ship only after human review by the
maintainers.
