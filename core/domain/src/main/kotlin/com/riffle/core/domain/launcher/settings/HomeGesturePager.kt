package com.riffle.core.domain.launcher.settings

/**
 * The page scroll direction whose pager takes this swipe, or null when no pager does.
 *
 * Home's pages are a pager that scrolls one way (sideways or up and down, per posture). A one-finger swipe along that
 * way belongs to the pager as soon as Home has more than one page: the pager consumes the touch first and the gesture
 * layer yields (`HomeGestureArbiter`, rule 1). So a binding on that swipe can only fire on a Home with one page, or
 * where the pages scroll the other way; Settings says so beside the binding. Nothing is removed: the binding is kept
 * and still applies where the pager does not take the swipe. See docs/product/gestures.md, "Resolution rules".
 */
val HomeGesture.pagerScrollDirection: PageScrollDirection?
    get() =
        when (this) {
            HomeGesture.ONE_FINGER_LEFT, HomeGesture.ONE_FINGER_RIGHT -> PageScrollDirection.HORIZONTAL
            HomeGesture.ONE_FINGER_UP, HomeGesture.ONE_FINGER_DOWN -> PageScrollDirection.VERTICAL
            else -> null
        }
