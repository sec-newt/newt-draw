# Newt Draw plan

A finger-friendly drawing app for sketching characters. The owner has low vision and no
stylus (for now), so the most important feature is **smoothing**: lines follow the finger
on a short "string" that soaks up wobble.

## Rules for this project

Another AI-built paint app had half its features broken. That came from building a huge
feature list at once and never checking each feature. So:

1. **One phase at a time.** A phase isn't done until it works on the owner's phone. "It
   compiles" doesn't count.
2. **Each feature has a written "done" test** (below), and an automated test where one is
   possible.
3. **No dead buttons.** A feature that doesn't work gets cut, not left half-built.
4. **The scope list is closed.** New ideas go under "Later", not into the current phase.

## Phase 1: Sketchpad (this phase)

| Feature | Done when |
|---|---|
| Draw with one finger on white paper, black ink | A swipe leaves a line where the finger went (automated: swipe adds 1 stroke) |
| **Smoothing**: Off, Low, Medium (default), High | A shaky line comes out smooth (automated: jitter test on the smoothing math); a blue ring and string show where the ink is while drawing |
| Brush size: Small, Medium, Large, Extra large | Each size draws a visibly different width |
| Eraser | Erasing over a line removes it (automated: pixel check) |
| Undo / Redo | Undo 50 times in a row with no crash or lag; Redo brings strokes back; a new stroke after Undo clears Redo (automated) |
| A second finger cancels the stroke | Resting a second finger doesn't leave a stray line (automated) |
| Clear | Asks first; can be undone |
| Save as PNG | The picture shows up in the phone's gallery under Pictures/Newt Draw, white background |
| Readable | Light, dark and 200% text screenshots pass the accessibility checks; buttons are worded, 56dp+ |

**How it's built (so later phases don't need a rewrite):**
- A drawing is a list of strokes (finger points + width + pen/eraser). Undo/Redo move
  strokes between lists; nothing stores whole pictures, so memory stays small.
- Finished strokes are drawn into one cached image; only the stroke being drawn is redrawn
  each frame. Undo rebuilds the cache by replaying the strokes.
- The eraser really erases (transparent ink layer over white paper), so Phase 3 layers work.

**Not in Phase 1 (known gaps, said plainly in the README):** no colors, no zoom, and the
drawing isn't kept if Android closes the app in the background. Save before leaving it.

## Phase 2: Real drawing
Pinch to zoom and pan, color picker, autosave (drawing survives the app being closed).

## Phase 3: Layers
Add, hide, reorder layers; opacity; save and reopen the project file.

## Later (not scheduled)
Brush types, reference image, two-finger tap to undo, dark paper option.
