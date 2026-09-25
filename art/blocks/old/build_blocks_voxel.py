#!/usr/bin/env python3
"""Builds the World Radio blocks: block models, textures and the Blockbench scripts that show them.

Run from the project folder:  python art/blocks/build_blocks.py

Writes  art/blocks/assets/worldradio/models/block/{radio,amplifier}_{0..3}.json
        art/blocks/assets/worldradio/textures/block/*.png
        art/blocks/blockbench_radio.js, blockbench_amplifier.js
            (risky_eval scripts: one java_block project per block, the case in one group and the four
             antenna variants in four more, so one can be shown at a time)

Radio: a 1940s table radio in walnut, an arched case, a cloth grille behind three bars, an amber dial with a
pilot lamp and two bakelite knobs. Amplifier: a painted steel speaker cabinet with a round grille in a chrome
ring, a carrying strap on top and the same pilot lamp.

Both carry the same telescopic antenna at the back right corner, three tubes of 8 px. Collapsed (power 0) it
sits in the case and only its top pokes out; pulled out it is 24 px (one and a half blocks) long, from y 8
inside the case to y 32, two blocks above the ground. Power 1 and 2 are the two steps in between.

The front is north (the blockstate turns the model by facing). Faces map their texture a texel per model
pixel with Minecraft's default UVs, so neighbouring boxes continue each other. Needs Pillow.
"""

import base64
import json
import math
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "assets", "worldradio")
MODELS = os.path.join(OUT, "models", "block")
TEXTURES = os.path.join(OUT, "textures", "block")
NS = "worldradio:block/"


# ---------------------------------------------------------------- colours

def hexrgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def shade(rgb, f, add=0.0):
    return tuple(max(0, min(255, int(round(c * f + add)))) for c in rgb[:3]) + (255,)


WALNUT = ["#2e1b0e", "#432813", "#5c3a1d", "#734b28", "#8b5f36", "#a3764a"]
CLOTH = ["#6b5126", "#93713a", "#b08a4c", "#c6a162", "#dcbb7e"]
DIAL = ["#3a2a12", "#8a6a2a", "#d8ab4e", "#efcc79", "#fbe6ad"]
BAKELITE = ["#140d08", "#241811", "#33241a", "#4a362a"]
CHROME = ["#5c646c", "#8b949c", "#b6bfc6", "#d9e0e6", "#f2f5f8"]
STEEL = ["#2b2926", "#3f3a34", "#554e45", "#6a6255", "#857c6d"]  # painted metal, warm grey
GRILLE = ["#0b0d0c", "#161a18", "#232826", "#313834"]
LAMP_OFF = "#4a2b23"
LAMP_ON = "#ffd98a"


# ---------------------------------------------------------------- textures

def new():
    return Image.new("RGBA", (16, 16))


def put(img, x, y, colour, f=1.0):
    img.putpixel((x % 16, y % 16), shade(hexrgb(colour) if isinstance(colour, str) else colour, f))


def runs(rnd, length, lo, hi):
    """A line of values that stays put for a few pixels, then steps: grain."""
    values, v = [], rnd.randint(lo, hi)
    while len(values) < length:
        values += [v] * rnd.randint(2, 5)
        v = max(lo, min(hi, v + rnd.choice([-1, 1])))
    return values[:length]


def paint_wood(seed, sheen=0.0):
    """Polished walnut: an even tone with fine grain lines along the veneer, hardly any contrast."""
    rnd = random.Random(seed)
    img = new()
    for y in range(16):
        f = 1.0 + sheen * (1.0 - abs(y - 4) / 14.0) + 0.02 * rnd.uniform(-1, 1)
        for x in range(16):
            put(img, x, y, WALNUT[3 if rnd.random() < 0.7 else 4], f)
    for _ in range(6):  # grain: thin darker lines that fade in and out
        y, x0, n = rnd.randrange(16), rnd.randrange(16), rnd.randint(5, 10)
        for k in range(n):
            if rnd.random() < 0.8:
                put(img, x0 + k, y, WALNUT[2])
    for _ in range(5):  # the sheen of the varnish
        y, x0, n = rnd.randrange(16), rnd.randrange(16), rnd.randint(2, 5)
        for k in range(n):
            put(img, x0 + k, y, WALNUT[5 if rnd.random() < 0.3 else 4])
    return img


