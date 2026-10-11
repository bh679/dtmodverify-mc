# Store listing — Dungeon Train Mod Verification (Modrinth + CurseForge)

**Slug:** dungeon-train-mod-verification · **Categories:** utility, library · **Loader:** NeoForge 1.21.1
**Environment:** client optional, server optional · **License:** PolyForm Shield 1.0.0
**Dependencies:** none
**Source:** https://github.com/bh679/dtmodverify-mc

## Summary (one line)
Checks your installed mod files against the published builds of Dungeon Train's approved mods. Shipped inside Dungeon Train.

## Description
Checks the mod files you have installed against the published builds of the mods on Dungeon
Train's approved list, so that Free Play only applies when something is genuinely not an approved
mod. Shipped inside Dungeon Train; installing it separately lets your launcher keep it up to date.

**What it does**
- Reads each installed mod file once per launch, off the main thread, and works out its file hash,
  the mod ids it declares and the names of the code packages inside it.
- Hands that to Dungeon Train when Dungeon Train asks, so a fresh release or a GitHub build of an
  approved mod is still recognised as that mod.
- Sends file hashes and package names, never file contents, to Dungeon Train's relay when Dungeon
  Train asks it to.

**What it doesn't do**
- No gameplay changes, no config, no commands. On its own it does nothing in the world.
- Nothing is sent anywhere unless Dungeon Train is installed and asks for it.

Originally part of [Dungeon Train](https://modrinth.com/mod/dungeon-train).
