// Fixture (not compiled): floating-point values in money code in data. The total is never 0.5 (comment).
package com.onemind.core.data

fun vatMinor(amountMinor: Long): Long = (amountMinor * 0.21).toLong()

fun toMajor(minor: Long) = minor / 100.0

fun tipRatio(tipMinor: Long) = tipMinor.toDouble()

// Not flagged: no money name on the line, money in a string, or an exact integer computation.
val scale = 1.5f
val label = "total 2.5"
fun vatMinorExact(amountMinor: Long): Long = amountMinor * 21 / 100
