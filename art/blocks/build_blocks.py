#!/usr/bin/env python3
"""Builds the World Radio blocks: 16x16 textures and the block models.

Run from the project folder:  python art/blocks/build_blocks.py

Writes  art/blocks/assets/worldradio/textures/block/*.png (+ .mcmeta for the animated fronts)
        art/blocks/assets/worldradio/models/block/{radio,amplifier}_{0..2}.json
from where they are copied into src/main/resources.

Both blocks are plain full cubes in the vanilla style of the work blocks (jukebox, furnace, loom): the shape
is a cube, everything else is painted, with few tones, a dark frame around every face and a regular two tone
weave instead of noise.

The fronts keep the layout of the first draft: a case around a dark panel, the speaker cloth on the left and
three signal bars on the right. `power` says how many bars are lit - 0 means the set is off, 1 and 2 are the
ranges (strength 3 was dropped in round 2, so the third bar stays dark). While it plays, the fronts are animated: the topmost lit bar rides up and down like a meter needle and
the cloth breathes a little. The old voxel models are in art/blocks/old. Needs Pillow.
"""

import json
import math
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "assets", "worldradio")
MODELS = os.path.join(OUT, "models", "block")
TEXTURES = os.path.join(OUT, "textures", "block")
NS = "worldradio:block/"

# the first draft's colours, toned down towards vanilla's jukebox wood and furnace greys
WOOD = ["#3a2718", "#4e3421", "#6b4a2e", "#7d5836", "#94663f"]
STEEL = ["#33383d", "#474d53", "#5f666d", "#767d85", "#8f969d"]
PANEL = ["#151413", "#1e1d1c", "#272524"]          # the dark board the cloth and the bars sit on
CLOTH = ["#2a2119", "#3a2e21", "#4a3b2a", "#5c4a34"]  # speaker cloth, the same on both blocks
GREEN = ["#26482a", "#3f7a37", "#5faa45", "#8bd45f"]  # the radio's bars
BLUE = ["#1f3350", "#2f5b8a", "#4a86c0", "#79b6e6"]   # the amplifier's bars
OFF = "#2f2e2c"
FRAMES = 6   # animation frames of a playing set
BARS = [(8, 3), (10, 6), (12, 9)]   # x and height of the three bars, bottom at y 12, shadow to the right


def hexrgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def new(fill, height=16):
    return Image.new("RGBA", (16, height), hexrgb(fill) + (255,))


def put(img, x, y, colour):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), hexrgb(colour) + (255,))


def rect(img, x0, y0, x1, y1, colour):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(img, x, y, colour)


def weave(img, x0, y0, x1, y1, tones, off=0):
    """Vanilla's way of filling a surface: a regular two tone weave, not noise."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(img, x, y, tones[(x + y + off) % 2])


def frame(img, colour, y0=0, y1=15):
    """The dark border every vanilla block face has."""
    for x in range(16):
        put(img, x, y0, colour)
        put(img, x, y1, colour)
    for y in range(y0, y1 + 1):
        put(img, 0, y, colour)
        put(img, 15, y, colour)


# ---------------------------------------------------------------- the case

def case(tones, vents=False, feet=False, seams=(5, 10)):
    """A side, top or bottom of either block: the material in a dark frame, with a few pressed lines."""
    img = new(tones[2])
    weave(img, 1, 1, 14, 14, [tones[2], tones[3]])
    for y in seams:
        rect(img, 1, y, 14, y, tones[1])
    frame(img, tones[0])
    if vents:
        for y in (4, 6, 8):
            rect(img, 4, y, 11, y, tones[1])
            rect(img, 4, y + 1, 11, y + 1, tones[4])
    if feet:
        for (x, y) in [(2, 2), (12, 2), (2, 12), (12, 12)]:
            rect(img, x, y, x + 1, y + 1, tones[0])
    return img


def front(tones, bars, power, frame_no):
    """The front of a set: the case's frame, a dark panel, the cloth on the left and the three bars on the
    right. `power` lit bars; while it plays the topmost one wavers and the cloth breathes."""
    img = new(tones[2])
    beat = math.sin(2 * math.pi * frame_no / FRAMES)
    # the case: a two pixel wide frame around the panel, lighter at the top, darker at the bottom
    weave(img, 0, 0, 15, 15, [tones[2], tones[3]])
    rect(img, 0, 0, 15, 0, tones[3])
    rect(img, 0, 14, 15, 15, tones[1])
    frame(img, tones[0])
    # the panel the cloth and the bars sit on
    rect(img, 2, 2, 13, 13, PANEL[2])
    weave(img, 3, 3, 12, 12, [PANEL[1], PANEL[0]])
    # the speaker cloth on the left, a little brighter while the set plays
    step = 1 if power and beat > 0.4 else 0
    weave(img, 3, 3, 7, 12, [CLOTH[1 + step], CLOTH[2 + step]])
    # the signal bars: the lit ones flicker a little, the topmost rides up and down like a meter needle
    for i, (x, height) in enumerate(BARS):
        lit = i < power
        if lit and i == power - 1:
            height = max(2, height + round(beat))
        top = 12 - height + 1
        for y in range(top, 13):
            if not lit:
                colour = OFF
            elif y == top:
                colour = bars[3]
            else:
                colour = bars[2] if (y + frame_no) % 3 else bars[1]
            put(img, x, y, colour)
            if lit:
                put(img, x + 1, y, bars[0])
    return img


def animated(tones, bars, power):
    """One tall image with FRAMES frames for a playing set, a single frame when it is off."""
    if power == 0:
        return front(tones, bars, 0, 0), None
    sheet = new(PANEL[1], 16 * FRAMES)
    for f in range(FRAMES):
        sheet.paste(front(tones, bars, power, f), (0, 16 * f))
    return sheet, {"animation": {"frametime": 3}}


# ---------------------------------------------------------------- output

BLOCKS = {"radio": (WOOD, GREEN), "amplifier": (STEEL, BLUE)}


def main():
    os.makedirs(MODELS, exist_ok=True)
    os.makedirs(TEXTURES, exist_ok=True)
    for old in os.listdir(TEXTURES):
        os.remove(os.path.join(TEXTURES, old))

    for block, (tones, bars) in BLOCKS.items():
        case(tones).save(os.path.join(TEXTURES, block + "_side.png"))
        case(tones, vents=True).save(os.path.join(TEXTURES, block + "_top.png"))
        case([tones[0], tones[0], tones[1], tones[2], tones[3]], feet=True).save(
            os.path.join(TEXTURES, block + "_bottom.png"))
        for power in range(3):
            img, meta = animated(tones, bars, power)
            name = "%s_front_%d" % (block, power)
            img.save(os.path.join(TEXTURES, name + ".png"))
            if meta:
                with open(os.path.join(TEXTURES, name + ".png.mcmeta"), "w", encoding="utf-8",
                          newline="\n") as f:
                    json.dump(meta, f, indent=2)
                    f.write("\n")
            model = {
                "parent": "minecraft:block/orientable_with_bottom",
                "textures": {"front": NS + name, "side": NS + block + "_side",
                             "top": NS + block + "_top", "bottom": NS + block + "_bottom"},
            }
            with open(os.path.join(MODELS, "%s_%d.json" % (block, power)), "w", encoding="utf-8",
                      newline="\n") as f:
                json.dump(model, f, indent=2)
                f.write("\n")
    print("textures and models written (fronts 1-2 animated, %d frames)" % FRAMES)


if __name__ == "__main__":
    main()