def paint_cloth():
    """Speaker cloth: a woven gold, darker in the weave's gaps."""
    rnd = random.Random(5)
    img = new()
    for y in range(16):
        for x in range(16):
            t = 2 if (x + y) % 2 else 3
            if (x % 4 == 0 or y % 4 == 0) and rnd.random() < 0.7:
                t = 1
            if rnd.random() < 0.08:
                t += 1
            put(img, x, y, CLOTH[max(0, min(4, t))])
    return img


def paint_dial(lit):
    """The dial: an amber scale with ticks, a red pointer and the pilot lamp at the right end.
    The face uses columns 5-11 and rows 12-14.5 of this texture (see the model)."""
    img = new()
    for y in range(16):
        for x in range(16):
            put(img, x, y, DIAL[3 if 12 <= y <= 14 else 1], 1.0 - 0.04 * abs(x - 8))
    for x in range(5, 12):  # the scale: long ticks every second station mark
        put(img, x, 12, DIAL[0] if x % 2 else DIAL[1])
        put(img, x, 13, DIAL[4] if x % 2 == 0 else DIAL[2])
    put(img, 9, 12, "#b02a1e")  # the pointer
    put(img, 9, 13, "#d8402c")
    put(img, 9, 14, "#b02a1e")
    put(img, 11, 14, LAMP_ON if lit else LAMP_OFF)  # pilot lamp
    put(img, 5, 14, DIAL[0])
    return img


def paint_knob():
    """Bakelite, lit from the top left; a 2 px knob takes the top left corner."""
    img = new()
    for y in range(16):
        for x in range(16):
            put(img, x, y, BAKELITE[2 if (x % 2 + y % 2) else 3])
    for y in range(0, 16, 2):
        for x in range(0, 16, 2):
            put(img, x + 1, y + 1, BAKELITE[0])
            put(img, x, y, BAKELITE[3], 1.08)
    return img


def paint_chrome():
    """A polished tube: a bright line to the left, shadow to the right; every column is one tube pixel."""
    img = new()
    tones = [CHROME[3], CHROME[4], CHROME[2], CHROME[1]]
    for y in range(16):
        for x in range(16):
            put(img, x, y, tones[x % 4], 1.0 - 0.01 * (y % 5))
    return img


def paint_steel(seed, vents=False):
    """Painted steel: an even olive grey, a little worn along the edges."""
    rnd = random.Random(seed)
    img = new()
    for y in range(16):
        for x in range(16):
            t = 3 if rnd.random() < 0.8 else 2
            if x in (0, 15) or y in (0, 15):
                t = 1
            put(img, x, y, STEEL[t], 1.0 + 0.02 * rnd.uniform(-1, 1))
    for _ in range(10):  # scuffs where the paint is worn
        x, y = rnd.randrange(1, 15), rnd.randrange(1, 15)
        put(img, x, y, STEEL[4] if rnd.random() < 0.5 else STEEL[2])
    if vents:
        for y in (5, 7, 9):
            for x in range(3, 13):
                put(img, x, y, STEEL[1] if x % 2 else STEEL[0])
    for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:  # screws
        put(img, x, y, CHROME[2])
    return img


def paint_speaker_front(lit):
    """The amplifier's front: a round grille of dark cloth with a chrome rim, corner screws and the lamp."""
    rnd = random.Random(9)
    img = new()
    for y in range(16):
        for x in range(16):
            t = 3 if rnd.random() < 0.8 else 2
            put(img, x, y, STEEL[t], 1.0 + 0.02 * rnd.uniform(-1, 1))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 7.5)
            if d < 5.0:  # the cloth over the cone, a fine mesh
                put(img, x, y, GRILLE[1 if (x + y) % 2 else 2])
            elif d < 5.9:  # the chrome rim, lit from the top left
                put(img, x, y, CHROME[4 if x + y < 15 else 2])
    for (x, y) in [(1, 1), (14, 1), (1, 14), (14, 14)]:  # the screws that hold the front plate
        put(img, x, y, CHROME[1])
    put(img, 13, 13, LAMP_ON if lit else LAMP_OFF)  # pilot lamp
    return img


