# SWallet cartoon character

Both male and female guides are available through **Profile > Preferences > Animation gender**, with Male as the saved-setting fallback and default. The female guide uses the latest supplied woman's face, a blue blouse and white trousers. Current female artwork, prompt set and validation are in [FEMALE-CHARACTER.md](FEMALE-CHARACTER.md). The preview below now includes a Male/Female selector. Home's Important today card keeps its static checklist icon.

The original male artwork below was created with the built-in `image_gen` tool using the earlier photo attached in chat as a facial reference. No external API/CLI was used.

Project asset: `app/src/main/res/drawable-nodpi/swallet_guide_atlas.png`.

Interactive, self-contained preview: `artifacts/SWallet-character-preview.html`. This embeds the original atlas and uses the same frame selections as Android. It supports light/dark backgrounds, replay and reduced motion. It demonstrates the artwork and gestures, not a captured Android screen.

The selected original output is a **1254 × 1254 RGBA PNG** with a 4 × 4 grid. It is copied unchanged from the generated output. The renderer calculates proportional integer cell boundaries, so the non-divisible image dimensions do not lose pixels. All 16 cells contain character artwork and transparent space, confirmed by decoding and examining alpha bounds. The decoded bitmap uses approximately 6 MiB and is shared between character views.

Rows: welcome wave, checklist planning, thumbs-up encouragement, trophy celebration. Frame selections avoid switching hands between welcome/trophy poses. Each native animation runs twice through four selected poses over 1.8 seconds, then rests. Motion stops when hidden, detached or the owning screen stops, and follows Android's animator setting. Reduced motion retains a still character. Decorative views are excluded from TalkBack because the adjacent text explains the state.

Placements: sign-in/sign-up branding; Home balance and important-task summary; task summaries and empty states; empty transaction views; goal overview and empty states; the goal-achieved dialog alongside confetti. Existing profile initials and the SWallet brand logo are retained.

## Final generation prompt

Use case: stylized-concept. Asset type: ONE production sprite atlas for a native Android personal-finance and checklist app named SWallet. Input image 1 is a facial identity reference, not the background. Create a charming polished 3D cartoon HUMAN man recognizably based on this reference: preserve the same medium-brown complexion, distinctive high voluminous wavy black hair, thick eyebrows, dark eyes, nose, short beard and moustache, jaw shape and friendly face. Young adult, never a robot or animal. He wears a neat black blazer with subtle gold piping, white shirt, brown tie, dark trousers and simple shoes, inspired by his reference outfit; remove the medal, institutional lettering and institutional badge. A friendly professional app guide with a moderately enlarged cartoon head and clean expressive hands. Do not reproduce the yellow photographic background. The entire background must be actually transparent, not a checkerboard illustration.

CRITICAL ATLAS LAYOUT: square 2048x2048 PNG with exactly FOUR EQUAL COLUMNS and FOUR EQUAL ROWS, 16 equal 512x512 transparent cells. No gutters between cells, no panel outlines, no printed labels, no text, no UI, no watermark. Every cell contains the same full-body character at the same scale, feet baseline and camera perspective. All parts, props, hair and shoes stay entirely inside each cell with at least 44 px clear padding on every cell edge. Each character is about 390 px tall, face clear and large enough to recognize. Same lighting and clothing in all cells. Use the cell centers x=256,768,1280,1792 and y=256,768,1280,1792 for alignment.

ROW 1 (top, cells 1-4): WELCOME / friendly wave animation keyframes. Left to right: smiling with raised right hand palm facing viewer; hand tilted left; hand tilted right; hand gently returning to first pose. Body/head stable, subtle natural movement, no motion blur or drawn motion lines.
ROW 2 (cells 5-8): PLANNING / checklist action animation keyframes. Holding a small plain blue clipboard in left hand, right hand with pen checks a simple tick on the sheet. Four gentle sequential poses: look at page, touch pen to page, write tick, look up with pleased smile. No lettering or numbers on clipboard, just a few abstract short horizontal lines and a checkmark.
ROW 3 (cells 9-12): ENCOURAGEMENT / thumbs-up animation keyframes. Friendly supportive smile, right hand thumbs up; gentle small lift, slight nod, settle; keep face identity exactly consistent. No props in this row.
ROW 4 (bottom, cells 13-16): ACHIEVEMENT / celebration animation keyframes. Holding small gold trophy with one hand and raising other hand in celebration, happy smile; four sequential small cheering poses, slight bend then rise, settle. No external confetti: app will animate confetti separately. Maintain exact cell alignment and complete visible character in every frame.

Style: premium friendly 3D cartoon mobile-app illustration, smooth rounded forms, subtle soft studio shading, saturated blue and small gold accents matching finance app, sharp clean transparent edges. Anatomically plausible human hands; no extra fingers, no duplicate limbs. This is a sprite sheet consumed as four rows of animation frames, so grid precision and consistent identity/scale are essential.
