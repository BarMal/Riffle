# Standard launcher mode

Standard launcher mode is Riffle's default configuration. It keeps the familiar Android
launcher experience fully usable without contextual pages, cards, or smart behaviour.

> **Since ADR 0003.** There is no separate "Standard mode" and no workspace to choose. Home is the pool arrangement of
> each layout (the apps, folders and widgets the user placed), the Dock is the handle for the other Places, the Library
> (every app, and search) replaced the app drawer, and the Stream holds the dynamic content. This page's parity contract
> applies to Home, the Dock and the Library; "contextual pages, cards" means the Stream, which is reached only through
> the Dock, its handle and the gestures bound to it. Earlier text about the Nova-style workspace, the app drawer as the
> Finder, presets and lens pages is history (`workspaces-default.md`).

## Default behaviour

- The Stream, the Dock's live apps and the Library are each a switch (Settings > Stream, Settings > Dock, Settings >
  Library). Simple mode (Settings > Home) is the locked-down Home: every app A to Z, not arrangeable, with the Stream,
  the live apps and the Library pull off.
- Home screens, the Library and its search, the Dock, folders, widgets, wallpaper, shortcuts, grid editing, gestures,
  backup and restore, profiles, hidden apps and notification indicators continue to work as standard launcher features.
- Dynamic pages, cards and TimeScape-inspired surfaces are optional enhancements. They must not be required for
  navigation, layout persistence, settings or launcher performance.

## Disabled-feature contract

With the Stream and the live apps switched off (or Simple mode on), Riffle must keep the standard Home, Library, Dock
and Settings surfaces available. A Stream section that needs access (notifications, calendar, usage) asks only when the
person taps its "Allow" button or the matching step of the set-up tour; nothing is requested at launch.

Calendar access (`READ_CALENDAR`) follows the same rule: it is never requested at launch or because a calendar source
exists, only from Settings > Privacy and access or an explicit action on a surface that needs it. Standard surfaces are
fully functional without it. See the Calendar access policy in `workspaces-sources-lenses.md`.

## Product and design guardrails

- Use current Android patterns and Material components for standard launcher UI: settings,
  menus, dialogs, sheets, permissions, search, and edit controls.
- Reserve custom visual language and motion for Riffle's differentiating dynamic and card
  surfaces. TimeScape is an interaction inspiration, not a visual template.
- Keep defaults restrained, clear, and usable without configuration. Put advanced controls
  behind progressive disclosure.
- Treat reduced motion, accessibility, system insets, and large-screen layouts as baseline
  requirements for shared launcher UI.

## Change checklist

When changing a dynamic or smart feature, verify that the default Home still has a
complete Home-to-Library-to-launch flow, full home editing (move, folders, widgets, pages,
dock) and that disabled features leave no empty or blocked launcher surfaces. Add regression coverage at the domain or UI boundary that owns
the behaviour.
