"""Generate soft dusk sky artwork with palm-frond-only silhouettes for Bare.

v0.17 deliberately has no trunks, skyline blocks, horizon bars, or repeated stem
lines.  The only dark foreground shapes are broad, filled palm fronds entering
from the left/right (with small corner crowns), leaving the centre clear for UI.
"""
from PIL import Image, ImageDraw, ImageFilter
import math
import random
from pathlib import Path

random.seed(17)
OUT = Path(__file__).resolve().parents[1] / "app/src/main/res/drawable-nodpi"
OUT.mkdir(parents=True, exist_ok=True)


def lerp(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3))


def gradient(w, h, stops):
    im = Image.new("RGB", (w, h))
    px = im.load()
    for y in range(h):
        t = y / max(1, h - 1)
        for j in range(len(stops) - 1):
            if stops[j][0] <= t <= stops[j + 1][0]:
                a, c = stops[j]
                b, d = stops[j + 1]
                col = lerp(c, d, (t - a) / (b - a))
                break
        else:
            col = stops[-1][1]
        for x in range(w):
            px[x, y] = col
    return im


def glow(im, xy, radius, color, alpha=120, blur=80):
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    x, y = xy
    d.ellipse((x - radius, y - radius, x + radius, y + radius), fill=(*color, alpha))
    return Image.alpha_composite(im.convert("RGBA"), layer.filter(ImageFilter.GaussianBlur(blur)))


def curve_points(p0, p1, p2, p3, n=28):
    points = []
    for i in range(n + 1):
        t = i / n
        u = 1 - t
        points.append((
            u**3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t**3 * p3[0],
            u**3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t**3 * p3[1],
        ))
    return points


def tapered_ribbon(d, points, widths, fill):
    """Draw a filled, tapered blade; never a one-pixel line or bar."""
    left, right = [], []
    for i, (x, y) in enumerate(points):
        if i == 0:
            dx, dy = points[1][0] - x, points[1][1] - y
        elif i == len(points) - 1:
            dx, dy = x - points[i - 1][0], y - points[i - 1][1]
        else:
            dx, dy = points[i + 1][0] - points[i - 1][0], points[i + 1][1] - points[i - 1][1]
        length = max(1.0, math.hypot(dx, dy))
        nx, ny = -dy / length, dx / length
        width = widths[i]
        left.append((x + nx * width, y + ny * width))
        right.append((x - nx * width, y - ny * width))
    d.polygon(left + right[::-1], fill=fill)


def leaflet(d, base, tip, width, fill):
    """A broad pointed leaflet, giving each frond a palm silhouette."""
    bx, by = base
    tx, ty = tip
    dx, dy = tx - bx, ty - by
    length = max(1.0, math.hypot(dx, dy))
    nx, ny = -dy / length, dx / length
    # A curved-looking lance: widest close to its base, pointed at its tip.
    shoulder = (bx + dx * 0.34, by + dy * 0.34)
    d.polygon([
        (bx, by),
        (shoulder[0] + nx * width, shoulder[1] + ny * width),
        (tx, ty),
        (shoulder[0] - nx * width * 0.72, shoulder[1] - ny * width * 0.72),
    ], fill=fill)


def frond(layer, origin, angle, length, width, fill):
    """One curved filled frond with paired leaflets; no trunk or stem pattern."""
    d = ImageDraw.Draw(layer)
    ox, oy = origin
    a = math.radians(angle)
    ux, uy = math.cos(a), math.sin(a)
    # Gentle droop makes the fans read as palm leaves rather than rays.
    end = (ox + ux * length, oy + uy * length)
    bend = length * 0.18
    points = curve_points(
        origin,
        (ox + ux * length * 0.30 - uy * bend, oy + uy * length * 0.30 + ux * bend),
        (ox + ux * length * 0.70 + uy * bend * 0.45, oy + uy * length * 0.70 - ux * bend * 0.45),
        end,
        30,
    )
    widths = [width * (0.95 - 0.78 * i / (len(points) - 1)) for i in range(len(points))]
    tapered_ribbon(d, points, widths, fill)

    # Pairs of substantial leaflets alternate along the blade. Their angle is
    # deliberately oblique, so nothing forms a row of vertical bars.
    nx, ny = -uy, ux
    for i in range(1, 8):
        q = i / 8
        idx = int(q * (len(points) - 1))
        bx, by = points[idx]
        cur_len = length * (0.20 - q * 0.065)
        leaflet_w = max(2.0, width * (0.58 - q * 0.25))
        for side in (-1, 1):
            # Point backwards slightly and out from the blade, like a palm pinna.
            tip = (
                bx - ux * cur_len * 0.42 + nx * side * cur_len,
                by - uy * cur_len * 0.42 + ny * side * cur_len,
            )
            leaflet(d, (bx, by), tip, leaflet_w, fill)


