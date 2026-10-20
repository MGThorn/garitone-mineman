# Garitone Mineman

Client-side mining assistant for Minecraft 26.3 (Fabric), built on [Baritone](https://github.com/cabaletta/baritone) and [MaLiLib](https://github.com/sakura-ryoko/malilib).

## Features

- **Mining tasks:** select an area with Flint and let Baritone mine it, managed in the Task Manager (Smart Mineman, Hungry Mineman, stop when a tool is missing).
- **Storage points:** register chests and other storage blocks, remember their contents and quick-deposit your inventory into them, with per-chest rules.
- **Auto eat / easy eat:** eat automatically when hungry or with a hotkey, with a pick order, a blacklist and allowed hotbar slots.
- **Inventory sorting** (needs Item Scroller) and tool switch-back for Tweakeroo's tool switch (needs Tweakeroo).
- **ESP:** highlight dropped items, chosen blocks and chosen entities through walls.
- **Quality of life:** mute all sounds, hide the pumpkin overlay.

## Usage

Press **B** to open the menu. All options and hotkeys can be changed there, or through Mod Menu.

## Requirements

- Minecraft 26.3, Fabric Loader 0.19.5+, Fabric API
- MaLiLib 0.30.2+
- Baritone (standalone Fabric build)
- Optional: Item Scroller 0.33.1+ (inventory sorting), Tweakeroo 0.30.1+ (tool switch-back), Litematica, MiniHUD, Mod Menu

This is a client-only mod. Servers do not need it.

## Building

Put the Baritone `baritone-standalone-fabric-*.jar` into `libs/`, then run:

```
./gradlew build
```

The jar ends up in `build/libs/mineman-26.3-<version>.jar`.

## License

[LGPL-3.0-only](LICENSE)
