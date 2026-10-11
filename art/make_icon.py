"""Draws the Dungeon Train Mod Verification icon: a shield with a check mark.

Pixel art on a 32x32 grid, scaled to 512x512 with a pure-Python PNG writer (no Pillow needed).
Run: python3 art/make_icon.py  ->  art/icon.png and src/main/resources/dtmodverify_logo.png
"""
import os
import struct
import zlib

N = 32
SCALE = 16
CORNER_RADIUS = 72  # px at 512: rounded corners, transparent outside, anti-aliased edge

BG_TOP = (36, 40, 56)
BG_LOW = (22, 24, 34)
SHIELD = (72, 132, 212)
SHIELD_DARK = (48, 96, 168)
SHIELD_LIGHT = (108, 166, 236)
RIM = (230, 236, 248)
CHECK = (246, 250, 255)
CHECK_SHADOW = (30, 70, 130)


def shield_half_width(y):
    """Half-width of the shield at row y (rows 3..28): straight shoulders, tapering to a point."""
    if y < 3 or y > 28:
        return -1
    if y <= 14:
        return 11
    t = (y - 14) / 14.0
    return int(round(11 * (1 - t * t)))


def draw():
    img = [[None] * N for _ in range(N)]
    for y in range(N):
        t = y / (N - 1)
        c = tuple(int(BG_TOP[i] + (BG_LOW[i] - BG_TOP[i]) * t) for i in range(3))
        for x in range(N):
            img[y][x] = c

    cx = 15.5
    # shield body with a rim
    for y in range(N):
        hw = shield_half_width(y)
        if hw < 0:
            continue
        x0, x1 = int(round(cx - hw)), int(round(cx + hw))
        next_hw = shield_half_width(y + 1)
        for x in range(x0, x1 + 1):
            outside_next = next_hw < 0 or abs(x - cx) > next_hw
            edge = x in (x0, x1) or y == 3 or outside_next
            if edge:
                img[y][x] = RIM
            else:
                img[y][x] = SHIELD_LIGHT if x < cx - 1 else (SHIELD_DARK if x > cx + 1 and y > 8 else SHIELD)

    # the check mark: short down-stroke, long up-stroke, two pixels thick, with a shadow
    down = [(8, 15), (9, 16), (10, 17), (11, 18), (12, 19)]
    up = [(13, 18), (14, 17), (15, 16), (16, 15), (17, 14), (18, 13), (19, 12), (20, 11), (21, 10)]
    for (x, y) in down + up:
        for dy in (1, 2):
            if 0 <= y + dy < N:
                img[y + dy][x] = CHECK_SHADOW
    for (x, y) in down + up:
        img[y][x] = CHECK
        img[y + 1][x] = CHECK
    return img


def corner_alpha(x, y, size, radius):
    """Coverage (0-255) of pixel (x, y) by a rounded square, sampled 4x4 for a smooth edge."""
    cx = min(max(x + 0.5, radius), size - radius)
    cy = min(max(y + 0.5, radius), size - radius)
    if abs(x + 0.5 - cx) < 1e-9 and abs(y + 0.5 - cy) < 1e-9:
        return 255
    hits = 0
    for i in range(4):
        for j in range(4):
            px, py = x + (i + 0.5) / 4, y + (j + 0.5) / 4
            if (px - cx) ** 2 + (py - cy) ** 2 <= radius * radius:
                hits += 1
    return round(255 * hits / 16)


def write_png(path, img):
    size = N * SCALE
    raw = bytearray()
    for y in range(size):
        raw.append(0)
        row = img[y // SCALE]
        for x in range(size):
            r, g, b = row[x // SCALE]
            raw += bytes((r, g, b, corner_alpha(x, y, size, CORNER_RADIUS)))

    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(bytes(raw), 9))
    png += chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(png)


if __name__ == '__main__':
    here = os.path.dirname(os.path.abspath(__file__))
    img = draw()
    for out in (os.path.join(here, 'icon.png'),
                os.path.join(here, '..', 'src', 'main', 'resources', 'dtmodverify_logo.png')):
        write_png(out, img)
        print(os.path.normpath(out))
