# Newt Draw

A finger-friendly sketchpad for Android, part of the Newt family (built from
[newt-android-template](https://github.com/sec-newt/newt-android-template)).
It follows the [house style](NEWT_STYLE.md): big worded buttons, high contrast,
no internet, no permissions.

> **A personal project, in early development.** This is Phase 1 of [the plan](PLAN.md).

## Using it

- **Draw** with one finger on the white paper. If a second finger touches the paper,
  the line you're drawing is cancelled, so a resting finger doesn't leave a stray mark.
- **Smooth** steadies your line. The ink follows your finger on a short invisible
  string, so small wobbles never reach the paper. While you draw, a blue ring shows
  where the ink is and a thin blue line shows the string. More smoothing means steadier
  lines, but the ink trails further behind your finger. Medium is the default.
  Very short strokes may not draw with High; use Low or Off for tiny details.
- **Pen / Eraser** picks what your finger does. The eraser really removes ink.
- **Size** sets how thick the pen or eraser is.
- **Undo / Redo** step back and forward through your strokes. Undo also brings back
  a cleared page.
- **Save** puts the picture in your gallery, in **Pictures/Newt Draw**, as a PNG with
  a white background.
- **Clear** wipes the paper after asking first.

## Not yet (on purpose)

- Only black ink: colors come in Phase 2.
- No zoom yet: Phase 2.
- **Save before you leave the app for long.** Turning the phone or switching dark mode
  keeps your drawing, but if Android closes the app in the background, the drawing is
  lost. Autosave comes in Phase 2.

## Install

Releases are built and signed by GitHub Actions. Install and update with
Obtainium from this repository's Releases page.
