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
        return tierOf(f, approvedSha1s, knownPackages, exemptIds, Set.of());
    }

    /**
     * {@link #tierOf(FileFingerprint, Set, Map, Set)} that also honours a {@linkplain FileFingerprint#carrier()
     * carrier}: the file is {@link Verdict#VERIFIED} when every id it declares (exempt ids aside)
     * is in {@code carrierApprovedIds} and the carrier's SHA-1 is in {@code approvedSha1s}. The
     * allowlist is the operator's: a carrier only vouches for the mods it has been accepted for,
     * so a file with a carrier and an unlisted id is judged exactly as it would be without one.
     *
     * @param carrierApprovedIds lowercase ids whose files may be verified by their carrier
     */
    public static Verdict tierOf(FileFingerprint f, Set<String> approvedSha1s, Map<String, Set<String>> knownPackages,
                                 Set<String> exemptIds, Set<String> carrierApprovedIds) {
        if (f != null && f.sha1() != null && approvedSha1s.contains(f.sha1())) return Verdict.VERIFIED;
        if (f == null || f.modIds().isEmpty()) return Verdict.SUSPECT;
        if (carrierVerified(f, approvedSha1s, exemptIds, carrierApprovedIds)) return Verdict.VERIFIED;
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

    /** Pure: an approved carrier for a file whose every non-exempt id is on the carrier allowlist. */
    static boolean carrierVerified(FileFingerprint f, Set<String> approvedSha1s, Set<String> exemptIds,
                                   Set<String> carrierApprovedIds) {
        if (f.carrier() == null || f.carrier().sha1() == null || carrierApprovedIds == null) return false;
        if (!approvedSha1s.contains(f.carrier().sha1())) return false;
        boolean any = false;
        for (String id : f.modIds()) {
            if (exemptIds.contains(id)) continue;
            if (!carrierApprovedIds.contains(id)) return false;
            any = true;
        }
        return any;
    }
}
