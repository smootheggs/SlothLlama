package app.localmind.assistant

import app.localmind.assistant.data.books.CoverStore
import app.localmind.assistant.data.books.PaperFiles
import app.localmind.assistant.data.books.SheetReader
import app.localmind.assistant.domain.books.Book
import app.localmind.assistant.domain.books.DefaultShelf
import app.localmind.assistant.domain.books.Entry
import app.localmind.assistant.domain.books.GroupBy
import app.localmind.assistant.domain.books.Grouping
import app.localmind.assistant.domain.books.ReferenceRule
import app.localmind.assistant.domain.books.ShelfPick
import app.localmind.assistant.domain.books.Topics
import app.localmind.assistant.domain.books.Works
import app.localmind.assistant.domain.books.Forms
import app.localmind.assistant.domain.books.GistCard
import app.localmind.assistant.domain.books.LibrarianContext
import app.localmind.assistant.domain.books.Shelf
import app.localmind.assistant.domain.books.ShelfImport
import app.localmind.assistant.domain.books.ShelfStats
import app.localmind.assistant.domain.books.Status
import app.localmind.assistant.domain.books.withStatus
import app.localmind.assistant.domain.persona.BuiltInPersonas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** SlothBooks: reading a list, the shelf's counts and links, gist cards and the Librarian's notes. Made-up books only. */
class SlothBooksUnitTests {

    private var n = 0
    private fun id() = "b${n++}"

    private fun book(title: String, author: String = "A. Writer", year: Int? = 1900, form: String = Forms.NOVEL, genre: String = "Mystery",
                     series: String = "", no: Int? = null, status: String = Status.UNREAD, region: String = "Europe", language: String = "English") =
        Book(id = id(), title = title, author = author, year = year, form = form, genre = genre, series = series, seriesNo = no,
            status = status, region = region, language = language)

    // --- reading a list ---

    @Test
    fun `a raw list's columns are found by name and the extras left out`() {
        val row = mapOf(
            "Title" to "The Lantern Road", "Author" to "Mira Hale", "Publication Year" to "1844", "Genre" to "Fiction",
            "Subgenre" to "Adventure / Historical Fiction", "Fiction/Nonfiction" to "Fiction", "Original Language" to "French",
            "Continent" to "Europe", "ISBN" to "9780000000000", "Confidence Score" to "HIGH", "Description" to "A road, a lantern."
        )
        val b = ShelfImport.toBook(row, 1L) { "x" }!!
        assertEquals("The Lantern Road", b.title)
        assertEquals(1844, b.year)
        assertEquals(Forms.NOVEL, b.form)
        assertEquals("Adventure", b.genre)
        assertEquals("French", b.language)
        assertEquals("Europe", b.region)
        assertEquals("A road, a lantern.", b.about)
        assertEquals(Status.UNREAD, b.status)
    }

    @Test
    fun `the tidied layout keeps form, series and reading language`() {
        val row = mapOf("Title" to "Tide Clock", "Author" to "R. Penn", "Year" to "-440", "Form" to "Drama", "Series" to "Harbour plays",
            "Number" to "2", "Written in" to "Ancient Greek", "Reading in" to "English")
        val b = ShelfImport.toBook(row, 1L) { "x" }!!
        assertEquals(-440, b.year)
        assertEquals("c. 440 BC", b.yearText)
        assertEquals(Forms.DRAMA, b.form)
        assertEquals("Harbour plays", b.series)
        assertEquals(2, b.seriesNo)
        assertEquals("English", b.readingIn)
    }

    @Test
    fun `years read from words and numbers`() {
        assertEquals(1844, ShelfImport.year("1844.0"))
        assertEquals(-380, ShelfImport.year("380 BC"))
        assertEquals(1400, ShelfImport.year("c. 1400"))
        assertNull(ShelfImport.year("unknown"))
    }

