package games.brennan.dtmodverify.api;

/** The verdict tiers of the file check — only {@link #SUSPECT} should cost a run (see {@link FileVerdicts#tierOf}). */
public enum Verdict {
    /** Its hash is on the approved list. */
    VERIFIED,
    /** Hash unknown, but every declared id has known packages and the jar's packages intersect each. */
    PLAUSIBLE,
    /** Hash unknown and some declared id has no package knowledge at all. */
    UNKNOWN,
    /** Declares no ids, or its packages share nothing with a declared id's known set. */
    SUSPECT
}
