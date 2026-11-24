package com.onemind.core.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Stroke icons from the design canvas (24 × 24 grid, round caps). Tint them with `Icon(tint = …)`.
 * Add new icons here from the canvas SVG paths instead of pulling in an icon library.
 */
object OneMindIcons {
    val Back = strokeIcon("Back", 2f, "M19 12H5", "m11 6-6 6 6 6")

    /** The brand mark (a glass), white on a dark 36 box on A1. */
    val Brand = strokeIcon("Brand", 2f, "M5 4h14l-7 8z", "M12 12v7", "M8 20h8")
    val Forward = strokeIcon("Forward", 2f, "M5 12h14", "m13 6 6 6-6 6")
    val ChevronDown = strokeIcon("ChevronDown", 2f, "m6 9.5 6 6 6-6")
    val Check = strokeIcon("Check", 3f, "m5 12 5 5 9-10")
    val Warning = strokeIcon("Warning", 1.9f, "M12 4 2.5 20h19z", "M12 10v4", "M12 17v.5")
    val Sparkle = strokeIcon("Sparkle", 1.9f, "M12 3l2 6 6 2-6 2-2 6-2-6-6-2 6-2z")
    val Calendar =
        strokeIcon(
            "Calendar",
            1.8f,
            "M6 5h12a2.5 2.5 0 0 1 2.5 2.5v10A2.5 2.5 0 0 1 18 20H6a2.5 2.5 0 0 1-2.5-2.5v-10A2.5 2.5 0 0 1 6 5z",
            "M3.5 10h17",
            "M8 3v4",
            "M16 3v4",
        )
    val Today = strokeIcon("Today", 1.75f, "M3 12h4l3-7 4 14 3-7h4")
    val Sales = strokeIcon("Sales", 1.75f, "M4 20h16", "M7 16v-5", "M12 16V6", "M17 16v-8")
    val Staff =
        strokeIcon(
            "Staff",
            1.75f,
            "M12.5 8a3.5 3.5 0 1 1-7 0a3.5 3.5 0 1 1 7 0z",
            "M2.5 20c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6",
            "M16 4.8a3.2 3.2 0 0 1 0 6.4",
            "M18 14.2c2.1.6 3.5 2.6 3.5 5.8",
        )

    /** One person: the account button without initials. */
    val Account =
        strokeIcon("Account", 1.75f, "M15.5 8a3.5 3.5 0 1 1-7 0a3.5 3.5 0 1 1 7 0z", "M5.5 20c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6")
    val Stock =
        strokeIcon("Stock", 1.75f, "M3.5 7.5 12 3.5l8.5 4v9L12 20.5l-8.5-4z", "M3.5 7.5 12 11.5l8.5-4", "M12 11.5v9")
}

private fun strokeIcon(
    name: String,
    strokeWidth: Float,
    vararg paths: String,
): ImageVector =
    ImageVector
        .Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            paths.forEach { data ->
                addPath(
                    pathData = PathParser().parsePathString(data).toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
