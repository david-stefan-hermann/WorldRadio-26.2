# World Radio – round 3: antennas instead of signal strength

Read `HANDOFF-PLAN.md` (architecture, tools) and `../CLAUDE.md` (Gradle line, reporting rules) first. This round
replaces the manual signal strength with antenna columns. It supersedes points 5 and 6 of `HANDOFF-ROUND2.md`
(click swap, dropping strength 3): if those are already implemented, undo the click swap as described here; if not,
skip them. Everything else from round 2 stays. Version `0.3.0+26.2`. Confirmed by the user on 2026-09-25.

## What changes for the player

- A radio and an amplifier always have a base range of **32 blocks**. There is no on/off and no strength to cycle.
- Stacking **antenna blocks** directly on top of the block extends the range, one step per block, without limit
  (config cap). Antenna blocks: end rods, iron bars, iron chains, lightning rods, copper bars, copper chains – for
  everything copper all oxidation stages and the waxed variants count.
- **Right-click opens the screen** (radio: station screen, amplifier: signal list), with or without an item in the
  hand. Sneak + right-click does nothing special any more.
- The model still shows the strength: `level=0` no station (radio) / no signal (amplifier), `level=1` base range,
  `level=2` one to three antenna blocks, `level=3` four or more. The four textures from round 1 stay in use.
- The screens show the range: "Range 56 blocks (3 antenna blocks)".

## Antenna rules (server side, `worldradio.signal.Antenna`, no Minecraft classes where possible)

- The column is scanned straight up from the block above the radio/amplifier; it ends at the first block that is not
  an antenna block. Mixed materials are fine. `count` = number of antenna blocks in that column.
- `range = baseRange + count * antennaStep`, capped at `baseRange + maxAntenna * antennaStep`.
  Defaults in `Config.Values`: `baseRange = 32`, `antennaStep = 8`, `maxAntenna = 32` (→ 288 blocks max).
  Remove `ranges` from the config; a round-1/2 config file with a `ranges` list must still load (Gson ignores it).
- Antenna blocks are the block tag `worldradio:antenna` (`data/worldradio/tags/block/antenna.json`) so packs can add
  more. Fill it with the vanilla ids verified against the 26.2 client jar
  (`unzip -l ~/.gradle/caches/fabric-loom/26.2/minecraft-client.jar | grep blockstates | grep -E "bars|chain|rod"`):
  `end_rod`, `iron_bars`, `iron_chain`, `lightning_rod`, `exposed_lightning_rod`, `weathered_lightning_rod`,
  `oxidized_lightning_rod`, `waxed_lightning_rod`, `waxed_exposed_lightning_rod`, `waxed_weathered_lightning_rod`,
  `waxed_oxidized_lightning_rod`, `copper_bars`, `exposed_copper_bars`, `weathered_copper_bars`,
  `oxidized_copper_bars`, `waxed_copper_bars`, `waxed_exposed_copper_bars`, `waxed_weathered_copper_bars`,
  `waxed_oxidized_copper_bars`, `copper_chain`, `exposed_copper_chain`, `weathered_copper_chain`,
  `oxidized_copper_chain`, `waxed_copper_chain`, `waxed_exposed_copper_chain`, `waxed_weathered_copper_chain`,
  `waxed_oxidized_copper_chain` (27 ids; `chain_command_block` is not one). Orientation does not matter: a chain or
  bar block counts whatever way it faces.
- Copper oxidising in place changes the block id but not the tag membership, so the range does not change.

## Detecting antenna changes

Antenna blocks are vanilla, so there is no hook on their placement, and the column may be 30 blocks tall (block
updates only reach direct neighbours). Do both:

- `RadioNetwork.tick()` rescans the antenna column of every tracked radio/amplifier once every 20 ticks (a few
  `getBlockState` calls per source, cheap) and marks the network dirty when a count changed. Unloaded chunks above
  the block: stop the scan there (count what is loaded).
- `SignalBlock.neighborChanged` (and `onPlace`) marks the network dirty immediately so the first antenna block placed
  or removed is instant. The one-second rescan covers the rest (higher blocks, pistons, oxidation).
