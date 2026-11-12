package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.split.SplitMode
import com.riffle.core.domain.launcher.workspace.StoredValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class StreamSplitTest {
    private fun obj(vararg entries: Pair<String, StoredValue>) = StoredValue.Obj(mapOf(*entries))

    private fun decodeFoldable(vararg extra: Pair<String, StoredValue>): StreamLayout {
        val posture =
            obj(
                "deviceClass" to StoredValue.Str("FOLDABLE"),
                "sections" to StoredValue.Arr(emptyList()),
                *extra,
            )
        val root = obj("version" to StoredValue.Num(1), "postures" to StoredValue.Arr(listOf(posture)))
        val decoded = assertNotNull(StreamLayoutCodec.decode(root))
        return decoded.forPosture(HomeLayoutDeviceClass.FOLDABLE)
    }

    @Test
    fun theDefaultSplitIsAStreamOfFortyPercentShownAsASplit() {
        assertEquals(0.4f, StreamSplit().fraction)
        assertEquals(SplitMode.SPLIT, StreamSplit().lastShown)
        assertEquals(StreamSplit(), StreamLayout.DEFAULT.split)
    }

    @Test
    fun aLayoutStoredBeforeTheSplitDecodesToTheDefaults() {
        assertEquals(StreamSplit(), decodeFoldable().split)
    }

    @Test
    fun theSplitRoundTripsPerPosture() {
        val layouts =
            StreamLayouts()
                .with(HomeLayoutDeviceClass.FOLDABLE, StreamLayout(split = StreamSplit(0.55f, SplitMode.STREAM_ONLY)))
                .with(HomeLayoutDeviceClass.TABLET, StreamLayout(split = StreamSplit(0.3f, SplitMode.SPLIT)))
        val decoded = assertNotNull(StreamLayoutCodec.decode(StreamLayoutCodec.encode(layouts)))
        assertEquals(
            StreamSplit(0.55f, SplitMode.STREAM_ONLY),
            decoded.forPosture(HomeLayoutDeviceClass.FOLDABLE).split,
        )
        assertEquals(StreamSplit(0.3f, SplitMode.SPLIT), decoded.forPosture(HomeLayoutDeviceClass.TABLET).split)
        assertEquals(StreamSplit(), decoded.forPosture(HomeLayoutDeviceClass.DESKTOP).split)
    }

    @Test
    fun nonsenseInTheSplitDecodesToSafeValues() {
        val wild =
            decodeFoldable(
                "split" to obj("fractionPermille" to StoredValue.Num(9_000), "lastShown" to StoredValue.Str("home")),
            ).split
        assertEquals(StreamSplit.MAX_FRACTION, wild.fraction)
        assertEquals(SplitMode.HOME_ONLY.let { SplitMode.SPLIT }, wild.lastShown)
        val unknown =
            decodeFoldable(
                "split" to obj("fractionPermille" to StoredValue.Num(-5), "lastShown" to StoredValue.Str("sideways")),
            ).split
        assertEquals(StreamSplit.MIN_FRACTION, unknown.fraction)
        assertEquals(SplitMode.SPLIT, unknown.lastShown)
        assertEquals(StreamSplit(), decodeFoldable("split" to StoredValue.Str("oops")).split)
    }

    @Test
    fun aSplitFieldMissingItsKeysKeepsTheDefaults() {
        assertEquals(StreamSplit(), decodeFoldable("split" to obj()).split)
    }

    @Test
    fun theReducerSetsTheSplitAndClampsIt() {
        val after =
            StreamLayoutReducer.reduce(
                StreamLayout.DEFAULT,
                StreamLayoutAction.SetSplit(0.9f, SplitMode.HOME_ONLY),
            )
        assertEquals(StreamSplit(StreamSplit.MAX_FRACTION, SplitMode.SPLIT), after.split)
        val streamOnly = StreamLayoutReducer.reduce(after, StreamLayoutAction.SetSplit(0.5f, SplitMode.STREAM_ONLY))
        assertEquals(StreamSplit(0.5f, SplitMode.STREAM_ONLY), streamOnly.split)
        assertEquals(StreamLayout.DEFAULT.sections, streamOnly.sections)
    }

    @Test
    fun aSplitMustBeValid() {
        assertFailsWith<IllegalArgumentException> { StreamSplit(0.1f) }
        assertFailsWith<IllegalArgumentException> { StreamSplit(0.5f, SplitMode.HOME_ONLY) }
    }

    @Test
    fun aChangedSplitIsNotTheDefaultForBackup() {
        val changed = StreamLayouts().with(HomeLayoutDeviceClass.FOLDABLE, StreamLayout(split = StreamSplit(0.6f)))
        assertEquals(false, changed.isDefault)
        assertNotNull(StreamLayoutBackup.export(changed))
    }
}