def frond_crown(layer, origin, length, scale, mirror=1, color=(3, 4, 16, 245), small=False):
    """Fan of fronds rooted at an off-canvas corner, with the centre kept open."""
    # Angles are fan directions, not trunks: the base is the only shared point.
    angles = [-154, -132, -110, -88, -66, -45, -25, -6, 14]
    if mirror < 0:
        angles = [180 - a for a in angles]
    if small:
        angles = [-150, -120, -92, -64, -35, -8] if mirror > 0 else [150, 120, 92, 64, 35, 8]
    for i, angle in enumerate(angles):
        variation = 0.84 + (i % 4) * 0.055
        frond(
            layer,
            origin,
            angle,
            length * scale * variation,
            max(4.0, length * scale * (0.036 if not small else 0.030)),
            color,
        )


def stars(layer, w, h, count):
    d = ImageDraw.Draw(layer)
    for _ in range(count):
        x = random.randint(10, w - 11)
        y = random.randint(8, int(h * 0.52))
        r = random.choice([1, 1, 1, 2])
        d.ellipse((x - r, y - r, x + r, y + r), fill=(255, 205, 232, random.randint(65, 145)))


def make(name, w, h, kind):
    if kind == "header":
        stops = [
            (0.0, (25, 37, 96)),
            (0.30, (57, 57, 130)),
            (0.58, (106, 73, 139)),
            (0.82, (166, 91, 126)),
            (1.0, (222, 137, 101)),
        ]
        im = gradient(w, h, stops).convert("RGBA")
        im = glow(im, (w * 0.20, h * 0.34), w * 0.38, (75, 105, 225), 125, 120)
        im = glow(im, (w * 0.77, h * 0.60), w * 0.30, (227, 76, 135), 70, 110)
        im = glow(im, (w * 0.52, h * 0.91), w * 0.30, (255, 157, 105), 125, 80)
        count = 30
        main_len, corner_len = 390, 235
    else:
        stops = [
            (0.0, (17, 22, 67)),
            (0.30, (42, 29, 91)),
            (0.58, (89, 35, 107)),
            (0.82, (157, 48, 103)),
            (1.0, (209, 86, 78)),
        ]
        im = gradient(w, h, stops).convert("RGBA")
        im = glow(im, (w * 0.20, h * 0.35), w * 0.36, (60, 78, 190), 115, 120)
        im = glow(im, (w * 0.77, h * 0.62), w * 0.32, (245, 48, 143), 95, 110)
        im = glow(im, (w * 0.52, h * 0.92), w * 0.30, (255, 115, 76), 125, 80)
        count = 15
        main_len, corner_len = 270, 165

    overlay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    stars(overlay, w, h, count)

    # No horizon, skyline, trunks, or repeated bars: only edge/corner frond fans.
    if kind == "header":
        frond_crown(overlay, (-18, h * 0.82), main_len, 1.0, 1)
        frond_crown(overlay, (w + 18, h * 0.82), main_len * 0.96, 1.0, -1)
        frond_crown(overlay, (w * 0.04, h * 1.03), corner_len, 1.0, 1, color=(4, 4, 18, 185), small=True)
        frond_crown(overlay, (w * 0.96, h * 1.03), corner_len * 0.96, 1.0, -1, color=(4, 4, 18, 185), small=True)
    else:
        frond_crown(overlay, (-16, h * 0.88), main_len, 1.0, 1)
        frond_crown(overlay, (w + 16, h * 0.88), main_len * 0.98, 1.0, -1)
        frond_crown(overlay, (w * 0.06, h * 1.08), corner_len, 1.0, 1, color=(4, 4, 18, 175), small=True)
        frond_crown(overlay, (w * 0.94, h * 1.08), corner_len * 0.96, 1.0, -1, color=(4, 4, 18, 175), small=True)

    im = Image.alpha_composite(im, overlay)

    # Keep the centre calm for chrome text while preserving the edge silhouettes.
    scrub = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sd = ImageDraw.Draw(scrub)
    for x in range(w):
        edge = min(x, w - 1 - x) / (w * 0.22)
        alpha = int(14 + 24 * max(0.0, min(1.0, edge)))
        sd.line((x, 0, x, h), fill=(8, 6, 22, alpha))
    im = Image.alpha_composite(im, scrub)

    fade = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    fd = ImageDraw.Draw(fade)
    for y in range(h):
        edge = min(y / (h * 0.12), (h - 1 - y) / (h * 0.14), 1.0)
        fd.line((0, y, w, y), fill=(4, 4, 16, int(32 * (1 - edge))))
    im = Image.alpha_composite(im, fade)

    im.convert("RGB").save(OUT / name, optimize=True, quality=94)
    print("wrote", OUT / name, im.size)


make("header_palms.png", 1600, 480, "header")
make("dock_palms.png", 1600, 320, "dock")
