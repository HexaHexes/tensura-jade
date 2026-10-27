# Tensura Jade

Puts **Tensura: Reincarnated**'s analysis window inside **Jade**'s look-up tooltip, so
the information you already read for every block and mob now includes Tensura's stats —
in the same place, drawn once, instead of in a second floating panel.

Aim at a mob and its race, alignment, HP, SHP, armor, aura, magicule, EP and abilities
appear in the Jade tooltip. Aim at a block and its hardness, resistance, light, harvest
check, age and EP appear the same way. Tensura's own overlay — which showed the same
thing a second time — stays hidden while this addon is doing that.

## What it does

- **Entities show Tensura's stats.** Race (in the target's alignment colour), alignment,
  HP, SHP, armor, aura, magicule, EP, and every ability the target has actually learned.
  Abilities are listed one per line rather than cycling one at a time, so you can read
  them all at once.
- **Blocks show Tensura's analysis.** Hardness (negative reads as *Unbreakable*), blast
  resistance, light, whether the correct tool is equipped, crop/vine age, redstone
  strength, and EP for the Charybdis core.
- **No duplicate overlay.** Tensura's floating analysis panel is hidden so the same
  information isn't drawn twice. You can still move and scale that panel from Tensura's
  own HUD editor — suppression lifts while the editor is open so you can see what
  you're placing.
- **Same rules as Tensura.** The addon honours Tensura's analysis gating by default:
  if your analysis level, analysis distance or analysis mode say you shouldn't see
  something, it stays hidden here too, and high EP is blurred the same way Tensura
  blurs it. This keeps the addon from becoming a way around Tensura's own balance.

## Getting it in-game

Install it and play — no commands, no configuration required. Once both Tensura and
Jade are installed, just aim at something as you normally would with Jade and the
Tensura lines appear in the tooltip.

### Settings

Every toggle lives in **Jade's settings screen**, under a **Tensura Jade** group,
right next to the tooltip it controls:

| Setting | Default | What it does |
|---|---|---|
| Entity analysis | On | Add Tensura's stats to mob tooltips |
| Block analysis | On | Add Tensura's analysis to block tooltips |
| Show abilities | On | Include the target's ability list |
| Hide Tensura overlay | On | Suppress Tensura's floating panel |
| Respect analysis gating | On | Honour Tensura's analysis rules |

Turn **Respect analysis gating** off and the panel shows everything to everyone —
handy for testing or screenshots, but a balance choice to leave to your server.

## Dependencies

The mod loads and does nothing without these — install all three:

| | Mod | Why |
|---|---|---|
| Required | [Tensura: Reincarnated](https://www.curseforge.com/minecraft/mc-mods/tensura-reincarnated) | The analysis content being shown |
| Required | [Jade](https://www.curseforge.com/minecraft/mc-mods/jade) | The tooltip it appears in |
| Required | [ManasCore](https://www.curseforge.com/minecraft/mc-mods/manascore) | Tensura's core library |

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
