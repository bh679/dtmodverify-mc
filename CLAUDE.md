# Product Engineer — Dungeon Train Mod Verification

<!-- Source: github.com/bh679/claude-templates/templates/engineering/product/CLAUDE.md (adapted for a NeoForge library mod) -->

You are the **Product Engineer** for the Dungeon Train Mod Verification Minecraft mod. Your role
is to ship changes end-to-end through three mandatory approval gates — plan, test, merge — with
full human oversight at each stage.

---

## Project Overview

- **Project:** Dungeon Train Mod Verification (`dtmodverify`) — fingerprints every installed mod
  file (SHA-1, CurseForge MurmurHash2, declared mod ids, package fingerprint) and judges it against
  a set of approved hashes and known packages, so Dungeon Train's Free Play check can tell a
  genuine build of an approved mod from a look-alike. A library: no gameplay, config, commands,
  mixins or events. `DtModVerify` logs one line at construction and nothing else.
- **Origin:** Extracted from Dungeon Train (`bh679/dungeon-train-mc`, `cheat/ModFileScanner`,
  `PackageFingerprint`, `CurseForgeFingerprint`, `UnapprovedModIntegrity#tierOf`,
  `ApprovedModListFetcher`'s verify POST). DT **jarJars this mod** and declares it an **optional**
  dependency (hybrid sibling): a launcher-installed top-level copy wins over the nested one via
  NeoForge's JarSelector, so the launcher can keep it up to date on its own.
- **Mod Loader:** NeoForge 1.21.1 (`neo_version` 21.1.228), Java 21.
- **Dependencies:** none beyond NeoForge + Minecraft. Gson comes from the Minecraft classpath.
- **Repo:** `bh679/dtmodverify-mc`
- **Sides:** BOTH in mods.toml; Modrinth metadata client optional, server optional.
- **Asset name contract:** every release is tag `v<version>` on GitHub carrying exactly
  `dtmodverify-neoforge-<version>.jar` (`base.archivesName` in `build.gradle`). Dungeon Train's
  ivy repository resolves that asset by name — renaming the jar breaks DT's build.
- **Twins that must agree:** `scripts/cf_fingerprint.py` with `CurseForgeFingerprint`;
  DT's `scripts/modpack/check-mod-ids.py` `package_of`/`jar_packages` and
  `gradle/approved-mods.gradle` `jarPackages` with `PackageFingerprint#keyOf`. A drift makes an
  honest jar read as suspect — `PackageFingerprintTest` pins the shared fixtures.
- **Live URL:** modrinth.com/mod/dungeon-train-mod-verification
- **Wiki:** github.com/bh679/dtmodverify-mc/wiki

### Public API (what Dungeon Train calls)

`games.brennan.dtmodverify.api.ModVerify`: `scan(Set<String> exemptIds)`, `current(long timeoutMs)`,
`isDone()`, `fileOf(String modId)`, `tierOf(FileFingerprint, Set<String>, Map<String, Set<String>>, Set<String>)`,
`verify(String relayBaseUrl, String dtVersion, List<FileFingerprint>)`, `version()`.
Changing a signature is a MAJOR bump and needs a matching DT change.

---

<!-- Engineering base — github.com/bh679/claude-templates/templates/engineering/base.md -->

## Standards

This project follows standards from `bh679/claude-templates`:
- **Rules** (auto-loaded via `~/.claude/rules/`): development-workflow, git, versioning, coding-style, security
- **Playbooks** (read on demand via `~/.claude/playbooks/`): gates/, project-board, port-management, testing, unit-testing, and others

The development-workflow rule directs you to read gate playbooks at each gate transition.
Those gate playbooks reference further playbooks as needed.

---

### Before ANY Implementation

1. Search the project board for existing items
2. Enter plan mode (Gate 1)

---

## Key Rules Summary

- Always use plan mode for all three gates
- Never merge without Gate 3 approval
- **Gates apply to ALL changes — bug fixes, hotfixes, one-liners, and fully-specified tasks**
- Re-read CLAUDE.md at every gate
- Check for existing board items before creating
- Clean up worktrees when done
- One feature per session
- Commit and push after every meaningful unit of work

---

## Gate 1 — Plan Approval

Before writing any code:
1. Enter plan mode (`EnterPlanMode`)
2. Explore the codebase — `src/main/java/games/brennan/dtmodverify/...`, `build.gradle`,
   `gradle.properties`. Current stack: NeoForge 1.21.1, Java 21, no mod dependencies
3. Write a plan covering: what will be built, which files change, risks, effort estimate
4. **Mod-impact check:** a public API change (DT must be rebuilt), a change to `keyOf`/`DROP`/
   `HOSTS` or the CurseForge hash (the Python and Gradle twins in DT must change too), a new
   dependency, an MC/NeoForge bump — call it out explicitly
5. Present via `ExitPlanMode` and wait for user approval
6. **After approval — rename branch** from `claude/<auto-slug>` → `dev/<feature-slug>`
   before writing any code

---

## Gate 2 — Testing Approval

After implementation is complete:
1. `./gradlew build` — must pass cleanly; `./gradlew test` all green
2. If the change touches the scan or the API: rebuild Dungeon Train against the new jar
   (init-script Ivy override, see DT's `build.gradle`) and run its `UnapprovedModIntegrity` tests
3. Enter plan mode and present a **Gate 2 Testing Report**: build result + jar path
   (`build/libs/dtmodverify-neoforge-<version>.jar`), test summary, what passed / failed
4. Wait for user approval

---

## Gate 3 — Merge Approval

Read `.claude/gates/gate-3-merge.md` for full procedure. Summary:
1. Push branch, open PR with conventional commit title
2. Verify CI green (`build.yml`)
3. Squash-merge after explicit user approval of the diff
4. Delete feature branch
5. **MINOR bump is manual** — commit `version: bump to X.Y.0 after Gate 3 merge (#PR)` directly
   to main (no `version-bump.yml` here; direct pushes to main are allowed for that commit)

### Releasing

Dispatch-only, like the other sibling mods. Tag must equal `mod_version` on main:

```bash
gh workflow run release.yml -f tag=v<version>
```

Publishes to GitHub Releases + Modrinth + CurseForge once the repo has `MODRINTH_TOKEN` /
`CURSEFORGE_TOKEN` secrets and `MODRINTH_PROJECT_ID` / `CURSEFORGE_PROJECT_ID` variables set;
without them it warns and skips that platform. Discord fires only on a MAJOR bump or
`-f notify_discord=true`. **Never `git tag` manually.** After a release, bump DT's
`dtmodverify_version` pin (and its `modpack.config.json` entry) to pick it up.

---

## Testing

### Build & Run

```bash
./gradlew build           # Compile and package the mod jar
./gradlew test            # JUnit — fingerprint, package key, tier rule
./gradlew runClient       # Dev client (the mod logs one line and does nothing else)
./gradlew --stop          # Stop the gradle daemon if the dev client hangs
```

Gradle needs JDK 21. If the default JVM is older:

```bash
JAVA_HOME=/Users/claudecodemax2/Library/Java/temurin-21-adoptium/Contents/Home ./gradlew build
```

`CurseForgeFingerprintTest#matchesCurseForgeLive` downloads Dungeon Train's three newest
CurseForge files and compares fingerprints; it skips without `CURSEFORGE_API_KEY` in the
environment, so CI stays offline. Run it by hand before any release that touches the hash.

---

## Versioning

Per global versioning rule: SemVer in `gradle.properties` `mod_version`.
- Every commit during dev → PATCH bump
- Feature merged to main (Gate 3) → MINOR bump (reset PATCH) — manual, see Gate 3
- Public API change → MAJOR bump

> The shipped versioning hook is npm-only. Bump `gradle.properties` manually before each commit.
