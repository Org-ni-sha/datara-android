# DATAra brand assets and launch screen

Assets come from the user-supplied `DATAra-App-Kit.zip`. Its README and integration examples were treated as reference material; no supplied scripts were executed and no React Native code was introduced.

- `drawable-nodpi/datara_brand_symbol.png`: supplied transparent two-ribbon mark, used by `DataraLogo` in authentication, session loading and the Home app bar. Proportions and colors are preserved without the old glow, shadow or rounded clipping.
- `drawable/datara_foreground.xml` and `datara_monochrome.xml`: supplied native adaptive-icon paths. Launcher density PNGs, API 26 adaptive resources and API 33 monochrome resources also come from the kit. The kit's dark launcher background is retained.
- `drawable/datara_splash_symbol.xml`: same ribbon paths with additional padding for Android's circular splash mask. Device review caught clipping in the original kit spacing; scale is reduced to preserve the complete silhouette.
- `Theme.DATAra.Starting`: native AndroidX SplashScreen theme, using the app's navy launch background and handing off to `Theme.DATAra`.

`MainActivity` installs the native splash before `super.onCreate`. It adds no fixed wait and does not change the existing session decision or navigation stack. If session restoration remains pending after the first Compose frame, the matching navy loading screen shows the logo, app name and accessible loading status. Android controls the native transition; the mark does not loop or require a network download.

The kit's MP4 and Expo overlay are not used. They are optional platform examples, and playing the whole video would add an unnecessary startup gate. No separate splash activity, storage, backend or permission change is required. Native dependency `androidx.core:core-splashscreen:1.2.0` is declared through the version catalog.

Guidance: UI UX Pro Max accessibility/loading recommendations and [Android splash screens](https://developer.android.com/develop/ui/views/launch/splash-screen). Keep the emblem readable at small sizes, preserve clear space and let authentication decide the destination.

Validation: unit tests and debug builds passed; 11 Compose UI tests passed on emulator-5554, including session loading at 200% font scale. Cold launch reached Login; a subsequent launch reused the running activity. Inspected native launch, session-loading, Login and Home screenshots in `build/ui-review/branding/`. API 26 launcher/splash behavior and themed launcher appearance still need physical-device/version checks before release.
