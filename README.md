# NextDNS Manager+

An Android app for the [NextDNS](https://nextdns.io) dashboard, with several accounts side by side.

- **Several NextDNS accounts**, each signed in separately, switched instantly from a numbered chip
- **Connection status** in the top bar: is this phone using NextDNS, and encrypted?
- **Find in page**, **app lock** (fingerprint, face or PIN), **White / Black / Match system** theme
- Ping test, Private DNS shortcut, downloads, desktop site, pinch to zoom
- No ads, no analytics, no crash reporting. One library (AndroidX WebKit). About 100 KB to download.

**Requirements:** Android 10 or newer. Several accounts need *Android System WebView* 119 or newer (update it through
your app store if "Add account" is missing).

Not affiliated with NextDNS.

## Building

```bash
./gradlew test lint assembleDebug assembleRelease
```

The debug APK is in `app/build/outputs/apk/debug/`. CI builds it on every push (Actions → the run → Artifacts).
Developer documentation: [`PROJECT.md`](PROJECT.md) (how the app is built and how to change it),
[`CHANGES.md`](CHANGES.md).

## Credits

Built on [NextDNS Manager](https://github.com/doubleangels/nextdnsmanager) by doubleangels and
[LLM Central](https://github.com/UpperPanda1/assist). Icons: Material Icons (Apache License 2.0).

## License

Free software under the [GNU General Public License v3.0](LICENSE).
