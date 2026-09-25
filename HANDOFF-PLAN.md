# World Radio – implementation plan (Fabric 26.2)

Status 2026-09-26 (0.5.0, jar `build/libs/worldradio-0.5.0+26.2.jar`, not committed, not yet in Prism): the teak GUI
("walnut-teak" from `tools/MakeGuiTextures.java`: black glass dial, speaker cloth, piano keys; panels in
`textures/gui`, keys as nine-slice sprites, `KeyButton`/`KeySlider`), a volume slider per radio (BE `volume`,
`Packets.SetVolume`, sent in the sources and multiplied into amplifier signals, `/worldradio volume`), favorites per
player in the client's `config/worldradio-favorites.json` with a rename pencil (the world's old shared list is taken
over once per station; the favourite packets/commands are gone), US spelling in en_us, and the list marks the
tuned station again after a click. Evidence: `test-signal.sh` 52/52, `dev-test-network.sh` 41/41 (incl. volume),
dev client: import took over 1 station; marked rows [Radio Dismuke] → click → [98.8 KISS FM Berlin]; slider to 0.4 →
server `volume=40`, heard 0.383 instead of 0.96; rename via pencil + typing + Enter → file "Kiss Berlin"; a removed
imported station stays removed; in-game shots of all screens in run/screenshots (docs/ waits for the user's OK).
Before that, after round 4 three follow-ups (0.4.1, jar `build/libs/worldradio-0.4.1+26.2.jar`): the items and
the creative tab icon show level 3; the far-range cut-off is fixed (cause: the client only heard radios in its loaded
chunks, so the sound stopped at the view distance – reproduced in the dev client at 120 → 160 blocks with range
288; now the server keeps every radio/amplifier in `NetworkData` (saved per dimension, also when unloaded) and
sends `Packets.Sources` to the players; the fade past the range is R/4 with the same slope; walk test 5 → 380
blocks: 0.986 … 0.222 at 280, 0.056 at 340, silent at 360; `dev-test-network.sh` 40/40 incl. an unloaded radio
that keeps reaching an amplifier, across a restart; `test-signal.sh` 52/52); three GUI themes (walnut, classic,
hifi) exist as generator previews in `art/gui/preview` (`tools/MakeGuiTextures.java`), the user picks one.
Round 4 (2026-09-25 night): `HANDOFF-ROUND4.md` (6 points) done, jar 0.4.0,
not committed (no git repo), not yet tested in Prism. Round 4 evidence: `tools/test-signal.sh` 49/49 (32/64/128/1056),
`dev-test-network.sh` 35/35 (old config with `ranges` and step 8 → migrated to 32 with `configVersion` 4; recipes
and advancements load without parse errors; 3 bars → 128, top bar broken → 96 within 25 ticks and the amp at 110
loses it; mixed column → 160; 40 rods → 1056; `/worldradio enable … false` → level 0, `enabled=false`, station kept,
the amp at 20 loses the signal, `true` → back; a round-3 radio loads switched on), dev client: sneak + right-click
(empty hand) toggles off → on (level 0/1, station kept, no screen, sound stops and restarts), the Turn off button
sends `SetEnabled` (radio off, the client hears nothing), `recipe give` unlocks the radio recipe, a copper ingot
unlocks the amplifier advancement (test failed before, passed after), tooltip prints "+32 blocks each". Textures
confirmed by the user from the generator preview before any screenshot; `docs/blocks.png` (after tuning: radio 0/2,
amplifier 1/2) and `docs/blocks-off.png` (before: all level 0), `docs/favourites.png`, `docs/radio-off.png`, all other
docs shots retaken. Round 4 deviations: an amplifier at level 0 and one at level 2 cannot stand side by side (the
signal reaches both), so level 0 is the before shot; lang keys name the action (`station.off` = "Turn off", Clear
moved to `station.clear`); 0.3 config files with step 8 are migrated; the footer hint wraps onto two lines.
Round 3 (0.3.0): antennas instead of strength, `level=0..3`, right-click opens the screen, an antenna block on the
top face is placed, range tooltip; old `power=` worlds load with their stations.
Round 2 evidence (0.2.0): stream probe
`--pan` (Kiss FM L/R 1.61 / 1.00 / 0.62), favourites 3/3, `E` closes screens, pan and volume walks.
Round 1 evidence (0.1.0): signal tests, stream probe (Kiss FM, Dismuke, DLF https, radioeins redirect; AAC
rejected), source hand-over radio → amplifier without restart, 48 000 real samples/s. Deviations: output is 48 kHz
stereo made from a mono decode (the mod pans itself, the sound sits relative at the listener); no sounds.json
entry (the sound instance resolves its own `Sound`); favourites are also sent on join; admin `/worldradio
favourite` for tests; the search puts the player's own country first (Windows region, else game language) so
`kiss` finds Kiss FM Berlin (named "98.8 Kiss FM" in Radio-Browser, 15th by clicks) in the first row.
Not tested: two clients at once, whether the Radio slider and the 30 % panning sound right (user's ears in Prism),
pistons moving antenna blocks and copper oxidising in place (both covered by the one-second rescan, not run).
Mod id `worldradio`, package `worldradio`, author BaconCakeFactory,
licence MIT. Build setup is copied from `../bc-quarry-fluids` (loom 1.17.0-alpha.7, loader 0.19.3,
Fabric API 0.158.0+26.2, Java 25). Build with the Gradle line from `../CLAUDE.md`.

## 1. What the mod does (agreed with the user)

- **Radio block** – the only real sound source. Right-click (with or without an item) opens the station screen, `E`
  closes it; sneak + right-click (empty hand) or the screen's Turn off/Turn on button switches it off and on without
  losing the station. The range is 32 blocks plus 32 per antenna block stacked on top (end rods, iron bars, iron
  chains, lightning rods, copper bars/chains in every oxidation stage, waxed or not; tag `worldradio:antenna`),
  capped at 32 antenna blocks (1056). An antenna block clicked onto the top face (the socket) is placed. Four
  display levels (`level=0..3`: no station or off / base / 1–3 antenna blocks / 4+), models and animated fronts
  (speaker grille with rings, meter, three bars, LED) from `tools/MakeTextures.java`.
- **Amplifier block** – same range rule with its own antenna column, no switch; level 0 = no signal. Same front
  layout as the radio in steel and blue. Every radio signal
  that reaches the amplifier (directly from a radio or through any number of other amplifiers, unlimited hops) is
  re-emitted from the amplifier with the amplifier's own range. One signal → 100 %, two or more overlapping
  signals → each at 50 % (generalised: `1 / max(1, n)` with a floor of 50 %, i.e. n ≥ 2 all play at 50 %).
  Right-click opens a read-only list of all incoming signals: station name, position of the radio, distance
  (straight line to the radio) and hops.
- **Volume curve** around every source (radio or amplifier) with range `R`:
  `d ≤ R`: linear from 1.0 at the block down to 0.2 at `R`; then linear from 0.2 to 0.0 over `max(10, R/4)`
  blocks (the same slope, since 0.8/R = 0.2/(R/4));
  beyond: silent. Multiplied by the source factor (100 % / 50 %) and the player's radio volume slider.
  When several sources of the **same station** are audible (radio plus a nearby amplifier), only the loudest
  one plays; the others stay silent until they become the loudest.
- **Direction**: 70 % of a station plays evenly in both ears, 30 % is panned left/right towards the place it plays
  from (equal-power, `worldradio.signal.Panner`; front and back sound the same). `directionalShare` in the config.
- **Ranges**: `baseRange` 32, `antennaStep` 32, `maxAntenna` 32 in `config/worldradio.json`. The antenna columns are
  counted on every network change, rescanned once a second, and a neighbour update counts the first block at once.
  Both screens show "Range 56 blocks (3 antenna blocks)" with the stacking hint as a tooltip.
- **Station screen** (radio): tabs `Favourites`, `Browse` (country → region → station list with a text filter),
  `URL` (free text, any http(s) MP3 stream or .m3u/.pls playlist), `Search` (name or genre, 400 ms debounce).
  "Clear" removes the station, "Turn off"/"Turn on" switches the radio. Favourites are **per world, server side**
  (everyone shares the list); a star next to the current station adds/removes it.
- Own creative tab `World Radio` (radio and amplifier only there); the items and the tab icon show level 3.
- **Unloaded chunks**: the server keeps every radio and amplifier of a dimension in `NetworkData` (SavedData) and
  computes the graph over all of them; clients get the audible sources as `Packets.Sources` (sent on change, join,
  dimension change, respawn), so nothing depends on the client's or server's view distance.
- **Recipes** (redstone, unlocked by a copper ingot): radio `PIP / CNR / PPP` (planks, iron, copper coil, note block
  speaker, redstone tuner); amplifier `ICI / CWC / IRI` around a radio.
- Anybody may use any radio; no whitelist, no permission checks beyond "player is within reach of the block".
- **Volume slider**: a new `Radio` category in Options → Music & Sounds (SoundSource enum extended by mixin).
  If the enum extension turns out unstable in 26.2, fall back to `SoundSource.RECORDS` (Jukebox/Note Blocks).
- Frequencies are dropped: the Radio-Browser API has no frequency data and the user does not need it.

Non-goals for v1: AAC/HLS streams (the screen shows "codec not supported"), synchronised playback between
players (each client streams for itself, a few seconds of drift are fine), redstone control, a portable radio.

## 2. Prior art (checked 2026-09-25)

- [Radio Mod (Fabric, MIT)](https://github.com/AnonBOTpl/Fabric-Radio-Mod) – client-only, already on 26.2,
  Radio-Browser search, JLayer/MP3SPI decoding in a background thread. Reference for the decoder, the API
  client and the User-Agent handling; MIT, so code may be reused with attribution.
- [Radio (radio-sws4)](https://modrinth.com/mod/radio-sws4) – transmitter/receiver/frequencies/lightning-rod
  antennas, but ARR, 1.21.11 and audio only via Simple Voice Chat. Ideas only.
- SimpleVoiceChat-Radio, Simple Voice Radio, Analog, Radio Craft – all need Simple Voice Chat. Not used.
- Stream facts: Kiss FM Berlin = MP3 128 kbit (`http://stream.kissfm.de/kissfm/mp3-128/internetradio/`),
  Radio Dismuke = MP3 24 kbit (`http://stream2.early1900s.org:8000/`). Radio-Browser codec statistics:
  MP3 44 169 stations, AAC(+) ~18 700, OGG 756 → MP3-only covers the user's examples and ~70 % of the catalogue.

## 3. Architecture

```
server                                  client
------                                  ------
RadioBlockEntity   (url, name, range, antenna)   RadioScreen / AmplifierScreen (GUIs)
AmplifierBlockEntity (range, antenna, signals[]) SourceTracker  – decides which loaded sources are audible
SignalGraph        (pure Java, BFS)     StreamPool     – one MP3 decoder thread per distinct URL
RadioNetwork       (per ServerLevel)    RadioAudioStream – vanilla AudioStream fed from the pool
FavouritesData     (SavedData)          RadioSoundInstance – TickableSoundInstance, manual volume
packets: SetStation C2S, Favourite C2S, FavouritesSync S2C
```

The server never touches the internet. It only stores station URL/name per radio, computes the signal graph
and syncs block entity data. Every client streams the audio itself.

### 3.1 Server side

- `SignalGraph` (no Minecraft classes, unit-testable): input = list of radios `(id, pos, range, url, name)` and
  amplifiers `(id, pos, range)`; output = per amplifier the list of `Signal(radioId, url, name, distance,
  hops, factor)`. BFS from every radio: a radio reaches an amplifier when `dist(radio, amp) ≤ radioRange`; an
  amplifier reaches another when `dist ≤ ampRange`; visited set per radio, so cycles and unlimited hops are safe.
  Amplifiers with `power=0` neither receive nor relay. `factor = signals.size() >= 2 ? 0.5 : 1.0`.
- `RadioNetwork` per `ServerLevel`: tracks loaded radio/amplifier block entities (register in `onLoad`, remove in
  `setRemoved`), has a `dirty` flag set by place/break/power change/station change/chunk load, recomputes at most
  once per server tick (`ServerTickEvents.END_WORLD_TICK`) and writes the new signal list into each amplifier
  (`setChanged` + `sendBlockUpdated` only when the list actually changed).
- Block entities sync via `getUpdatePacket`/`getUpdateTag` (vanilla `ClientboundBlockEntityDataPacket`).
- `FavouritesData extends SavedData` (`worldradio_favourites.dat`): list of `(name, url, countryCode)`.
  Sent to a player when they open a radio (`FavouritesSync`), changed with the `Favourite` C2S packet.
- `SetStation` C2S: `(pos, url, name)`; server checks the block exists, the player is within 8 blocks, the url is
  http/https and ≤ 512 chars, then stores it and marks the network dirty.

### 3.2 Client audio

- `StreamPool`: map `url → StationStream`. A `StationStream` runs one daemon thread: `HttpClient` GET with
  `User-Agent: WorldRadio/<version> (BaconCakeFactory)` and `Icy-MetaData: 1`, resolves `.m3u`/`.pls`, follows
  redirects, strips ICY metadata blocks (keeps the current title for the GUI), decodes MP3 with JLayer to 16-bit
  PCM and pushes frames into every registered consumer's bounded ring buffer (~2 s). A lagging consumer drops old
  frames, so all sources of one station stay in sync. Reconnect with backoff on errors; the stream is closed when
  the last consumer leaves (plus a 10 s grace period).
- `RadioAudioStream implements AudioStream`: `getFormat()` from the decoder (44.1/48 kHz, mono or stereo),
  `read(n)` pulls from its consumer buffer and returns silence when the buffer is empty (never blocks the sound
  thread).
- `RadioSoundInstance extends AbstractTickableSoundInstance`: `Attenuation.NONE`, position = block centre,
  looping, `getVolume()` = curve × factor. Ticks every client tick: recompute distance to the camera, stop when
  the block is gone or the station changed. Vanilla `SoundEngine` handles OpenAL, panning and the category slider.
  Registering the stream: a `sounds.json` entry `worldradio:stream` with `"stream": true`; a mixin on
  `SoundBufferLibrary.getStream` returns our `RadioAudioStream` for that identifier (like other streaming mods do).
  Verify against the real 26.2 jar before writing it (`javap -p ... SoundBufferLibrary`).
- `SourceTracker`: every client tick, scan loaded radio/amplifier block entities (`ClientLevel` block entity
  ticker or a small registry filled from `onLoad`), compute the curve volume for each (source, station) pair,
  group by station url and keep only the loudest source per station, then keep the loudest 6 stations overall
  (vanilla has only 8 streaming channels) and start/stop `RadioSoundInstance`s accordingly. Switching the
  active source of a station keeps the shared decoder running, so the audio does not restart.
  Hysteresis: a new source takes over only when it is louder by ≥ 0.05, so two equal sources do not flicker.
- Sound category: `SoundSourceMixin` adds `RADIO("radio")` via the usual enum-extension pattern
  (`@Shadow @Mutable $VALUES`, `@Invoker("<init>")`, inject at the end of `<clinit>`). Translation key
  `soundCategory.radio`. Test: the slider appears in Music & Sounds and `options.txt` saves `soundCategory_radio`.

### 3.3 Client GUIs

- `RadioScreen` (plain `Screen`, no container menu): header with the current station and the ICY title, tabs:
  - Favourites: list from the last `FavouritesSync`, click = tune.
  - Browse: three columns/steps – countries (`/json/countries`, cached per session), regions
    (`/json/states/<country>`; "all regions" entry), stations (`/json/stations/search?countrycode=..&state=..&
    codec=MP3&hidebroken=true&order=clickcount&reverse=true&limit=200`) with a filter box. Only MP3 stations are
    listed; the `Browse` tab shows a small note about that.
  - URL: text box + "Play"; name = host name until ICY gives a title.
  - Star button toggles the favourite (C2S packet). Volume hint "Options → Music & Sounds → Radio".
- Radio-Browser client: pick a server via DNS lookup of `all.api.radio-browser.info` (fall back to
  `de1.api.radio-browser.info`), all requests off-thread, results delivered to the render thread.
- `AmplifierScreen`: read-only list from the amplifier's synced signal list.

### 3.4 Blocks and models

- `RadioBlock`/`AmplifierBlock` with `IntegerProperty POWER 0..3` and `FACING`. `useWithoutItem`: sneaking →
  cycle power (server side, plays a click), otherwise open the screen (client side via `BlockEntity` data).
- Art (2026-09-25): `art/blocks/build_blocks.py` (needs Pillow) writes the eight block models and the 16×16
  textures into `art/blocks/assets/worldradio/`, from where they are copied into `src/main/resources`. The radio
  is a 1940s walnut table set (arched case, cloth grille behind three bars, amber dial with a pilot lamp, two
  bakelite knobs), the amplifier a painted steel speaker (round grille in a chrome rim, carrying strap). Both
  carry the same telescopic antenna at the back right corner: three 8 px tubes, collapsed in the case at
  `power=0` and 24 px (one and a half blocks) long at `power=3`, ending two blocks above the ground. The dial
  lamp is dark at `power=0`. Blockstate `power=N` → `models/block/<block>_N.json`; items use the `power=0`
  model. Blockbench previews: `art/blocks/blockbench_{radio,amplifier}.js` (case plus one group per antenna).
- Recipes: radio = iron + redstone + note block; amplifier = iron + copper + redstone (placeholders, cheap).

### 3.5 Files

```
radio/
  build.gradle, gradle.properties, settings.gradle, gradlew*        (from bc-quarry-fluids, minus BuildCraft)
  src/main/java/worldradio/
    WorldRadio.java                    registries, packets, config
    block/RadioBlock.java, RadioBlockEntity.java, AmplifierBlock.java, AmplifierBlockEntity.java
    signal/SignalGraph.java, Signal.java, VolumeCurve.java           (no MC imports – plain javac tests)
    server/RadioNetwork.java, FavouritesData.java
    net/SetStationPacket.java, FavouritePacket.java, FavouritesSyncPacket.java
  src/client/java/worldradio/client/
    WorldRadioClient.java
    audio/StreamPool.java, StationStream.java, Mp3Decoder.java, RadioAudioStream.java, RadioSoundInstance.java,
          SourceTracker.java
    screen/RadioScreen.java, AmplifierScreen.java, StationList.java
    api/RadioBrowser.java
    mixin/SoundSourceMixin.java, SoundBufferLibraryMixin.java
  src/main/resources/ assets/worldradio/{blockstates,models,textures,lang/en_us.json,lang/de_de.json,sounds.json}
                      data/worldradio/recipe/*.json, fabric.mod.json, worldradio.mixins.json
  art/blocks/build_blocks.py, tools/MakeResources.java, StreamProbe.java, dev-screenshot.sh
  README.md, LICENSE
```

Dependencies: JLayer from Maven Central (`com.github.umjammer:jlayer:1.0.3` or `javazoom:jlayer:1.0.1`), bundled
with loom `include`. No other mod dependencies (Fabric API only).

## 4. Phases and tests

Each phase ends with a build (`build/libs/worldradio-<version>.jar`) and the listed evidence.

0. **Skeleton** – gradle files, blocks, block entities, power cycling, textures, lang files.
   Evidence: build passes; dev-client screenshot of a radio and an amplifier at power 0..3 (`tools/dev-screenshot.sh`
   after the goblin-labour pattern: dev server on 25598, `/setblock` the eight variants, screenshot at tick 100).
1. **Signal graph** – `SignalGraph`, `VolumeCurve`, `RadioNetwork`, amplifier sync, `AmplifierScreen`.
   Evidence: `tools/test-signal.sh` compiles `signal/*` with plain javac and runs `SignalGraphTest` (cases: direct,
   two hops, chain of five, cycle of three amplifiers, two radios on one amplifier → 0.5, out of range, power 0
   blocks relaying, curve values at 0/R/R+5/R+10/R+11, loudest-source pick with hysteresis); dev screenshot of
   the amplifier list with two radios.
2. **Audio** – `StreamPool`, decoder, `RadioAudioStream`, `RadioSoundInstance`, `SourceTracker`, sound category.
   Evidence: `tools/StreamProbe.java` decodes 5 s of Kiss FM and Radio Dismuke outside Minecraft and prints format
   and frame count; in the dev client `/worldradio tune <pos> <url>` (dev command) plays Kiss FM, moving away lowers
   the volume, the slider in Music & Sounds works; log lines `Radio: stream <url> connected/closed`.
3. **Radio screen** – Radio-Browser client, tabs, favourites (SavedData + packets).
   Evidence: screenshots of all three tabs; favourite survives a server restart; a second client sees the same
   favourites.
4. **Polish** – ICY titles, config, README with a usage section, de_de check, jar for Prism.

Order of risk: phase 2 first if time is short – the streaming mixin and the enum extension are the only parts
that can fail on 26.2; everything else is routine.

## 5. Open assumptions (change here if the user says otherwise)

- Ranges 32/64/128 blocks (confirmed); the 10-block fade-out tail is fixed.
- A radio with power 0 is silent but keeps its station.
- Chunk unloads simply stop the sound; nothing plays from unloaded radios.
- Same station from several sources: only the loudest source plays (confirmed).
- Mod name "World Radio", id `worldradio` (confirmed).
- No noise/static sound between stations, no "no signal" sound.
