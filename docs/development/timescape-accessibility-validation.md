# TimeScape accessibility validation

Use a device or emulator with notification access granted and at least two notifications from one app.

1. Enable TalkBack. Enter Cards mode and confirm that one focused card is announced with its type, title, body, and `Card n of m` position. Move with **Previous card** and **Next card**; decorative cards must not be traversed separately.
2. From the focused card, use TalkBack actions to choose **Previous card**, **Next card**, and **Show details**. Confirm that the contextual notification actions, **Details**, and **Back** are named and reachable.
3. With a hardware keyboard, D-pad, Switch Access, or rotary controller, focus the card stack. Up/Down moves between cards; Enter, D-pad center, or Space opens details. Close details and confirm focus returns to **Details** for the same card.
4. Change stage with the named previous/next controls. Confirm the stage title announces its app/profile identity, pinned/dynamic origin, lifecycle, and card count once after the change settles.
5. Turn on Android and Riffle reduced motion, then repeat card and detail navigation. Card changes and detail transitions must snap without spring, rotation, parallax, or long travel.
6. At maximum font/display scale, in RTL, and with high-contrast/reduced-transparency settings, verify controls remain visible, have 48 dp targets, and retain an opaque, readable card hierarchy.
