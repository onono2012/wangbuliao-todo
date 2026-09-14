#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# 生成在线主题内容: 5套壁纸(1080x2340)+封面(360x648)+玻璃雨珠动态包+themes.json
# 运行: /usr/bin/python3.9 tools/gen_theme_content.py
import os, json, zipfile
import numpy as np
from PIL import Image, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TD = os.path.join(ROOT, 'themes')
W, H, CW, CH = 1080, 2340, 360, 648
rng = np.random.default_rng(20260914)
YY, XX = np.mgrid[0:H, 0:W]

def save_art(arr, tid, q=88):
    im = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
    im.save(os.path.join(TD, 'wallpapers', tid + '.jpg'), 'JPEG', quality=q)
    im.resize((CW, CH), Image.LANCZOS).save(os.path.join(TD, 'covers', tid + '.jpg'), 'JPEG', quality=86)

def vgrad(stops):
    ys = np.linspace(0, 1, H)
    cols = np.zeros((H, 3))
    for i in range(len(stops) - 1):
        p0, c0 = stops[i]
        p1, c1 = stops[i + 1]
        m = (ys >= p0) & (ys <= p1)
        if not m.any():
            continue
        t = ((ys[m] - p0) / max(p1 - p0, 1e-9))[:, None]
        t = t * t * (3 - 2 * t)
        cols[m] = np.array(c0) * (1 - t) + np.array(c1) * t
    return np.repeat(cols[:, None, :], W, axis=1)

def glow(cx, cy, r, color, power=2.0):
    d = np.sqrt(((XX - cx) / r) ** 2 + ((YY - cy) / r) ** 2)
    return (np.clip(1 - d, 0, 1) ** power)[..., None] * np.array(color, float)

def gblur(arr, rad):
    u8 = np.clip(arr, 0, 255).astype(np.uint8)
    return np.asarray(Image.fromarray(u8).filter(ImageFilter.GaussianBlur(rad))).astype(float)

def vign(arr, v=0.45):
    dx = (XX - W * 0.5) / (W * 0.62)
    dy = (YY - H * 0.5) / (H * 0.62)
    return arr * np.clip(1 - (dx * dx + dy * dy) * v, 0, 1)[..., None]

def grain(arr, amt=3.2):
    return arr + rng.normal(0, amt, (H, W, 1))

def stars(arr, n=280, ymax=0.62, k=1.0):
    for _ in range(n):
        x = int(rng.integers(0, W)); y = int(rng.integers(0, int(H * ymax)))
        v = float(rng.uniform(30, 170)) * k; s = float(rng.uniform(0.5, 1.6))
        r = int(s * 3) + 1
        x0, x1 = max(0, x - r), min(W, x + r + 1)
        y0, y1 = max(0, y - r), min(H, y + r + 1)
        gx = np.arange(x0, x1)[None, :] - x
        gy = np.arange(y0, y1)[:, None] - y
        arr[y0:y1, x0:x1] += (v * np.exp(-(gx * gx + gy * gy) / (2 * s * s)))[..., None]
    return arr

def aurora():
    a = vgrad([(0, (4, 8, 28)), (0.4, (6, 14, 44)), (0.75, (9, 22, 56)), (1, (5, 10, 32))])
    for amp, wl, ph, col, sig, tp in [(120, 6.283 * 1.6, 0.4, (46, 226, 186), 48, 0.30),
                                      (170, 6.283 * 2.4, 2.1, (118, 240, 150), 66, 0.36),
                                      (100, 6.283 * 1.2, 4.1, (128, 100, 246), 54, 0.24)]:
        yc = H * tp + amp * np.sin(np.linspace(0, wl, W) + ph)
        d = (YY - yc[None, :]) / sig
        strip = np.exp(-d * d) * (0.55 + 0.45 * np.sin(np.linspace(0, 9.0, W))[None, :] ** 2)
        a = a + (strip * 0.5)[..., None] * np.array(col, float)
    a = gblur(a, 24)
    a = stars(a, 320, 0.62)
    a = a * 0.85 + gblur(a, 70) * 0.30
    return vign(grain(a, 3.0), 0.5)

