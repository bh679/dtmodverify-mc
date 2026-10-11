package games.brennan.dtmodverify.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The tier rule, with the same cases Dungeon Train's {@code UnapprovedModIntegrityTest} pins. */
class FileVerdictsTest {

    private static final Set<String> EXEMPT = Set.of("minecraft", "neoforge", "forge", "dungeontrain");

    private static final Map<String, Set<String>> KNOWN = Map.of(
        "jei", Set.of("mezz.jei"),
        "alpha", Set.of("dev.alpha"),
        "beta", Set.of("dev.beta"),
        "datapackmod", Set.of());

    private static FileFingerprint file(String name, String sha1, List<String> packages, String... ids) {
        return new FileFingerprint(name, 100, sha1, 1L, List.of(ids), packages);
    }

    /** What a host does with the rule: the display names of the suspect files, sorted. */
    private static List<String> unverifiedFrom(List<FileFingerprint> files, Set<String> approved,
                                               Map<String, Set<String>> known) {
        List<String> found = new ArrayList<>();
        for (FileFingerprint f : files) {
            if (FileVerdicts.tierOf(f, approved, known, EXEMPT) == Verdict.SUSPECT) found.add(f.display());
        }
        found.sort(String::compareTo);
        return List.copyOf(found);
    }

    @Test
    @DisplayName("Verified by hash, plausible by packages, unknown without knowledge, suspect on a mismatch")
    void tiers() {
        Set<String> hashes = Set.of("a".repeat(40));
        assertEquals(Verdict.VERIFIED,
            FileVerdicts.tierOf(file("x.jar", "a".repeat(40), List.of("net.xray"), "jei"), hashes, KNOWN, EXEMPT),
            "an approved hash is verified whatever the packages say");
        assertEquals(Verdict.SUSPECT,
            FileVerdicts.tierOf(file("x.jar", "b".repeat(40), List.of("mezz.jei")), hashes, KNOWN, EXEMPT), "no ids");
        assertEquals(Verdict.SUSPECT,
            FileVerdicts.tierOf(file("x.jar", "b".repeat(40), List.of("net.xray"), "datapackmod"), hashes, KNOWN, EXEMPT),
            "classes in a jar claiming a resource-only mod");
        assertEquals(Verdict.UNKNOWN,
            FileVerdicts.tierOf(file("x.jar", "b".repeat(40), List.of("mezz.jei"), "jei", "newmod"), hashes, KNOWN, EXEMPT),
            "one id without knowledge makes the file unknown, not suspect");
        assertEquals(Verdict.PLAUSIBLE,
            FileVerdicts.tierOf(file("x.jar", "b".repeat(40), List.of("mezz.jei"), "jei", "minecraft"), hashes, KNOWN, EXEMPT),
            "exempt ids are skipped");
        assertEquals(Verdict.UNKNOWN,
            FileVerdicts.tierOf(file("x.jar", "b".repeat(40), List.of(), "jei"), Set.of(), Map.of(), EXEMPT),
            "no knowledge at all is unknown");
        assertEquals(Verdict.UNKNOWN,
            FileVerdicts.tierOf(file("x.jar", "b".repeat(40), List.of("mezz.jei"), "jei", "minecraft"), hashes, KNOWN, Set.of()),
            "exemptions are the caller's: without them an unknown game id makes the file unknown");
    }

    @Test
    @DisplayName("Only a SUSPECT file is reported: the renamed cheat jar, not an honest unplaced build")
    void onlySuspectFilesAreReported() {
        String good = "a".repeat(40);
        List<FileFingerprint> files = List.of(
            file("jei-19.jar", good, List.of("mezz.jei"), "jei"),                          // verified by hash
            file("jei-20-fresh.jar", "b".repeat(40), List.of("mezz.jei", "org.apache"), "jei"), // plausible: fresh release
            file("totally-jei.jar", "c".repeat(40), List.of("net.xray"), "jei"),            // suspect: xray's code
            file("bundle.jar", "d".repeat(40), List.of("dev.alpha"), "alpha", "beta"),      // suspect: beta's code missing
            file("newmod.jar", "e".repeat(40), List.of("a.b"), "newmod"),                   // unknown: no knowledge
            file("resource.jar", "f".repeat(40), List.of(), "datapackmod"));               // plausible: no classes, known []
        assertEquals(List.of("bundle.jar (alpha, beta)", "totally-jei.jar (jei)"),
            unverifiedFrom(files, Set.of(good), KNOWN));
        assertEquals(List.of(), unverifiedFrom(List.of(), Set.of(), Map.of()),
            "no files scanned means nothing suspect — never 'everything is fake'");
    }
}