TEXTURE_PAINTERS = {
    "radio_wood": lambda: paint_wood(11),
    "radio_wood_top": lambda: paint_wood(13, sheen=0.06),
    "radio_cloth": paint_cloth,
    "radio_dial": lambda: paint_dial(False),
    "radio_dial_lit": lambda: paint_dial(True),
    "radio_knob": paint_knob,
    "amplifier_steel": lambda: paint_steel(17),
    "amplifier_steel_top": lambda: paint_steel(19, vents=True),
    "amplifier_front": lambda: paint_speaker_front(False),
    "amplifier_front_lit": lambda: paint_speaker_front(True),
    "antenna": paint_chrome,
}


# ---------------------------------------------------------------- geometry

DIRS = ("north", "south", "east", "west", "up", "down")


def default_uv(d, f, t):
    (x1, y1, z1), (x2, y2, z2) = f, t
    return {
        "north": [16 - x2, 16 - y2, 16 - x1, 16 - y1],
        "south": [x1, 16 - y2, x2, 16 - y1],
        "west": [z1, 16 - y2, z2, 16 - y1],
        "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1],
        "up": [x1, z1, x2, z2],
        "down": [x1, 16 - z2, x2, 16 - z1],
    }[d]


def fit(uv):
    """Wraps a UV rectangle into the texture (by 16, so tiling continues), else slides it in."""
    for a, b in ((0, 2), (1, 3)):
        lo, hi = uv[a], uv[b]
        while hi <= 0:
            lo, hi = lo + 16, hi + 16
        while lo >= 16:
            lo, hi = lo - 16, hi - 16
        if lo < 0:
            lo, hi = 0, hi - lo
        if hi > 16:
            lo, hi = lo - (hi - 16), 16
        uv[a], uv[b] = lo, hi
    return uv


def r(v):
    return round(v, 3)


ELEMENTS = []  # (group, element) of the model being built


def box(group, name, frm, to, faces):
    frm, to = [r(c) for c in frm], [r(c) for c in to]
    assert all(to[i] > frm[i] for i in range(3)), name
    assert all(-16 <= c <= 32 for c in frm + to), "%s is outside the model range" % name
    sides = {}
    for d in DIRS:
        spec = faces(d, frm, to)
        if spec is None:
            continue
        tex, uv = spec if isinstance(spec, tuple) else (spec, default_uv(d, frm, to))
        sides[d] = {"uv": [r(c) for c in fit(list(uv))], "texture": "#" + tex}
    ELEMENTS.append((group, {"name": name, "from": frm, "to": to, "faces": sides}))


def plain(tex, skip=()):
    """One texture with Minecraft's default UVs: neighbouring boxes continue each other."""
    return lambda d, f, t: None if d in skip else tex


def cased(side, top, skip=(), front=None):
    """A case: its own texture on top and bottom, another on the sides, and one on the front (north)."""
    def faces(d, f, t):
        if d in skip:
            return None
        if d == "north" and front:
            return front
        return top if d in ("up", "down") else side
    return faces


def patch(tex, uvs, skip=()):
    """Hand-placed UVs: direction (or "*") -> [u, v]; the face's size is added."""
    def faces(d, f, t):
        if d in skip:
            return None
        uv = default_uv(d, f, t)
        spot = uvs.get(d, uvs.get("*"))
        if spot is None:
            return tex
        w, h = abs(uv[2] - uv[0]), abs(uv[3] - uv[1])
        return tex, [spot[0], spot[1], spot[0] + w, spot[1] + h]
    return faces


# ---------------------------------------------------------------- the antenna

ANTENNA_X, ANTENNA_Z = 11.0, 10.0   # the back right corner of both cases
ANTENNA_BASE = 8.0                  # where the thickest tube starts, inside the case
TUBE = 8.0                          # length of each of the three tubes


