package games.brennan.dtmodverify.api;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/** The pure tier rule. No live state: every input is an argument, so a host can unit-test its whole scan. */
public final class FileVerdicts {

    private FileVerdicts() {}

    /**
     * Which tier is this file in?
     * <ul>
     *   <li>{@link Verdict#VERIFIED} — its SHA-1 is in {@code approvedSha1s}.</li>
     *   <li>{@link Verdict#PLAUSIBLE} — hash unknown, but every id it declares has known packages
     *       and the jar's packages intersect each id's — a fresh release or a GitHub build of a
     *       mod we approve.</li>
     *   <li>{@link Verdict#UNKNOWN} — hash unknown and some declared id has no package knowledge
     *       at all. The player did nothing wrong; the catalogue is behind.</li>
     *   <li>{@link Verdict#SUSPECT} — it declares no ids, or its packages share nothing with a
     *       declared id's known set: code that is not the mod it claims to be.</li>
     * </ul>
     * A resource-only mod (no classes) is known as an empty set: a jar with classes claiming it
     * is suspect, one without is plausible. Ids in {@code exemptIds} (the game, the loader, the
     * host mod) are skipped — they are judged by id elsewhere.
     *
     * @param f              the file
     * @param approvedSha1s  lowercase SHA-1s of approved published files
     * @param knownPackages  mod id → the package keys its genuine builds contain
     * @param exemptIds      lowercase ids that never take part in the package comparison
     */
    public static Verdict tierOf(FileFingerprint f, Set<String> approvedSha1s, Map<String, Set<String>> knownPackages,
                                 Set<String> exemptIds) {
        if (f != null && f.sha1() != null && approvedSha1s.contains(f.sha1())) return Verdict.VERIFIED;
        if (f == null || f.modIds().isEmpty()) return Verdict.SUSPECT;
        Set<String> jar = Set.copyOf(f.packages());
        boolean unknown = false;
        for (String id : f.modIds()) {
            if (exemptIds.contains(id)) continue;
            Set<String> known = knownPackages.get(id);
            if (known == null) { unknown = true; continue; }
            if (known.isEmpty() ? !jar.isEmpty() : Collections.disjoint(jar, known)) return Verdict.SUSPECT;
        }
        return unknown ? Verdict.UNKNOWN : Verdict.PLAUSIBLE;
    }
}
