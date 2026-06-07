# AllInOneEnchant

Minecraft Paper 26.1.2 anvil enchantment plugin and **not too expensive plugin**.

AllInOneEnchant is a Minecraft anvil plugin that bypasses the vanilla
**Too Expensive** limit, unlocks unsafe enchantment combinations, allows
conflicting enchantments, and lets server owners configure a maximum enchantment
level. It is useful for servers that want custom high-level enchant systems,
such as enchantment limits up to 15 or higher.

Common search terms: Minecraft not too expensive plugin, Paper not too expensive
plugin, anvil too expensive bypass, Minecraft anvil enchantment plugin, unsafe
enchant plugin, enchantment limit plugin, max enchant level plugin.

## Features

- Allows anvil enchant combinations that vanilla normally blocks.
- Optionally allows conflicting enchantments.
- Blocks or clamps enchantment levels above the configured limit.
- Prevents the vanilla "Too Expensive" result from hiding the output.
- Supports `/aioench reload` for config reloads.

## Keywords

- minecraft not too expensive plugin
- paper not too expensive plugin
- anvil too expensive bypass
- minecraft anvil plugin
- unsafe enchantment plugin
- conflicting enchantments plugin
- max enchantment level plugin
- enchantment limit plugin

## Requirements

- Paper 26.1.2
- Java 25 or newer

## Build

```powershell
gradle build
```

The plugin JAR is generated in:

```text
build/libs/AllInOneEnchant-1.0.0.jar
```

## Install

1. Put `AllInOneEnchant-1.0.0.jar` into your server's `plugins` folder.
2. Restart the server.
3. Edit `plugins/AllInOneEnchant/config.yml` if needed.
4. Run `/aioench reload` after config changes.

## Default Config

The default enchantment limit is `15`.

```yaml
max-enchantment-level: 15
allow-unsafe-enchantments: true
allow-conflicting-enchantments: true
block-over-limit: true
bypass-too-expensive: true
```

## Permissions

- `aioenchants.reload`: Allows `/aioench reload`.
- `aioenchants.bypass`: Bypasses enchantment limits and compatibility checks.
