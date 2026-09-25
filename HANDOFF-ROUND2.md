# World Radio – round 2 (user feedback after the first Prism test)

Read `HANDOFF-PLAN.md` (architecture, tools, evidence of round 1) and `../CLAUDE.md` (Gradle line, reporting rules)
first. State before this round: 0.1.0+26.2 built and tested in Prism, no git repo yet. Eight feedback points, all
confirmed by the user on 2026-09-25; nothing here is optional. Bump the version to `0.2.0+26.2`.

Work in this order: 6 → 5 → 4 → 1, 2, 7 (small) → 3 (audio, the only risky one) → 8 (search). Build after every
step (`./gradlew build -q > build.out 2>&1; grep -E "Fehler|error:|FAILED" build.out`), keep `tools/test-signal.sh`
green, and finish with the dev screenshots listed under each point. Update README.md, both lang files and
`HANDOFF-PLAN.md` (status line + section 1) at the end.

## 6. Drop signal strength 3 (ranges become 32 / 64)

- `SignalBlock.POWER` → `IntegerProperty.create("power", 0, 2)`, the cycle is `(power + 1) % 3`.
- `Config.Values.ranges` default `List.of(32, 64)`; `Config.range()` clamps to the list size (a config file with
  three entries from round 1 must still load: ignore the third value, do not crash).
- Remove `radio_3` / `amplifier_3` from `tools/MakeResources.java` (models, blockstates, textures) and regenerate;
  delete the stale files. Any old world with `power=3` in a chunk falls back to the default state – acceptable, say
  so in the README.
- `tools/SignalGraphTest.java`: replace ranges of 128 with 64 where they stand for "strength 3", keep the count of
  checks equal or higher. `tools/scenes/*.txt`: no `power=3` anywhere.
- Lang: `worldradio.power.on` unchanged; README table and `docs/blocks.png` (six variants now).

## 5. Swap the click actions

- Plain right-click cycles the strength (server side, same click sound and overlay message as today);
  sneak + right-click opens the screen (`screenOpener` on the client, `onOpened` on the server).
- Caveat to keep in mind and to write into the README: vanilla skips block interaction on sneak + use while the
  player holds any item (`ServerPlayerGameMode.useItemOn`), so the screen opens with an empty hand only. That is the
  same limit the strength cycle had in round 1; the user accepted it there.
- `DevClientHooks` / `tools/dev-screenshot.sh`: whatever opens screens or cycles power for the screenshots must
  follow the swap (check `-PdevScreen=` and the STAR hook).
- Lang keys that describe the gesture: `worldradio.status.off`, `worldradio.amplifier.off` ("sneak + right-click"
  → "right-click"), and the README usage table.

## 4. Steeper falloff inside the range

`VolumeCurve.EDGE` 0.6 → 0.2: the volume falls linearly from 1.0 at the block to 0.2 at the range edge (twice the
drop of round 1), then from 0.2 to 0 over the 10-block tail. Update the curve checks in `SignalGraphTest` (values at
0 / R/2 / R / R+5 / R+10 / R+11 – expect 1.0 / 0.6 / 0.2 / 0.1 / 0 / 0) and the README line about volume.

## 1. `E` (inventory key) closes the screens

`RadioScreen` and `AmplifierScreen`: override `keyPressed` (check the 26.2 signature with
`javap -p ... net.minecraft.client.gui.screens.Screen | grep keyPressed`; it takes a `KeyEvent` in 26.2, compare
`mouseClicked(MouseButtonEvent, boolean)` in `RadioScreen`). When the key matches
`minecraft.options.keyInventory` and no `EditBox` has focus (`getFocused() instanceof EditBox` false), call
`onClose()` and return true; otherwise `super.keyPressed`. Typing an "e" into the filter or URL box must still work –
verify with a dev screenshot of the URL tab after typing `http://e.example` (`-PdevType=` hook or the existing
one), and one showing the screen closed after `E` with nothing focused.

## 2. Own creative tab

- `FabricItemGroup.builder().icon(() -> new ItemStack(RADIO_ITEM)).title(Component.translatable("itemGroup.worldradio"))
  .displayItems((params, output) -> { output.accept(RADIO_ITEM); output.accept(AMPLIFIER_ITEM); }).build()`,
  registered in `BuiltInRegistries.CREATIVE_MODE_TAB` under `worldradio:main`. Check `FabricItemGroup` exists in
  Fabric API 0.158.0+26.2 (`unzip -l` the fabric-item-group jar in the loom cache), otherwise use the vanilla
  `CreativeModeTab.builder(...)` – the icon is the radio's `power=0` item model either way.
- Remove the `FUNCTIONAL_BLOCKS` entries (the items live in the new tab only).
- Lang `itemGroup.worldradio`: "World Radio" / "World Radio". Evidence: dev screenshot of the creative inventory on
  the new tab (`-PdevScreen=creative` hook: open `CreativeModeInventoryScreen` and select the tab).

