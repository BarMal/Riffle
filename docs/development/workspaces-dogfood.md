# Dogfooding Workspaces (preview)

Workspaces (pages of lenses over live sources, the workspace menu and the editor) are wired into the
app behind a runtime switch, **Workspaces (preview)**, so you can try them on a real phone without
touching the standard launcher. Nothing about this has been run on a device yet: treat the first
session as the first real validation and report what you find.

## Get the build

1. Install the newest **Alpha Release** prerelease APK (`riffle-alpha.apk`) from the repository's
   GitHub Releases page. It is published automatically after every green push to `main`
   (`.github/workflows/alpha-release.yml`); to force one, run the **Alpha Release** workflow
   manually from the Actions tab (`workflow_dispatch`).
2. It is signed with the project's release key (GitHub Actions secrets, see
   [`docs/release/signing.md`](../release/signing.md); nothing to configure on your side), so it
   installs over the previous alpha with your data kept.
3. Open Riffle, then **Settings > Workspaces (preview)** (a *Developer* section at the top of the
   main page) and turn it on. Tap **Open Workspaces (preview)**.

The debug build (`./gradlew assembleDebug`) has the id suffix `.debug`, so it is a separate app and
does **not** replace the alpha. The preview is in release builds too; there is no debug-only gate.
It adds no dependencies or assets, so it should not move the APK size budget
([`apk-size-budget.md`](../release/apk-size-budget.md)); the release notes list the actual size.

## What the switch does

- **Off (default):** the standard launcher is exactly as before. No workspace code runs: no source
  subscriptions, no workspace storage reads, nothing built.
- **On:** a *Workspaces* handle appears beside the dock on Home (the existing workspace menu) and the
  preview can be opened from Settings or from that menu's jump, Finder and Edit choices.
- **Leaving:** *Exit preview* at the top of the preview, the system Back button (an open menu closes
  first), or a Home press. Turning the switch off closes the preview. Nothing is deleted either way;
  home, drawer, dock and settings keep working in both states.

The preview shows the **active workspace for the current device class** (phone, foldable, tablet).
On first use each device class gets the Nova-style default preset; stored workspaces are never
overwritten (seeded layouts are saved only when you edit something).

## Checklist

- [ ] Turn the switch on, open the preview: pages render with real data (apps, recents, and so on)
      and icons load without jank.
- [ ] Open the workspace menu (handle beside the dock). Switch between presets/workspaces; the
      preview changes. Note any workspace that falls back to the default with a notice.
- [ ] Home page: your real icons, folders and widgets appear in their cells; tapping an app launches it; a folder opens a
      list; **Refresh home items** catches up after you rearrange Home (full list in
      [`workspaces-pool-cutover.md`](../product/workspaces-pool-cutover.md)).
- [ ] Jump to a page from the menu; open **Finder** (scrolls to the Finder page).
- [ ] **Edit workspace**: change a page's lens or expression, check the live preview, tap Done; the
      preview re-renders with the change. Close without saving and confirm the discard prompt.
- [ ] In the editor add a widget (lens widget) to a widget page.
- [ ] Calendar: in Settings > Permissions tap **Allow calendar access** (or use the editor's
      *Review access*); read the rationale, grant. Calendar events then appear on pages that read
      the calendar. Nothing may prompt before you tap.
- [ ] Notification cards need notification access (Settings > Permissions); recents need Usage
      access. Check each shows an honest "Allow access to show this" state until granted.
- [ ] Tap an app item (launches it) and a notification card's Dismiss.
- [ ] TalkBack: the dock offers the action **Workspace menu**; the menu, Exit preview and the editor
      are reachable and read sensibly.
- [ ] Reduced motion (Settings > Motion, or the system animation scale): jumps snap, no animation.
- [ ] Rotate, and fold/unfold if you have a foldable: layout respects the system bars and cutout,
      nothing is drawn under them, and the right device class's workspace is shown.
- [ ] Turn the switch off again and confirm Home, drawer, dock and Settings behave as before.

## Known gaps

- **Home pages show your real home items, read-only** (S4, see
  [`workspaces-pool-cutover.md`](../product/workspaces-pool-cutover.md)): apps, shortcuts, folders and widgets from a
  one-time import of your standard home. They go stale when you rearrange Home; use **Refresh home items** in the preview
  bar. No editing, and the dock is still a placeholder.
- **No search box UI**: the search source exists but nothing writes a query yet.
- **RSS shows cached articles only** (no refresh path; see #1374); with no cache it is empty.
- **Calendar events appear only after access is granted**, and only then.
- Artwork images for feed articles and notification large icons are not loaded (placeholder is
  drawn); app, shortcut and notification-app icons are.
- The dock is a marked **placeholder** (or the workspace's dynamic section); the real dock is not
  part of the preview.
- Jump offers whole pages, not individual groups of a page-set; reply, snooze and custom item
  actions do nothing.
- A page-set page inside the pager relies on nested-scroll hand-off at its edges; the gesture-axis
  declarations are not arbitrating yet.

## Reporting problems

Open a GitHub issue with: the alpha version shown in Settings > About, the device and its fold or
window state, the steps, and a screen recording or screenshot when it is visual. Do not paste item
content (notification text, calendar titles): item content is transient and never stored or logged
by the app, so please do not add it to reports either. If the preview ever gets in the way, Exit
preview and turn the switch off; your launcher data is untouched.
