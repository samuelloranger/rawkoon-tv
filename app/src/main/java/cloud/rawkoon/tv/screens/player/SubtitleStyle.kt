package cloud.rawkoon.tv.screens.player

import cloud.rawkoon.tv.data.SubtitleColor
import cloud.rawkoon.tv.data.SubtitleEdge
import cloud.rawkoon.tv.data.SubtitleSize

/** How subtitles are drawn, as plain values the player hands to its subtitle view. */
data class SubtitleStyle(
    val textColor: Int,
    val backgroundColor: Int,
    val edgeType: Int,
    val edgeColor: Int,
    /** The text height as a fraction of the video's height. */
    val textSizeFraction: Float,
)

private const val TRANSPARENT = 0x00000000
private const val BLACK = 0xFF000000.toInt()

/** The semi-transparent black box behind the text. */
// CaptionStyleCompat's EDGE_TYPE_* values, which are Android's CaptioningManager ones; kept as
// plain ints so this stays free of media3's opt-in API (a test checks they match)
internal const val EDGE_TYPE_NONE = 0
internal const val EDGE_TYPE_OUTLINE = 1
internal const val EDGE_TYPE_DROP_SHADOW = 2

private const val BOX = 0xB3000000.toInt()

fun subtitleStyle(size: SubtitleSize, color: SubtitleColor, box: Boolean, edge: SubtitleEdge) = SubtitleStyle(
    textColor = when (color) {
        SubtitleColor.WHITE -> 0xFFFFFFFF.toInt()
        SubtitleColor.YELLOW -> 0xFFFFEB3B.toInt()
        SubtitleColor.GREY -> 0xFFD0D0D0.toInt()
    },
    backgroundColor = if (box) BOX else TRANSPARENT,
    edgeType = when (edge) {
        SubtitleEdge.OUTLINE -> EDGE_TYPE_OUTLINE
        SubtitleEdge.SHADOW -> EDGE_TYPE_DROP_SHADOW
        SubtitleEdge.NONE -> EDGE_TYPE_NONE
    },
    edgeColor = BLACK,
    textSizeFraction = when (size) {
        SubtitleSize.SMALL -> 0.045f
        SubtitleSize.MEDIUM -> 0.058f
        SubtitleSize.LARGE -> 0.072f
        SubtitleSize.EXTRA_LARGE -> 0.088f
    },
)
