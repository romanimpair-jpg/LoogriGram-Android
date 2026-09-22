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
| Pending build | `gaf5d70a5`, dispatched 2026-09-22 ([run 35796008121](https://github.com/romanimpair-jpg/LoogriGram-Android/actions/runs/35796008121)) — 22 commits past the installed one, 26,440 lines lighter. Every commit in it passed `compile`. **Not yet known to succeed** |
| App name | Done — launcher, in-app strings, and the two wordmark screens |
| Phone contacts | **Never touched.** Permissions, account and sync adapter all gone |
| Updater | Ours, from this repo's releases. Checks on every cold start, then hourly; manual row in Settings (2026-09-21). The installed `gf20af361` is the first build with that behaviour — **its automatic check is still untested**, it needs a later release to find |
| Ads | **Gone**, all three surfaces, down to `MessageObject`'s fields (2026-09-21) |
| Money messages | Held in history, never drawn — desktop's hidden-content rule. The chat list no longer rises for one |
| Paid messages | **Done** (2026-09-21/22): users who charge are locked, nothing ever pays, nothing charges. The price-setting half went with the privacy option, the group permission and a live's price per comment |
| Paid media, live comments | **Gone** (2026-09-22): no price on a photo or album, no paid or highlighted live comment, no Star donations to a live |
| Paid reactions | **Gone** (2026-09-22): the star is not offered among the reactions, and its sheet and flying-star overlay are deleted. Left: the drawing of one that *arrives*, and the now-uncalled bookkeeping in `StarsController` |
| Gifts | Sending, auctions, selling, buying and crafting all **deleted** (2026-09-22). Receiving is untouched. Left: the buy-a-collectible tab in `PeerColorActivity`, which still holds `ResaleGiftsFragment` |
| Location | **Gone** (2026-09-22): the map screens are deleted, every received location opens in a maps app, and the weather sticker went with them |
| Photo/video viewer | **Fixed** 2026-09-20; was our own null dereference, see the traps |
| Build warnings | **None of ours are left.** Native, CMake, Gradle, Kotlin and CI fixed in `d6b0890c`/`f2f12360`; both AAPT sets fixed 2026-09-22 (`663ec903`, `77673c28`). What remains is javac's two notes, which are upstream's and third-party's — see "The javac notes are not ours" |
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

### Start here next session (written 2026-09-22, late)

1. **Did `gaf5d70a5` build?** The user reports back; don't poll. Every commit
   in it passed `compile`, and nothing in it touches `jni/`, so a failure
   would be packaging or resources rather than native.
2. **Look at the three screens this session rebuilt.** None of it has been
   seen running - `compile` type-checks, it does not draw:
   - the **gift sheet** (`StarGiftSheet`): open a gift you were given. Its
     pages animate between info, upgrade and wear, and crafting's two pages
     were cut out of that machine; check the sheet opens on the right page,
     the height animates cleanly, and the action row reads Transfer / Wear /
     Share, with Share where Sell used to be.
   - the **reactions picker**: the star must not appear in it, and the other
     reactions must still send.
   - the **profile colour screen** (`PeerColorActivity`): its collectible
     tabs still list gifts you own; only buying is meant to be gone, and the
     buy tab is still there until the next cut.
3. **Test the updater, for real this time.** `gf20af361` is the first build
   that checks on every cold start. When `gaf5d70a5` is published, a cold
   start of the installed app should show the sixth tab ("Update?"), then the
   percentage, then "Install". Also try Settings → "Check for updates" (bottom
   of the help section, the build's tag underneath): with nothing newer it says
   "LoogriGram is up to date (g…)". After installing, the new build must not
   offer itself. The phone is reachable over USB:
   `C:\Users\Loogris\platform-tools\adb.exe` (not on PATH), package
   `com.loogrimedia.loogrigram`.
4. **Check the paid-message lock on the phone** (needs a user who charges per
   message - any account can set a price on itself in official Telegram):
   their chat shows "X only accepts paid messages, which LoogriGram doesn't
   send." instead of a compose field; their row is padlocked in the share and
   forward pickers and a tap there says the same line; a story reply to them
   is locked too. A group that charges must still open and read normally, and
   a send there should end in the same line as a toast.
5. Then continue the money removal where it stopped - see "Remaining work".

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

9b. **A range cut needs a unique anchor and a size cap.** Cutting from a
    signature to the next one is the fastest way to remove a method, and it
    reached past its target three times on 2026-09-22. Once because the
    closing anchor matched later than intended, taking `isInScheduleMode`
    out of `RichEditor` with `openLocationPicker`. Once because two loose
    methods sat *between* the nested classes being moved out of `GiftSheet`,
    so they travelled with them. And once, worst, because the opening anchor
    was not unique: `if (visibleReaction != null && visibleReaction.isStar)`
    appears twice in `ChatActivity`, sixty lines apart, and the cut started
    at the wrong one and swallowed **681 lines** - `closeMenu`,
    `createEmptyView` and `selectReaction` among them. Nothing about the
    result looked wrong; the reported line count did.

    So: assert the opening anchor appears exactly once, assert how many
    lines the cut may span, and read the count the script prints. All three
    were caught that way in the end, the third only after it had been
    applied and had to be reverted.

10. **What survives a removal is the reference nowhere near it.** Every
    compile failure on 2026-09-22 was the same shape: the feature's own code
    came out cleanly, and what broke the build was a mention of it somewhere
    structurally unrelated - `DialogsActivity` nulling the auctions panel in
    its destroy path and registering its theme colours, `hideHints` hiding
    the birthday hint, the gift sheet detaching the craft picker in
    `dismiss()`, `PendingPaidReactions` holding the overlay it hid on
    cancel, a `runLinkRequest` call site passing `null, null` where the
    others passed the variables, and a second caller of a constructor whose
    signature had just lost two arguments. Grepping the feature's own names
    finds none of these. Grep for the *field* and the *type* as well, and
    expect the compile to find one or two anyway.

10c. **Large scripted removals fail in the same few ways.** Each of these cost a
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
Received locations open in any maps app via a `geo:` intent - from a message,
a poll answer, shared media, the admin log, a group's address, a business
address and a story's location sticker, each handing the point over directly
with a "no maps app installed" bulletin when nothing answers. **The map
screens are deleted** (2026-09-22, 8,408 lines): `LocationActivity`,
`ChatAttachAlertLocationLayout`, their adapters and cells,
`SharingLocationsAlert`, `IMapsProvider` and the provider hook in
`ApplicationLoader`, plus `isMapsInstalled`, which had been answering false to
keep them shut. Picking a location is gone with them: the attach menu's
Location layout, a story's location sticker, an article's map block and the
`/map` command, a business address's "set it on a map", a group's location,
and the live-location bar and its deep links, none of which could work anyway
without a location permission. The weather sticker went too - it fetches your
coordinates to look up the forecast, so without the permission its button
spun and did nothing. What still renders: a weather or location sticker
someone else placed, a map block in an article you are reading, and the
server-rendered map image of a business address.

**Gifts: received, never traded** (2026-09-22). **Receiving must keep
working**, which is why `ui/Stars` and `ui/Gifts` come apart file by file
rather than as a block, and why the pattern for a mixed file is to extract
the display half first and then delete the rest. `GiftSheet` was the model
case: two thirds of it was display - the gift card, ribbon, cell and tabs
that `ChatActionCell` draws a received gift with, `ItemOptions` scrims, and
`PeerColorActivity` picks profile collectibles from - so those moved out
unchanged as **`GiftViews`** (2,080 lines, 73 references repointed) and the
sheet itself went.

Deleted with it: every way to send a gift (a channel's Gift button, "Send
gift to X" on a message, the profile's Gift action and menu item, the two
birthday prompts, the compose bar's gift button - all of which sat behind
`premiumPurchaseBlocked()` and had been unreachable since the premium economy
was neutralised); the gift auctions, five sheets and a hint panel and
`SendGiftSheet` with them; listing a gift for sale, changing its price and
buying a listed one; the bot and channel earnings screen; and crafting,
which reads like tidying what you own but chooses the other gifts from
`ResaleGiftsList(...).forCraft()` - gifts **for sale, sorted by price** - so
it is buying, and took `GiftAuctionController` and `StarGiftPreviewSheet`
with it.

Still standing: `PeerColorActivity`'s buy-a-collectible tab, the last holder
of `ResaleGiftsFragment` and `ResaleBuyTransferAlert`, and gift transfers.
`StarGiftSheet` itself stays - it is how a gift someone was given is shown.

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

### The javac notes are not ours

Every build ends with javac's two summary notes - "uses or overrides a
deprecated API" and "uses unchecked or unsafe operations" - and they are the
last warnings in the log. They are not this fork's, and chasing them is a
trap worth refusing once, in writing.

A one-off `-Xlint:deprecation,unchecked` run (`dc7a5695`, reverted in
`e2cfe9dc`) counted **7,945** warnings behind them. 7,609 are in
`org/telegram/**`: the client's own source, which *is* ours to edit - there is
no other project to fork - but which we did not write. Of those, 4,393 are
calls to Android and JDK APIs that Google deprecated, and 3,053 are calls to
helpers Telegram deprecated itself and never finished migrating. The rest are
in code vendored into the tree: ExoPlayer, AndroidX RecyclerView, WebRTC, and
the `jlatexmath` module the second note names by file. **The fork's own three
files produce none**, and the seven that sat on lines this fork touched were
fixed in `f812b149`.

So the answer to "can we fix the warnings" is: ours are all fixed. Rewriting
7,609 upstream call sites would change no behaviour, would bury the
`LoogriGram:` comments that are this fork's only audit, and the Android half
(theming in `getDrawable`, layout defaults in `StaticLayout.Builder`) carries
visual risk no compile can catch.

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

- **The buy-a-collectible tab in `PeerColorActivity`** - next, and the
  smallest of what is left. Choosing a profile colour lists collectible gifts
  per model in tabs; alongside the ones you own it lists ones **for sale**,
  and buying one applies it. 50 references to `resaleGifts`,
  `selectedResaleGift` and `selectedTabGift`, woven through that screen's tab
  paging, plus its `buy()` and the `ResaleBuyTransferAlert` it opens. It is
  the last thing holding `ResaleGiftsFragment` (2,293 lines) and that alert,
  so both go with it. Risky only in that the screen has tabs and a pager and
  cannot be checked by compiling.
- **The Stars wallet.** `StarsIntroActivity` (5,344), `TONIntroActivity`
  (855), `StarsController` (3,600) and `BotStarsController`, plus
  `ExplainStarsSheet`, `BalanceCloud` and `StakedDiceSheet`. Two things fall
  out when the controller goes: the paid-reaction bookkeeping inside it
  (`sendPaidReaction`, `commitPaidReaction`, `undoPaidReaction`,
  `PendingPaidReactions`), which nothing has called since paid reactions were
  removed, and `ReactionsLayoutInBubble`'s drawing of a paid reaction that
  *arrives* on a message - desktop dropped that display too.
- **Transfers.** `onTransferClick` in the gift sheet moves a collectible to
  someone else, with a TON export option beside it. Not a purchase, but it is
  the same market, and the TON half is crypto.
- **`MessageSuggestionOfferSheet`** and the priced suggested posts around it.
- **Premium economy.** The three forced getters leave every branch behind them
  in place. Settings and the own-profile menu lost their rows on 2026-09-21,
  and this session turned the gift entry points behind
  `premiumPurchaseBlocked()` into real deletions, but
  `PremiumPreviewFragment` (with its "no ads" row), `GiftPremiumBottomSheet`,
  `LimitReachedBottomSheet`'s boost-level lists and the tier cells are still
  dead weight.
- **Chromecast.** Four files reduced to Google-free stubs so ~98 call sites in
  `MediaController`, `PhotoViewer` and `AudioPlayerAlert` keep compiling.
  Deleting them means editing those three files (4k, 24k and 6k lines).
- **`if (true)` guards.** Both gift guards the earlier notes listed are now
  deletions: `GiftSheet.show` and `SendGiftSheet.show` went with the sheets
  themselves. `isMapsInstalled` went with the map screens. A plain
  `grep -rn "if (true)"` still finds `MessagesController`
  (`addPhotoAtStart`), `AndroidUtilities`, `ChatActivity` and
  `DialogsSearchAdapter` - their origin was never checked; some may be
  upstream's own.
- **The javac notes are not ours.** A one-off `-Xlint` run measured 7,945
  warnings behind javac's two summary notes. 7,609 are in
  `org/telegram/**` - the client's own source, which is ours to edit but not
  ours to blame: 4,393 are calls to Android and JDK APIs Google deprecated
  (`Resources.getDrawable` 590, `View.setBackgroundDrawable` 323, the
  `StaticLayout` constructor 260, `Canvas.saveLayerAlpha` 223, `Camera`,
  `AsyncTask`), and 3,053 are calls to helpers **Telegram itself** deprecated
  and never finished migrating (`LocaleController.getString(String,int)` 840,
  `formatString` 730, `AndroidUtilities.statusBarHeight` 556, `UndoView` 295).
  About 336 more are in vendored ExoPlayer, AndroidX and WebRTC, and the
  `jlatexmath` note comes from a vendored library module. **The fork's own
  three files produce zero**, and the seven on lines this fork touched were
  fixed in `f812b149`. Leave the rest: rewriting 7,609 upstream call sites
  changes no behaviour, buries the `LoogriGram:` diff that is this fork's only
  audit, and the Android half carries real visual risk. The only safe subset,
  if the number ever matters, is the two `LocaleController` helpers - 1,570 of
  them, a pure rename with both sides in our tree.
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
- **Done on 2026-09-22, for the record** (22 commits, `77673c28..af5d70a5`,
  26,440 lines net): both AAPT warning sets; paid live comments, in both
  directions, and the send button's price pill; paid media; gift offers; the
  whole Location subsystem, 8,408 lines, and the weather sticker with it -
  it asks the phone where you are to look the forecast up, and could not
  work without the permission this build never requests; gift sending and
  `GiftSheet`, whose display half was extracted first as `GiftViews`; gift
  auctions, taking `SendGiftSheet`; selling, pricing and buying a listed
  gift; the bot and channel earnings screen; paid reactions; and crafting,
  which took `GiftAuctionController` and `StarGiftPreviewSheet` with it.

  Two repairs on the way, both from a crash the night before: nine source
  files had been truncated to zero bytes (`ChatActivity.java` among them),
  restored from `HEAD`, and four loose git objects were empty, refetched
  from GitHub's API and checked against their SHAs. `git fsck` is clean
  apart from one unreachable commit left by the original shallow fetch of
  upstream.

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
