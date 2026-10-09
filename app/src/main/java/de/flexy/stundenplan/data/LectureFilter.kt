package de.flexy.stundenplan.data

/** Persönliche Filter: ausgeblendete Fächer und einzeln ausgeblendete Stunden. */
data class LectureFilter(
    val hiddenModules: Set<String> = emptySet(),
    val skipped: Set<String> = emptySet(),
) {
    fun shows(l: Lecture): Boolean = l.title !in hiddenModules && key(l) !in skipped

    companion object {
        fun key(l: Lecture) = "${l.title}|${l.start}"
    }
}
