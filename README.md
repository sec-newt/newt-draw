# Newt Draw

A finger-friendly sketchpad for Android, part of the Newt family (built from
[newt-android-template](https://github.com/sec-newt/newt-android-template)).
It follows the [house style](NEWT_STYLE.md): big worded buttons, high contrast,
no internet, no permissions.

> **A personal project, in early development.** Phase 2 of [the plan](PLAN.md) is under way.

## Using it

- **Draw** with one finger on the white paper.
- **Move and zoom the paper.** The paper is a big square (3000 x 3000), larger
  than the screen. Drag with two fingers to scroll, pinch to zoom in for fine details,
  and tap **Whole page** to see all of it again. Zoomed in, your finger is more precise:
  at 3x, a wobble is a third the size on the paper. A second finger landing cancels the
  line you were drawing, and nothing draws until all fingers are lifted.
  To drag with one finger without drawing, choose **Move**. Choose **Pen** to draw again.
  Tap **Zoom in** for a closer view or **Zoom out** to see more of the paper.
- **Smooth** steadies your line. The ink follows your finger on a short invisible
  string, so small wobbles never reach the paper. While you draw, a blue ring shows
  where the ink is and a thin blue line shows the string. More smoothing means steadier
  lines, but the ink trails further behind your finger. Medium is the default.
  Every line stops a little short of where you lift your finger (further on High),
  and a very short flick on High comes out as just a dot. Use Low or Off for tiny details.
- **Pen / Eraser** picks what your finger does. The eraser really removes ink.
- **Size** sets how thick the pen or eraser is. Sizes are on the paper, like a real pen,
  so lines look thicker when you zoom in.
- **Undo / Redo** step back and forward through your strokes. Undo also brings back
  a cleared page.
- **Save** puts the whole paper in your gallery, in **Pictures/Newt Draw**, as a
  3000 x 3000 PNG with a white background, however far you're zoomed in.
- **Clear** wipes the paper after asking first.
- **Your drawing keeps itself.** It's saved inside the app a moment (1.5 seconds) after
  you stop drawing, and straight away when you leave the app. If Android closes the app
  in the background, or the phone restarts, your drawing is there when you come back,
  and Undo still works, even to bring back a cleared page. If the app crashes or the
  phone dies while you're drawing, the last second or two of lines can be lost. This copy is private to the app; use **Save** to put a picture in your
  gallery. Uninstalling the app deletes it.

## Not yet (on purpose)

- Only black ink: colors and a fill bucket are coming.
- Shapes (line, circle, oval, square) are coming.
- The app stays upright; it doesn't turn sideways yet.
- One drawing at a time. To start fresh, Save the one you have, then Clear.

## Install

Releases are built and signed by GitHub Actions. Install and update with
Obtainium from this repository's Releases page.