def dusk():
    a = vgrad([(0, (26, 10, 46)), (0.40, (96, 32, 88)), (0.60, (200, 78, 96)),
               (0.74, (246, 142, 94)), (0.85, (255, 192, 132)), (1, (76, 34, 64))])
    a = a + glow(W * 0.52, H * 0.80, W * 1.1, (255, 150, 88), 2.4)
    for i, ystop in enumerate((0.30, 0.45, 0.60)):
        col = [(255, 205, 175), (255, 175, 155), (250, 150, 150)][i]
        band = np.exp(-((YY - H * ystop) / 42.0) ** 2) * (0.5 + 0.5 * np.sin(XX / 180.0 + i * 2.0) ** 2)
        a = a + band[..., None] * np.array(col, float) * 0.30
    a = gblur(a, 6)
    return vign(grain(a, 3.0), 0.4)

def abyss():
    a = vgrad([(0, (2, 7, 12)), (0.5, (3, 16, 24)), (0.85, (4, 26, 36)), (1, (2, 10, 16))])
    a = a + glow(W * 0.5, H * 0.92, W * 0.95, (0, 200, 190), 2.6)
    for i in range(5):
        x0 = W * (0.12 + 0.19 * i)
        xs = x0 + 60 * np.sin(np.linspace(0, 9, H) + i * 1.7)
        d = (XX - xs[:, None]) / 90.0
        streak = np.exp(-d * d) * np.linspace(0.5, 0.1, H)[:, None]
        a = a + streak[..., None] * np.array((60, 240, 220), float) * 0.22
    a = gblur(a, 18)
    a = stars(a, 90, 0.85, 0.5)
    return vign(grain(a, 2.6), 0.55)

def sakura():
    a = vgrad([(0, (22, 10, 28)), (0.45, (56, 24, 52)), (0.75, (110, 52, 84)), (1, (52, 22, 44))])
    a = a + glow(W * 0.78, H * 0.30, W * 0.75, (255, 160, 210), 2.2)
    for _ in range(70):
        cx = float(rng.uniform(0, W)); cy = float(rng.uniform(0, H * 0.95))
        rr = float(rng.uniform(18, 130)); v = float(rng.uniform(8, 30))
        col = np.array([(255, 170, 205), (228, 160, 235), (255, 200, 220)][int(rng.integers(0, 3))], float)
        d = np.sqrt(((XX - cx) / rr) ** 2 + ((YY - cy) / rr) ** 2)
        a = a + (np.clip(1 - d, 0, 1) ** 1.6)[..., None] * col * (v / 255.0)
    a = gblur(a, 5)
    a = stars(a, 60, 0.5, 0.6)
    return vign(grain(a, 3.0), 0.42)

def gold():
    a = vgrad([(0, (8, 7, 10)), (0.5, (16, 13, 18)), (1, (10, 8, 12))])
    a = a + glow(W * 0.5, H * 0.55, W * 1.2, (120, 90, 40), 2.8)
    t = np.linspace(0, 1, H)
    for i in range(3):
        xs = W * (0.25 + 0.25 * i) + 140 * np.sin(t * 6.283 * 1.3 + i * 2.1)
        d = (XX - xs[:, None]) / (70.0 + 30.0 * i)
        flow = np.exp(-d * d) * (0.4 + 0.6 * np.sin(t * 40 + i) ** 2)[:, None]
        a = a + flow[..., None] * np.array((235, 190, 90), float) * 0.18
    a = gblur(a, 10)
    for _ in range(150):
        cx = float(rng.uniform(0, W)); cy = float(rng.uniform(0, H))
        s = float(rng.uniform(0.6, 2.2)); v = float(rng.uniform(60, 220))
        r = int(s * 3) + 1
        x0, x1 = max(0, int(cx) - r), min(W, int(cx) + r + 1)
        y0, y1 = max(0, int(cy) - r), min(H, int(cy) + r + 1)
        gx = np.arange(x0, x1)[None, :] - cx
        gy = np.arange(y0, y1)[:, None] - cy
        a[y0:y1, x0:x1] += (v * np.exp(-(gx * gx + gy * gy) / (2 * s * s)))[..., None] * np.array((1.0, 0.85, 0.5))
    return vign(grain(a, 2.8), 0.5)

