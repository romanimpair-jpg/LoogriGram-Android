# LoogriGram for Android

A personal, privacy-minded build of [Telegram for Android](https://github.com/DrKLO/Telegram). It has no ads, no Premium, no Stars or other paid features and no stories, ghost mode is on by default, and the APK carries no Google libraries.

**Not affiliated with Telegram.** LoogriGram is an unofficial third-party client built from the official Telegram for Android source and the public Telegram API, with its own API ID. It talks to Telegram's servers as the official app does, so nothing here hides your activity from Telegram itself.

It's built for one person's daily use and published because the license requires the source. There are no support or feature promises. It is the Android side of [LoogriGram Desktop](https://github.com/romanimpair-jpg/LoogriGram-Desktop) and follows the same decisions.

## What's different

- **Ghost mode, on by default.** No "typing…" or other activity indicators are sent, you appear offline, and Last Seen is set to *Nobody* and read times hidden on the server. **Read receipts are still sent:** Telegram uses the same request to tell the sender and to sync your read position to your other devices.
- **No ads**, and no reading telemetry or view-count reporting.
- **Nothing to do with money, in either direction:** Premium, Stars, TON, gifts, giveaways, paid media, paid posts, paid reactions, paid messages, boosts, Telegram Business, payments and earnings. A message that carries one stays in your history but isn't shown. Chats with people who charge per message are locked, with a note saying why.
- **Premium is shown for nobody.** No Premium badges, emoji statuses, collectible colours or decorated profiles on anyone.
- **No stories**, anywhere.
- **No Google.** No Play Services, Firebase, ML Kit or Chromecast. Notifications come over the app's own background connection to Telegram, because a renamed app cannot receive Telegram's push messages.
- **Updates come from this repository's releases**, not from Telegram or Google Play. The app checks them itself with a plain request to GitHub.

## Installing

Download the APK from the [latest release](https://github.com/romanimpair-jpg/LoogriGram-Android/releases/latest) and install it. It is built for arm64 only. The package name is `com.loogrimedia.loogrigram`, so it installs beside an official Telegram app rather than over it, and releases are signed with this fork's own key.

## Building

Builds run on GitHub Actions and are started by hand; see [`.github/workflows/android.yml`](.github/workflows/android.yml). The fork's work lives on the `patches` branch, and `dev` holds the upstream baseline it was forked from. [`LOOGRIGRAM.md`](LOOGRIGRAM.md) on `patches` holds the maintainer notes: the current state, build details, pitfalls, and the reasons behind each change.

Every change from upstream in the source carries a `LoogriGram:` comment, so

```
grep -rn "LoogriGram:" TMessagesProj/src
```

lists the behavioural difference from Telegram for Android.

## License

GNU GPL version 2, the same as Telegram for Android. See [LICENSE](LICENSE).
