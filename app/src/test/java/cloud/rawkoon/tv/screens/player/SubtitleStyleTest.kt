package cloud.rawkoon.tv.screens.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import cloud.rawkoon.tv.data.SubtitleColor
import cloud.rawkoon.tv.data.SubtitleEdge
import cloud.rawkoon.tv.data.SubtitleSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
class SubtitleStyleTest {
    private fun style(
        size: SubtitleSize = SubtitleSize.MEDIUM,
        color: SubtitleColor = SubtitleColor.WHITE,
        box: Boolean = false,
        edge: SubtitleEdge = SubtitleEdge.OUTLINE,
    ) = subtitleStyle(size, color, box, edge)

    @Test
    fun `the defaults are white outlined text on no background at the original size`() {
        val s = style()
        assertEquals(0xFFFFFFFF.toInt(), s.textColor)
        assertEquals(0, s.backgroundColor)
        assertEquals(EDGE_TYPE_OUTLINE, s.edgeType)
        assertEquals(0xFF000000.toInt(), s.edgeColor)
        assertEquals(0.058f, s.textSizeFraction, 0f)
    }

    @Test
    fun `sizes grow from small to extra large`() {
        val sizes = SubtitleSize.entries.map { style(size = it).textSizeFraction }
        assertEquals(sizes.sorted(), sizes)
        assertEquals(sizes.size, sizes.toSet().size)
    }

    @Test
    fun `the box is a translucent black`() {
        val bg = style(box = true).backgroundColor
        assertEquals(0, bg and 0x00FFFFFF)
        val alpha = bg ushr 24
        assertTrue(alpha in 1..254)
    }

    @Test
    fun `the edge ints match media3's`() {
        assertEquals(CaptionStyleCompat.EDGE_TYPE_NONE, EDGE_TYPE_NONE)
        assertEquals(CaptionStyleCompat.EDGE_TYPE_OUTLINE, EDGE_TYPE_OUTLINE)
        assertEquals(CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, EDGE_TYPE_DROP_SHADOW)
    }

    @Test
    fun `each colour and edge maps to its own value`() {
        assertEquals(SubtitleColor.entries.size, SubtitleColor.entries.map { style(color = it).textColor }.toSet().size)
        assertEquals(EDGE_TYPE_DROP_SHADOW, style(edge = SubtitleEdge.SHADOW).edgeType)
        assertEquals(EDGE_TYPE_NONE, style(edge = SubtitleEdge.NONE).edgeType)
    }
}
