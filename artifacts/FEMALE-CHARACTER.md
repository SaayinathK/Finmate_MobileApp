# Female character and animation selection — 20 October 2026

The female character uses the newly supplied woman's face as its identity reference. The final outfit is a SWallet-blue blouse, **white wide-leg trousers**, neutral shoes and a subtle bracelet. White trousers replace the coral trousers in the initial generation.

Both male and female characters are available. In **Profile > Preferences > Animation gender**, choose **Male** or **Female**. The preview on Profile updates immediately. The choice is saved in the existing local settings under `animation_gender`; missing or unrecognized values use **Male**. The choice applies to the startup runner, in-app guide gestures, achievement celebrations and the home-screen tasks widget. Existing character views refresh on lifecycle start, and each guide atlas is cached separately to prevent the previous choice's artwork being reused. Placed widgets refresh immediately after a Profile selection change; see [WIDGET-CHARACTER.md](WIDGET-CHARACTER.md).

The existing male artwork is retained. Home's **Important today** card keeps the static checklist icon requested earlier. The launch page and logo are retained. Reduced motion and finite animation durations apply to both choices.

## Final saved artwork

Generated and edited with the built-in `image_gen` tool. No external API/CLI fallback or raster post-processing was used. The final selected PNGs are copied unchanged into the workspace:

- `app/src/main/res/drawable-nodpi/swallet_female_guide_atlas.png`: 1254 × 1254 RGBA, 4 columns × 4 rows, 16 poses. Rows are welcome, planning, encouragement and achievement.
- `app/src/main/res/drawable-nodpi/swallet_female_runner_atlas.png`: 1774 × 887 RGBA, 4 columns × 2 rows, eight running poses.

Selected generated originals:
- `C:/Users/User/.codex/generated_images/01a1589e-2f90-78b3-ab29-62992bcb22be/exec-316c8ad2-17a9-4dc7-88b4-7fd9d3d76d90.png`
- `C:/Users/User/.codex/generated_images/01a1589e-2f90-78b3-ab29-62992bcb22be/exec-c8647713-50e4-4743-98c3-d3217fdbe306.png`

## Review and verification

The self-contained [startup preview](SWallet-startup-preview.html) and [guide preview](SWallet-character-preview.html) now contain a Male/Female selector and embed both final atlases. Preview choices apply only to that browser preview; the app choice is saved in Profile.

Reviewed browser captures: [female startup](SWallet-female-startup-preview.png) and [female gestures](SWallet-female-character-preview.png). These are design previews, not Android screenshots.

Headless Edge verified male as the initial preview selection, female loading and visible rendering, white lower-body pixels in the runner and all four guide gestures, reduced-motion landscape startup, switching back to male, and no browser script exceptions. The results are saved in [character-preview-check.json](character-preview-check.json).

Android instrumentation tests cover the default, canceling without saving, selecting Female, preserving the choice across activity recreation, switching back to Male, unknown-value fallback, and actual guide/runner artwork changing without a stale cached atlas. The tests compile but could not run without an Android device; the earlier emulator disk-space limitation remains. Current build verification is recorded in [VERIFICATION.md](VERIFICATION.md).

## Final prompt set

The final assets were produced in three steps: initial identity-based generation, white-trouser edit and alpha cleanup.

### Initial guide generation

