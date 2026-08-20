package ir.tafakoritech.tpcall.util

import ir.tafakoritech.tpcall.data.contacts.CorporateContact

/**
 * Local search normalization for the corporate directory.
 *
 * Handles the practical Persian/Arabic differences without turning this into a
 * search engine:
 *  - trims whitespace
 *  - lower-cases Latin input
 *  - normalizes Arabic ي (U+064A) to Persian ی (U+06CC)
 *  - normalizes Arabic ك (U+0643) to Persian ک (U+06A9)
 *  - normalizes Arabic ة (U+0629) to ه (U+0647)
 *  - normalizes Arabic/Persian digit variants to ASCII digits
 */
object PersianSearch {

    /** Returns a normalized search key; empty input normalizes to empty. */
    fun normalize(input: String): String {
        val s = input.trim().lowercase()
        val sb = StringBuilder(s.length)
        for (ch in s) {
            sb.append(when (ch) {
                '\u064A' -> 'ی' // Arabic yeh -> Persian yeh
                '\u0649' -> 'ی' // alif maqsura -> Persian yeh
                '\u0626' -> 'ی' // yeh with hamza above -> Persian yeh
                '\u0643' -> 'ک' // Arabic kaf -> Persian kaf
                '\u0629' -> 'ه' // ta marbuta -> heh
                '\u06F0' -> '0'; '\u0660' -> '0'
                '\u06F1' -> '1'; '\u0661' -> '1'
                '\u06F2' -> '2'; '\u0662' -> '2'
                '\u06F3' -> '3'; '\u0663' -> '3'
                '\u06F4' -> '4'; '\u0664' -> '4'
                '\u06F5' -> '5'; '\u0665' -> '5'
                '\u06F6' -> '6'; '\u0666' -> '6'
                '\u06F7' -> '7'; '\u0667' -> '7'
                '\u06F8' -> '8'; '\u0668' -> '8'
                '\u06F9' -> '9'; '\u0669' -> '9'
                else -> ch
            })
        }
        return sb.toString()
    }

    /** True when [name] or [extension] matches [query] (local, normalized). */
    fun matches(name: String, extension: String, query: String): Boolean {
        val q = normalize(query)
        if (q.isEmpty()) return true
        return normalize(name).contains(q) || normalize(extension).contains(q)
    }

    /**
     * Deterministic sort: extension ascending. Employees learn extensions, and
     * a stable order prevents rows jumping around when presence changes.
     */
    fun sortByExtension(contacts: List<CorporateContact>): List<CorporateContact> {
        return contacts.sortedWith(
            Comparator { a: CorporateContact, b: CorporateContact ->
                compareValuesBy(a, b, { it.extension.length }, { it.extension }, { it.name })
            },
        )
    }
}
