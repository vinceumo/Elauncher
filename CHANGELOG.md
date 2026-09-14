# Changelog

All notable changes to Elauncher are documented here. Elauncher is released
only on GitHub — see [Releases](https://github.com/vinceumo/Elauncher/releases).

## v7.2.0

### Added
- **Widgets can overlap** — drag one widget on top of another instead of
  being blocked, then use its long-press menu's Bring to Front, Bring
  Forward, Send Backward, or Send to Back to control which one shows on
  top. Tap the same spot repeatedly to reach a widget hidden underneath.
- **App List direction** — a new Direction setting lets an App List lay its
  apps out horizontally instead of only vertically. Its width and height
  can now be drag-resized independently, without changing how many apps
  are shown.

## v7.1.0

### Added
- **Widget picker with previews** — "Add widget" now opens an in-app screen
  that groups every installed widget under its owning app (icon + label),
  shows each widget's real preview image instead of a plain text list, and
  has a search box to filter by app or widget name. Covers the primary
  profile, Work profile, and Private Space (when unlocked).

## v7.0.1

### Fixed
- Swiping left/right over a widget on the home screen now changes pages
  instead of opening the widget's own app.
- Long-pressing a Clock or Date widget now shows a "Change app / Edit widget"
  menu instead of jumping straight to the app picker.
- "Reduce animations" now actually disables the page-swipe transition (it
  previously had no effect on swiping between pages).

## v7.0.0

First public release.

### Added
- **Multiple home pages** — add, remove, rename and reorder pages from
  Settings.
- **A real widget grid** — place Android app widgets on the home screen,
  move and resize them freely on a visible grid.
- **App List widget** — home-screen app shortcuts are now a widget: pick
  1-8 apps, rename any of them, and set alignment independently per page.
- **Clock and Date & Screen Time as separate widgets** — add either one only
  where you want it, each with its own alignment; the date widget can show
  screen time and links to the usage-access permission if it's missing.
- **App-wide custom fonts** — pick a `.ttf`/`.otf` file from storage and
  apply it everywhere, or just switch on bold.
- **Adjustable background transparency** — one opacity setting for how much
  of your wallpaper shows through Settings, the app drawer and the pages
  screen, from fully see-through to a solid black/white background.
- **A manual "Reduce animations" option** — for e-ink or slow-refresh
  displays the automatic detection doesn't catch.

### Changed
- Settings redesigned: solid black/white background instead of boxed
  sections, matching system or app-overridden theme.
- Custom font picker: the file name now sits on its own line instead of
  overlapping the label.
- System font is now the default instead of a bundled custom font.

### Removed
- The automatic rate/review/share popup chain.
- The welcome popup on first launch.
- The FAQ entry in Settings.
- "Show date time", "App alignment" and "Screen time" rows in Settings
  (superseded by the Clock/Date widgets' own per-widget settings).
- Share and Rate rows at the bottom of Settings.
- Page-swipe transition animation (now covered by "Reduce animations").

### Fork
- Forked from [Olauncher](https://github.com/tanujnotes/Olauncher) as
  Elauncher — new package id, app icon, and branding; GitHub-only
  distribution (no F-Droid or Play Store).
