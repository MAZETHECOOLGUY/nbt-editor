# NBT Editor

A [Fabric](https://fabricmc.net/) mod for **Minecraft 26.1.2** that lets you edit the NBT / data components of any item **in-game**, from a structured GUI — no commands, no external tools.

Intended for singleplayer and your own server (it requires operator permission, level 2). Because edits are applied **server-authoritatively**, it also works in survival.

## Features

- **Open on any slot** — hover a slot in any container screen and press **Shift + Space**.
- **Structured NBT tree editor** — browse and edit the whole item (`{id, count, components}`): add / delete / rename keys, change value types, edit scalars and strings live.
- **Quick-edit presets** in a side toolbar:
  - **Rename** with a visual formatting toolbar — toggle **bold / italic / underline / strikethrough / obfuscated** and pick from the 16 vanilla colours with clickable swatches (legacy `&` colour codes still work too).
  - **Set count**.
  - **Toggle unbreakable**.
  - **Enchantments** — type-to-filter picker; left-click to add/edit a level, right-click to remove.
  - **Attributes** — edit / add / remove attribute modifiers (attack damage, attack speed, armor, etc.) with per-modifier slot and operation.
- **Live edits** — changes apply as you go; press **Save** to commit to the item (Cancel / Esc discards).

## How it works

The client encodes the hovered `ItemStack` to a `CompoundTag`, opens the editor, and on **Save** sends the edited NBT to the server, which verifies the player is an operator, decodes the tag authoritatively, and writes it back to the slot.

## Building

```bash
./gradlew build
```

The built jar lands in `build/libs/`. Requires JDK 25.

## License

Released under the [MIT License](LICENSE) — free and open source.

## Credits

- **Developer:** [MAZETHECOOLGUY](https://github.com/MAZETHECOOLGUY)
- Built with the help of **Claude** (Anthropic).
