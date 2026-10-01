package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.workspace.sources.MAX_SEARCH_QUERY_LENGTH
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LensParametersCodecTest {
    private val search = SourceIds.SEARCH
    private val apps = SourceIds.ALL_APPS

    private fun query(text: String) = assertNotNull(SourceParameter.query(text))

    private fun roundTrip(lens: Lens) = LensCodec.decode(LensCodec.encode(lens))

    @Test
    fun aLensWithoutParametersEncodesExactlyAsBefore() {
        val encoded = LensCodec.encode(Lens(listOf(search)))
        assertFalse("params" in encoded.fields)
        assertEquals(Lens(listOf(search)), roundTrip(Lens(listOf(search))))
    }

    @Test
    fun aParameterRoundTrips() {
        val lens = Lens(listOf(apps, search), parameters = mapOf(search to query("camera")))
        val decoded = assertNotNull(roundTrip(lens))
        assertEquals(lens, decoded)
        assertEquals("camera", decoded.parameterFor(search)?.text)
        assertNull(decoded.parameterFor(apps))
    }

    @Test
    fun twoLensesOverTheSameSourceKeepDifferentQueries() {
        val a = Lens(listOf(search), parameters = mapOf(search to query("mail")))
        val b = Lens(listOf(search), parameters = mapOf(search to query("maps")))
        assertEquals("mail", roundTrip(a)?.parameterFor(search)?.text)
        assertEquals("maps", roundTrip(b)?.parameterFor(search)?.text)
        assertTrue(a != b)
    }

    @Test
    fun parametersForSourcesTheLensDoesNotReadAreNotEncoded() {
        val lens = Lens(listOf(apps), parameters = mapOf(search to query("x")))
        assertNull(lens.parameterFor(search))
        assertFalse("params" in LensCodec.encode(lens).fields)
    }

    @Test
    fun decodingIsSafeForMalformedParameters() {
        val base = LensCodec.encode(Lens(listOf(search))).fields
        val junk =
            listOf(
                str("not an array"),
                arr(listOf(str("x"), num(3), obj())),
                arr(listOf(obj("source" to str("search")))),
                arr(listOf(obj("source" to str("search"), "query" to str("   ")))),
                arr(listOf(obj("source" to str("search"), "query" to num(5)))),
                arr(listOf(obj("source" to str("rss"), "query" to str("not in lens")))),
            )
        junk.forEach { value ->
            val decoded = LensCodec.decode(StoredValue.Obj(base + ("params" to value)))
            assertEquals(Lens(listOf(search)), decoded)
        }
    }

    @Test
    fun decodingNormalizesAndKeepsTheFirstEntryPerSource() {
        val long = "q".repeat(MAX_SEARCH_QUERY_LENGTH + 40)
        val params =
            arr(
                listOf(
                    obj("source" to str("search"), "query" to str("  first  ")),
                    obj("source" to str("search"), "query" to str("second")),
                ),
            )
        val base = LensCodec.encode(Lens(listOf(search))).fields
        val decoded = assertNotNull(LensCodec.decode(StoredValue.Obj(base + ("params" to params))))
        assertEquals("first", decoded.parameterFor(search)?.text)
        assertEquals(MAX_SEARCH_QUERY_LENGTH, query(long).text.length)
    }

    @Test
    fun theQueryNeverAppearsInAnyStringForm() {
        val sentinel = "zq-sentinel-7731"
        val lens = Lens(listOf(search), parameters = mapOf(search to query(sentinel)))
        assertFalse(sentinel in lens.toString())
        assertFalse(sentinel in query(sentinel).toString())
        assertFalse(sentinel in LensBinding(lens, ExpressionKind.LIST).toString())
    }

    @Test
    fun blankQueriesAreNotParameters() {
        assertNull(SourceParameter.query(""))
        assertNull(SourceParameter.query("   \n"))
        assertEquals("a b", SourceParameter.query("  a b ")?.text)
    }

    @Test
    fun seededRandomLensesRoundTripWithTheirParameters() {
        repeat(SEEDS) { seed ->
            val random = Random(seed)
            val sources = SourceIds.BUILT_IN.shuffled(random).take(1 + random.nextInt(3))
            val parameters =
                sources.filter { random.nextBoolean() }.mapNotNull { id ->
                    SourceParameter.query(randomText(random))?.let { id to it }
                }.toMap()
            val lens =
                Lens(
                    sources,
                    limit = if (random.nextBoolean()) 1 + random.nextInt(9) else null,
                    parameters = parameters,
                )
            assertEquals(lens, roundTrip(lens), "seed $seed")
        }
    }

    private fun randomText(random: Random): String =
        buildString { repeat(random.nextInt(0, 20)) { append("ab cé\"\\"[random.nextInt(7)]) } }

    private companion object {
        const val SEEDS = 200
    }
}
