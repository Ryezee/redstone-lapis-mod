# Redstone & Lapis Mod

A NeoForge mod for **Minecraft 1.21.1** that adds a set of redstone- and lapis-themed
items and gadgets. Built for a high-difficulty, lightly-horror modpack — the tools are meant to
help you survive and explore a darker, more dangerous world without trivializing it.

## Features

### Redstone Goggles
A head-slot wearable with two abilities:
- **Night vision** while worn — your way to see through dark caves and nights (pairs well with
  shader packs tuned for a darker world).
- **Ore pulse scan** — press the scan key (default `G`) to reveal nearby ores through walls with
  a brief, fading highlight. Works with modded ores automatically (matched via the `c:ores` tag).

More redstone and lapis gadgets are planned.

## Requirements
- Minecraft 1.21.1
- NeoForge 21.1.x

## Building
```bash
./gradlew build
```
The built jar lands in `build/libs/`. Use `./gradlew runClient` to launch a dev client.

## License
All Rights Reserved.