- `RadioNetwork.recompute()` writes the new range into the block entities (`setRange`, already synced to the
  clients) and sets the `LEVEL` blockstate as described above (only when it changes, `Block.UPDATE_CLIENTS`).

## Code changes

- `SignalBlock`: remove `POWER`; add `IntegerProperty LEVEL 0..3` (display only, the server sets it). Keep `FACING`.
  `useWithoutItem`: open the screen (client) / `onOpened` (server), no sneak check. Also override `useItemOn` to
  return `InteractionResult.TRY_WITH_EMPTY_HAND`-equivalent behaviour so right-click with an item in hand opens
  the screen too (check the 26.2 signature: `useItemOn(ItemStack, BlockState, Level, BlockPos, Player,
  InteractionHand, BlockHitResult)` returning `InteractionResult`). Remove `power()` and its callers
  (`RadioNetwork`, `SourceTracker`, `RadioScreen.status`, `AmplifierScreen`, `RadioCommand status` output – print
  `range=… antenna=…` instead of `power=…`).
- `RadioBlockEntity` / `AmplifierBlockEntity`: add `antenna` (int) next to `range`, saved and synced like `range`.
- `SourceTracker`: a radio plays when it has a URL (no power check); an amplifier relays when it has signals.
- `RadioNetwork.recompute()`: range from `Antenna.range(count, config)` instead of `Config.range(power)`; every
  amplifier is active (no power 0).
- Models/blockstates: `tools/MakeResources.java` writes `level=0..3` instead of `power=0..3` (same textures);
  regenerate, delete stale files. Old worlds with `power=` in a chunk fall back to the default state – mention in
  the README.
- Screens: `RadioScreen` status line, when a station is set and the stream plays, keeps the title; add a second
  soft line or extend the header with `worldradio.range` = "Range %s blocks (%s antenna blocks)". `AmplifierScreen`
  shows the same line under its header. Remove the `*.off` status texts and every "sneak + right-click" wording.
- Lang (en_us, de_de): drop `worldradio.power.*`, `worldradio.status.off`, `worldradio.amplifier.off`; add
  `worldradio.range`, `worldradio.range.hint` = "Stack end rods, bars, chains or lightning rods on top to extend the
  range (+%s blocks each)." shown in the radio screen where the volume hint sits when there is room, else as the
  star's neighbour tooltip.
- Recipes: unchanged.

## Tests and evidence

- `tools/SignalGraphTest.java`: `Antenna.range` for count 0/1/3/40 with defaults (32/40/56/288 – the cap), and the
  level mapping (0 antenna → level 1 with a station, 3 → 2, 4 → 3, no station → 0). Keep `tools/test-signal.sh`
  green; report the count.
- `tools/dev-test-network.sh`: replace the `power=` checks; add scenes: radio with 3 iron bars above → `range=56`
  and an amplifier at 50 blocks receives the signal; break one bar (`setblock … air`) → after ≤ 25 ticks
  `range=48` and the amplifier at 50 loses it; a mixed column (lightning rod, waxed oxidized copper chain,
  end rod, exposed copper bars) → `range=64`; a stone block in the column stops the count; 40 rods → `range=288`.
  `/worldradio status` must print `antenna=<count>`.
- Dev screenshots (`tools/dev-screenshot.sh`, scenes in `tools/scenes/`): `docs/blocks.png` – radios with 0, 2 and 5
  antenna blocks of different kinds side by side showing levels 1/2/3 and an untuned one at level 0;
  `docs/amplifier-screen.png` and `docs/favourites.png` with the new range line.
- README: usage table (right-click opens, antennas extend), the antenna block list, the range formula, the note about
  old `power=` blocks.

## Reporting

Per item: done / evidence / deviations. Name the jar path (`build/libs/worldradio-0.3.0+26.2.jar`); the user tests
in Prism. Do not commit or create a repo. Update the status line of `HANDOFF-PLAN.md` and its section 1.