def antenna(power):
    """Three telescopic tubes of 8 px. Collapsed they sit in each other (power 0); every step pulls the two
    upper ones out by a third of their travel, so at power 3 the antenna is 24 px long and ends at y 32."""
    g = "antenna_%d" % power
    f = power / 3.0
    x, z = ANTENNA_X, ANTENNA_Z
    tops = [ANTENNA_BASE + TUBE]
    tops.append(tops[0] + TUBE * f)
    tops.append(tops[1] + TUBE * f)
    widths = [2.0, 1.5, 1.0]
    last = max(i for i in range(3) if i == 0 or tops[i] > tops[i - 1] + 0.01)
    for i in range(last + 1):
        top, inset = tops[i], (2.0 - widths[i]) / 2
        if i == last:
            top -= 1  # the knob sits on the tip of the topmost tube
        box(g, "tube_%d" % i, [x + inset, tops[i] - TUBE, z + inset], [x + 2 - inset, top, z + 2 - inset],
            plain("antenna", skip=("down",)))
    box(g, "tip", [x + 0.25, tops[last] - 1, z + 0.25], [x + 1.75, tops[last], z + 1.75], plain("antenna"))
    # the bushing the antenna comes out of, on the case
    box("antenna_%d" % power, "mount", [x - 0.5, 13.0, z - 0.5], [x + 2.5, 14.5, z + 2.5],
        plain("antenna", skip=("down",)))


# ---------------------------------------------------------------- the radio

def radio_case(power):
    """A 1940s table radio: walnut case with an arched top, a cloth grille behind three bars, the dial and
    two knobs on the plinth below it."""
    g = "case"
    wood = lambda skip=(): cased("radio_wood", "radio_wood_top", skip)
    box(g, "plinth", [0.5, 0, 2.5], [15.5, 1.5, 13.5], wood())
    box(g, "back", [1, 1.5, 11], [15, 14, 13], wood(skip=("north",)))
    box(g, "side_w", [1, 1.5, 3], [3, 14, 11], wood())
    box(g, "side_e", [13, 1.5, 3], [15, 14, 11], wood())
    box(g, "front_low", [3, 1.5, 3], [13, 5, 11], wood(skip=("east", "west")))
    box(g, "front_high", [3, 12, 3], [13, 14, 11], wood(skip=("east", "west")))
    # the arch on top, over the front part; the back stays flat for the antenna
    box(g, "arch_1", [2.5, 14, 3], [13.5, 15, 11], wood(skip=("down",)))
    box(g, "arch_2", [4.5, 15, 3.5], [11.5, 16, 10.5], wood(skip=("down",)))
    # the grille: cloth set back in the opening, three bars in front of it
    box(g, "cloth", [3, 5, 4.5], [13, 12, 5], plain("radio_cloth", skip=("south",)))
    for i, x in enumerate((4.5, 7.5, 10.5)):
        box(g, "bar_%d" % i, [x, 5, 3.5], [x + 1, 12, 4.5], wood())
    # the dial on the plinth, its scale and lamp, and the two knobs
    dial = "radio_dial_lit" if power else "radio_dial"
    box(g, "dial", [4.5, 1.6, 2.6], [11.5, 4.6, 3], patch(dial, {"north": [4.5, 11.4]}))
    for i, x in enumerate((3.4, 11.1)):
        box(g, "knob_%d" % i, [x, 2, 2], [x + 1.5, 3.5, 3], plain("radio_knob"))


# ---------------------------------------------------------------- the amplifier

def amplifier_case(power):
    """A painted steel speaker cabinet: a round grille in a chrome rim on the front, a carrying strap on top."""
    g = "case"
    steel = lambda skip=(), front=None: cased("amplifier_steel", "amplifier_steel_top", skip, front)
    front = "amplifier_front_lit" if power else "amplifier_front"
    box(g, "case", [1, 0, 3], [15, 15, 13], steel(front=front))
    box(g, "back", [1, 0, 13], [15, 15, 14], steel())
    box(g, "foot_w", [1.5, 0, 3.5], [3.5, 0.5, 12.5], plain("amplifier_steel"))
    box(g, "foot_e", [12.5, 0, 3.5], [14.5, 0.5, 12.5], plain("amplifier_steel"))
    # the screws that hold the front plate, a little proud of it
    for i, (x, y) in enumerate([(1.5, 1.5), (13.5, 1.5), (1.5, 12.5), (13.5, 12.5)]):
        box(g, "screw_%d" % i, [x, y, 2.7], [x + 1, y + 1, 3], plain("amplifier_steel"))
    # the carrying strap over the top
    box(g, "strap_w", [4.5, 15, 6.5], [6, 16, 9.5], plain("amplifier_steel"))
    box(g, "strap_e", [10, 15, 6.5], [11.5, 16, 9.5], plain("amplifier_steel"))
    box(g, "strap_top", [4.5, 16, 7], [11.5, 16.75, 9], plain("amplifier_steel"))