## 7. "Off" → "Clear"

Lang key `worldradio.station.off` → "Clear" / "Leeren"; tooltip stays ("Clear the station …"). README and the
`docs/favourites.png` screenshot follow.

## 3. Sound: 30 % directional, 70 % at the player

Goal: the station is always clearly audible in both ears, only a part of it moves with the head. OpenAL cannot
spatialise a source partially and plain OpenAL has no per-source "spatialize mix", so do the panning in our own
PCM path and hand OpenAL a non-spatial stereo source:

- `RadioAudioStream.FORMAT` → stereo (48 000 Hz, 16 bit, 2 channels). `StationStream` keeps decoding to 48 kHz
  mono (unchanged); the stream turns each mono sample `m` into `left = m * gainL`, `right = m * gainR`.
- `RadioSoundInstance`: `relative = true`, position `(0,0,0)`, `Attenuation.NONE`. A stereo buffer is never
  spatialised by OpenAL, `relative` keeps it that way whatever vanilla sets. Volume stays the curve value ×
  factor as today. Vanilla's `SoundEngine` keeps the category slider and the streaming channel; nothing else changes.
- Gains: every client tick `SourceTracker` computes the pan of the playing place: `pan` in −1..1 from the angle
  between the camera's look direction (yaw only) and the vector to the source (`sin` of the horizontal angle, positive
  = right). Equal-power panning of the directional share, the rest constant in both ears:
  `gainL = 0.7 * 0.7071 + 0.3 * cos(θ)`, `gainR = 0.7 * 0.7071 + 0.3 * sin(θ)` with `θ = (pan + 1) * π / 4`
  (pan −1 → the directional part fully left, 0 → centre, +1 → fully right; the 0.7 part sits at −3 dB in both
  ears). Normalise so that the centre position gives 1.0 per channel. Put the formula into
  `worldradio.signal.Panner` (no Minecraft classes) with checks in `SignalGraphTest`: pan −1 → left > right and
  right ≥ 0.7 × centre-level, pan 0 → equal, pan +1 mirrored, a source straight behind → equal (no front/back cue).
- The gains are two `volatile float`s on `RadioAudioStream` (set by `RadioSoundInstance.setPan`, read by the sound
  thread in `read`). Smooth them in `read` over one chunk (linear ramp from the previous pair) so turning the head
  does not click.
- Constant `DIRECT = 0.3f` in `Config.Values` as `directionalShare` (0 = all at the player, 1 = fully panned),
  default 0.3, so the user can tune it without a rebuild.
- Evidence: `tools/StreamProbe.java` gets a `--pan <-1|0|1>` option that writes 2 s of stereo PCM through the same
  code and prints the RMS per channel (left ≫ right for −1, equal for 0); dev client with `-PdevWalk` circling a
  radio logs `Radio: pan=… gainL=… gainR=…` once a second, and the user listens in Prism (the acceptance test is
  the user's ear – say so in the report, do not claim it "sounds right").
- Keep the 8-streaming-channel budget: still one sound per station.

## 8. Search stations by name and genre

Add a fourth tab **Search** (tabs Favourites · Browse · URL · Search, width per tab adjusts from `tabs.length`):

- One `EditBox` at the top; after the text changed and 400 ms passed without another change (debounce in
  `tick()`, ≥ 2 characters) run two Radio-Browser queries in parallel:
  `/json/stations/search?name=<text>&codec=MP3&hidebroken=true&order=clickcount&reverse=true&limit=100` and
  `/json/stations/search?tag=<text>&codec=MP3&hidebroken=true&order=clickcount&reverse=true&limit=100`
  (`tag` matches genres like `jazz`, `oldies`, `80s`; use `tagExact=false`). Merge both lists, dedupe by URL,
  name matches first, then by click count. `RadioBrowser.search(String)` returns `CompletableFuture<List<Station>>`,
  cancelling stale requests the same way `load()` does with `request` ids.
- Rows: station name left, `country · first two tags · bitrate` right (`StationList.Row` already has a right
  column); clicking tunes as in Browse. Empty text → hint "Type a station name or a genre, e.g. jazz"; no hits →
  the existing `worldradio.browse.nothing`.
- The Browse tab keeps its filter box as it is.
- Lang: `worldradio.tab.search`, `worldradio.search.hint`, `worldradio.search.hint.empty` (both languages).
- Evidence: dev screenshots of the Search tab for `kiss` (Kiss FM Berlin in the first rows) and `jazz`
  (`-PdevSearch=<text>` hook), saved as `docs/search.png`; README section updated.

## Reporting

Report per point: done / evidence (test counts, screenshot paths) / anything that deviates. Name the jar path
(`build/libs/worldradio-0.2.0+26.2.jar`); the user tests it in Prism. Do not commit or create a repo.
