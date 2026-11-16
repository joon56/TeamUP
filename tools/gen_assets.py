# Generates bundled binary resources: launcher PNGs per density, 9-patch raised button, raw sounds.
# Usage: python gen_assets.py   (writes into app/src/main/res)
import math, os, struct, wave
from PIL import Image, ImageDraw

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'res')
RES = os.path.normpath(RES)


def ensure(d):
    os.makedirs(os.path.join(RES, d), exist_ok=True)
    return os.path.join(RES, d)


# ───────── launcher icon (same drawing as ic_launcher_background/foreground vectors) ─────────
def launcher(size):
    s = 4  # supersample
    n = size * s
    img = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    grad = Image.new('RGBA', (n, n))
    px = grad.load()
    c0, c1 = (0x8F, 0x72, 0xFF), (0x55, 0x36, 0xD9)
    for y in range(n):
        for x in range(n):
            t = (x + y) / (2.0 * (n - 1))
            px[x, y] = tuple(int(c0[i] + (c1[i] - c0[i]) * t) for i in range(3)) + (255,)
    mask = Image.new('L', (n, n), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, n - 1, n - 1], radius=int(n * 0.23), fill=255)
    img.paste(grad, (0, 0), mask)

    def line(x1, y1, x2, y2, alpha):
        layer = Image.new('RGBA', (n, n), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        m = lambda v: (v - 18) / 72.0 * n  # adaptive-icon viewport(108) safe zone -> full legacy icon
        w = 9 / 72.0 * n
        d.line([m(x1), m(y1), m(x2), m(y2)], fill=(255, 255, 255, alpha), width=int(w))
        for cx, cy in ((x1, y1), (x2, y2)):
            d.ellipse([m(cx) - w / 2, m(cy) - w / 2, m(cx) + w / 2, m(cy) + w / 2], fill=(255, 255, 255, alpha))
        return layer

    img = Image.alpha_composite(img, line(40, 70, 54, 38, 255))
    img = Image.alpha_composite(img, line(54, 70, 68, 38, 178))
    return img.resize((size, size), Image.LANCZOS)


for name, size in (('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)):
    launcher(size).save(os.path.join(ensure('mipmap-' + name), 'ic_launcher.png'))


# ───────── splash logo tile per density (115 x 121 dp, raised look: darker 3dp edge at the bottom) ─────────
def logo_tile(scale):
    w, h, radius, edge = int(115 * scale), int(121 * scale), 20 * scale, 3 * scale
    s = 3  # supersample
    W, H = w * s, h * s
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, W - 1, H - 1], radius=radius * s, fill=(0x41, 0x27, 0xB5, 255))  # bottom edge colour
    face_h = H - int(edge * s)
    grad = Image.new('RGBA', (W, face_h))
    px = grad.load()
    c0, c1 = (0x8F, 0x72, 0xFF), (0x55, 0x36, 0xD9)
    for y in range(face_h):
        for x in range(W):
            t = (x / (W - 1) + y / (face_h - 1)) / 2.0
            px[x, y] = tuple(int(c0[i] + (c1[i] - c0[i]) * t) for i in range(3)) + (255,)
    mask = Image.new('L', (W, face_h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, W - 1, face_h - 1], radius=radius * s, fill=255)
    img.paste(grad, (0, 0), mask)

    # the "//" mark, centred on the face: same proportions as the launcher foreground (viewport 108, lines 40,70-54,38)
    unit = W / 86.0
    ox, oy = W / 2.0 - 54 * unit, face_h / 2.0 - 54 * unit
    stroke = 9 * unit

    def line(x1, y1, x2, y2, alpha):
        layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer)
        p = lambda vx, vy: (ox + vx * unit, oy + vy * unit)
        ld.line([p(x1, y1), p(x2, y2)], fill=(255, 255, 255, alpha), width=int(stroke))
        for cx, cy in (p(x1, y1), p(x2, y2)):
            ld.ellipse([cx - stroke / 2, cy - stroke / 2, cx + stroke / 2, cy + stroke / 2], fill=(255, 255, 255, alpha))
        return layer

    img = Image.alpha_composite(img, line(40, 70, 54, 38, 255))
    img = Image.alpha_composite(img, line(54, 70, 68, 38, 178))
    return img.resize((w, h), Image.LANCZOS)


for name, scale in (('mdpi', 1), ('hdpi', 1.5), ('xhdpi', 2), ('xxhdpi', 3), ('xxxhdpi', 4)):
    logo_tile(scale).save(os.path.join(ensure('drawable-' + name), 'logo_tile.png'))