    @Test
    fun `an import skips what's already on the shelf and keeps two editions apart`() {
        val shelf = listOf(book("Tide Clock", "R. Penn"))
        val rows = listOf(
            mapOf("Title" to "Tide Clock", "Author" to "R. Penn"),
            mapOf("Title" to "The Three Bridges", "Author" to "Lo Wen", "Reading in" to "English"),
            mapOf("Title" to "The Three Bridges", "Author" to "Lo Wen", "Reading in" to "Chinese"),
            mapOf("Author" to "Nobody")
        )
        val p = ShelfImport.preview(rows, shelf, 1L) { id() }
        assertEquals(2, p.fresh.size)
        assertEquals(1, p.known)
        assertEquals(1, p.blank)
        assertEquals(1, p.twins.size)
    }

    @Test
    fun `a csv list is read, quotes and all`() {
        val rows = SheetReader.csv("Title,Author,Year\n\"Salt, and Iron\",K. Moss,1999\nTide Clock,R. Penn,\n")
        assertEquals(2, rows.size)
        assertEquals("Salt, and Iron", rows[0]["Title"])
        assertFalse(rows[1].containsKey("Year"))
    }

    @Test
    fun `an xlsx list is read from its shared strings, preferring the Shelf sheet`() {
        val bytes = xlsx(
            sheets = listOf("Notes" to "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>2</v></c></row>", "Shelf" to
                "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c><c r=\"D1\" t=\"inlineStr\"><is><t>Year</t></is></c></row>" +
                "<row r=\"2\"><c r=\"A2\" t=\"s\"><v>3</v></c><c r=\"B2\" t=\"s\"><v>4</v></c><c r=\"D2\"><v>1844.0</v></c></row>"),
            strings = listOf("Title", "Author", "just notes", "The Lantern Road", "Mira Hale")
        )
        val rows = SheetReader.read(bytes, "list.xlsx")
        assertEquals(1, rows.size)
        assertEquals("The Lantern Road", rows[0]["Title"])
        assertEquals("Mira Hale", rows[0]["Author"])
        assertEquals("1844", rows[0]["Year"])
        assertEquals(27, SheetReader.column("AB12"))
    }

    // --- the shelf ---

    @Test
    fun `finishing a book dates it and counts the read; starting again starts afresh`() {
        val today = LocalDate.of(2026, 11, 20)
        val read = book("Tide Clock").withStatus(Status.READING, today).copy(progress = 40).withStatus(Status.READ, today)
        assertEquals("2026-11-20", read.finished)
        assertEquals(100, read.progress)
        assertEquals(1, read.reads)
        val again = read.withStatus(Status.READING, today.plusDays(3))
        assertEquals("2026-11-23", again.started)
        assertEquals("", again.finished)
        assertEquals(0, again.progress)
    }

    @Test
    fun `series keep their order and connections come series first`() {
        val two = book("Second Light", series = "Harbour", no = 2)
        val one = book("First Light", series = "Harbour", no = 1, status = Status.READ)
        val other = book("Other Night", author = "B. Other", genre = "Mystery", year = 1905)
        val all = listOf(two, one, other)
        val s = ShelfStats.seriesOf(all).single()
        assertEquals(listOf("First Light", "Second Light"), s.books.map { it.title })
        assertEquals(1, s.read)
        assertEquals("Second Light", s.next?.title)
        val links = Shelf.connections(one, all)
        assertEquals("Second Light", links.first().book.title)
        assertTrue(links.any { it.book.title == "Other Night" && it.why == "same genre and era" })
    }

    @Test
    fun `picks start with the queue and the next in a series`() {
        val read = book("First Light", series = "Harbour", no = 1, status = Status.READ).copy(updatedAt = 5)
        val next = book("Second Light", series = "Harbour", no = 2)
        val queued = book("Queued One", author = "C. Q").copy(upNext = 1)
        val picks = Shelf.picks(listOf(read, next, queued))
        assertEquals("Queued One", picks[0].book.title)
        assertEquals("next in Harbour", picks[1].why)
    }

    @Test
    fun `stats are counted, eras in order`() {
        val all = listOf(book("A", year = 1700), book("B", year = 1850, form = Forms.NONFICTION), book("C", year = 2015, language = "French"))
        val s = ShelfStats.of(all, LocalDate.of(2026, 1, 1))
        assertEquals(3, s.books)
        assertEquals(2, s.fiction)
        assertEquals(1, s.nonfiction)
        assertEquals(1, s.eras.first { it.first == "Before 1800" }.second)
        assertEquals(1, s.originals["French"])
    }

