# Wallpaper Validation

This checklist tracks closure evidence for GitHub issue #7. It covers the current wallpaper slice only: Riffle can show the Android system wallpaper behind launcher content, can switch to a solid Material background fallback, and persists that source in launcher appearance settings.

## Current Support Matrix

| Issue #7 scope | Current evidence | Status |
| --- | --- | --- |
| Show current system wallpaper behind launcher surfaces | `WallpaperSource.SYSTEM` applies `FLAG_SHOW_WALLPAPER` and a transparent window background through `AndroidLauncherWallpaperController`. `LauncherShellContent` leaves the root background transparent for the system source. | Supported |
| Support wallpaper selection/setting where Android APIs allow | Settings > Appearance exposes System and Solid source choices. The choice is saved in `LauncherSettings.appearance.wallpaper`. | Partial: source selection is supported; launching the Android wallpaper picker or setting a new image is not implemented. |
| Support scrolling/static wallpaper behaviour | Riffle delegates display to Android's wallpaper window flag and does not manage wallpaper offsets. | Gap |
| Ensure cards, pages, dock, and settings respect wallpaper/theme contrast | Home labels and Material surfaces use existing theme colors and label backgrounds over wallpaper. | Needs device validation across high-contrast, low-contrast, light, and dark wallpapers. |
| Wallpaper access behind platform interfaces | `LauncherWallpaperController` isolates window wallpaper behavior from shell state and Compose UI. | Supported |
| Wallpaper settings represented in central settings model | `AppearanceSettings.wallpaper` is part of `LauncherSettings`. | Supported |
| Unit tests for wallpaper setting state and configuration | Domain defaults, JSON fallback, ViewModel persistence, backup export, and window-command mapping are covered by existing tests. | Supported |
| Manual/device validation for platform API differences | Use the checklist below before closing #7. | Pending manual run |

## Manual Device Checklist

Run these checks on at least one API 29-30 device or emulator and one API 31+ device or emulator. Prefer one light wallpaper, one dark wallpaper, and one busy/high-detail wallpaper.

1. Install Riffle from a clean app data state.
2. Set Riffle as the default home app.
3. Confirm the first home surface shows the current Android system wallpaper behind home content.
4. Open Settings > Appearance and switch Wallpaper from System to Solid.
5. Confirm the wallpaper disappears immediately and the launcher root uses the Material background color.
6. Force-stop and reopen Riffle, then confirm Solid remains selected and visible.
7. Switch Wallpaper back to System.
8. Confirm the current Android system wallpaper appears again without restarting the app.
9. Change the device wallpaper from Android system settings, then return to Riffle and confirm Riffle shows the new wallpaper.
10. Rotate the device and confirm home content, dock content, folders, app drawer, search, and settings remain readable over the wallpaper.
11. Toggle Settings > Appearance > Fullscreen home, Hide status bar, and Hide navigation bar while System wallpaper is selected.
12. Confirm system bars hide only on Home and reappear on app drawer, search, notifications, and settings.
13. Repeat with Solid selected and confirm system bar behavior does not depend on wallpaper source.
14. On API 31+, repeat System and Solid checks in both system light and dark theme modes to validate Material dynamic color contrast.
15. If a foldable or tablet emulator is available, repeat rotation and readability checks with each available layout device class.

## Known Limitations

- Riffle does not launch Android's wallpaper picker or set a new wallpaper image.
- Riffle does not expose static versus scrolling wallpaper controls or manage wallpaper offsets.
- Riffle currently offers only the system wallpaper and a solid Material fallback; it does not offer custom colors, gradients, or per-page wallpaper choices.
- Graceful failure UX is limited to the Solid fallback source. There is no explicit user-facing error state for device or policy restrictions around wallpaper APIs.
