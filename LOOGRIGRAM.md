# LoogriGram Android — fork notes and handoff

Personal fork of Telegram for Android. Same goals as the desktop side: no ads,
no non-essential telemetry, no auto-updates, ghost mode on by default — plus
fully Google-free, because the phone runs GrapheneOS with no Play Services at
all. Started 2026-09-10.

Upstream's own conventions still apply to code style. This file covers what is
specific to the fork. The desktop fork's `LOOGRIGRAM.md` is worth reading too:
several traps are shared, and it records the read-receipt finding that this side
depends on.

---

## Status

| Part | State |
|---|---|
| Fork, CI, degoogling | Done. No Google bytecode in the APK, verified in the dex |
| Installed on the phone | **Yes.** `g25038ef5`, 2026-09-20, signed with our own key |
| App name | Done — launcher, in-app strings, and the two wordmark screens |
| Phone contacts | **Never touched.** Permissions, account and sync adapter all gone |
| Updater | Ours, from this repo's releases; a sixth tab appears when one exists |
| Money messages | Held in history, never drawn — desktop's hidden-content rule |
| Photo/video viewer | **Fixed** 2026-09-20; was our own null dereference, see the traps |
| Build warnings | Native: 4 left, all in the crypto path that is going. Resources: 40, upstream's |
| Ghost mode | Working in first use; not yet checked against a second account |
| Push transport | Working; **not yet trusted over hours idle**. FCM is impossible here — see below |

**The phone is now a Pixel 10a running stock Android with Play Services**, not
GrapheneOS - so Google code could come back where it buys something. It has
not: the one thing worth having, FCM push, cannot work here. Telegram's
Firebase project registers `org.telegram.messenger`, `.beta` and `.web` only,
and their servers push with their own credentials, so a renamed package signed
with our key can never receive it. Checked in their `google-services.json`, not
assumed. The MTProto foreground service stays.

What is still not earned is confidence in the push transport. That path is entirely ours - foreground service, `specialUse`
type, MTProto connection - and its failure mode is *delayed* notifications after
hours of idle, which no amount of testing in the first few minutes will reveal.
If messages start arriving late or only on unlock, look there first, and question
the `specialUse` choice before anything else.

The installed APK: ~44.5 MB, `lib/arm64-v8a/libtmessages.49.so` only, signed
`O=LoogriMedia, CN=LoogriGram`, certificate SHA-256
`97b5106a0796100b36f7aea5e42ceae51b5861bd0dec6e672ee5a85bc1e49030`. That
fingerprint is how to confirm a later build carries the same key - and it must,
because Android will refuse an update signed with any other.

---

## Repo layout

- **`dev`** — baseline plus CI. Root commit is upstream `DrKLO/Telegram` master
  @ `62b56a0` (v12.10.1, build 7038), imported as a single commit because a
  shallow clone cannot be pushed to a fresh remote. All ten submodule pointers
  are preserved.
- **`patches`** — all fork work. **Build from this branch.**
- `origin` is `github.com/romanimpair-jpg/LoogriGram-Android` (public),
  `upstream` is `github.com/DrKLO/Telegram`.
- Every deviation carries a `LoogriGram:` comment, so
  `grep -rn "LoogriGram:" TMessagesProj/src` lists the whole behavioural diff.
  Keep this up — it is the only practical audit of the fork.

The workflow must exist on the default branch for `workflow_dispatch` to work,
which is why the CI commits are on `dev` as well as `patches`.

---

## Building

Manual dispatch only, three staged modes:

```
gh workflow run "Android." --repo romanimpair-jpg/LoogriGram-Android \
  --ref patches -f mode=build -f config=Release
```

| Mode | Measured | Proves |
|---|---|---|
| `validate` | 2m | secrets, submodules, NDK/CMake install, Gradle config |
| `compile` | 4m | every source file type-checks; **no native build** |
| `build` | 22m | full arm64 APK, ~48 MB |

`compile` exists because javac has no dependency on `externalNativeBuild`, so a
typo costs four minutes instead of twenty-two. **Use it** - freely, it needs no
asking - but know what it cannot see: it never compiles a line of C++. It
validates the tree, not individual commits, so batch two or three commits per
run rather than compiling each one.

**A `build` + `Release` run now publishes a release**, tagged `g<short sha>`,
with the APK attached, plus a private `symbols` artifact holding `mapping.txt`
and the unstripped `.so`. The installed app reads those releases - see the
updater below - so a build is outward facing and is asked about every time. A
`compile` is not.

