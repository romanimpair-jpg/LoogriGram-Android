# LoogriGram for Android

A personal, privacy-minded build of [Telegram for Android](https://github.com/DrKLO/Telegram). It has no ads, no Premium, no Stars or other paid features, no stories, no non-essential telemetry and no Google libraries, and ghost mode is on by default.

**Not affiliated with Telegram.** LoogriGram is an unofficial third-party client built on the official Telegram for Android source and the public Telegram API, with its own API ID. It talks to Telegram's servers exactly as the official app does, so nothing here hides your activity from Telegram itself.

It's built for one person's daily use and published because the license requires the source. There are no support or feature promises. It is the Android side of [LoogriGram Desktop](https://github.com/romanimpair-jpg/LoogriGram-Desktop), and the two make the same choices.

## What's different

### Ghost mode (on by default)

One switch in the tab bar, where the Contacts tab used to be.

- No "typing…" or other activity indicators are sent.
- You always appear offline.
- It sets **Last Seen** to *Nobody* and turns on **hide read time** on the server, once when an account first logs in and again each time you switch ghost mode on. People on your Last Seen "Always share with" list keep seeing it. Turning ghost mode off doesn't change either setting back.

**Read receipts are still sent.** Telegram uses the same request both to tell the sender you've read a message and to sync your read position to your other devices, so blocking it would make everything you read on the phone show up unread everywhere else. Hiding the read *time* is the part that can be had without breaking sync.

### Removed

- **Ads.** No sponsored messages in channels, in the video player, or above search results.
- **Telemetry that isn't needed to use Telegram.** Per-message reading time, and view-count reporting from channels.
- **Everything to do with money, in either direction.** Premium subscriptions and upsells, Stars, TON, gifts, giveaways, paid media, paid posts, paid reactions, paid messages, boosts, Telegram Business, payments and channel earnings. Messages that carry a gift, a giveaway, a payment or a price stay in your history but aren't shown. Chats with people who charge per message are locked, with a note explaining why.
- **Premium is shown for nobody.** Everyone looks the same: no Premium badges, emoji statuses, collectible colours or decorated profiles on anyone, and no Premium-only tools in the interface. Limits that Telegram's servers enforce on free accounts still apply.
- **Stories**, entirely: the strip, avatar rings, profile tabs, the camera and editor, the viewer and statistics. Messages that carry a story are hidden. Replies to a story keep their text.
- **AI compose**, the **article editor**, **large animated emoji**, the **greeting sticker** in empty chats, and **emoji and sticker suggestions** above the message field.
- **Trending stickers and emoji.** Telegram's suggested packs don't appear anywhere: no Trending tab, no trending packs in the sticker or emoji panel, and no dot for packs you haven't looked at. Your own packs, sticker search and packs opened from a link work as before.
- **Big animations over the chat.** No burst when a reaction is added or arrives, and no message effects: none are offered when sending, and those on messages you receive are neither played nor shown. No Premium sticker effects, whether the sticker is sent, tapped or held to preview it, and Premium stickers appear as drawn rather than flipped toward the chat. No emoji interactions (tapping a big emoji, or the "watching" note when someone taps yours), and no birthday balloons on profiles. Long-pressing a reaction shows only who reacted, without dimming the chat. Reactions, stickers and emoji themselves work as before.
- **Bots can't set your emoji status**, and an account verified by a bot rather than by Telegram looks unverified.
- **Nags and Telegram's help:** Ask a Question, the FAQ, Telegram Features and the Privacy Policy row, the "is this still your number?" prompt, and the "Add your birthday" and "Add your photo" hints above the chat list. The two-step verification password reminder is kept, because forgetting that password locks you out.
- **Your location.** The app has no location permission, so it can't find you or share where you are, and there is no map to pick a place on. A location someone sends you opens in your maps app.
- **Your address book.** The app has no contacts permission and never reads or uploads your phone's contacts. Your Telegram contacts work as usual.
- **Google, entirely.** No Play Services, Firebase (push or crash reporting), Google Pay, Play Billing, Play Integrity, SafetyNet, reCAPTCHA, Google sign-in, ML Kit, Chromecast, Wear OS, Android Auto or the Play install referrer.
- **Telegram's own updates.** Updates come from this repository instead (see below).

### Changed defaults

- Media auto-download: photos and GIFs only.
- Notifications for pinned messages are off.
- Saved media goes to folders named LoogriGram: Pictures, Movies, Download and Music/LoogriGram, and the app's own LoogriGram storage. Files saved earlier stay in the old Telegram folders; the app's own downloaded media moves over by itself.

### Kept on purpose

- Read receipts (see above).
- Delivery confirmations for login codes sent through Telegram Gateway. Without them, services tend to resend the code by SMS.
- Verified, scam and fake badges. They're warnings, not purchases.
- The proxy sponsor channel, because it discloses who runs the proxy.

## Installing and updating

1. Download `LoogriGram.apk` from the [latest release](https://github.com/romanimpair-jpg/LoogriGram-Android/releases/latest).
2. Open it on the phone. The first time, Android asks you to allow installs from the app you opened it with.

It runs on 64-bit ARM phones only. The package name is `com.loogrimedia.loogrigram`, so it installs beside an official Telegram app rather than over it.

Releases are signed with this fork's own key. Its certificate's SHA-256 fingerprint is:

```
97b5106a0796100b36f7aea5e42ceae51b5861bd0dec6e672ee5a85bc1e49030
```

Android refuses an update signed with any other key, so every later release must carry the same one.

The app checks this repository's releases each time it starts, and at most once an hour when you come back to it. A new build shows up as an extra tab at the end of the tab bar. You can also check by hand with **Check for updates** in Settings, below Language; its subtitle shows the build you're on. Android always asks before installing. The check is an anonymous request to GitHub and carries nothing about your account.

**Notifications** come over the app's own connection to Telegram, because a renamed app can't receive Telegram's push messages through Google. Android requires a permanent notification for an app that stays connected like this. It's silent and sits at the bottom of the shade.

## Building

Builds run on GitHub Actions and are started by hand. See [`.github/workflows/android.yml`](.github/workflows/android.yml). The fork's work lives on the `patches` branch, and `dev` holds the upstream baseline it was forked from. [`LOOGRIGRAM.md`](LOOGRIGRAM.md) on `patches` holds the maintainer notes: build details, pitfalls, and the reasons behind each change.

Every change from upstream in the source carries a `LoogriGram:` comment, so

```
grep -rn "LoogriGram:" TMessagesProj/src
```

lists the complete behavioural difference from Telegram for Android.

## License

GNU GPL version 2, the same as Telegram for Android. See [LICENSE](LICENSE).
