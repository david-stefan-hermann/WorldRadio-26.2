# Changelog

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