# ---------------------------------------------------------------- output

BLOCKS = {"radio": radio_case, "amplifier": amplifier_case}


def build(block, power):
    ELEMENTS.clear()
    BLOCKS[block](power)
    antenna(power)
    return list(ELEMENTS)


def write_model(block, power, elements):
    used = sorted({f["texture"][1:] for _, e in elements for f in e["faces"].values()})
    textures = {"particle": NS + ("radio_wood" if block == "radio" else "amplifier_steel")}
    textures.update({k: NS + k for k in used})
    model = {"parent": "minecraft:block/block", "textures": textures,
             "elements": [e for _, e in elements]}
    with open(os.path.join(MODELS, "%s_%d.json" % (block, power)), "w", encoding="utf-8", newline="\n") as f:
        json.dump(model, f, indent=2)
        f.write("\n")
    return used


def main():
    os.makedirs(MODELS, exist_ok=True)
    os.makedirs(TEXTURES, exist_ok=True)
    for name, painter in TEXTURE_PAINTERS.items():
        painter().save(os.path.join(TEXTURES, name + ".png"))

    scripts = {}
    for block in BLOCKS:
        groups, used = {}, set()
        for power in range(4):
            elements = build(block, power)
            used.update(write_model(block, power, elements))
            for group, e in elements:
                if group == "case" and power:
                    continue  # the case is the same in every variant, keep one copy for Blockbench
                groups.setdefault(group, []).append(e)
            print("%s_%d: %d elements" % (block, power, len(elements)))
        scripts[block] = (groups, sorted(used))

    for block, (groups, used) in scripts.items():
        # embedded as data URLs: Blockbench caches images by path; URL-safe base64, because risky_eval
        # refuses code with two slashes in a row
        tex_data = {}
        for k in used:
            with open(os.path.join(TEXTURES, k + ".png"), "rb") as png:
                tex_data[k] = base64.urlsafe_b64encode(png.read()).decode()
        with open(os.path.join(HERE, "blockbench_%s.js" % block), "w", encoding="utf-8", newline="\n") as f:
            f.write(BLOCKBENCH_JS.replace("__GROUPS__", json.dumps(groups))
                    .replace("__TEXTURES__", json.dumps(tex_data)).replace("__PROJECT__", "worldradio_" + block))


BLOCKBENCH_JS = r"""
const GROUPS = __GROUPS__;
const TEXTURES = __TEXTURES__;
for (const p of ModelProject.all.filter(p => p.name === '__PROJECT__')) p.close(true);
newProject(Formats.java_block);
Project.name = '__PROJECT__';
const tex = {};
for (const [key, data] of Object.entries(TEXTURES)) {
  const url = 'data:image/png;base64,' + data.replace(/-/g, '+').replace(/_/g, '/');
  tex[key] = new Texture({name: key + '.png'}).fromDataURL(url).add(false);
}
for (const [name, elements] of Object.entries(GROUPS)) {
  const group = new Group({name, origin: [8, 8, 8]}).init();
  for (const e of elements) {
    const faces = {};
    for (const [dir, f] of Object.entries(e.faces)) {
      faces[dir] = {uv: f.uv, texture: tex[f.texture.slice(1)].uuid};
    }
    for (const dir of ['north', 'south', 'east', 'west', 'up', 'down']) {
      if (!faces[dir]) faces[dir] = {texture: null};
    }
    const cube = new Cube({name: e.name, from: e.from, to: e.to, autouv: 0, faces}).init();
    cube.addTo(group);
  }
}
Canvas.updateAll();
({cubes: Cube.all.length, groups: Group.all.map(g => g.name + ':' + g.children.length)});
"""

if __name__ == "__main__":
    main()