There is no JDK on the dev machine — **CI is the only compiler.** Plan edits
accordingly: read code back after scripted edits rather than trusting them,
because a four-minute round trip punishes guessing.

Secrets: `TG_API_ID`, `TG_API_HASH` (one api_id serves both platforms),
`ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
`ANDROID_KEY_PASSWORD`. Actions secrets are **write-only** — they cannot be read
back, so they cannot be copied between repos and are not a backup of anything.

### Signing

The keystore is a **PKCS12**, not a JKS, generated with `openssl` because there
is no `keytool`. Android accepts either, but Gradle must be told which, so
`RELEASE_STORE_TYPE` is a Gradle property: `jks` for the committed dummy,
switched to `pkcs12` by CI when it installs ours. The build prints the signing
certificate of the finished APK so the signer is a fact in the log.

It lives at `C:\LoogriProjects\LoogriGram\loogrigram.keystore` with its password
beside it, outside the checkout. **Android refuses an update signed with a
different key** — losing it means uninstalling and losing local data on every
future build. One disk is not a backup.

---

## Traps that cost real time

Each of these was hit here. Do not relearn them.

0. **A removal that returns null must be checked against its callers, not
   its call sites' shape.** `VideoAds.make` was made to return null with a
   comment saying "PhotoViewer null-checks the result at every use". It does
   not: `setImageIndex` assigns the result and calls `setWaitingPaused` on it
   in the very next line, so **every photo opened in a chat dereferenced
   null**. The viewer's window was already up, so the chrome and caption drew
   while the image stayed black; `animationInProgress` was left at 1, which
   spins the invalidate loop at ~50% CPU forever; `isVisible` stayed true, so
   every later tap was refused and fell through to the chat list, which looked
   like messages "selecting themselves". One null dereference, four symptoms,
   none of them resembling its cause. Nothing crashed, and nothing reached
   logcat - something in the touch path swallows it.

   Two lessons, both already written down and both ignored: *verify by reading
   the result back*, and a guard is not free. It cost a session. The cure was
   deleting VideoAds outright, which is what should have happened first.

0b. **`mode=compile` does not build the native side.** It is javac only, so a
   C++ mistake sails through it and fails the 22-minute build instead. Two did:
   `std::vector` passed where `.data()` was wanted, and a local left unused
   when its only reader was deleted. Anything under `jni/` needs a full build
   to mean anything.

0c. **The phone is the fastest instrument.** `adb` settled in ten minutes what
   three sessions of reading could not: a screenshot showed the viewer open
   and black, `top` showed the process burning 50% CPU on a static screen, and
   a temporary `android.util.Log.e` tag showed exactly which two statements the
   failure sat between. Release builds log nothing by default (Telegram's own
   logging is off until Settings → tap the version ten times → Enable logs),
   so print rather than reason when a bug is reproducible.

1. **A dependency you remove may be supplying something unrelated.** Dropping
   `androidx.mediarouter` with Chromecast took `androidx.media` with it, which
   supplies `MediaSessionCompat` — lock screen controls, PiP, Android Auto and
   vendored ExoPlayer's mediasession extension. The errors pointed 30 lines deep
   into a vendored file with no hint that Chromecast caused it. `appcompat` then
   broke the same way, *two commits later*, because it arrived via mediarouter
   **and** the ML Kit libraries, so removing one left the other propping it up.
   `play-services-auth` also supplied a **drawable resource**
   (`googleg_standard_color_18`). **Before removing a dependency, list what the
   tree imports that it might be the only source of.** The desktop side's dav1d
   note is the same lesson.

2. **`MediaController.java` contains a NUL byte**, so git classifies it as
   *binary* and does not apply `core.autocrlf=input` to it. Python's
   `write_text` emits CRLF on Windows, so a 9-line edit committed as a
   **13,946-line diff**. Write that file with explicit LF (`open(p,"wb")`), and
   check `git show --stat` before pushing. Every other file is normalised, so
   git's CRLF warnings elsewhere are harmless.

3. **The APK verification check was lying.** It grepped the *zip listing* for
   `com/google/android/gms`, which reports a clean zero even on unmodified
   upstream — compiled classes are not zip entries, they live inside
   `classes*.dex`. It now greps the dex bytes, whose string tables carry the type
   descriptors. Note `zxing`, `gson` and `guava` legitimately appear under
   `com/google/`; they are offline libraries and were kept deliberately.

4. **Brace-matched deletions swallow adjacent statements.** Cutting the Play
   repricing out of `getPremiumGiftCodeOptions` also took the loop that filled
   the result list — syntactically valid, silently returning **no gift options**.
   The compiler cannot catch this class of mistake. Read the result back.

5. **Gut to an immediate answer, not to a failure.** Several Google paths retry:
   ML Kit's failure handler reschedules every two seconds while it believes a
   module is downloading, which without Play Services never resolves. Return the
   "unavailable" answer directly instead of letting it fail.

6. **Requests carry tokens the native layer waits on.** Play Integrity, the
   captcha and the billing flows all hand a request token to native code.
   Dropping one silently leaves that request hanging, so each must *answer* with
   a failure string upstream already sends. This is the same shape as the
   desktop side's MTProto warning.

7. **Preserve resource release when gutting.** `PhotoViewer`'s face check had a
   not-operational branch that releases the bitmap; skipping it would leak a
   full-size bitmap per image viewed.

8. **`MSYS_NO_PATHCONV=1`** is needed for `openssl -subj "/CN=..."` in Git Bash,
   or the leading slash is converted to a Windows path and the command fails.
   Do not suppress stderr while doing one-shot crypto — the first keystore
   silently was not created.

9. **Forcing a getter is a probe, not the finished job.** Upstream almost always
   already ships an "unavailable" branch — for regions where Premium cannot be
   sold, or devices without Play Services — and making that the live one is the
   cheapest way to see what a removal touches while keeping local state
   coherent. `premiumFeaturesBlocked()`, `premiumPurchaseBlocked()` and
   `starsPurchaseAvailable()` between them neutralised the premium economy across
   ~90 call sites *and* the gold star and badge gradients that the desktop side
   needed a separate edit for.

   But the intended end state is **deletion**, not a guard with dead code behind
   it. Everything currently sitting at the probe stage is listed under
   "Remaining work" below and should be finished that way.

---

## Architecture

**Push.** No FCM. A provider reporting `hasServices() == false` makes upstream
register a null token, and notifications then arrive over the MTProto push
connection. `NotificationsService` is a real foreground service to keep the
process alive — registered as **`specialUse`** on API 34+, because Android 15
caps a `dataSync` foreground service at six hours a day, which for a service
whose whole job is staying connected would stop notifications partway through
every day. Android 8+ requires a permanently visible notification; it is posted
silently on an `IMPORTANCE_MIN` channel. Both keep-alive settings default on.
`AppStartReceiver` was registered for `org.telegram.start` but only ever acted on
`ACTION_BOOT_COMPLETED`, so the restart broadcast did nothing — harmless
upstream, load-bearing here.

**Ghost mode.** One switch, default on, stored in `mainconfig` via
`getGlobalMainSettings` (not `SharedConfig.saveConfig`, which is the
`userconfing` security block). It replaces the **Contacts tab**: a toggle, not a
destination, so it has a tab index but no pager position (`POSITION_NONE`) and is
skipped in the selection and gesture loops. Suppresses typing/activity
(`sendTyping` → false), online presence (only the `updateStatus` condition, so
the offline branch still latches and the rest of `updateTimerProc` runs), and
story views (**after** `seenStories`/`saveCache`, or stories stay unread
locally). Server-side: Last Seen → Nobody and `hide_read_marks`, once per
account and on every explicit enable, never reversed;
`setGlobalPrivacySettings` replaces the whole object so the current settings are
read back first or the archive settings get reset.

**Read receipts are deliberately NOT suppressed** — see the desktop notes. It is
architectural, not a bug. Do not re-attempt.

**Ads.** Three surfaces, not one: `getSponsoredMessages`, `VideoAds.make` (video
player) and `contacts.getSponsoredPeers` (search). `VideoAds` is deleted
outright (889 lines) and all four view/click beacons are deleted with their
eighteen call sites. What is left is `getSponsoredMessages` returning null and
the empty sponsored UI around it — the next thing to go.

**Updater.** Ours, reading this repository's releases: `LoogriGramUpdate` asks
`api.github.com` which release is newest, compares its tag with
`BuildConfig.LOOGRIGRAM_TAG` (CI stamps the short sha; a hand build carries
`dev` and never updates), downloads the APK, checks its size and zip magic,
and hands it to the system installer. The request is anonymous and is the only
outbound traffic here that does not go to Telegram. **Android never installs
silently** - the system installer always asks, and wants this app allowed to
install unknown apps the first time. The UI is a sixth tab after the profile,
present only when there is something to say: "Update?", then the percentage,
then "Install"; refusing leaves the tab to tap later, and an update downloaded
but not installed is offered again once per run. `LaunchActivity.checkAppUpdate`
keeps its name and its callers and drives this now. Testing it needs two
builds: the one that publishes a release also installs as that tag, so it sees
itself as current.

**Money messages are held, not shown.** `LoogriGramHidden` lists the TL types -
invoices, paid media, giveaways, payments, gift codes, Stars gifts and
transfers, boosts, suggested-post payments - and `MessageObject.setType` gives
them `contentType = -1, type = -1`, which is **upstream's own state for a
message that exists and is never drawn** (it uses it for a cleared history), so
nothing downstream had to learn a new case. The message is still parsed and
counted, because the read position only moves past messages we hold; dropping
one leaves an unread badge that scrolling cannot clear. `setType` also clears
the text `updateMessageText` produced a line earlier, `generateCaption` (which
runs after) refuses to put a caption back, and notifications skip these in the
loop that already drops handled conference calls. **Still open:** the chat list
row still rises with an empty preview - desktop walks back to the newest
displayable message instead.

**Phone contacts: none.** Not reduced, gone. No `READ_CONTACTS`,
`WRITE_CONTACTS`, `GET_ACCOUNTS`, `MANAGE_ACCOUNTS`, `AUTHENTICATE_ACCOUNTS`
or sync-settings permissions, no account authenticator, no sync adapter, no
cached phonebook (its two database tables are not created), no address book
tab in the attach menu, no invite screens, no `ContactsController.Contact`
type. Uploading every number in the address book to find out which of them had
accounts was the largest thing this app said about people who never installed
it. **Telegram-side contacts are untouched** - loading them, adding one by
phone number by hand, deleting them, and "Delete synced contacts", which
matters because an account can still hold contacts imported before this.

**Location.** Cannot geolocate: every location permission is gone from all six
manifests, which the OS enforces and GrapheneOS shows in app info.
`ACCESS_MEDIA_LOCATION` went too — that one exposes photo EXIF coordinates.
Received locations open in any maps app via a `geo:` intent, guarded at
`LocationActivity.onFragmentCreate` because seven of nineteen entry points do
not check `isMapsInstalled`.

**Gifts.** Sending blocked at `GiftSheet.show`/`SendGiftSheet.show` — one
chokepoint for eighteen call sites. **Receiving must keep working:**
`ChatMessageCell` draws received gifts through `StarGiftSheet` and
`MessageObject` formats text through `StarsIntroActivity` in nine places, so
deleting the gift and Stars UI breaks ordinary message rendering.

---

## Survived, against expectation

Upstream had non-Google implementations sitting behind the Google ones:

- **QR scanning still works.** `CameraScanActivity` kept a complete **zxing**
  fallback; removing the Google branch promotes it to the only one. Scanning a QR
  to log in is unaffected.
- **Passport/ID scanning still works.** Only the driver-licence *barcode* path
  was Google's; `recognizeMRZ` beside it is Telegram's own pure-Java reader.

Genuinely lost: automatic mask placement (face detection positioned masks by
themselves — they can still be placed by hand), Chromecast, Wear OS, the sticker
cut-out, and auto-translate language detection.

---

## Remaining work

### Finish the removals properly

Several systems here were neutralised with a guard or reduced to an inert stub
rather than deleted, because a guard is cheap and safe on a four-minute compile
loop. That is **not** the intended end state — the target is a leaner tree with
no dead code. All of this is our code and upstream's structure is not a
boundary, so rewriting the callers is fair game, including extracting a helper
out of a class that is being deleted or changing how a message renders.
**The desktop fork has the same backlog; see its `LOOGRIGRAM.md`.**

In rough order of how much is left behind:

- **Stars / Gifts / TON UI.** Still present and compiled, and now the largest
  thing left. What used to block it is half gone: money messages are no longer
  drawn at all (see "Money messages are held, not shown"), so the old reason -
  that deleting the directories breaks rendering of any chat that merely
  *mentions* a gift - is much weaker. What remains to check before deleting:
  `StarsIntroActivity.replaceStars` / `replaceStarsWithPlain` (86 calls between
  them) and `formatStarsAmount` / `formatTON` / `replaceDiamond`, all of which
  are text-span helpers living in an activity class; extract them the way
  `CurrencyFormat` was extracted, then the screens can go. `AmountUtils.Currency`
  modelling STARS/TON as a core money type is the part most likely to fight
  back. `BillingController`'s last eleven callers are in these screens and it
  dies with them.
- **Location.** `LocationActivity` and `ChatAttachAlertLocationLayout` remain,
  unreachable, holding ~135 references to `IMapsProvider` between them. Deleting
  them lets the interface and the `onFragmentCreate` guard go too.
- **Chromecast.** Four files reduced to Google-free stubs so ~98 call sites in
  `MediaController`, `PhotoViewer` and `AudioPlayerAlert` keep compiling.
  Deleting them means editing those three files (4k, 24k and 6k lines).
- **Premium economy.** The three forced getters leave every branch behind them in
  place; `PremiumPreviewFragment`, `GiftPremiumBottomSheet` and the tier cells
  are largely dead weight now.
- **`if (true) return;` guards** still standing, each of which should become a
  deletion: `getSponsoredMessages` (and the empty sponsored UI around it),
  `isMapsInstalled`, `GiftSheet.show` / `SendGiftSheet.show`. Done on
  2026-09-20: `VideoAds.make` (deleted, and it was actively broken - see the
  traps), the four ad beacons, `checkAppUpdate` (now drives our updater).
- **Build warnings.** Native: four variable length arrays remain, all
  `aesOut[MSC_STACK_FALLBACK(...)]` in libtgvoip's crypto path, left because
  that code is going. Resources: 40 AAPT "multiple substitutions in
  non-positional format" on upstream strings - about half are gift/Stars
  strings that die with the money removal; the rest (`AddManyMembersAlert*`,
  `Languages_*`, `NoContactsYet*`, `YourEmailCode*`, `ResetAccount*`,
  `formatterMonth*`, `UnconfirmedAuthMultipleFrom_*`, `WidgetPasscodeEnable2`,
  `StoryAddedToAlbum*`) want `%s` turned into `%1$s`, which no compile can
  check and a wrong one throws at display time.
- **Done on 2026-09-20, for the record:** `ObjectDetectionEmojis` deleted,
  `CaptchaController` folded into its one caller, `BillingController`'s currency
  half extracted as `CurrencyFormat` (61 call sites) with `BillingUtilities`
  deleted.

### Then

1. **Verify what first use could not.** Installed and working, but still open:
   notifications after hours idle and after a reboot (the push path is ours and
   fails *slowly*); ghost mode's four signals confirmed from a second account,
   including that the read date is hidden and no burst of receipts follows
   turning it off; a received location opening in a maps app; and a sweep for any
   premium, Stars or gift surface still reachable.
2. **Cosmetic pass**, artwork half only — the naming half is done. The launcher
   label, the in-app strings and the two screens that drew Telegram's *wordmark
   over* the app name (the intro page and the chat list header, both now plain
   text) are handled; `KEEP_COMPILED` in `LocaleController` stops the cloud
   language pack putting "Telegram" back, which is the trap that makes renaming
   look like it did nothing. Still to do: the launcher icon itself — L and G
   laid diagonally over the default, `icon_plane.xml` being a vector so the mark
   can be hand-written as paths, `icon_foreground.png` raster at five densities;
   the five alternative app icons and their `activity-alias` blocks; the
   `telegram_logo_2` wordmark still drawn by the stories row; and the dead
   pre-API-26 launcher paths.
3. Consider caching the native build (`.cxx`) the way desktop caches `out/`, if
   22 minutes becomes annoying. Same mtime problem applies.

## Known limits

- Sending a message is inherently visible; none of this hides anything from
  Telegram's servers.
- Last-seen concealment is reciprocal, and hiding the read date costs seeing
  other people's.
- A permanent push-service notification is an OS requirement, not a choice.
- Story rings may reappear unread on other devices (view suppression is the same
  coupling as read receipts, accepted because stories expire in 24h).
- If Telegram ever hard-requires a captcha or integrity token to log in, login
  fails and this client cannot do anything about it.
- Premium limits still apply server-side; removing the upsell does not grant
  Premium.
