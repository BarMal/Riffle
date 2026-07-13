package com.riffle.core.domain.launcher.cards

enum class CardStackLayoutProfile(
    internal val policy: CardStackLayoutPolicy,
) {
    DECK(CardStackLayoutPolicy()),
    FAN(CardStackLayoutPolicy(offsetStep = 28f, verticalOffsetStep = 4f, rotationStep = 4f)),
    VERTICAL(CardStackLayoutPolicy(offsetStep = 0f, verticalOffsetStep = 36f)),
    CAROUSEL(CardStackLayoutPolicy(offsetStep = 48f, verticalOffsetStep = 6f, rotationStep = 8f)),
    COMPACT(CardStackLayoutPolicy(maxVisibleDepth = 2, scaleStep = 0.03f, offsetStep = 12f, alphaStep = 0.08f)),
}
