# Dungeon Train Mod Verification

A NeoForge 1.21.1 library mod that fingerprints the mod files a player has installed and judges
them against a list of approved file hashes and known code packages. A mod id is a line in
`mods.toml` that any jar can claim; this mod looks at the file itself — its SHA-1 (what Modrinth
and the relay key on), its CurseForge MurmurHash2 fingerprint, every mod id the file declares
(embedded jar-in-jar mods included) and the top-level packages of its classes — so Dungeon
Train's Free Play check can tell a genuine build of an approved mod from a look-alike. It has no
gameplay, config, commands, mixins or events of its own.

## Origin

Extracted from [Dungeon Train](https://github.com/bh679/dungeon-train-mc) (`cheat/ModFileScanner`,
`PackageFingerprint`, `CurseForgeFingerprint` and the tier rule of `UnapprovedModIntegrity`).
Dungeon Train bundles this mod **jar-in-jar** and calls it from its approved-mod check; it also
declares it as an optional dependency, so a launcher-installed top-level copy wins over the
nested one (NeoForge's JarSelector drops the nested duplicate).

## Building

JDK 21.

```bash
./gradlew build     # jar -> build/libs/dtmodverify-neoforge-<version>.jar
./gradlew test      # JUnit 5 pure-logic tests
```

Releases are dispatch-only (`release.yml`): the GitHub release tagged `v<version>` carries the
asset `dtmodverify-neoforge-<version>.jar`, which is the name Dungeon Train's ivy repository
resolves.

## Public API

All entry points are static, under `games.brennan.dtmodverify.api`:

| Class | Members |
|---|---|
| `ModVerify` | `scan(Set<String> exemptIds)` → `CompletableFuture<List<FileFingerprint>>` (once per JVM, daemon thread) · `current(long timeoutMs)` → `List<FileFingerprint>` · `isDone()` · `fileOf(String modId)` · `tierOf(FileFingerprint, Set<String> approvedSha1s, Map<String, Set<String>> knownPackages, Set<String> exemptIds)` → `Verdict` · `verify(String relayBaseUrl, String dtVersion, List<FileFingerprint>)` → `CompletableFuture<VerifyClient.VerifyAnswer>` · `version()` |
| `FileFingerprint` | record `(fileName, size, sha1, murmur2, modIds, packages)` · `toJson()` · `display()` |
| `Verdict` | `VERIFIED`, `PLAUSIBLE`, `UNKNOWN`, `SUSPECT` — only `SUSPECT` should cost a run |
| `FileVerdicts` | the pure `tierOf(...)` rule |

Lower level: `fingerprint.PackageFingerprint` (`keyOf`, `isValid`, `ofJar`, `ofBytes`, `MAX`),
`fingerprint.CurseForgeFingerprint` (`hash`, `normalizedLength`), `scan.InstalledFileScanner`,
`relay.VerifyClient` (`verifyUrl`, `verifyBody`, `verify`, `logVerdicts`).

`scripts/cf_fingerprint.py` is the Python twin of `CurseForgeFingerprint`; the unit-test fixtures
come from it.

## Licence

PolyForm Shield 1.0.0 — see [LICENSE](LICENSE).
