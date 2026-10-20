# Home-screen widget character — 20 October 2026

The Today's tasks home-screen widget now shows the character chosen in **Profile > Preferences > Animation gender**. Male remains the default. Selecting a different character refreshes all placed widgets immediately; no widget removal or re-add is required.

The header character waves when there are no tasks due today, checks a clipboard while tasks remain, and gives a thumbs-up when all of today's tasks are complete. Tapping the character opens today's checklist, matching the existing header action. Task completion, inline subtask expansion, scrolling, the add button and existing widget themes are retained. The Important today card inside the app keeps its static checklist icon.

## Implementation

- Reuses the existing male/female guide atlases, including the female blue blouse and white trousers. No new generated artwork is needed.
- `WidgetCharacter.kt` renders four 128 × 128 RGBA frames per character/gesture and caches two sets. A set uses 256 KiB of bitmap pixels. Duplicate resting frames share the same bitmaps.
- Native widget `ViewFlipper` cycles through five resting frames and the four gesture frames at 350 ms per frame. The launcher runs this locally; the app does not send per-frame broadcasts, schedule animation alarms or keep a service running.
- Reduced motion uses one ImageView instead of a ViewFlipper, with no animation timer. Motion settings are evaluated when a widget refreshes. If system animation settings change while the app is closed, the widget adopts them at its next refresh.
- Android's ViewFlipper stops dispatching flips when detached or its window becomes invisible. Individual launchers can pause or suppress widget animation.
- Decorative artwork is excluded from accessibility navigation, while the task and header controls remain available. The header wraps to fit its contents; the task collection continues to scroll.

Platform references: [RemoteViews-supported views](https://developer.android.com/reference/android/widget/RemoteViews), [ViewFlipper](https://developer.android.com/reference/android/widget/ViewFlipper), and [ViewFlipper source and window visibility handling](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/widget/ViewFlipper.java).

## Verification

`WidgetCharacterTest` covers RemoteViews serialization/inflation, male/female still artwork, the timer-free reduced-motion layout, all three gesture states, frame advancement, stopping after detachment and usable task/control space at the minimum widget size. The three tests compile but require a connected device to run. Build, Android test APK assembly, all 28 unit tests and lint passed (0 errors, 431 warnings). Detailed status is recorded in [VERIFICATION.md](VERIFICATION.md).

Existing collection and widget interaction tests continue to cover current-day filtering, completion, scrolling and parent/subtask expansion. Native launcher visual verification remains pending because no Android device is connected and the earlier emulator disk-space limitation remains.
