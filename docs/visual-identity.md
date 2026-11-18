# Visual identity

Pocket Gameplay is a focused, offline-first Android utility for playing HTML5 games stored by the user. It should feel dependable and unobtrusive rather than like a browser, storefront, or social gaming service.

## Brand principles

- **The app is the shelf; the games are the stars.** Library controls stay quiet so user-owned game artwork can carry the personality.
- **Offline by default.** The interface reinforces local ownership and direct access, never implying cloud sync, accounts, or an online catalogue.
- **Practical, not ornamental.** Clear hierarchy, predictable actions, comfortable touch targets, and restrained decoration take priority over novelty.
- **Android-native.** Use platform controls and accessibility behavior; do not add a UI framework just for visual polish.

## Palette

| Token | Hex | Use |
| --- | --- | --- |
| pg_background | #101316 | Main app canvas |
| pg_surface | #191E22 | Cards and editable surfaces |
| pg_surface_raised | #252B31 | Secondary controls and raised surfaces |
| pg_outline | #354049 | Quiet borders and separators |
| pg_accent | #D8A85F | Primary actions and small highlights |
| pg_on_accent | #211A10 | Text on the amber primary action |
| pg_text_primary | #F2F0EB | Main text |
| pg_text_secondary | #B2B7B8 | Supporting text |
| pg_text_muted | #879199 | Hints and inactive controls |
| pg_warning | #E0A273 | Recoverable library problems |

The warm amber accent is intentionally muted. Use it sparingly; do not turn every button, label, or card border into an accent element.

## Typography and layout

Use Android's system sans-serif family. Titles are medium or bold, supporting metadata is smaller and muted, and long paths or entry-point names should remain readable. Prefer 8dp spacing increments, 12–16dp card insets, restrained rounded corners, and touch targets around 48dp or larger.

## Components

- **Primary action:** amber filled button reserved for opening a selected game folder.
- **Secondary action:** dark raised button with a quiet outline, used for ZIP import, sorting, library operations, and player controls.
- **Library card:** dark surface, thin outline, title first, storage/entry-point metadata second, and actions grouped below.
- **Search field:** dark inset surface with visible text, muted hint, and the platform's amber focus state.
- **Status messaging:** supporting text on a raised neutral surface. Reserve the warning color for actionable problems.
- **App mark:** a contained play glyph that remains distinct from game artwork.

## Do not use

Avoid neon gradients, excessive glow, glossy gamer clichés, decorative gamepad motifs, crowded dashboards, and unnecessary accent-colored surfaces. Do not introduce network, account, or cloud imagery that conflicts with the app's offline scope.

## Implementation

Canonical color values live in app/src/main/res/values/colors.xml; component styles and backgrounds live in styles.xml and drawable/. The home screen is assembled in MainActivity, so keep visual changes there aligned with these tokens. This document records the direction, not a promise that every future screen already follows it.