# ───────── 9-patch: raised ("3D") button, white so it can be tinted with multiply ─────────
def nine_patch(pressed):
    W, H, R, BAND = 144, 156, 48, 9  # xxhdpi px: 48dp wide min, 52dp tall, radius 16dp, band 3dp
    s = 4
    big = Image.new('RGBA', (W * s, H * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    if pressed:
        d.rounded_rectangle([0, BAND * s, W * s - 1, H * s - 1], radius=R * s, fill=(226, 226, 226, 255))
    else:
        d.rounded_rectangle([0, 0, W * s - 1, H * s - 1], radius=R * s, fill=(176, 176, 176, 255))          # bottom band
        d.rounded_rectangle([0, 0, W * s - 1, (H - BAND) * s - 1], radius=R * s, fill=(255, 255, 255, 255))  # top highlight edge
        d.rounded_rectangle([0, 3 * s, W * s - 1, (H - BAND) * s - 1], radius=R * s, fill=(240, 240, 240, 255))  # body
    body = big.resize((W, H), Image.LANCZOS)
    out = Image.new('RGBA', (W + 2, H + 2), (0, 0, 0, 0))
    out.paste(body, (1, 1))
    px = out.load()
    black = (0, 0, 0, 255)
    top = BAND if pressed else 0
    bottom = H if pressed else H - BAND
    for x in range(1 + R, 1 + W - R):          # stretch horizontally between the corners
        px[x, 0] = black
    for y in range(1 + top + R, 1 + bottom - R):  # stretch vertically between the corners (band stays fixed)
        px[0, y] = black
    for x in range(1 + R, 1 + W - R):          # content: 16dp side padding
        px[x, H + 1] = black
    for y in range(1 + top, 1 + bottom):       # content: the raised face only
        px[W + 1, y] = black
    return out


d9 = ensure('drawable-xxhdpi')
nine_patch(False).save(os.path.join(d9, 'btn_raised_base.9.png'))
nine_patch(True).save(os.path.join(d9, 'btn_raised_pressed_base.9.png'))


# ───────── raw sounds (synthesised, no third-party audio) ─────────
RATE = 22050


def tone(freqs, seconds, volume=0.5, attack=0.01, release=0.08):
    n = int(RATE * seconds)
    out = []
    for i in range(n):
        t = i / RATE
        env = min(1.0, t / attack) * min(1.0, (seconds - t) / release)
        v = sum(math.sin(2 * math.pi * f * t) for f in freqs) / len(freqs)
        out.append(v * env * volume)
    return out


def silence(seconds):
    return [0.0] * int(RATE * seconds)


def bell(freq, seconds, volume=0.5):
    n = int(RATE * seconds)
    out = []
    for i in range(n):
        t = i / RATE
        env = math.exp(-t * 6.0) * min(1.0, t / 0.004)
        v = math.sin(2 * math.pi * freq * t) + 0.35 * math.sin(2 * math.pi * freq * 2.01 * t) + 0.12 * math.sin(2 * math.pi * freq * 3.0 * t)
        out.append(v / 1.47 * env * volume)
    return out


def mix(a, b, offset_s):
    off = int(RATE * offset_s)
    out = list(a) + [0.0] * max(0, off + len(b) - len(a))
    for i, v in enumerate(b):
        out[off + i] += v
    return out


def save(name, samples):
    path = os.path.join(ensure('raw'), name)
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b''.join(struct.pack('<h', int(max(-1.0, min(1.0, s)) * 32767)) for s in samples))
    print(name, round(len(samples) / RATE, 2), 's', os.path.getsize(path), 'bytes')


# alarm: two rising double-beeps, then a pause (loops cleanly)
beep = lambda f: tone([f, f * 2], 0.16, 0.55)
alarm = beep(880) + silence(0.08) + beep(1175) + silence(0.30) + beep(880) + silence(0.08) + beep(1175) + silence(0.85)
save('alarm_modo.wav', alarm)

# task done: short two-note chime (E6 -> A6)
chime = mix(bell(1318.5, 0.55, 0.5), bell(1760.0, 0.6, 0.5), 0.11)
save('task_done.wav', chime)

for root, _, files in os.walk(RES):
    for f in files:
        if f.endswith('.png') and ('mipmap-' in root or 'drawable-' in root):
            p = os.path.join(root, f)
            print(os.path.relpath(p, RES), Image.open(p).size)
