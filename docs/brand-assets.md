# DATAra brand assets and launch screen

Assets come from the user-supplied `DATAra-App-Kit.zip`. Its README and integration examples were treated as reference material; no supplied scripts were executed and no React Native code was introduced.

- `drawable-nodpi/datara_brand_symbol.png`: supplied transparent two-ribbon mark, used by `DataraLogo` in authentication, session loading and the Home app bar. Proportions and colors are preserved without the old glow, shadow or rounded clipping.
- `drawable/datara_foreground.xml` and `datara_monochrome.xml`: supplied native adaptive-icon paths. Launcher density PNGs, API 26 adaptive resources and API 33 monochrome resources also come from the kit. The launcher background is white (`datara_icon_background`) since 2026-10-31.
- `drawable/datara_splash_symbol.xml`: same ribbon paths with additional padding for Android's circular splash mask. Device review caught clipping in the original kit spacing; scale is reduced to preserve the complete silhouette.
- `Theme.DATAra.Starting`: native AndroidX SplashScreen theme, using the app's light launch background (`#F5F7FB`) and handing off to `Theme.DATAra`.

`MainActivity` installs the native splash before `super.onCreate`. It adds no fixed wait and does not change the existing session decision or navigation stack. If session restoration remains pending after the first Compose frame, the matching light loading screen shows the logo, app name and accessible loading status. Android controls the native transition; the mark does not loop or require a network download.

The kit's MP4 and Expo overlay are not used. They are optional platform examples, and playing the whole video would add an unnecessary startup gate. No separate splash activity, storage, backend or permission change is required. Native dependency `androidx.core:core-splashscreen:1.2.0` is declared through the version catalog.

Guidance: UI UX Pro Max accessibility/loading recommendations and [Android splash screens](https://developer.android.com/develop/ui/views/launch/splash-screen). Keep the emblem readable at small sizes, preserve clear space and let authentication decide the destination.

Historical validation (recorded in the documentation audit on 2026-10-29; not rerun by this docs change): unit tests and debug builds passed; 11 Compose UI tests passed on emulator-5554, including session loading at 200% font scale. Cold launch reached Login; a subsequent launch reused the running activity. Inspected native launch, session-loading, Login and Home screenshots in `build/ui-review/branding/`. API 26 launcher/splash behavior and themed launcher appearance still need physical-device/version checks before release.

## Optical centering (2026-10-31)

The mark's bounding box was centered, but its visual weight is not: the solid stem sits left and the curve thins out to the right, so the mark looked off-center to the left with extra space on the right. Measured on the 1024 px PNG, the alpha-weighted centroid is 38.5 px left of and 15.9 px below the box center (box width 866 px). Every copy of the mark was moved about two-thirds of that distance — the usual optical compromise, since a full shift overcorrects: 25 px right and 10 px up on `datara_brand_symbol.png`, and the equivalent `translateX`/`translateY` in `datara_foreground.xml`, `datara_monochrome.xml` and `datara_splash_symbol.xml`. The paths, colors and proportions are unchanged. The legacy density PNGs in `mipmap-*dpi/` were not regenerated; with `minSdk = 26` the adaptive icons are always used.

