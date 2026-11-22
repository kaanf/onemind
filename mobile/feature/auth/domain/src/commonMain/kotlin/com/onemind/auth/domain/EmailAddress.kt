package com.onemind.auth.domain

/** Code length and resend wait of the email sign-in (ADR 0004). */
object EmailCodeRules {
    const val CODE_LENGTH = 6
    const val RESEND_AFTER_SECONDS = 30
}

/**
 * A light client-side check that only enables *Send me a code*: one `@`, something before it and a dot
 * in the domain. The server validates for real; this only catches typos like a missing `@`.
 */
fun isPlausibleEmail(input: String): Boolean {
    val email = input.trim()
    if (email.any { it.isWhitespace() }) return false
    val at = email.indexOf('@')
    if (at <= 0 || at != email.lastIndexOf('@')) return false
    val domain = email.substring(at + 1)
    val dot = domain.lastIndexOf('.')
    return dot > 0 && dot < domain.length - 1
}