    @Test
    fun `search ignores case, accents and a leading article`() {
        val b = book("The Café Years", author = "Élise Marchand")
        assertTrue(Shelf.matches(b, "cafe"))
        assertTrue(Shelf.matches(b, "elise"))
        assertFalse(Shelf.matches(b, "harbour"))
    }

    // --- organising (1.30) ---

    @Test
    fun `courses, cookbooks and guides go to Reference, a travel memoir doesn't`() {
        val course = book("Speak Lumarian Today", form = Forms.NONFICTION, genre = "Language Learning")
        val guide = book("Harbour Towns", form = Forms.NONFICTION, genre = "Travel").copy(subgenre = "Travel Guide / Coast")
        val memoir = book("Walking the Coast", form = Forms.NONFICTION, genre = "Travel").copy(subgenre = "Travel writing")
        val novel = book("The Cook's Guide to Murder", form = Forms.NOVEL)
        assertTrue(ReferenceRule.looksLikeReference(course))
        assertTrue(ReferenceRule.looksLikeReference(guide))
        assertFalse(ReferenceRule.looksLikeReference(memoir))
        assertFalse(ReferenceRule.looksLikeReference(novel))
        val imported = ShelfImport.toBook(mapOf("Title" to "Speak Lumarian Today", "Genre" to "Language Learning", "Fiction/Nonfiction" to "Nonfiction"), 1L) { "x" }!!
        assertEquals(Status.REFERENCE, imported.status)
        assertTrue(DefaultShelf.REFERENCE.holds(imported))
        assertFalse(DefaultShelf.TO_READ.holds(imported))
    }

    @Test
    fun `an illustrated edition and a translation are copies; a sequel isn't`() {
        val a = book("The Long Road", "Ines Varga")
        val b = book("The Long Road Illustrated", "Ines Varga")
        val c = book("Romance of the Two Rivers", "Lo Wen")
        val d = book("Two Rivers", "Lo Wen").copy(readIn = "Chinese")
        val sequel = book("The Long Road Home", "Ines Varga")
        val copies = Works.copies(listOf(a, b, c, d, sequel))
        assertEquals(listOf(b.id), copies[a.id]?.map { it.id })
        assertEquals(listOf(c.id), copies[d.id]?.map { it.id })
        assertNull(copies[sequel.id])
    }

    @Test
    fun `grouped by author, prolific authors stack and copies are one row`() {
        val plays = (1..5).map { book("Play $it", "Will Quill", form = Forms.DRAMA) }
        val lone = book("Quiet Shore", "Ada Quinn")
        val one = book("The Long Road", "Ines Varga")
        val two = book("The Long Road Illustrated", "Ines Varga")
        val sections = Grouping.sections(plays + lone + one + two, GroupBy.AUTHOR)
        assertEquals(listOf("Q", "V"), sections.map { it.title })
        val q = sections[0].entries
        assertTrue(q.any { it is Entry.Stack && it.books.size == 5 })
        assertTrue(q.any { it is Entry.One && it.book.title == "Quiet Shore" })
        val v = sections[1].entries.single() as Entry.One
        assertEquals(1, v.copies.size)
        assertEquals(2, sections[1].count)
    }

    @Test
    fun `topics split history; your own topic wins`() {
        val h = book("Dragon Courts", form = Forms.NONFICTION, genre = "History").copy(subgenre = "Asian History")
        assertEquals("History · China and East Asia", Topics.of(h))
        assertEquals("Harbours", Topics.of(h.copy(topic = "Harbours")))
        assertEquals("Mystery", Topics.of(book("Fog Lane")))
    }

    @Test
    fun `authors are filed by surname, particles kept`() {
        assertEquals("Balzac, Honoré de", Grouping.filed("Honoré de Balzac"))
        assertEquals("balzac", Grouping.surname("Honoré de Balzac"))
        assertEquals("Quill, Will", Grouping.filed("Will Quill"))
    }

