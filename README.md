# Newt Draw

A finger-friendly sketchpad for Android, part of the Newt family (built from
[newt-android-template](https://github.com/sec-newt/newt-android-template)).
It follows the [house style](NEWT_STYLE.md): big worded buttons, high contrast,
no internet, no permissions.

> **A personal project, in early development.** Phase 2 of [the plan](PLAN.md) is under way.

## Using it

The paper gets most of the screen. Under it are **Pen / Eraser / Fill**, then
**Undo**, **Redo**, the **Color** button and **More**. More holds everything else:
Size, Smooth, Zoom in, Zoom out, the Move tool, Whole page, Drawings, Save and Clear.

- **Draw** with one finger on the white paper.
- **Move and zoom the paper.** The paper is a big square (3000 x 3000), larger
  than the screen. Drag with two fingers to scroll, pinch to zoom in for fine details,
  and tap **Whole page** to see all of it again. Zoomed in, your finger is more precise:
  at 3x, a wobble is a third the size on the paper. A second finger landing cancels the
  line you were drawing, and nothing draws until all fingers are lifted.
  To drag with one finger without drawing, choose **More**, then **Move tool**. Tap **Pen**
  to draw again.
  Tap **Zoom in** for a closer view or **Zoom out** to see more of the paper.
- **Smooth** steadies your line. The ink follows your finger on a short invisible
  string, so small wobbles never reach the paper. While you draw, a blue ring shows
  where the ink is and a thin blue line shows the string. More smoothing means steadier
  lines, but the ink trails further behind your finger. Medium is the default.
  Every line stops a little short of where you lift your finger (further on High),
  and a very short flick on High comes out as just a dot. Use Low or Off for tiny details.
- **Pen / Eraser / Fill** picks what your finger does. The eraser really removes ink.
- **Color** shows the color you're using, by name. Tap it to pick another: 14 colors,
  each with its name under it. Pen lines and fills use it.
- **Fill** (the paint bucket): tap inside an outline to fill it with the color. The fill
  goes behind your lines, so they stay crisp. Small gaps where a line doesn't quite meet
  (up to about 24 pixels on the 3000-pixel paper) don't let it leak out; a wider opening
  does, and Undo takes the fill back. Tap a filled area with another color to recolor it.
- **Size** sets how thick the pen or eraser is. Sizes are on the paper, like a real pen,
  so lines look thicker when you zoom in.
- **Undo / Redo** step back and forward through your strokes. Undo also brings back
  a cleared page.
- **Save** puts the whole paper in your gallery, in **Pictures/Newt Draw**, as a
  3000 x 3000 PNG with a white background, however far you're zoomed in.
- **Clear** wipes the paper after asking first.
- **Drawings** shows all your drawings, newest first, each with a small picture.
  **New drawing** starts fresh paper; the one you were on is kept. **Open** goes back to
  one, with its own Undo. **Rename** gives it a name, and **Delete** removes it after
  asking first. Delete can't be undone (pictures you saved to your gallery stay).
  A new drawing you never drew on isn't kept when you move to another.
- **Your drawings keep themselves.** It's saved inside the app a moment (1.5 seconds) after
  you stop drawing, and straight away when you leave the app. If Android closes the app
  in the background, or the phone restarts, your drawing is there when you come back,
  and Undo still works, even to bring back a cleared page. If the app crashes or the
  phone dies while you're drawing, the last second or two of lines can be lost. This copy is private to the app; use **Save** to put a picture in your
  gallery. Uninstalling the app deletes it.

## Not yet (on purpose)

- 14 set colors; no mixing your own yet.
- Shapes (line, circle, oval, square) are coming.
- The app stays upright; it doesn't turn sideways yet.
- No way yet to open a picture (a photo, or a PNG you saved) to draw on; that comes
  with layers.

## Install

Releases are built and signed by GitHub Actions. Install and update with
Obtainium from this repository's Releases page.
