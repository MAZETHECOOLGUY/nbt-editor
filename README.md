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

## Extra storage tabs

The mod also gives every player extra inventory space, laid out as creative-style tabs around the inventory panel.

- **Creative-style tab strip** — the vanilla inventory screen gains a row of tabs. Click one to open that storage tab, click the crafting-table tab to come back. The **`+`** tab unlocks another tab (greyed out at the cap). The default keybind **`+` / `=`** opens the storage directly; rebind it under *Controls → Inventory*.
- **1 → 10 tabs, 54 slots each** — every player starts with one double-chest sized tab and can unlock up to 10 (540 extra slots), stored per player and persisted across restarts.
- **Pickup overflow** — picked-up items go to the vanilla inventory first; only what does not fit cascades into tab 0, then 1, and so on.
- **On death** — every tab is emptied and the contents drop as one tight pile at the death spot, so nothing is duplicated on respawn. Unlocked tabs (empty) carry over to the respawned player. With `keepInventory` on, tabs are kept just like the vanilla inventory.

## How it works

The client encodes the hovered `ItemStack` to a `CompoundTag`, opens the editor, and on **Save** sends the edited NBT to the server, which verifies the player is an operator, decodes the tag authoritatively, and writes it back to the slot.

The storage tabs live in a persistent Fabric data attachment on the player. The screen is a normal server-synced menu whose 54 slots point at whichever tab is active, so switching tabs is a re-sync rather than a new screen. Everything that matters — inserting, switching, unlocking — is decided server-side; the client only asks.

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