    @Test
    fun `your shelves hold what you put on them`() {
        val b = book("Quiet Shore").copy(shelves = listOf("s1"))
        assertTrue(ShelfPick.Mine("s1").holds(b))
        assertFalse(ShelfPick.Mine("s2").holds(b))
        assertTrue(ShelfPick.Default(DefaultShelf.ALL).holds(b))
    }

    // --- the Librarian ---

    @Test
    fun `the Librarian is told where the reader is, so it keeps to it`() {
        val b = book("Tide Clock").copy(status = Status.READING, progress = 17, upTo = "chapter 20")
        val text = LibrarianContext.forBook(b, listOf(b))
        assertTrue("Progress: 17% (up to chapter 20)" in text)
        assertTrue("NOT read past this point" in text)
    }

    @Test
    fun `a gist card is read from a reply and finds its book`() {
        val reply = """
            Here's a gist.
            **Book:** Tide Clock — R. Penn
            **Thesis:** Time is a harbour.
            Claims:
            1. Tides keep the town honest.
            2. Clocks lie at sea.
            Pushback: Too neat.
            Recall: What keeps the town honest?
        """.trimIndent()
        val parsed = GistCard.parse(reply)!!
        assertEquals("Time is a harbour.", parsed.gist.thesis)
        assertEquals(2, parsed.gist.claims.size)
        assertEquals("Too neat.", parsed.gist.pushback)
        assertEquals(listOf("What keeps the town honest?"), parsed.recall)
        val shelf = listOf(book("Tide Clock", "R. Penn"), book("Tide Clock", "S. Other"))
        assertEquals("R. Penn", GistCard.find(parsed.book, shelf)?.author)
        assertFalse(GistCard.has("Just a chat about tides."))
    }

    @Test
    fun `the Librarian is a built-in persona and a default favourite`() {
        assertTrue(BuiltInPersonas.all.any { it.id == BuiltInPersonas.LIBRARIAN.id })
        assertTrue(app.localmind.assistant.data.home.HomeSettings.DEFAULT_FAVOURITES.contains(BuiltInPersonas.LIBRARIAN.id))
    }

    // --- files ---

    @Test
    fun `file names are made safe and titles read from them`() {
        assertEquals("evil.pdf", PaperFiles.safeName("../../evil.pdf"))
        assertEquals("organizing intelligence", PaperFiles.titleFrom("organizing-intelligence.pdf"))
        assertEquals("application/epub+zip", PaperFiles.mimeOf("a.EPUB"))
    }

    @Test
    fun `cover files are plain names and only real pictures are taken`() {
        assertEquals("abc-1.jpg", CoverStore.nameFor("abc-1"))
        assertEquals("abc.c2.jpg", CoverStore.nameFor("abc", 2))
        assertEquals("evilpath.jpg", CoverStore.nameFor("../evil/path"))
        assertFalse(CoverStore.safe("../x.jpg"))
        assertFalse(CoverStore.safe(".hidden"))
        assertTrue(CoverStore.safe("abc.c1.jpg"))
        val jpeg = ByteArray(16).also { it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte() }
        assertTrue(CoverStore.looksLikeImage(jpeg))
        assertFalse(CoverStore.looksLikeImage("<svg>not a picture</svg>".toByteArray()))
    }

    private fun xlsx(sheets: List<Pair<String, String>>, strings: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(name: String, text: String) {
                z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray()); z.closeEntry()
            }
            put("xl/workbook.xml", "<workbook xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>" +
                sheets.mapIndexed { i, (name, _) -> "<sheet name=\"$name\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>" }.joinToString("") + "</sheets></workbook>")
            put("xl/_rels/workbook.xml.rels", "<Relationships>" +
                sheets.indices.joinToString("") { i -> "<Relationship Id=\"rId${i + 1}\" Target=\"worksheets/sheet${i + 1}.xml\"/>" } + "</Relationships>")
            sheets.forEachIndexed { i, (_, rows) -> put("xl/worksheets/sheet${i + 1}.xml", "<worksheet><sheetData>$rows</sheetData></worksheet>") }
            put("xl/sharedStrings.xml", "<sst>" + strings.joinToString("") { "<si><t>$it</t></si>" } + "</sst>")
        }
        return out.toByteArray()
    }
}
