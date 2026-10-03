# LoogriGram Android — fork notes and handoff

Personal fork of Telegram for Android. Same goals as the desktop side: no ads,
no non-essential telemetry, no auto-updates, ghost mode on by default — plus
fully Google-free, because the phone runs GrapheneOS with no Play Services at
all. Started 2026-09-10.

Upstream's own conventions still apply to code style. This file covers what is
specific to the fork. The desktop fork's `LOOGRIGRAM.md` is worth reading too:
several traps are shared, and it records the read-receipt finding that this side
depends on.

**Rule: desktop and Android stay in parity, both ways** (the user, 2026-10-03).
Whatever is removed, added or modified in one client is done in the other too —
in the same stretch of work, or recorded in that client's notes as owed. Don't
ask per change; ask only when a change looks wrong (either client can be:
desktop's code once removed *free* suggested posts against its own money rule)
or has no counterpart (Play Services, launcher mechanics, Windows packaging).
Check against the other client's **commits**, not only its notes - a
commit-by-commit audit of desktop (2026-10-01) found 13 gaps its notes never
summarised. The reverse audit, Android's commits against desktop, followed on
2026-10-04 and found four gaps (see "Parity with desktop" below).

---

## Status

| Part | State |
|---|---|
| Fork, CI, degoogling | Done. No Google bytecode in the APK, verified in the dex. The last Google-shaped code went on 2026-09-23/24: the Play install referrer and the four Chromecast stubs |
| Installed on the phone | **Yes.** `gf20af361`, installed 2026-09-22 over `adb` (`adb install -r` succeeded, so the key matched); launches clean. Not rechecked since: the phone was not on USB on 2026-09-24. `gaf5d70a5` built green on 2026-09-22 but was never installed |
| Latest release | `g55475fe4` (full build run 37110897841, green, 2026-10-03): everything through the desktop-parity pass, the icon, iTunes and Help. Not installed. Before it: `g83c5583d` (36907722985, 2026-10-01), `g20294896`, `gb81ce49d`, `gf2478ebb` (which superseded the crashing `g1ec92ae0`, trap 0e) - a buggy Latest is superseded by the next build, never deleted, the user's rule |
| Pending build | None on Android: `55475fe4` is Latest and only `56d3930b` (notes) follows it. **Desktop** has one in flight - see "Start here" |
| Premium pass | **Done** (2026-10-01). The Premium screens are deleted (`51d67433`), `UserConfig.isPremium()` is gone and every caller takes the non-Premium path, others' Premium changes nothing drawn (`b90da4f6`), limits are the free ones. The user's two exceptions stand: the Premium-users-only notice's wording, dimmed folders past the cap |
| App name and icon | Done - launcher, in-app strings (56 more in `5bd6a07d`), the two wordmark screens, and the launcher icon: desktop's mark, one icon only (`037d897a`, built by `branding/make_android_icons.py`) |
| Phone contacts | **Never touched.** Permissions, account and sync adapter all gone |
| Updater | Ours, from this repo's releases. Checks on every cold start, then hourly; manual row in Settings (2026-09-21). Since `ec7c9d55` (2026-09-24) a download waiting to be installed no longer blocks the check: a newer release replaces it and one no longer Latest is dropped - the installed `gf20af361` does not have that yet. **Nobody has seen the automatic check find a release** |
| Ads | **Gone**, all three surfaces, down to `MessageObject`'s fields (2026-09-21) |
| Money messages | Held in history, never drawn — desktop's hidden-content rule. The chat list no longer rises for one. **Their drawing code is deleted too** (2026-09-24/25): ChatActionCell's gift cards, the invoice card, paid media and `GroupMedia`, the eight message types nothing can have, the extended-media checks |
| Stars wallet | **Gone** (2026-09-25): StarsIntroActivity, TONIntroActivity, PaymentFormActivity, BotStarsController and the wallet half of StarsController. Its gift-list half went with the Gifts tab (2026-10-03, `92281bd2`); `messenger.MessageId` is its own class |
| Paid messages | **Done** (2026-09-21/22): users who charge are locked, nothing ever pays, nothing charges. The price-setting half went with the privacy option, the group permission and a live's price per comment |
| Paid media, live comments | **Gone** (2026-09-22): no price on a photo or album, no paid or highlighted live comment, no Star donations to a live |
| Paid reactions | **Gone** (2026-09-22/23). The star is not offered, its sheet and flying-star overlay are deleted, the bookkeeping that tracked one of ours in flight is gone, and one that *arrives* is no longer drawn — no button is built for it, and the particle halo that was the whole of the reaction row's overlay pass went with it |
| Gifts | Sending, auctions, selling, buying, crafting, the buy-a-collectible tab, transfers and the TON export all **deleted** (2026-09-22/23), and wearing a collectible, which needs Premium (2026-09-24). Nothing is paid for a gift and nothing is paid *by* one: no converting one back into Stars, no paying to erase its provenance, and an upgrade only when the sender already paid for it. Receiving went too, for desktop parity (2026-10-03): no profile Gifts tab, no pinned-gift ring, no gift sheet or data layer (`22836784`, `92281bd2`); gift messages are held |
| Payments and earnings | **Gone** (2026-09-23). No payment form opens anywhere - bots, merchants, mini apps, invoice links, receipts - and nothing earns: affiliate programs, a referrer's commission, channel earnings and the charging half of Stars subscriptions are deleted. A number whose login code costs money gets an alert instead of a price |
| Boosts | **Gone** (2026-09-24), honoured for nobody, ours included: no level locks, no booster badge, no Boost items, tab, screens or links, no giveaways or gift codes, and a group's restrictions apply as written to members who boosted it. Free transcription in a boosted group stays, as on desktop |
| Suggested posts | **Free only** (2026-09-23; a priced one received is held, `ec4885b2`; desktop restored the same free case on 2026-10-03). Suggesting a post to a channel stays — it is a publishing time and nothing else, upstream's own "Offer for free" case. The price, the Stars/TON tabs, the balance, the accept dialog's payment and commission paragraphs and the "Edit Price" menu row are gone |
| Staked dice | **Gone** (2026-09-23). A 🎲 could be rolled with TON staked on it; `StakedDiceSheet` and the won/lost banner are deleted. Plain dice and the slot machine are untouched |
| Paid search | **Gone** (2026-09-23). Global post search stays free-with-a-daily-quota; the "Search for N Stars" button past the limit is a countdown now |
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

### Start here next session (written 2026-10-03, handoff)

1. **Ask how the desktop full build `dd0ea5658d` went** - run 37158363951,
   https://github.com/romanimpair-jpg/LoogriGram-Desktop/actions/runs/37158363951.
   The earlier one (`f7bbe51779`, run 37138072076) failed before compiling
   anything of ours: every dependency cache had been evicted after the idle
   fortnight, and the rebuild died at MSYS2's "target not found:
   mingw-w64-x86_64-diffutils". The fix is desktop's frozen dependencies
   (see its `LOOGRIGRAM.md`, Building, "Frozen dependencies") - **not**
   upstream's ucrt64 switch, which was tried (`6b3c48e7d1`) and reverted
   (`d4958b8bab`) on the user's rule: "stop following upstream until break".
   This run is also the first compile of the free-suggested-posts restore
   (`12f173a996`, `f7bbe51779`); its risk list: `data_msg_id.h`
   `SuggestOptions`, `api_common.cpp` `SuggestToMTP`, `apiwrap.cpp` ~3737,
   `history_widget.cpp` ~2424/~3242, `history_view_compose_controls.cpp`
   ~1914, `history_view_chat_section.cpp`, `history_item_reply_markup.h`
   ~100. If it fails, fix from `--log-failed`. **If green, ask the user
   whether to dispatch one desktop `cache` run** (no app release; it stores
   the built ThirdParty and Libraries trees as the `deps-trees`
   pre-release, so an evicted cache can never force a rebuild again).
2. **Then freeze every other dependency, in both projects** - the user's
   decision, 2026-10-03, for the start of the next session. Pin each to
   exactly what the last good build used (read it from that run's log),
   never to upstream's newer choice:
   - Android (`.github/workflows/android.yml`): `runs-on: ubuntu-latest`
     -> `ubuntu-24.04` (the last good build ran image 20260927.320.1);
     `java-version: '17'` -> `'17.0.20+101'` (Temurin, as `g55475fe4`
     used); `actions/checkout@v5`, `actions/setup-java@v5`,
     `gradle/actions/setup-gradle@v5`, `actions/upload-artifact@v6` ->
     exact commit SHAs. `platforms;android-36` installs its latest revision
     and sdkmanager cannot pin one - record it, low risk. Already pinned:
     NDK 27.2.12479018, CMake 3.22.1, build-tools 36.0.0, Gradle 8.11.1, no
     dynamic dependency versions, native sources at submodule commits.
     Validate with a compile run.
   - Desktop (`win.yml`, `prepare.py`): `Eden-CI/msvc-dev-cmd@master` (a
     branch!) and every `actions/*@vN` -> commit SHAs; `runs-on:
     windows-latest` -> the image version the last good build used (the
     VS toolset is already pinned to 14.44); `prepare.py`'s python stage
     `pip install pywin32 six meson` -> exact versions; NuGet's
     `.../latest/nuget.exe` -> a fixed version (or mirror it, as the MSYS2
     packages are); then audit every other `stage(...)` for a download that
     is not a fixed tag, commit or hash. Desktop has no compile-only mode -
     each change costs a build, so batch them and ask.
3. **Then the reverse parity audit** (the user's, 2026-10-03). Parity
   is two-way now (the rule at the top of this file). Desktop's 233 commits
   were audited against Android on 2026-10-01 - 13 gaps, all closed by
   `0d90a619..090da946` - but Android's own commits (`9f8c35d1..patches`)
   have never been checked against desktop. Do it the same way: classify
   every Android commit as Android-only (Play Services, launcher, Android UI
   with no desktop counterpart), notes/tooling, done on desktop (with
   evidence: a desktop commit or `LoogriGram:` comment, or the feature
   absent there), differs by decision, MISSING ON DESKTOP, or unsure, in a
   resumable tally file. Candidates to check first: the iTunes cover lookup
   (`8fed48d0` - does desktop look covers up anywhere?), the rest of the
   Help section and the Privacy Policy row (`46fc6ede`), held priced
   suggested posts and paid-message notices (`ec4885b2`, `5a23f8e8`), and
   anything the Premium convergence (`2531579c..ada551b5`) removed that
   desktop still draws. Then mirror the gaps on desktop - each desktop change
   costs a full build, so ask before dispatching - and watch for a client
   being wrong (desktop once was): raise it, don't copy it.
4. **Install `g55475fe4`** (Latest, green) when the phone is on USB, and walk
   the lists below, newest first. `adb` is at
   `C:\Users\Loogris\platform-tools\adb.exe`, the package
   `com.loogrimedia.loogrigram`. The notes know of nothing since `gf20af361`
   on the phone.

Each pass since 2026-09-26 went to a subagent with a full brief (rules,
every checker, the other client's decisions quoted) and was reviewed here
after: every checker over the pass's whole range (`git reset --soft
<base>`, run, `reset --soft` back), then the riskiest joins, and for
dropped positional parameters `argcheck_all.py` (the dropped values must be
null or the removed variable).

**Look first where a mistake would be silent** - a compile draws nothing.
Every list below is in `g55475fe4` and none has been seen on the phone.
Gift items in the older lists are obsolete: the gift sheet, the Gifts tab
and the gift data went on 2026-10-03.

   From 2026-10-01..03:
   - the launcher icon in round and squircle masks, the themed icon on
     Android 13+, our picture in notifications and on the call screen;
     Appearance has no App Icon section; an install that had Vintage or Aqua
     still has an icon after updating (`LauncherIconController`);
   - music without its own cover shows the plain music icon (no iTunes
     lookup); covers carried by the file still show;
   - Settings has no Help section, the update check follows Language,
     `tg://settings/privacy-policy` opens Settings;
   - a private chat's read-time line says "read", no "show when" pill; a
     profile with hidden last seen has no "when?" pill;
   - chat theme sheet: emoji themes only; a chat with a collectible theme
     shows the default; wallpapers carry no gift symbol;
   - replies and quotes from someone with a collectible colour use the plain
     colour; profile headers are plain, buttons legible in light and dark,
     the music bar included;
   - `t.me/nft/...`: the "unsupported" alert and a plain preview; a
     collectible username copies on tap; username editors show "taken" for a
     name on sale; a +888 number's menu has no Fragment note;
   - admin log "changed emoji pack": a tap does nothing; the group sticker
     chooser works;
   - permission prompts and the passcode screens say LoogriGram;
   - profiles: no Gifts tab, no ring; `my-profile/gifts` opens the profile;
   - free suggested posts get Accept/Decline; a priced one is not shown;
     chat-list badges and order are unaffected by the held messages.

   From 2026-09-29..10-01 (article editor, Premium, leftovers):
   - no Article button in the attach menu, no expand button on long
     messages; a received article copies and pastes with its formatting, its
     checkboxes do not tick, it offers no Edit; a cloud draft holding an
     article shows as text; html paste works; an inline bot's article sends;
   - GIFs auto-download with photos (within the photo size limit), videos
     and files do not by default;
   - limit sheets: centred badge, plain sentence, OK; a file over 2 GB says
     so; folders past the cap dimmed and locked; no folder tags on rows;
   - emoji panel: the category row only on the stickers/GIF tabs, no
     Premium packs; the double-tap reaction setting is the plain list; the
     message-effect picker has no padlocks; Trending Stickers, never
     "Premium";
   - a received checklist draws with its ticks, tapping a task does nothing;
     no checklist button in the attach menu;
   - adding people to a group call works; the storage chart's star burst
     after clearing the cache;
   - global search shows the Posts tab; the scheduler has no repeat row; at
     most three accounts can be added (a fourth already logged in stays);
   - a gift or payment arriving shows no text in the chat list; a paid group
     message has no star before its time.

   From 2026-09-28/29:
   - the chat list: no story strip or rings, avatar taps work; the
     archive folder appears and goes correctly; unread counts and read
     positions sync with another device;
   - profiles: no story, archive, album or bot-preview tabs; shared
     media, calendar and gifts still work;
   - sending in chats and topics, with reply and quote: text, sticker,
     GIF, inline result, photo, video, file, poll, voice (the story
     parameter left ~22 send methods); a resumed voice draft;
   - Storage clears old story files under Miscellaneous; "Clear local
     database" completes (it now also empties the story tables);
   - blocking and unblocking; the send-as picker in a channel;
   - a message of only emoji, or one custom emoji, is ordinary text in
     a bubble; dice and premium sticker effects still play;
   - typing a word or `:word` shows no emoji strip, a lone emoji offers
     no stickers; @mentions, #hashtags, /commands, @bot inline work;
     emoji and sticker search work;
   - an empty chat shows "No messages here yet"; a business intro still
     shows and its sticker sends;
   - Settings > Help: only Privacy Policy and the update check; no
     phone-number banner; the password reminder can still appear; the
     log-out screen has no Contact Support;
   - channel posts show views and comment buttons (counts now update
     only from the server); Reset in Data settings leaves only Photos
     on; Pinned Messages notifications keep their state.

   From 2026-09-27, Stories stages 1-2:
   - the chat photo editor: stickers, and the sticker sheet's Photo
     widget; choosing a video cover (GallerySheet, TimelineView); the
     round-video camera and the in-chat camera's flash;
   - share sheets (chat, photo viewer, a gift link) and sharing a photo
     from another app: no "My Story" row;
   - the chat list: no story button above the pencil, the new-chat hint's
     arrow is back;
   - our own profile's stories tab: its button shows only while stories
     are selected; archiving and "Add to album" still work;
   - a mini app calling shareToStory: the "not supported" popup;
     tg://post links are refused as unopenable;
   - a forwarded story and a story mention: no bubble, no chat-list
     preview, no unread badge that will not clear;
   - a reply to a story: its text, no quote;
   - `t.me/x/s/1`, `/a/1`, `?story=`, `?album=` open the peer; a story
     link previews as a plain page;
   - channel statistics: the overview in 3 rows, no story charts.

   From 2026-09-26/27, Business:
   - Devices: the session list, and Terminate all shows the plain alert;
   - Edit profile: no Chat Automation, hours or location rows;
   - a chat with a person: no bot bar, "/" offers nothing; a bot chat:
     "/" lists commands and sends them, the menu button works;
   - the attach menu: no Quick Replies tab, the rest work, photo
     multi-select is not capped;
   - the folder include/exclude picker: type rows show avatars and
     toggle, search finds users, bots and groups; the auto-delete
     "existing chats" picker;
   - a group's Welcome messages: empty state, hint row, sending;
   - a business user's profile: hours expand, my-time toggle, location;
     their chat intro in an empty chat; their t.me/m/ link opens the chat
     with the preset text;
   - the giveaway, Stars and suggested-post placeholder avatars (the
     avatar drawable slots were renumbered);
   - sending, forwarding, editing, deleting and retrying messages,
     albums included.

   From the second 2026-09-26 session, built in `g20294896`, unseen:
   - **the chat list**: rows lost the space an emoji status or bot icon
     reserved - names, the mute icon and the verified check must sit right
     (`873fe3ab` rewrote DialogCell's layout math); the title reads
     "LoogriGram" with nothing beside it;
   - **a group's messages**: author names, admin badges and topic chips,
     with no status after the name;
   - **profiles**: the name row (verified or scam only); the header colour
     of someone wearing a collectible (their own profile colour now); the
     pinned-gift ring and the story ring; a bot's profile has no
     emoji-status permission row; a private chat's menu offers only
     "Enable Sharing", and only when sharing is off;
   - **Privacy and Security**: no Voice Messages, Messages or Gifts rows;
     the Invites and Calls exception pickers have no "User types" section,
     and each row's summary still reads sensibly;
     tg://settings/privacy/voice, /messages and /gifts open Privacy and
     Security;
   - **Appearance**: no name-colour row, and three launcher icons
     (Default, Vintage, Aqua);
   - **a folder's edit screen**: the tag colour picker (PeerColorGrid, cut
     down to folders in `b2682a10`);
   - **a channel's admin log**: "changed colour" entries keep their dots
     (PeerColorSpan moved out of the deleted screen);
   - **pinning a gift past the limit**: the unpin sheet's gift cells
     (`ProfileGiftsContainer.UnpinGiftCell`);
   - **a long press on a custom emoji**: no "Set as Status";
   - **the report bar** in a chat with a stranger: no emoji-status or
     bot-verification hint under it;
   - **an empty chat with a Business greeting**: the line has no "how?"
     and takes no tap;
   - Settings -> Reactions lists no Premium reactions; a channel's Similar
     tab has no "More similar" block and a counter matching the list; the
     translation sheet has no "Translate Entire Chat".

   From the first 2026-09-26 session, built in `gb81ce49d` and also unseen:
   - **Saved Messages**: no tags anywhere, no reactions (double-tap, the
     menu's row); search there works as plain search; forwarding to it
     shows the ordinary "Forwarded to Saved Messages" bulletin;
   - **the emoji panel and pack sheets**: no padlocked packs, a mixed
     pack shows its free half, Premium packs add without "Unlock"; paste
     text holding a custom emoji into a chat and send it - it must go out,
     the Premium ones as plain emoji;
   - **global search**: no Posts tab; the chat list's downloads shortcut
     must still open the Downloads tab (`80072e50` replaced a fixed index
     with a lookup by type);
   - **Settings**: Folders won't drag All Chats and shows no tags switch;
     Privacy has no voice-messages or "who can message me" rows;
     Language has no "Translate Entire Chats"; Archive's non-contacts
     switch only shows where the server allows it;
   - **compose**: no AI button anywhere (chat field, attach captions,
     photo caption); hints still hide on a touch (`77f0cb10`); the attach
     menu has no Checklist button; a long press on Send offers no
     padlocked effects;
   - **a voice message past the free trials**: the bulletin reads "...
     Wait until <date> to use it again." (`LoogriGramTranscribeTrialsOver`);
   - **a group that lets members send as a channel**: the popup lists only
     free identities and still scrolls to the current one;
   - **a channel's similar-channels strip**: no "Unlock Similar" tile.

   From 2026-09-24/25, also still unseen:
   - **the chat list**: tapping a chat must open *that* chat. `61055a34`
     changed `DialogsAdapter`'s position offsets when it took out the
     "Recently viewed" section, and an off-by-one there shows nothing else;
   - **video and music**: quality, speed, mute, and the music player's
     options menu ("remove from profile" must still do that) - `bb8137fb`
     edited `MediaController` and `PhotoViewer`;
   - **a collectible gift**: its sheet opens, the action row is Share only,
     and back from the upgrade page works;
   - **Settings → Add account** walks the login pages without touching the
     main session (`LoginActivity`'s page array went from 19 to 18);
   - **a group that restricts us**: the bottom bar names the restriction;
     slow mode, the attach menu, the emoji panel and the voice button's hint
     behave; its stories offer no reply field;
   - **a channel we admin**: no Appearance or Auto-translate rows, the
     standard reactions editor, invite links without a subscription switch,
     and Statistics as one page, shown only where `can_view_stats`;
   - **the limit sheets**: a pinned chat past the limit, folders past the
     cap;
   - **colours and wallpapers**: our own name-colour row in Appearance,
     `PeerColorActivity` without its tab row and with the "Use a gift"
     header, setting a wallpaper, a private chat's theme, and the story
     recorder's theme picker (`ThemeChooser`, moved out of the deleted
     channel screen);
   - **the gift upgrade page**, from the previous list and still unseen: it
     should appear *only* when the sender prepaid the upgrade, reading
     "Upgrade for Free" and then Confirm;
   - **service messages** (ChatActionCell was cut by 1,335 lines): a
     collectible's chat theme ("set a gift theme", View), a sharing offer
     with its two buttons, a community change, a suggested profile photo,
     a wallpaper message and a story mention must all still draw;
   - **a received collectible's sheet**: no "Value" row, Share has no story
     cell, the sticker and "Availability" row still draw (helpers moved to
     GiftViews);
   - **link previews**: boost, gift-code and auction links preview as plain
     pages; a collectible's (`telegram_nft`) preview and button remain;
   - **limit sheets** (LimitPreviewView lost its dark-gradient paths): the
     bars and counters look as before.
**When a decision is needed, first check what the other client decided**
(its `LOOGRIGRAM.md`, its commits and its `LoogriGram:` comments) and copy
it - the user's rule, 2026-09-25, two-way since 2026-10-03.

Still unverified from earlier sessions, since a compile cannot see layout:
   - chat list: a gift or payment arriving must not move the chat to the top
     or blank its preview; the unread badge must still count it and clear;
   - opening a channel: scrolling to the newest message, the jump-to-bottom
     button, and new messages arriving while open;
   - message bubbles: link previews with photos, the side share/go-to button's
     position, name tap highlight, time placement;
   - global search results list, and "show more" there;
   - reporting a message or chat;
   - Settings and your own profile no longer list Premium, Stars, TON,
     Business or Send a Gift; a bot you own has no balance or affiliate rows;
   - the paid-message lock (needs a user who charges per message): their chat
     shows "X only accepts paid messages, which LoogriGram doesn't send."
     instead of a compose field, and their row is padlocked in the share and
     forward pickers;
   - the updater's automatic check, which has still never found a release.

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
- `loogrigram-tools/` holds this side's checkers, as the desktop fork does.
  `check_swallowed.py` lists declarations a commit range removed that nothing
  declares any more but something still calls — the mistake trap 0d describes.
  Run it before every compile; it takes seconds. Given a bare revision
  (`HEAD`) it checks the uncommitted working tree. It sees methods, not
  fields. `check_dangling_imports.py` lists `org.telegram` imports that name
  nothing: run it after deleting a class, because an import of it that was
  already unused still breaks the compile, and no diff-based check looks at
  an import the diff did not touch.

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

**Every dependency is frozen** at what the last good build used, and changes
only on purpose - never by following upstream or "latest" (the user's rule,
as on desktop; `2c60b2fb`, 2026-10-04, read from `g55475fe4`'s run
37110897841): `runs-on: ubuntu-24.04`, Temurin `17.0.20+101`, every action
by commit SHA, NDK 27.2.12479018, CMake 3.22.1, build-tools 36.0.0, Gradle
8.11.1, no dynamic dependency versions, native sources at submodule commits.
Two things cannot be pinned: a hosted image's build (only its label), and
the SDK platform's revision, which sdkmanager always installs at its latest -
`android-36` was revision 2, and the install step warns when that changes.

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

0d. **A brace-matched range deletion swallows its neighbours, and
   `gh run watch --exit-status` will not tell you.** Cutting a feature usually
   means deleting one contiguous range. Checking that the range's braces
   balance is not enough: an unrelated method sitting inside it balances too,
   so it goes with the cut. On 2026-09-23 the gift-transfer deletion (618
   lines) took `presentFragment` and both `getInputStarGift` forms with it,
   leaving thirteen call sites pointing at nothing.

   That would have been a four-minute fix, except that the next four compiles
   were read off `gh run watch --exit-status`, **which exits 0 for a failed
   run**. Four more commits were built on a tree that did not compile. Read the
   run's own verdict, always:

   ```
   gh run view <id> --repo romanimpair-jpg/LoogriGram-Android      --json conclusion --jq .conclusion
   gh run view <id> --repo ... --log-failed | grep "error:"
   ```

   `loogrigram-tools/check_swallowed.py` now catches the first half locally, in
   seconds: it lists every declaration a commit range removed that has no
   declaration left anywhere but still has callers. Pointed at the bad range it
   names `getInputStarGift` and its thirteen call sites. Run it before every
   compile.

0e. **Shrinking an array is invisible to the compiler.** `ed8f1b7e` took
   Transfer out of the gift sheet's button row and made the row two slots,
   but `TopView.setGift` kept its three indices and wrote Share into
   `buttons[2]`. Every collectible gift's sheet then threw as it opened -
   in `g1ec92ae0`, a green full build and now the Latest release. When a
   removal shrinks an array, find every index into it. `ad610a75` fixed this
   one and checked every other array a fork commit had resized.

0f. **A power cut can NUL-fill files without changing their size or mtime,
   and `git status` will not notice** - it trusts both. It happened twice:
   on 2026-09-22 nine source files were zeroed and four loose objects
   emptied; on 2026-09-24 the index was corrupt, three objects were
   truncated and four source files and a scratch helper were NUL-filled,
   two of them since an earlier cut. After any interruption, before
   trusting the tree, run `git update-index --really-refresh` (it re-hashes
   every file) and `git fsck`. To repair: fetch each bad blob with
   `gh api repos/romanimpair-jpg/LoogriGram-Android/git/blobs/<sha>`, check
   its hash, store it with `git hash-object -w --no-filters`, rebuild the
   index, and `git checkout --` the damaged files. Nothing of that covers
   files outside git, so scan scratch files for NUL bytes too.

0g. **A class's static helpers are used through `import static` lines,
   which `closure.py` skips.** Deleting StarsIntroActivity looked safe by
   closure until `check_dangling_imports.py` and `check_swallowed.py`
   found StarGiftSheet, TableView and GiftViews calling three of its
   helpers through static imports. After deleting any class run both, and
   move the helpers out first. Then look one level deeper: classes only
   the deleted files used (six after the wallet), resources only they
   used (41 files), and interfaces whose only implementer they were
   (LimitPreviewView's DarkGradientProvider).

0h. **Tooling slips seen 2026-09-24/25.** `ed.cut_method` looped forever
   when its replacement kept the method's signature - fixed, it now
   resumes after the replacement (`test_cutm_keep.py`). A compile run sat
   in "Clone" for 18 minutes; cancel and redispatch after five. The
   PowerShell tool refuses `git rm "$dir/$file"` in a loop as "removal on
   /" - run `git rm` from a Python script. And measure the numbers in a
   commit message (`--numstat -w`, file counts) before writing them.

0i. **What the checkers still miss, seen 2026-09-25/26.** Two compiles
   failed, each on one line. Deleting a field left its `@Keep` stacked on
   the next field's own - "Keep is not a repeatable annotation type"
   (`e16abbc3`); `android-edit-tools/check_annotations.py` catches that
   now. And a removed field was still read once, in a touch handler far
   from the rest (`77f0cb10`) - trap 12 again: check_swallowed sees
   methods only, so grep every removed field. Three smaller lessons: a
   substring anchor matches its deeper-indented twin too (`"\n" + indent`
   makes it unique); a fixed index into a list you just shortened breaks
   silently (`showDownloads`, `80072e50`); and `ed.unwrap`'s clash check
   is file-wide, so read the enclosing method before passing
   `allow_names`. `dropimports.py` (local, beside `ed.py`) removes what
   `new_unused_imports.py` reports.

   Large removals went well delegated to a subagent (Saved Messages tags,
   3,800 lines; custom emoji, 1,350) with a brief carrying these rules,
   the checkers and the desktop decision to copy - then reviewed here:
   re-run the checkers, read the risky joins, compile.

0j. **Run every checker, and what they still miss, seen 2026-09-26.**
   Three compiles failed. Two were stacked annotations again (`@Keep`,
   `@Nullable`, from `ee75d83b` and `873fe3ab`), which
   `check_annotations.py` catches - it was not run; run all of
   `dropimports.py`, `orphan_private.py`, `check_annotations.py` and
   `freed.py` before each commit. The rest the checkers cannot see:
   - a nested class moved out of a class being deleted still read the
     outer class's static constants (`PAGE_NAME`, `e53ce275`, fixed in
     `b2682a10`) - grep a moved body for every static member of the old
     outer class, not only for the helper names;
   - a cut took a local declaration (`int x` in ProfileSearchCell) that
     code after the cut still assigned;
   - a call-site rewrite keyed on an argument's text (`emoji_status`)
     missed the one call that passed a literal `null` - key on the
     method name and argument count;
   - removing a member another class overrode (`didPressUserStatus`) or
     read (`currentNameStatusDrawable` in PremiumPreviewBottomSheet)
     breaks that class - grep the whole tree for every removed public
     name, including from dead screens.

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
      collided with a same-named local further down the method (again on
      2026-09-24, `12d1c2c3`; the scratch `unwrap` helper now refuses a
      clash);
    - deleting a class breaks imports of it that were already unused, which
      no diff-based check sees (`a1bf6591`) - `check_dangling_imports.py`;
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
    Twice more on 2026-09-24, both fields: `SlowModeBtn.isPremiumMode` and
    `StarGiftSheet.onlyWearInfo`. `check_swallowed.py` sees methods only, so
    a field is still a grep.

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
    the path - and don't rewrite that file with `Set-Content -Encoding utf8`,
    which adds a BOM that git keeps as the subject's first character
    (`48e6a10d`). Also: `Select-String` with a backtracking regex over the whole
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
still *mentions* ads is not about showing them to you: Premium's "no ads" row
and the ad-revenue explainer it opens (`RevenueSharingAdsInfoBottomSheet`),
both going with the Premium removal. The channel owner's "switch off ads for
subscribers" toggle went with channel earnings on 2026-09-23.

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

The buy-a-collectible tab, `ResaleGiftsFragment`, `ResaleBuyTransferAlert`
and gift transfers followed on 2026-09-23, and wearing a collectible on
2026-09-24 - it sets the gift as your emoji status, which needs Premium.
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

Nothing where you write reads a price any more: `SendButton`'s price pill
and `ChatActivityEnterView.getStarsPrice` went with paid live comments, and
`SendGiftSheet` and `GiftOfferSheet` with gift sending (2026-09-22). What is
left is display: `ChatMessageCell.getStarsPrice` still draws the Stars
someone else paid to send a message in a group (see "Remaining work").

**Payments: nothing opens a form, nothing earns** (2026-09-23, as desktop).
Every way into paying a bot or a merchant is gone: `StarsController`'s
`openPaymentForm`, `PaymentFormActivity` from a Pay button or a link, and
the receipts afterwards. A mini app asking for an invoice is answered
"failed", the status it already handles for a form that could not be
fetched, so it is never left waiting; invoice links get the unsupported-link
answer, as `tg:stars_topup` did. A number whose login SMS costs money gets
one alert and the phone page stays editable. A channel link with a Stars
subscription gets the same kind of alert; one already paid for rejoins
through the ordinary join. Earning went too: affiliate programs, the
referrer a `?ref=` link named (no longer extracted or sent), channel
earnings with their charts' TON and Stars modes, and a link's subscription
price. `PaymentFormActivity` itself still stands, reached only from
`StarsController`'s `buy`, `buyGift` and `buyGiveaway`, and goes with the
wallet.

**Boosts: honoured for nobody, ours included** (2026-09-24, as desktop).
Boosts come from Premium, so a boost level changes nothing here. Every
level-locked admin option is deleted rather than padlocked: a channel's
auto-translate switch, channel and group Appearance (colours, profile
emoji, emoji status, wallpaper, a group's emoji pack - `ThemeChooser` was
extracted first, for the story recorder), and channel custom reactions -
channels get the standard editor, which never sends `paid_enabled`. A
group's restrictions apply as written even to members who boosted it, so
every "boost to send" offer is the plain restriction text now, and the
admin switch exempting boosters is gone. The booster badge, the Boost menu
items, Statistics' Boosts tab (Statistics is one page, shown only where
`can_view_stats`), the boost screens, giveaways, gift codes and every boost
type of `LimitReachedBottomSheet` are deleted. A boost link opens the chat
it names, a gift-code link is unsupported, and a channel story refused with
`BOOSTS_REQUIRED` gets a plain alert (`LoogriGramStoriesNeedBoosts`). What
another client set on the server - a channel's colours, a group's booster
exemption, a channel's paid reactions - is left as it is and still renders.
**Kept, as desktop kept it:** free voice transcription in a boosted group
(`groupTranscribeLevelMin`), which is not an admin option.

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

- **Done 2026-09-24/25:** the held money messages' drawing and the Stars
  wallet, both below in the record. Left over from them: `MessageObject`'s
  `updateMessageText` still builds text for the held actions before
  `setType` clears it; `ChatMessageCell` still observes
  `didUpdatePremiumGiftStickers`; `MediaDataController`'s Premium/TON gift
  sticker packs lost their last drawing reader; `starsUsdSellRate1000` and
  the TON config still have readers to trace.
- **The Stars wallet** (done; kept for the record of what it was). `StarsIntroActivity` (4,618), `PaymentFormActivity`
  (4,835), `TONIntroActivity` (842), `BotStarsController` (277),
  `BalanceCloud`, `ExplainStarsSheet`, and the wallet half of
  `StarsController` (2,512 lines in all): the Stars deep link
  (`showStarsTopup`); buying Stars, gifting them and funding a giveaway
  (`buy`, `buyGift`, `buyGiveaway` - the last ways into
  `PaymentFormActivity`); paying for Premium or a gift with them
  (`buyPremiumGift`, `buyStarGift`); and the balance, transactions and
  subscriptions. Four `StarsNeededSheet` call sites remain, three in
  `StarsController` and one in `StarsIntroActivity` (keeping a
  subscription).

  **`StarsController` cannot simply go.** Its gift-list half - `GiftsList`,
  `GiftsCollections`, `IGiftsList`, `sortedGifts`, `getStarGiftPreview` -
  serves *receiving* gifts, which is untouched, and 27 files reference the
  class. It wants the treatment `CurrencyFormat` got out of `BillingController`:
  extract the list half first, then delete the wallet half around it. `MessageId`
  is a second, smaller case of the same thing - it is a (dialog, message) pair
  that merely lives in the class, and `MessagesController` keys its delivery
  reports on it.
- **Premium - done** (2026-10-01). The Premium screens (`51d67433`) and
  the forced getters (`c50dae98`) are gone; `UserConfig.isPremium()` is
  deleted and every caller takes the non-Premium path, others' Premium
  changes nothing drawn, limits are the free ones (`2531579c..ada551b5`,
  and `39686991`/`0667c778` for the featured-stickers flag). The user's two
  exceptions stand. Left on purpose: a bot's request-a-user-with/without-
  Premium filter (desktop keeps it; the server checks it),
  `TL_availableReaction.premium`, read only to leave Premium reactions out,
  and `BotVerifySheet` (desktop keeps its counterpart too: `verify_peers_box`
  and the profile's `fillBotVerifyAccounts`, checked 2026-10-04).
- **Parity with desktop - both ways now** (the rule at the top of this
  file). Desktop to Android: every item of desktop's checklist is done
  (Business, Stories, the article editor, AI compose, large emoji,
  suggestion popups, greeting stickers, nags and help, telemetry, defaults),
  and a commit-by-commit audit of all 233 desktop commits (2026-10-01) found
  13 more gaps, all closed in `0d90a619..090da946`: the read-time and
  last-seen trade, held paid-message notices and priced suggested posts,
  collectible chat themes, wallpapers and name colours, decorated profile
  backgrounds, collectible previews and links, Fragment collectibles, the
  group emoji-pack chooser, 56 strings, the README, and the profile Gifts
  tab with the gift data. Desktop's code removing *free* suggested posts was
  judged a desktop mistake and restored there (`12f173a996`, `f7bbe51779`).
  **Android to desktop** (2026-10-04): all 337 Android commits classified
  against desktop - 210 done there or absent, 91 notes/tooling/internal, 25
  Android-only, 4 differing by decision (the ghost toggle's place, a paid
  login code's alert, the updater's waiting download, the avatar maker's
  locks), and 7 missing, which came to four gaps, all mirrored on desktop the
  same day: Telegram's help links and the pre-login FAQ row (`d062a5d77c`,
  `c3041e5e96`), a verifier bot's note (`6adee333cb`), the updater's hourly
  re-check (`00e97cf4bb`), and the location picker with geolocation and
  venue search (`680eee9198`). Desktop's builds of them are owed.
- **Stories - four stages, as desktop (2026-09-19), each a subagent with
  a full brief, reviewed here.** Desktop's decisions: story-carrying
  messages held, not dropped; a story reply keeps its text and loses the
  quote; story links open the peer; story previews are plain articles;
  admin story rights, `stories_muted` and the story-reaction notify
  setting are round-tripped unchanged; `updateStory`/`updateReadStories`
  ignored; bot previews gone for everyone (desktop has no bot-preview
  code); a mini app's share_to_story gets a "not supported" alert.
  Shared widgets in `ui/Stories/recorder/` stay in that package (moving
  them would churn ~100 imports and every rebase).
  1. **Posting - done** (`cdca4e1c..cf493c29`, ~44,500 lines).
  **All four stages done** (2026-09-28: stage 2 `7f098cd6..3d24fec0`,
  stage 3 `eea37f6f..19badd2d` + `a2692980`, stage 4 `599ffe0b..4e66e2f6`;
  the plan below is kept for the record).
  2. **Messages, links, statistics, settings** (was frozen mid-way on
     2026-09-27; the notes that follow were its plan). Left of it: notification settings - the Stories row
     and exceptions, NotificationsCustomSettingsActivity's stories screen,
     the story-reactions row, the per-chat stories switch, LinkManager's
     `notifications/stories` links; NotificationsSettingsActivity's
     reactions toggle also writes `EnableReactionsStories` and must stop.
     `stories_muted`, `stories_hide_sender` and the reaction settings are
     already read and sent back as the server had them; `stories_sound` is
     **not** - upstream sends the local `StoriesSound*` prefs (default
     NoSound), so the server's value must be read into them where the
     global settings load and update (which field holds it was being
     checked when work froze).
  3. **The viewer and every way into it**: StoryViewer, PeerStoriesView,
     SelfStoryViewsPage, StealthModeAlert, StoriesListPlaceProvider, live
     stories, the chat-list strip (DialogStoriesCell, DialogsActivity's
     stealth item), avatar rings, profile story tabs and albums, bot
     previews for everyone (BotPreviewsList too), story reporting, story
     push notifications (keys, channels, `TYPE_STORIES`), sending story
     replies and `replyStory` storage, hashtag story search in chats.
     Left for it by stage 1: our own stories' delete/archive/pin, the
     albums UI, the live-story end item, `isEditBotsPreview`,
     StoryPrivacyButton's always-false `animated`, the strip's self cell,
     SharedPhotoVideoCell2's privacy icons. By stage 2: the ChatActionCell
     branch in StoriesListPlaceProvider.
  4. **The data layer**: StoriesController (incl. `canPostStories`,
     `canEditStories`, album management, `fillMessagesWithStories`),
     StoriesStorage (its fill methods), the dead story link resolvers,
     story updates ignored, story state on peers, the story_drafts table
     and input media-area TL types, then the three forced Premium getters.
  Kept on purpose: `TYPE_STORY` (the file cache tags story files with
  it), the video pipeline's handling of story stickers and collages
  (VideoEditedInfo, TextureRenderer, MediaCodecVideoConvertor - nothing
  sets them now), TimelineView's audio/round/collage tracks (trim not
  clean yet), the debug menu's dual-camera slot left null so later
  indices hold.
- **Smaller leftovers - done** (2026-10-01, `0af7f619..83c5583d`): held
  messages build no text, the Premium/TON gift packs, the Stars/TON rate
  config, a paid group message's price, paid suggested-post branches, the
  price of writing to someone, the channel wallpaper modes, `venueSearchBot`.
  Kept: the two `if (true)` guards (upstream's own, blamed to `9f8c35d1`),
  and the story-sticker and collage fields of the video pipeline
  (VideoEditedInfo, TextureRenderer, MediaCodecVideoConvertor) - some are
  still read back from stored strings, the rest would need a shader and
  audio-mixing rewrite tested on a device.
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

- **Done on 2026-09-23, for the record** (13 commits, `8197b9f4..1ec92ae0`,
  about 6,900 lines net): the buy-a-collectible tab and with it
  `ResaleGiftsFragment` and `GiftViews.Tabs`; `ResaleBuyTransferAlert`,
  `PaymentFormState` and `StarsController`'s resale form and purchase; the
  paid-reaction bookkeeping and then the drawing of one that arrives, which
  took the reaction row's whole overlay pass, the picker's star and seven
  lottie and drawable resources; priced suggested posts, down to
  `MessageSuggestionParams.amount` and `StarsController.isEnoughAmount`; gift
  transfers and the TON export, taking `UserSelectorBottomSheet`'s
  `TYPE_TRANSFER`; paying to erase a gift's provenance; paying to upgrade a
  gift, keeping the case the sender paid for; converting a gift back into
  Stars; staked dice, including `StakedDiceSheet` and the won/lost banner; the
  pay-to-search button; and the keep-your-paid-subscriptions hint.

  One repair on the way, and it is trap 0d: the transfer cut swallowed
  `presentFragment` and both `getInputStarGift` forms, and four compiles were
  misread as green before anyone noticed. `loogrigram-tools/check_swallowed.py`
  exists because of it.

- **Done on 2026-09-23/24, for the record** (31 commits, `48e6a10d..a63e5697`,
  about 28,400 lines net): affiliate programs, and the referrer a link named;
  channel earnings; the login fee; every payment form; Stars subscriptions,
  both halves; the staked dice's error path; the Play install referrer and
  the chat list's "Recently viewed" section it fed; Chromecast's four stubs,
  and the Cast items they had kept alive in both players - the music
  player's called action 7, "remove from profile"; and boosts, entirely (see
  "Boosts" above), with Wear. Two checkers: `check_dangling_imports.py`, and
  `check_swallowed.py` reading the working tree when given a bare revision.

  Two repairs on the way. `ad610a75` fixed the crash on opening any
  collectible gift that is in the published `g1ec92ae0` (trap 0e). And a
  second power cut left a corrupt index, three truncated objects and four
  NUL-filled source files that `git status` did not show (trap 0f).

- **Done on 2026-09-24/25, for the record** (14 commits, `ec7c9d55`..`b5cf00ef`,
  about 18,400 lines net): the updater lets a newer release replace a
  waiting download; reposting a collectible to a story; ChatActionCell's
  cards for the held gift messages (with StarGiftUniqueActionLayout and
  SuggestBirthdayActionLayout); ChatMessageCell's invoice card; paid media
  and GroupMedia; the eight message types nothing can have; the
  extended-media checks; boost, gift-code and auction link previews as
  plain pages; the gift sheet's market value; GiftCell's Premium tier; the
  Stars and TON wallet (13,410 lines); LimitPreviewView's dark gradient;
  nine dead Stars settings; and StarsController renamed GiftsController,
  with MessageId in messenger. Full build `f2478ebb` covers the first
  eight; every commit compiles (last: `b5cf00ef`, run 36068062946).

- **Done on 2026-09-25/26, for the record** (31 commits,
  `cf3005e1..b81ce49d`, about 7,860 lines net; full build `b81ce49d`
  pending): the Premium pass through every getter site outside Stories -
  the caption-limit promo (which still showed in a channel's photo
  viewer), story-caption formatting's padlock, the download-speed promo
  with its native report, transcription's upsell (the free trial stays,
  with desktop's wording), reaction padlocks, the limit sheets' way past
  the limit, the chat list's Premium hints, the Premium-only Settings
  rows, the profile's Premium bits. Then live offers no getter guarded:
  dragging All Chats, the archive switch, more accounts, the schedule
  Repeat row, the bot limit, the summary limit, Unlock Similar, the
  Posts tab. Then whole features: Saved Messages tags (deleted with
  their drawing and Saved Messages' reactions, as desktop), to-do lists,
  the wallpaper "for both" button, folder tags, send-as identities,
  message effects, the avatar constructor, two Premium links, and custom
  emoji and Premium stickers (packs left out, nothing padlocked, a pasted
  Premium emoji becomes a plain one - all as desktop). AI compose's first
  half. Two one-line compile fixes (trap 0i).

- **Done on 2026-09-26, second session, for the record** (17 commits,
  `46da8199..20294896`, about 7,300 lines net and 105 artwork files;
  full build `20294896` pending): the ad revenue explainer and Premium's
  "show ads" switch; Premium reactions left out of the double-tap list;
  the similar-channels "More" block, "Translate Entire Chat", the
  forward-article and rich-checkbox Premium offers; Privacy's Voice
  Messages, Messages and Gifts screens with the Premium-users and
  Mini-apps exceptions (rules parsed so both are skipped, never read as
  "My Contacts"); a dead "Sync Contacts" search entry; sharing toggle
  re-enable only; the greeting's "how?"; our own name and profile colour
  (PeerColorActivity deleted, its helpers moved out: PeerColorGrid,
  PeerColorDrawable, PeerColorSpan, UnpinGiftCell); the three Premium
  launcher icons; and emoji statuses in six commits - bots refused,
  drawing removed everywhere, the chat-list title's picker, "Set as
  Status", the accounts/gifts/stories leftovers, then the plumbing. Two
  compile fixes (trap 0j).

- **Done on 2026-09-26/27, for the record** (Business `ba0abf9e..892c08b2`,
  ~11,100 lines; Stories stage 1 `cdca4e1c..cf493c29`, ~44,500; stage 2 so
  far `7f098cd6..6e806af0`, ~1,600; all compile-checked, none built):
  Business, the parts ours to set, as desktop - chatbots and the bot bar,
  away/greeting messages and the recipients picker, quick replies (their
  updates dropped), our own chat links, our own hours/location/intro
  editors; kept the bot-connection confirm prompt (security, desktop has
  none), others' hours, location, intro and t.me/m/ links, the "via
  business bot" label, and the Business tables unused in the schema (the
  recovery list's nonexistent `quick_replies` table went). Stories
  posting (the camera, editor, drafts, upload queue, posting limits, bot
  preview editing, seven story-only paint views, every add/repost/share
  entry point). Stories stage 2: story messages held (`LoogriGramHidden`
  now covers `TL_messageMediaStory`; the four story push keys are
  silent), story replies without the quote, story links to the peer and
  plain previews, story statistics. Checker fixes: `check_swallowed.py`
  counts `default` interface methods (`914849f9`); new local helpers
  `freed_res.py`, `removed_decls.py`, `keep_members.py`,
  `ed.cut_else_if`. Lesson: a file due for deletion in a *later* commit
  still compiles in this one - grep it too (two failed compiles).

- **Done on 2026-09-28/29, for the record** (all compile-checked, none
  built): Stories finished - stage 2's settings rows (`a7a57485`,
  `3d24fec0`: admin story rights and the story notification settings,
  `stories_sound` included, round-tripped unchanged); stage 3, the viewer
  and every way into it (`eea37f6f..19badd2d`, ~38,600 lines: the viewer,
  strip, rings, profile tabs, bot previews, story notifications, replies,
  shares, reports, hashtag story search, the preload and keep-media rows;
  old story files cleared under Storage's Miscellaneous); stage 4, the
  data layer (`599ffe0b..4e66e2f6`, ~5,900: StoriesController,
  StoriesStorage, story updates ignored, no story request can reach the
  server, "Clear local database" also empties the story tables). Then the
  smaller desktop removals (`efedb453..4caec8fd`, ~4,800): story UI modes,
  large emoji and a single emoji drawn as its animated sticker (desktop
  took both, `fcde819440`), emoji/sticker suggestions and their two
  settings, the greeting sticker, Ask a Question/FAQ/Features and the
  phone-number nag (the password reminder stays). Then reading telemetry
  (`a72f6144`, `reportReadMetrics`) and view-count contributions
  (`63d84bb0`, `getMessagesViews` - counts now come only from the server,
  as desktop), and the defaults (`c3a60aa7` videos and files off,
  `27d50187` pinned-message notifications off; muted chats were already
  out of the badge). New local checkers: `argcheck.py`,
  `argcheck_all.py`, `decl_ctx.py`, `all_checks.py`, `orphan_public.py`;
  `removed_decls.py` now sees declarations with modifiers.

- **Done on 2026-09-29..10-03, for the record** (all in `g55475fe4`): the
  smaller parity items (`efedb453..4caec8fd`: story UI modes, large emoji,
  suggestion popups, the greeting sticker, help rows and the phone nag),
  reading telemetry and view-count contributions (`a72f6144`, `63d84bb0`),
  the defaults (`c3a60aa7`, `27d50187`), GIFs follow Photos (`7fa8201f`,
  the user's choice), the article editor and AI compose's second half
  (`8807747d..f05cc9ad`, ~31,700 lines), the Premium screens
  (`9463978e..c50dae98`), the Premium convergence (`2531579c..ada551b5`),
  the leftovers (`0af7f619..83c5583d`), the icon (`037d897a..9d316ac1`), no
  iTunes cover lookup (`8fed48d0`), no Help section (`46fc6ede`), and the
  desktop-parity pass (`0d90a619..090da946`). Slips worth remembering: two
  compiles failed on a call in a neighbouring branch and on a `Foo.class` in
  a class list - grep every usage form, file-wide; and a `grep -P` (the
  locale) or a `--` before `--include` prints a false zero.

### Then

1. **Verify what first use could not.** Installed and working, but still open:
   notifications after hours idle and after a reboot (the push path is ours and
   fails *slowly*); ghost mode's four signals confirmed from a second account,
   including that the read date is hidden and no burst of receipts follows
   turning it off; a received location opening in a maps app; and a sweep for any
   premium, Stars or gift surface still reachable.
2. **Cosmetic pass - done** (2026-10-01, `037d897a`): the launcher icon is
   desktop's mark (black, the white plane, a blue L and G), built from
   `branding/LoogriGram/logo_256.png` by `branding/make_android_icons.py`
   (stdlib only); one icon, Vintage and Aqua gone; Telegram's unused
   wordmark drawable went too (`660b642a`). The pre-API-26 launcher PNGs
   stay - minSdk is 21.
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
