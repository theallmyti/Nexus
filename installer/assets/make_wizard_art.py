"""Pixel-art images for the Inno Setup wizard, in the Nexus app's colours.

wizard-image-*.bmp : tall side image on the Welcome / Finish pages (164x314 at 100 %)
wizard-small-*.bmp : square logo in the top banner of the other pages (55x55 at 100 %)
Both are drawn on a small pixel grid and scaled up with nearest-neighbour, like the app's art.
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

repo = Path(sys.argv[1])
out = repo / "installer" / "assets"
out.mkdir(exist_ok=True)
font_path = repo / "android/app/src/main/res/font/press_start_2p.ttf"
logo_path = repo / "android/app/src/main/res/drawable/app_logo.png"

NAVY = (15, 27, 46)
CREAM = (255, 248, 231)
YELLOW = (250, 204, 21)


def scene(w, h):
    """The splash landscape on a w x h pixel grid (1 cell = 1 art pixel)."""
    im = Image.new("RGB", (w, h), NAVY)
    d = ImageDraw.Draw(im)
    # sky gradient
    top, bottom = (79, 195, 247), (129, 212, 250)
    sky_h = int(h * 0.46)
    for y in range(sky_h):
        t = y / sky_h
        d.line([(0, y), (w, y)], fill=tuple(int(a + (b - a) * t) for a, b in zip(top, bottom)))
    # sun and clouds
    d.rectangle([w * 0.80, h * 0.185, w * 0.80 + 4, h * 0.185 + 4], fill=(255, 235, 59))
    d.rectangle([3, h * 0.13, 15, h * 0.13 + 2], fill="white")
    d.rectangle([5, h * 0.13 - 2, 12, h * 0.13], fill="white")
    # mountains
    base = h * 0.50
    d.polygon([(0, base), (w * 0.25, h * 0.30), (w * 0.5, base)], fill=(30, 136, 229))
    d.polygon([(w * 0.3, base), (w * 0.62, h * 0.24), (w, base)], fill=(30, 136, 229))
    d.rectangle([w * 0.60, h * 0.24, w * 0.60 + 3, h * 0.24 + 1], fill="white")
    # forest
    d.rectangle([0, h * 0.47, w, h * 0.56], fill=(46, 125, 50))
    for x in range(0, w, 3):
        d.rectangle([x, h * 0.45, x + 1, h * 0.47], fill=(27, 94, 32))
    # lake + cabin
    d.rectangle([0, h * 0.55, w, h * 0.64], fill=(2, 136, 209))
    d.rectangle([3, h * 0.57, w - 4, h * 0.585], fill=(41, 182, 246))
    d.rectangle([w * 0.66, h * 0.525, w * 0.92, h * 0.545], fill=(141, 110, 99))
    d.rectangle([w * 0.69, h * 0.545, w * 0.89, h * 0.575], fill=(93, 64, 55))
    d.rectangle([w * 0.75, h * 0.55, w * 0.79, h * 0.565], fill=(255, 241, 118))
    # meadow + path
    d.rectangle([0, h * 0.63, w, h], fill=(76, 175, 80))
    d.rectangle([0, h * 0.63, w, h * 0.65], fill=(56, 142, 60))
    d.rectangle([0, h * 0.80, w * 0.62, h], fill=(141, 110, 99))
    d.rectangle([0, h * 0.82, w * 0.57, h * 0.97], fill=(161, 136, 127))
    for i, c in enumerate([(229, 57, 53), (255, 235, 59), (255, 255, 255), (171, 71, 188)] * 3):
        d.point([(2 + i * 3) % w, int(h * 0.69 + (i % 4) * 3)], fill=c)
    return im, d


def adventurer(d, ox, oy):
    """The hat-wearing adventurer, waving (16x16 grid like the app)."""
    def r(c, x, y, w, h):
        d.rectangle([ox + x, oy + y, ox + x + w - 1, oy + y + h - 1], fill=c)
    r((249, 168, 37), 5, 1, 6, 3); r((251, 192, 45), 3, 4, 10, 1); r((198, 40, 40), 5, 3, 6, 1)
    r((255, 204, 128), 5, 5, 6, 4); r((33, 33, 33), 8, 6, 1, 1)
    r((109, 76, 65), 2, 8, 3, 5); r((30, 136, 229), 4, 9, 7, 4)
    r((21, 101, 192), 11, 6, 1, 4); r((255, 204, 128), 11, 4, 2, 2)  # raised arm + hand (wave)
    r((78, 52, 46), 5, 13, 2, 3); r((78, 52, 46), 8, 13, 2, 3)
    r((33, 33, 33), 5, 15, 3, 1); r((33, 33, 33), 8, 15, 3, 1)


def cat(d, ox, oy):
    def r(c, x, y, w, h):
        d.rectangle([ox + x, oy + y, ox + x + w - 1, oy + y + h - 1], fill=c)
    o, k = (251, 140, 0), (230, 81, 0)
    r(k, 4, 2, 1, 2); r(k, 7, 2, 1, 2); r(o, 3, 3, 6, 4); r((33, 33, 33), 7, 4, 1, 1)
    r(o, 3, 7, 6, 4); r((255, 255, 255), 6, 7, 2, 3); r(k, 1, 5, 2, 5)


def sign(img, scale, text="NEXUS"):
    """Wooden sign with the pixel-font title, drawn at final resolution so the text stays crisp."""
    d = ImageDraw.Draw(img)
    d.fontmode = "1"  # no anti-aliasing: hard pixel edges
    size = 8 * scale
    font = ImageFont.truetype(str(font_path), size)
    tw = d.textlength(text, font=font)
    pad = 4 * scale
    w, h = tw + pad * 2, size + pad * 2
    x0 = (img.width - w) / 2
    y0 = 22 * scale
    d.rectangle([x0, y0, x0 + w, y0 + h], fill=(212, 162, 92), outline=(93, 64, 55), width=max(1, scale))
    d.text((x0 + pad, y0 + pad), text, font=font, fill=(62, 39, 35))
    small = ImageFont.truetype(str(font_path), 4 * scale)
    for i, line in enumerate(["Your devices.", "Always in sight."]):
        lw = d.textlength(line, font=small)
        d.text(((img.width - lw) / 2, y0 + h + (5 + i * 7) * scale), line, font=small, fill=NAVY)


def wizard_image(scale):
    w, h = 41, 79  # art grid; x4 = 164 x 316 at 100 %
    im, d = scene(w, h)
    adventurer(d, 4, int(h * 0.78))
    cat(d, 20, int(h * 0.83))
    px = 4 * scale
    big = im.resize((w * px, h * px), Image.NEAREST)
    big = big.crop((0, 0, 164 * scale, 314 * scale))
    sign(big, scale)
    return big


def small_image(scale):
    side = 55 * scale
    bg = Image.new("RGB", (side, side), NAVY)
    logo = Image.open(logo_path).convert("RGBA")
    inner = int(side * 0.86)
    logo = logo.resize((inner, inner), Image.LANCZOS)
    bg.paste(logo, ((side - inner) // 2, (side - inner) // 2), logo)
    return bg


for pct, scale in [(100, 1), (150, 1.5), (200, 2)]:
    s = int(scale) if scale == int(scale) else scale
    if isinstance(s, float):
        img = wizard_image(2).resize((246, 471), Image.NEAREST)
        sm = small_image(2).resize((83, 83), Image.LANCZOS)
    else:
        img, sm = wizard_image(s), small_image(s)
    img.convert("RGB").save(out / f"wizard-image-{pct}.bmp")
    sm.convert("RGB").save(out / f"wizard-small-{pct}.bmp")
    print(pct, img.size, sm.size)