def make_glass_rain_pack():
    src = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'glass-rain')
    dst = os.path.join(TD, 'packs', 'glass-rain.zip')
    with zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for base, _, files in os.walk(src):
            for f in files:
                p = os.path.join(base, f)
                rel = os.path.relpath(p, src).replace(os.sep, '/')
                z.write(p, rel)
    return dst

def make_glass_rain_cover():
    a = vgrad([(0, (3, 8, 18)), (0.5, (5, 14, 30)), (1, (2, 6, 14))])
    for _ in range(46):
        cx = float(rng.uniform(0, W)); cy = float(rng.uniform(0, H)); rr = float(rng.uniform(10, 70))
        d = np.sqrt(((XX - cx) / rr) ** 2 + ((YY - cy) * 1.6 / rr) ** 2)
        a = a + (np.exp(-d * d))[..., None] * np.array((40, 120, 220), float) * 0.10
    a = gblur(a, 3)
    im = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))
    im.save(os.path.join(TD, 'covers', 'glass-rain.jpg'), 'JPEG', quality=86)

def main():
    os.makedirs(os.path.join(TD, 'wallpapers'), exist_ok=True)
    os.makedirs(os.path.join(TD, 'covers'), exist_ok=True)
    os.makedirs(os.path.join(TD, 'packs'), exist_ok=True)
    arts = [('aurora', aurora), ('dusk', dusk), ('abyss', abyss), ('sakura', sakura), ('gold', gold)]
    for tid, fn in arts:
        save_art(fn(), tid)
        print('wallpaper done:', tid)
    make_glass_rain_cover()
    pack = make_glass_rain_pack()
    print('pack done:', pack, os.path.getsize(pack), 'bytes')
    items = [
        ('aurora', '极光霜华', '冰蓝紫极光摇曳 · 满天星子', 'static', 'wallpapers/aurora.jpg'),
        ('dusk', '暮色霞光', '暖橙紫罗兰晚霞 · 落日余晖', 'static', 'wallpapers/dusk.jpg'),
        ('abyss', '深海幽蓝', '幽暗深海 · 青色光洄', 'static', 'wallpapers/abyss.jpg'),
        ('sakura', '夜樱雾霭', '粉紫夜色 · 朦胧光斑', 'static', 'wallpapers/sakura.jpg'),
        ('gold', '墨金流沙', '黑金粒子 · 流光溢彩', 'static', 'wallpapers/gold.jpg'),
    ]
    themes = []
    for tid, name, desc, typ, path in items:
        themes.append({'id': tid, 'name': name, 'desc': desc, 'type': typ,
                       'coverUrl': 'covers/' + tid + '.jpg', 'downloadUrl': path,
                       'resolution': '1080p', 'fileSize': os.path.getsize(os.path.join(TD, path)),
                       'author': 'wbl', 'createdAt': '2026-09-14'})
    themes.append({'id': 'glass-rain', 'name': '玻璃雨珠', 'desc': '实时玻璃雨滴物理动画(动态主题)',
                   'type': 'dynamic', 'coverUrl': 'covers/glass-rain.jpg', 'downloadUrl': 'packs/glass-rain.zip',
                   'resolution': 'auto', 'fileSize': os.path.getsize(pack), 'author': 'wbl', 'createdAt': '2026-09-14'})
    payload = json.dumps({'version': 1, 'themes': themes}, ensure_ascii=False, indent=1)
    for p in (os.path.join(TD, 'themes.json'), os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'themes.json')):
        with open(p, 'w', encoding='utf-8') as f:
            f.write(payload)
    print('themes.json done:', len(themes), 'themes')

if __name__ == '__main__':
    main()