Use case: identity-preserve / stylized-concept. The most recent attached photograph is the FEMALE FACIAL IDENTITY REFERENCE, not a background. Create a friendly polished 3D cartoon adult woman recognizably based on this exact photo: preserve her medium-brown complexion, oval face, natural dark eyebrows, dark eyes, nose, gentle smile and long black hair pulled back with a modest lifted front. Do not substitute a generic blonde or fair-skinned face. Outfit in ALL frames: neat SWallet cobalt/azure-blue blouse (#0069E0) with elbow-length sleeves, high-waisted muted coral-rose wide-leg trousers inspired by the photo, comfortable neutral closed shoes and a small subtle bracelet. Same face, hairstyle, clothing, body proportions and lighting in every cell. Tasteful professional adult human guide, moderately enlarged expressive head, anatomically plausible clean hands. True alpha-transparent background, no photographed architecture, floor, backdrop, checkerboard, text, watermark, extra people, motion lines or cell dividers. Complete head-to-toe character with padding, never clip hair, hands, props or shoes. Soft studio shading, clean transparent edges and friendly mobile-app finish matching SWallet's existing male cartoon style. Asset: ONE square PNG character animation SPRITE ATLAS, exactly FOUR equal columns and FOUR equal rows (16 square cells). Keep identical scale and feet baseline across all cells with generous 8% clear padding inside every cell. Each row is four sequential keyframes of ONE gesture. Row 1 top, WELCOME: gentle right-hand wave, palm tilted left, palm tilted right, settle into welcoming smile; keep same hand. Row 2 PLANNING: holding a small blue checklist clipboard in left hand, right hand with pen looks down, touches paper, makes a tick, looks up with pleased smile. Clipboard has abstract strokes and checkmarks only, no text. Row 3 ENCOURAGEMENT: right-hand thumbs-up, gentle lift, small nod, settle, face visible and supportive smile. Row 4 bottom ACHIEVEMENT: small gold trophy in left hand, raise right hand in celebration, subtle bend and rise, cheer and settle; same trophy hand all four cells. Exactly one full-body woman in each of the sixteen evenly aligned cells. Avoid pose scale changes or frames bleeding into neighbors. Native app reads row-major index and animates each row. Final output only transparent 4-by-4 atlas.

### Initial runner generation

Use case: identity-preserve / stylized-concept. The most recent attached photograph is the FEMALE FACIAL IDENTITY REFERENCE, not a background. Create a friendly polished 3D cartoon adult woman recognizably based on this exact photo: preserve her medium-brown complexion, oval face, natural dark eyebrows, dark eyes, nose, gentle smile and long black hair pulled back with a modest lifted front. Do not substitute a generic blonde or fair-skinned face. Outfit in ALL frames: neat SWallet cobalt/azure-blue blouse (#0069E0) with elbow-length sleeves, high-waisted muted coral-rose wide-leg trousers inspired by the photo, comfortable neutral closed shoes and a small subtle bracelet. Same face, hairstyle, clothing, body proportions and lighting in every cell. Tasteful professional adult human guide, moderately enlarged expressive head, anatomically plausible clean hands. True alpha-transparent background, no photographed architecture, floor, backdrop, checkerboard, text, watermark, extra people, motion lines or cell dividers. Complete head-to-toe character with padding, never clip hair, hands, props or shoes. Soft studio shading, clean transparent edges and friendly mobile-app finish matching SWallet's existing male cartoon style. Asset: ONE landscape 2:1 PNG RUN-CYCLE SPRITE ATLAS with exactly FOUR equal columns and TWO equal rows, eight equal square cells. Each of eight cells contains one complete full-body woman running to the RIGHT in a three-quarter view so her face stays recognizable. Same camera, scale and baseline, with 8% clear transparent padding inside every cell. Eight sequential alternating run keyframes, ordered left-to-right top row then bottom row: right foot contact, right support compression, left leg passing, flight with left foot forward, left contact, left support compression, right leg passing, flight with right foot forward returning into first frame. Arms alternate naturally, trousers and ponytail move gently. No skirts or sarongs for this woman, no male face. Same azure-blue blouse and coral trousers in all eight poses. No props, no scenery, no floor shadow. All shoes, hands and hair entirely inside each square cell, no overlaps across cell edges. Exactly eight distinct sequential running poses; output only the transparent 4-column/2-row atlas.

### White-trouser edit

Use case: precise-object-edit, identity-preserve. Edit the supplied transparent female SWallet cartoon SPRITE ATLAS. Change ONLY the woman's trousers in EVERY frame from coral/rose-pink to clean WHITE trousers, with subtle soft cool-grey fabric folds and natural shadows so they remain visibly white and retain their wide-leg silhouette. Keep the azure/cobalt-blue blouse, recognizable face, medium-brown skin, pulled-back black hair, bracelet, neutral shoes, body proportions, poses, expressions, lighting, props and ALL exact frame positions and scale unchanged. Preserve the original canvas dimensions and original grid exactly, including all transparent spacing and alpha transparency. No added background, matte fringe, text or watermark. No clipping or rearranging any cell. Trousers are WHITE in all poses, never pink, blue or grey. Preserve blue blouse and face exactly. Final output only the same original transparent PNG atlas with white trousers.

Guide suffix: This is the 4-COLUMN by 4-ROW square 16-pose guide atlas. Preserve the welcome, planning clipboard, thumbs-up and trophy gesture rows.

Runner suffix: This is the 4-COLUMN by 2-ROW landscape 2:1 eight-pose running atlas. Keep all eight rightward running poses identical.

### Final alpha cleanup

Use case: background-extraction / precise-object-edit. This is an EXISTING TRANSPARENT sprite atlas of a female SWallet cartoon with BLUE BLOUSE and WHITE TROUSERS. Keep every character, face, hairstyle, blue blouse, WHITE wide-leg trousers, shoes, bracelet, prop, pose, exact cell position and exact canvas dimensions unchanged. Fix ONLY the alpha cutout: erase the distracting white spray, white blotches, scattered white pixels, white clouds, red/yellow fringes and colored speckles floating OUTSIDE the true body/prop silhouettes. Every pixel outside each clean character/prop silhouette must be 100% transparent alpha 0, including gaps between legs and arms. Retain the white trouser fabric INSIDE her actual legs, retain all hair and hand details, and do not shrink the trousers or erase body parts. There are no smoke, dust, sparkles, spray, cast shadows or effects in this sprite: all the background blotches are unwanted cutout artifacts. Clean crisp natural anti-aliased transparent edges. Do not add a background or checkerboard. DO NOT redraw, reposition, scale, crop, change outfit color or change any animation pose. This is solely transparent background cleanup. Output transparent PNG.

Guide suffix: Preserve the 4-column by 4-row square 16-pose atlas exactly.

Runner suffix: Preserve the 4-column by 2-row 2:1 landscape eight-pose RUNNING atlas exactly. In particular erase all white spray and white patches trailing behind her white trouser legs; those patches are not fabric. The fabric ends at the real legs and shoes.
