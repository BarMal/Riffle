# Standard launcher mode

Standard launcher mode is Riffle's default configuration. It keeps the familiar Android
launcher experience fully usable without contextual pages, cards, or smart behaviour.

## Default behaviour

- Contextual behaviour is off by default and can remain off in **Settings > Contextual**.
- Home screens, the app drawer, dock, folders, widgets, wallpaper, search, shortcuts,
  grid editing, gestures, backup/restore, profiles, hidden apps, and notification indicators
  continue to work as standard launcher features.
- Dynamic pages, cards, and TimeScape-inspired surfaces are optional enhancements. They must
  not be required for navigation, layout persistence, settings, or launcher performance.

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

When changing a dynamic or smart feature, verify that standard launcher mode still has a
complete home-to-app-drawer-to-launch flow and that disabled features leave no empty or
blocked launcher surfaces. Add regression coverage at the domain or UI boundary that owns
the behaviour.
