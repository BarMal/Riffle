# TimeScape MVP closeout validation

Use this guide for the manual portion of the TimeScape MVP closeout. Record every completed
scenario in the repository's [device-evidence contract](../release/device-validation.md); do not
include notification content, account data, screenshots, or device serials in the manifest.

## Test data and device coverage

Prepare Personal and Work instances of at least one app where possible. Give one app multiple
notifications, including a dismissible notification and an ongoing notification, and keep another
app available with no notifications for a pinned empty-stage check.

Run the applicable scenarios on:

- a compact phone in portrait and landscape;
- a folded cover display and an unfolded foldable, including book/tabletop posture where the
  device supports it;
- a tablet or resizable desktop window; and
- light/dark themes with both difficult wallpaper or artwork and the minimum/default/maximum
  TimeScape appearance values.

For every device, record its actual form factor and window mode. Include at least one automated
emulator run and the required physical Honor rows from the device-evidence contract.

## Stage, card, and lifecycle checks

1. Enter Cards mode and return to Standard Home. Confirm Standard Home remains the default and
   preserves its selected page, layout, Dock, widgets, and wallpaper after each mode switch.
2. With zero, one, and many stages, confirm stage order is deterministic: pinned stages retain
   their user order, followed by active dynamic stages. Select a stage using the visible selector
   and the named previous/next actions; stage navigation must not move the classic Dock.
3. Pin an empty app stage and verify its launch and shortcut actions remain usable. Remove the
   last notification from a focused dynamic stage, then select another stage: it stays visible and
   empty while focused, then retires after focus leaves.
4. Exercise a first tap on a background card, vertical drag, fling, reverse, cancellation, and
   boundary snap. The first tap changes focus only, horizontal gestures are not stolen, and the
   settled card is the only card whose contextual actions can run.
5. Open details from the focused card, use Back, then remove the selected notification, revoke
   notification access, and switch modes. Confirm details close or recover with an explanation
   and focus returns to the originating control when it still exists.
6. Rapidly add, remove, and reorder notifications while dragging, while details are open, and
   after backgrounding and returning. The latest valid focused card or deterministic survivor
   remains selected; no duplicate stack, listener, or refresh is visible.
7. Lock or remove the Work profile and uninstall an app. Personal and Work stages must remain
   separate, unavailable data must not be exposed, and removed identities must recover without
   corrupting the remaining selection.

## Appearance, motion, and adaptive checks

1. Apply every preset, edit every appearance control at its minimum, default, and maximum value,
   then use Reset. Restart or rotate after each representative change and confirm settings round
   trip without making cards unreachable, unreadable, or off-screen.
2. Test notification artwork, app-derived colour, wallpaper accent, and custom solid backgrounds
   with light and dark content. Missing or corrupt artwork must fall back to a legible treatment.
   On a device or API level without supported blur, the selected preference remains saved while
   the visible fallback stays readable.
3. On compact windows, verify the header, focused Spline, contextual shelf, and stage selector
   remain reachable. On expanded windows, verify the stage rail, Spline, and supporting detail
   pane preserve the same stage/card/detail identity through fold, unfold, resize, rotation, and
   split-screen transitions. No control may cross a separating hinge or unsafe inset.
4. Turn on Android and Riffle reduced motion, then repeat card and detail navigation. Card changes
   and detail transitions must snap without spring, rotation, parallax, or long travel. Repeat
   with reduced transparency and high contrast; the hierarchy must remain opaque and legible.

## Accessibility and alternate input checks

1. Enable TalkBack. Enter Cards mode and confirm one focused card is announced with its type,
   title, body, and `Card n of m` position. Move with **Previous card** and **Next card**;
   decorative cards must not be traversed separately.
2. From the focused card, use TalkBack actions to choose **Previous card**, **Next card**, and
   **Show details**. Confirm contextual notification actions, **Details**, and **Back** are named
   and reachable.
3. With a hardware keyboard, D-pad, Switch Access, mouse, or rotary controller, focus the card
   stack. Up/Down moves between cards; Enter, D-pad center, or Space opens details. Close details
   and confirm focus returns to **Details** for the same card.
4. At maximum font/display scale and in RTL, verify controls remain visible, have 48 dp targets,
   and retain a non-colour focus cue.

## Closeout record

Only mark the MVP ready after every relevant scenario passes, `./gradlew verify` passes, and the
exact candidate SHA has complete device evidence. A blocked or failed required evidence row is a
release blocker; create a separate issue for unrelated findings rather than widening the closeout
change.
