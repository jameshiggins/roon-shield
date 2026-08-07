package com.roonshield.wiki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WikipediaServiceTest {

    private fun page(id: String, title: String, extract: String, disambig: Boolean = false): String {
        val props = if (disambig) ""","pageprops":{"disambiguation":""}""" else ""
        return """"$id":{"pageid":$id,"ns":0,"title":"$title","extract":"$extract"$props}"""
    }

    private fun body(vararg pages: String) =
        """{"batchcomplete":"","query":{"pages":{${pages.joinToString(",")}}}}"""

    @Test
    fun `parses title and extract`() {
        val entry = WikipediaService.parseEntry(
            body(page("42", "Aja (album)", "Aja is the sixth studio album by the American rock band Steely Dan, released in 1977.")),
        )
        assertEquals("Aja (album)", entry?.title)
        assertEquals(true, entry?.extract?.startsWith("Aja is the sixth"))
    }

    @Test
    fun `missing page returns null`() {
        val missing = """"-1":{"ns":0,"title":"No Such Album (album)","missing":""}"""
        assertNull(WikipediaService.parseEntry(body(missing)))
    }

    @Test
    fun `disambiguation and stub pages are skipped`() {
        val entry = WikipediaService.parseEntry(
            body(
                page("7", "Genesis", "Genesis may refer to: several things best listed elsewhere in this page.", disambig = true),
                page("9", "Genesis (band)", "Genesis are an English rock band formed at Charterhouse School, Godalming, Surrey, in 1967."),
            ),
        )
        assertEquals("Genesis (band)", entry?.title)
    }

    @Test
    fun `short extracts are rejected`() {
        assertNull(WikipediaService.parseEntry(body(page("3", "X", "Too short."))))
    }

    @Test
    fun `garbage json returns null`() {
        assertNull(WikipediaService.parseEntry("not json at all"))
    }

    @Test
    fun `release noise is stripped from album titles`() {
        assertEquals("Tour De France", WikipediaService.cleanTitle("Tour De France (2009 Digital Remaster; 2009 Remaster)"))
        assertEquals("Rumours", WikipediaService.cleanTitle("Rumours [Deluxe Edition]"))
        assertEquals("Aja", WikipediaService.cleanTitle("Aja (1977)"))
    }

    @Test
    fun `relevance uses whole words - EP must not match except`() {
        val race = WikiEntry(
            "Tour de France",
            "The Tour de France is an annual road cycling race held primarily in France, except during the World Wars; L'Équipe descends from its founding newspaper.",
        )
        org.junit.Assert.assertFalse(WikipediaService.relevant(race, listOf("album", "EP", "soundtrack", "Kraftwerk")))
        val record = WikiEntry("Tour de France Soundtracks", "Tour de France Soundtracks is the eleventh studio album by Kraftwerk.")
        org.junit.Assert.assertTrue(WikipediaService.relevant(record, listOf("album", "EP", "soundtrack", "Kraftwerk")))
    }

    @Test
    fun `real subtitles are kept`() {
        assertEquals(
            "Everything Harmony (Album of the Year)",
            WikipediaService.cleanTitle("Everything Harmony (Album of the Year)"),
        )
    }
}
