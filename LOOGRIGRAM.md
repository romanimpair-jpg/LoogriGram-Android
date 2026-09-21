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
| Installed on the phone | **Yes.** `gf20af361`, installed 2026-09-22 over `adb` (`adb install -r` succeeded, so the key matched); launches clean |
| Pending build | `gf2f12360`, dispatched 2026-09-22 ([run 35662105360](https://github.com/romanimpair-jpg/LoogriGram-Android/actions/runs/35662105360)) — 9 commits past the installed one: the rest of paid messages so far and every build warning. **Not yet known to succeed** |
| App name | Done — launcher, in-app strings, and the two wordmark screens |
| Phone contacts | **Never touched.** Permissions, account and sync adapter all gone |
| Updater | Ours, from this repo's releases. Checks on every cold start, then hourly; manual row in Settings (2026-09-21). The installed `gf20af361` is the first build with that behaviour — **its automatic check is still untested**, it needs a later release to find |
| Ads | **Gone**, all three surfaces, down to `MessageObject`'s fields (2026-09-21) |
| Money messages | Held in history, never drawn — desktop's hidden-content rule. The chat list no longer rises for one |
| Paid messages | **Mostly done** (2026-09-21/22): users who charge are locked, nothing ever pays, no price shown where you write. Left: setting a price on your own messages — see "Paid messages" |
| Photo/video viewer | **Fixed** 2026-09-20; was our own null dereference, see the traps |
| Build warnings | **All fixed** in `f2f12360` (native, CMake, Gradle, Kotlin, CI); the native half is only proven once the pending build finishes. Still open: 40 AAPT resource warnings, upstream's strings |
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

### Start here next session (written 2026-09-22)

1. **Did `gf2f12360` build?** The user reports back; don't poll. Every commit
   in it passed `compile`, so a failure is native or packaging. The native
   changes are exactly the five warning fixes in `d6b0890c` (libtgvoip's
   `aesOut` vectors, the `RtcEventLogFactory` constructor) and the
   `project(tmessages)` line in `jni/CMakeLists.txt` - look there first. On
   success, grep the log for `C/C++: .*warning:`; it should find none.
2. **Test the updater, for real this time.** `gf20af361` is the first build
   that checks on every cold start. When `gf2f12360` is published, a cold
   start of the installed app should show the sixth tab ("Update?"), then the
   percentage, then "Install". Also try Settings → "Check for updates" (bottom
   of the help section, the build's tag underneath): with nothing newer it says
   "LoogriGram is up to date (g…)". After installing, the new build must not
   offer itself. The phone is reachable over USB:
   `C:\Users\Loogris\platform-tools\adb.exe` (not on PATH), package
   `com.loogrimedia.loogrigram`.
3. **Check the paid-message lock on the phone** (needs a user who charges per
   message - any account can set a price on itself in official Telegram):
   their chat shows "X only accepts paid messages, which LoogriGram doesn't
   send." instead of a compose field; their row is padlocked in the share and
   forward pickers and a tap there says the same line; a story reply to them
   is locked too. A group that charges must still open and read normally, and
   a send there should end in the same line as a toast.
4. **The last cut from 2026-09-21 is still unverified on screen** - see the
   list below; it shipped in `ge9a33bc2` and nobody has looked yet.
5. Then finish paid messages: setting a price on your own messages (privacy,
   group permissions, channel direct messages). See "Paid messages" and
   "Remaining work".

Unverified from 2026-09-21 (ads and money-row removal), since a compile cannot
see layout:
   - chat list: a gift or payment arriving must not move the chat to the top
     or blank its preview; the unread badge must still count it and clear;
   - opening a channel: scrolling to the newest message, the jump-to-bottom
     button, and new messages arriving while open (`processNewMessages` lost
     all its ad placement);
   - message bubbles: link previews with photos, the side share/go-to button's
     position, name tap highlight, time placement;
   - global search results list, and "show more" there;
   - reporting a message or chat (the report sheet lost its ad mode);
   - Settings and your own profile no longer list Premium, Stars, TON,
     Business or Send a Gift; a bot you own has no balance or affiliate rows.

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

10. **Large scripted removals fail in the same few ways.** Each of these cost a
    failed compile on 2026-09-21; check them *before* dispatching:
    - deleting a public member: grep its callers across the tree, not the file
      (`removeFromSponsored` had seven outside `ChatActivity`);
    - de-nesting `Outer.Inner` to a top-level class: the declaring file's own
      bare `Inner` uses are invisible to a repoint of `Outer.Inner` (bit twice);
    - unwrapping a dead `if` also removes the scope it gave its locals, and one
      collided with a same-named local further down the method;
    - a symbol sweep finds a missing import, never a wrong one (androidx vs
      zxing `MathUtils`);
    - any brace-balance check must blank strings before comments, or
      `"tg://..."` loses everything after `//`; compare to `HEAD`, never trust
      the absolute count;
    - cut by exact text with asserted match counts, not by line numbers - a
      line-number cut shifted and overwrote the one field it meant to keep.

11. **"Constant false" is a proof, not a guess.** The ad removal was safe to do
    mechanically only because `isSponsored()` was *proven* constant: nothing
    left in the tree wrote `MessageObject.sponsoredId` (the one other write was
    `ReportBottomSheet`'s own field of that name). Before simplifying every
    `x.isFoo()` to false, find every writer of what it reads.

12. **Before deleting a variable, grep every reader of it - in the whole
    method, not the lines you are looking at.** Twice on 2026-09-22 a compile
    failed on a name removed with its obvious use: `ShareDialogCell`'s online
    dot also scaled itself by the price badge's `priceT`, and the attach
    menu's `paidUser` also hid the quick replies button sixty lines lower.

13. **Removing a parameter from a widely called method is a script job, and
    the script must match by name *and* arity.** The price parameter sat on
    ~25 methods with ~350 call sites in 48 files. What worked (2026-09-21): a
    small Java-aware parser that collects every declaration carrying the
    parameter across the tree, refuses any name/arity also declared without
    it, then removes the argument at that index from every call, logging each
    removed expression for review. Generic names (`send`, `onSend`,
    `sendMedia`) collide with unrelated methods; the arity check is what stops
    a paid-live-comment `send(text, stars)` being edited by mistake. Remember
    lambdas implementing an interface (`(a, b, payStars) ->`) - a declaration
    scan does not see them. The scripts lived in the session scratchpad and
    are gone; the approach is what matters.

14. **Inlining a callback is only safe once its body cannot `return`.** A
    `return` inside `x -> { ... }` leaves the lambda; pasted inline it leaves
    the enclosing method. Check each body - a `return` inside a *nested*
    `Runnable` is fine - and watch for locals that now share a scope with the
    enclosing block.

15. **Windows PowerShell 5.1 mangles quoting for native commands.** A commit
    message piped with `git commit -F -` from a here-string lost its quotes
    and arrived as pathspecs, and `python -c` scripts containing `"` broke the
    same way. Write the message or script to a file in the scratchpad and pass
    the path. Also: `Select-String` with a backtracking regex over the whole
    tree ran past two minutes; use the Grep tool (ripgrep).

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

**Ads: none, and no code for them.** Three surfaces, not one, all deleted
rather than guarded: `getSponsoredMessages` (in-chat), `VideoAds` (video player,
2026-09-20) and `contacts.getSponsoredPeers` (above global search results). The
chat's ad placement is gone from `processNewMessages` (`findAdPlace`, the
not-yet-placed queue, pasting messages under ads, `skipSponsored` on both
scroll-to-last methods), as are the viewer's ad subsystem, the cell's ad
rendering and two-part side button, the ad menus, the report sheet's ad mode,
and finally `MessageObject`'s ten `sponsored*` fields and `isSponsored()`
itself - so the compiler guarantees nothing is left asking. `BotAdView`,
`SearchAdsInfoBottomSheet` and `SponsoredMessageInfoView` are deleted. What
still *mentions* ads is not about showing them to you: the channel owner's
"switch off ads for subscribers" toggle (inside the ad-revenue screen) and
Premium's "no ads" row - both go with the money and Premium removals.

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
keeps its name and its callers (it runs on every `onResume`) and drives this
now.

When it asks: the first check of every run always goes out, later resumes at
most once an hour, and only a real answer from GitHub restarts that clock - a
failed request leaves the next resume free to retry. Until 2026-09-21 it was
once a day, counted from any attempt, and that is why `ge9a33bc2` never
appeared on the phone: the installed build had checked that morning, before
the release existed. There is also a manual check, the "Check for updates" row
at the bottom of Settings' help section (subtitled with the build's tag): a
newer build found that way re-opens the download prompt even if it was refused
before; otherwise a toast says up to date or GitHub unreachable. The only
earlier manual way was item 9 of upstream's hidden debug menu.

Testing it needs two builds: the one that publishes a release also installs as
that tag, so it sees itself as current.

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
loop that already drops handled conference calls.

A held message never becomes the dialog's last message either (2026-09-21), so
a gift does not lift the chat to the top with a blank preview. Two halves, kept
in step: `MessagesStorage.putMessagesInternal` puts null into the per-dialog
map, which is upstream's own "move the counts, keep `last_mid` and the date"
(the key is registered, not skipped, or the unread count never reaches the
database; `last_mid_group` is read back and rebound so an album preview
survives; a topic gets an `onlyCounters` update), and
`MessagesController.updateInterfaceWithMessages` tracks the held message apart
from `lastMessage`. A chat with no row yet is still created from the held
message - an empty preview beats a row pointing at message 0. Not covered, as
on desktop: a dialog list fetched from the server has one message per chat, so
a held one there shows blank until the next message.

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
chokepoint for eighteen call sites. **Receiving must keep working**, and that
settles the shape of the rest of the money removal: `ui/Stars` and `ui/Gifts`
**cannot be deleted as a block.** `StarGiftSheet` is genuinely mixed -
`ChatActionCell` opens it for a gift someone was *given* and `ChatMessageCell`
draws its gift icon in link previews, while `PeerColorActivity` uses its
resale/buy alert. The profile gifts tab (`ProfileGiftsContainer`/`View`) and
`StarGiftUniqueActionLayout` are display too. The end state is splitting the
display half out from the buying/selling half, not deleting directories.

**Helpers freed from money screens** (2026-09-20/21), so the screens can go
without taking ordinary rendering with them: `messenger.StarsFormat` (the
Stars/TON/diamond span and number formatters, 218 call sites), `CurrencyFormat`,
`ui.Components.Particles` (sparkle effect), `StarGiftPatterns`,
`SuperRipple`/`ISuperRipple`/`SuperRippleFallback`, `FeatureRow` (was
`ExplainStarsSheet.FeatureCell`), `FeatureIconCell` (was
`AffiliateProgramFragment.FeatureCell`), `ColorfulTextCell`, and
`AndroidUtilities.percents` / `replaceUnderstood`. Two were renamed on the way
out because four classes already declare a nested `FeatureCell`.

**Paid messages: locked, never paid, never priced** (2026-09-21/22, as
desktop). A user who charges Stars per message is treated like one who only
accepts Premium senders. `DialogObject.isPremiumBlocked` answers true for both
requirements, so every cell that draws the Premium padlock draws it for them
too, and the chat shows the `LoogriGramPaidMessagesLocked` line (*"%1 only
accepts paid messages, which LoogriGram doesn't send."*) in place of the
compose field (`ChatActivity.updateBottomOverlay`). The five "tapped a
padlocked row" toasts share `DialogObject.getLockedText`, which keeps the
Premium wording for the Premium case. A server refusal
(`ALLOW_PAYMENT_REQUIRED_*`) is handled once, in `AlertsCreator.processError`,
which every send path already calls: `MessagesController.lockPaymentRequired`
marks the user and refetches full info. That deliberately does *not* go into
`cachedIsUserContactBlocked`, which outranks full info and would keep them
locked for the session after they stopped charging.

**Groups that charge are not locked**, also as desktop: they open and read
normally, a send goes out unpaid, the server refuses, and the toast says why.
`ChatObject.getRequirementToContact` was deleted for this - it would have made
such a group padlocked in search, and tapping it would toast instead of open.

Nothing pays: the price is gone from every send method, delegate and lambda
(`payStars`, and `stars` where it meant the same), from `SendMessageParams`,
from the wire (`allow_paid_stars`, outgoing `paid_message_stars`), and with it
AlertsCreator's pay-to-send confirmations, StarsController's undo toast and
send queue, and the `BALANCE_TOO_LOW` buy-Stars sheet. Nothing shows a price
where you write: row badges, the share screens' totals, the compose hints,
the attach-menu and photo-viewer send buttons, story replies, and the old/new
price stored on a failed message (`MessageCustomParamsHelper` still *skips*
flags 64/128 when reading, for rows an older build wrote). Features upstream
switched off in a chat with a price - scheduling, the schedule hint - are on.

What still reads a price, on purpose: `SendButton`'s price pill and
`ChatActivityEnterView.getStarsPrice` (answers 0; the hook `PeerStoriesView`
overrides) belong to **paid live comments**, and the gift sheets
(`SendGiftSheet`, `GiftOfferSheet`) read `getSendPaidMessagesStars` but are
unreachable behind the gift-sending block. Both go with their own removals.

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

- **Paid messages, the price-setting half** - next. Desktop's `9d363a2689`
  is the map: the "Charge for messages" privacy option with its star slider
  and "Remove fee" exceptions (`NoPaidMessages` key), a group's "charge Stars"
  permission toggle, and a channel's direct-messages price (keep the toggle
  that allows direct messages at all - that is not money). Also the
  `messagesFeeUpdated` / `nopaid_messages_exception` machinery in
  `StarsController` and `TopicsController`, and the remaining
  `StarsNeededSheet` callers outside send paths (`ChatActionCell`,
  `PostsSearchContainer`, `StakedDiceSheet`). When `getSendPaidMessagesStars`
  and `DialogObject.getMessagesStarsPrice` lose their last readers (the gift
  sheets), delete both.
- **Paid live comments.** `LiveCommentsView.send(text, stars)`, the price
  pill in `ChatActivityEnterView.SendButton` (`setStarsPrice`), the
  `isLiveComment` tiers (`HighlightMessageSheet`) and
  `PeerStoriesView.getStarsPrice`. Desktop removed its equivalent.
- **Paid media** (sending photos with a price): `SendMessageParams.stars`,
  `ChatAttachAlertPhotoLayout.setStarsPrice`, `showMediaPriceSheet`. Separate
  from paid messages - left alone on purpose.
- **Stars / Gifts / TON UI.** The helper extraction is finished (see "Helpers
  freed from money screens"), so what is left is real coupling, measured
  2026-09-21 as non-money files still touching `ui/Stars`, `ui/Gifts`, `ui/TON`:
  `StarsController` 33 (`ChatActivity` 24 refs, `SendMessagesHelper` 17,
  `PeerColorActivity` 14, `MessagesController` 14, `AlertsCreator` 13),
  `StarsIntroActivity` 19, `GiftSheet` 8, `StarGiftSheet` 7,
  `BotStarsController` 5. Shape: split the display half (received gifts) out
  of the buy/sell half, then delete the rest - not the directories whole. The
  three affiliate fragments are now reached only from other money screens and
  go with them. `AmountUtils` lives in `messenger/utils/tlutils` and stays.
  `ChannelMonetizationLayout` (ad revenue, plus the "switch off ads for
  subscribers" toggle) and `BillingController`'s last callers go here too.
- **Location.** `LocationActivity` and `ChatAttachAlertLocationLayout` remain,
  unreachable, holding ~135 references to `IMapsProvider` between them. Deleting
  them lets the interface and the `onFragmentCreate` guard go too.
- **Chromecast.** Four files reduced to Google-free stubs so ~98 call sites in
  `MediaController`, `PhotoViewer` and `AudioPlayerAlert` keep compiling.
  Deleting them means editing those three files (4k, 24k and 6k lines).
- **Premium economy.** The three forced getters leave every branch behind them in
  place. Settings and the own-profile menu have lost their Premium, Stars,
  MyTON, Business and Send-a-Gift rows (2026-09-21), but
  `PremiumPreviewFragment` (with its "no ads" row), `GiftPremiumBottomSheet`,
  `LimitReachedBottomSheet`'s boost-level feature lists and the tier cells are
  still largely dead weight.
- **`if (true)` guards** still standing, each of which should become a
  deletion: `isMapsInstalled`, `GiftSheet.show` / `SendGiftSheet.show`. Done:
  `getSponsoredMessages` and everything behind it (2026-09-21), `VideoAds.make`,
  the four ad beacons, `checkAppUpdate` (2026-09-20). A plain `grep -rn "if
  (true)"` also finds `MessagesController` (`addPhotoAtStart`),
  `AndroidUtilities`, `ChatActivity` and `DialogsSearchAdapter` - their origin
  was not checked; some may be upstream's own.
- **Build warnings.** Native, CMake, Gradle, Kotlin (`buildSrc`) and CI all
  fixed 2026-09-22 (`d6b0890c`, `f2f12360`). The node `punycode` / `url.parse`
  deprecations in the log come from inside GitHub's own actions. Still open:
  40 AAPT "multiple substitutions in
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
- **Done on 2026-09-21, for the record** (21 commits, `0031a094..e9a33bc2`):
  the chat-list fix for held money messages; every money-screen helper freed
  (above); the Settings and profile money rows, bot and channel revenue rows,
  the affiliate rows, and the money deep links in `LinkManager` and
  `LaunchActivity` deleted; `ProfilePremiumCell` deleted; all three ad surfaces
  deleted down to `MessageObject`'s fields. 5,135 lines deleted, about 3,250 net.
- **Done on 2026-09-21/22, for the record** (`3935357b..f2f12360`): paid
  messages locked (`3935357b`), never paid (`1618bebd`, ~1,700 lines),
  `onSend`'s `showToast` dropped (`f20af361`), prices gone from rows, share
  screens and the compose field (`80e73326`..`60ef620c`), the refused-price
  fields (`8f352db4`); the updater's start-up check and Settings row
  (`a1d5345d`); every build warning but AAPT's (`d6b0890c`, `f2f12360`).

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
