# SWallet launch branding — 20 October 2026

The launch page uses the original full `app/src/main/res/drawable/swallet_logo.png`, a navy-to-blue gradient, a soft logo halo, the headline “Your money. Your momentum.” and a translucent running-character panel. The Money / Tasks / Goals labels complete the page. Portrait and landscape layouts share the same view-binding IDs. Reduced motion retains a still character and skips the existing 2.4-second animation delay.

Android 12+ uses a matching navy system splash with a padded wallet symbol, then hands off to the launch page. Startup still prepares reminders in the background, preserves elapsed startup time across recreation, waits until resumed before navigation and routes to Home or Sign in.

## Saved artwork

Both artwork edits used the built-in `image_gen` tool, with transparent output. No CLI fallback or raster post-processing was used.

- Blue-shirt runner: `app/src/main/res/drawable-nodpi/swallet_runner_atlas.png`, 1774 × 887 RGBA, eight frames in four columns and two rows. The original poses, face, white sarong with orange border, sandals and watch are retained.
- Wallet-only launcher symbol: `app/src/main/res/drawable-nodpi/swallet_launcher_symbol.png`, 1254 × 1254 RGBA. It retains the wallet, dollar, growth bars, arrow and sparkle. The full logo is unchanged.
- The selected generated PNGs were copied unchanged from `C:/Users/User/.codex/generated_images/01a1589e-2f90-78b3-ab29-62992bcb22be/exec-8da70140-9ca5-46ef-812f-27e556572bb4.png` and `exec-381449dc-2cc2-4e6a-a4ef-f5288f968538.png`, respectively.

## Launcher and system splash sizing

The adaptive foreground now uses the wallet symbol with 23% transparent inset on each side over a pale blue background. Both normal and round icons have API 33 monochrome variants. Legacy XML fallbacks also use the new symbol, and small in-app header icons use it instead of shrinking the full wordmark.

An alpha scan (alpha > 32) measured the symbol's furthest occupied pixel at radius 653.776 px on the 1254 px square source. Its adaptive foreground radius is 30.405 dp on the 108 dp layer, within Android's 33 dp safe-circle radius. The system splash's 20% inset yields a 90.090 dp radius in a 288 dp icon, within the 96 dp safe circle.

References: [Android adaptive icons](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive) and [Android splash screen dimensions](https://developer.android.com/develop/ui/views/launch/splash-screen/).

## Preview

Open `artifacts/SWallet-startup-preview.html` for a self-contained preview of the launch page, blue-shirt running cycle, portrait/landscape layouts, reduced motion, system splash and launcher masks. It embeds the actual app artwork. It is a browser design preview, not an Android device capture.

Headless Edge checks passed for replay, finite animation duration, visible character rendering, reduced motion and landscape layout. Reviewed design captures: [portrait](SWallet-launch-design-preview.png) and [landscape](SWallet-launch-landscape-preview.png). Native device checks remain pending; see [VERIFICATION.md](VERIFICATION.md).

## Final runner edit prompt

Use case: identity-preserve, precise color edit. Input image 1 is the existing transparent SWallet eight-frame RUNNING CHARACTER ATLAS and is the edit target. Input image 2 is the SWallet logo and is ONLY a blue color reference. Change ONLY the man's shirt color in every one of the eight frames from sage/grey-green to vivid SWallet blue: rich cobalt/azure #0069E0, using cyan-blue highlights similar to the wallet in the logo and natural darker blue fabric folds. Keep the shirt looking like fabric, not shiny plastic. Preserve EVERYTHING ELSE from input image 1 exactly: recognizable face, short black hair, skin, light facial hair, expression, white sarong and orange stripe/border, sandals, wristwatch, body proportions, poses, cloth movement, lighting and identical grid cell placements. Same FOUR COLUMNS and TWO ROWS in a landscape 2:1 PNG, exactly eight run poses ordered left to right top row then bottom row. Preserve the source canvas dimensions if possible, all transparent cell spacing and true alpha transparency. No zooming, reframing, moving or clipping any pose. The logo is only a color swatch reference: do not insert the logo, a watermark, text, scenery or other objects into the sheet. Clean transparent edges without any red, yellow, white or blue matte fringe or floating speckles. Do not change the face or the sarong. Final output is just the edited running sprite sheet, with a blue shirt in all eight frames.

## Final launcher symbol edit prompt

Use case: precise-object-edit / background-extraction. Edit the supplied SWallet logo for a crisp native Android launcher icon. Remove ONLY the large 'SWallet' wordmark underneath the wallet. Keep the upper wallet, gold dollar sign, blue growth bars, green rising arrow and small gold sparkle EXACTLY as supplied: preserve the shapes, glossy colors, proportions and details, without redrawing or inventing a new logo. Center the complete retained wallet/chart symbol on a square 1024x1024 transparent canvas with about 12 percent clear transparent padding on each side. Absolutely no clipping of the arrow tip or sparkle. Actual clean alpha transparency, not a baked white square or checkerboard. No white rounded-square container, no text, letters or watermark. This is a dedicated symbol-only icon asset; the original full logo will continue to be used separately on the launch page.
