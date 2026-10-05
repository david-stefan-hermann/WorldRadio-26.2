# Changelog

## 2.1.0
- New **World Radio Guide**: a guide book with a chapter for every block (what it does, its recipe, how to use it) and for range and antennas, finding stations and listening, with build pictures. English and German.
- The **Portable Radio** is a small 3D transistor radio now, in the inventory and in the hand too, and it can be **put down**: sneak + right-click on a block. There it plays to everyone within 10 blocks like a Radio (same tuner, speakers add to it; new config entry `portableHearing`) and while it plays its lights are green and its antenna is pulled out; a bare hand breaks it at once to take it along again. Station, volume and on/off travel with it both ways. Sneak + right-click into the air still switches it.
- Every player gets one guide the first time they join. A new one is crafted from a book and a copper ingot; it is also the first item of the creative tab.

## 2.0.0
Radio now works like real radio: transmitters send a signal, and sound comes from the radios that receive it.

**Changed for existing worlds:** the Radio block of 1.x is now the **Radio Transmitter** (same block, new look). It keeps its station, antenna and range, but neither it nor the amplifiers make a sound any more. Place a Radio or carry a Portable Radio to listen.

- New **Radio** block: its tuner lists the stations whose signal reaches it; pick one and it plays to everyone within its hearing range, with its own volume. It took over the wooden look of the old radio.
- New **Speaker** block: a radio is heard for 4 blocks; every speaker placed against it, or against a speaker that is already connected, adds 4 more (up to 64).
- New **Radio Channel** block: tune it like a transmitter and put it against one (or against another channel of it, chains work), and that transmitter sends its station too, over the same antenna. This is how one mast sends several stations.
- New **Portable Radio** item: plays the station you pick to you alone while it is anywhere in your inventory, as long as that station's signal reaches you. Right-click opens the tuner, sneak + right-click switches it.
- Where several signals overlap, every radio and every player chooses which station to hear.
- Signal range is unchanged (32 blocks plus 32 per antenna block); reception is full inside the range and fades out just past it.
- Amplifiers pass on every signal at full strength; the 50 % sharing rule is gone.
- The transmitter's screen checks the station silently while it is open and shows the song title or why the stream cannot be played.
- The transmitter's volume slider is gone (volume belongs to each radio). New config entries `hearingBase`, `speakerStep` and `hearingMax`.
- New looks: the transmitter is a dark steel case with a mast, the amplifier got a mast too, and their screens (and the channel's) are coloured like the blocks; the radios keep the teak.
- New recipes for the Radio Transmitter, the Radio Channel, the Speaker and the Portable Radio; the Radio keeps the old radio recipe.

## 1.0.1
- Fixed sneak + right-click not switching the radio on or off while the offhand holds something (a shield or totem, for example): Minecraft skips block interaction while sneaking as soon as either hand is occupied, so the switch is now taken before that check. It needs an empty main hand only.

## 1.0.0
First release for Minecraft 26.2 (Fabric).
- **Radio** block: plays an MP3 internet stream to everyone nearby. Pick a station from the Radio-Browser catalogue (country → region, or search by name and genre), or paste any stream address (`.m3u`/`.pls` playlists work too).
- **Radio Amplifier** block: re-sends every radio signal that reaches it, over any number of hops; two or more signals share an amplifier at half volume each. Its screen lists the incoming signals with distance and hops.
- Range 32 blocks, plus 32 for every antenna block stacked on top (end rods, iron bars, iron chains, lightning rods, copper bars and copper chains in every oxidation and waxed state).
- Your own favorites list that comes with you into every world, with renaming.
- A volume slider per radio, an on/off switch (button or sneak + right-click) that keeps the station, and a *Radio* slider in Options → Music & Sounds.
- The sound is 70 % at the listener and 30 % panned towards the radio; only the loudest source of a station plays, so nothing echoes.
- Own creative tab, crafting recipes, English and German.
