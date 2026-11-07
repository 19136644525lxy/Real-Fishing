"""把 64x64 的钓鱼竿图处理成 32x32 像素图（原图不动）。v2

v2 相对 v1 的改动：源图先把 alpha 二值化（去掉 AI 渲染的半透明光晕），
2x2 块用「至少 2 个实心像素」投票决定不透明，再去除孤立点，
最后统一压暗轮廓并量化到 16 色（顺序调整，保证色数真的 <=16）。
"""
from PIL import Image
import os

ART = os.path.dirname(os.path.abspath(__file__))
SRC_DIR = os.path.dirname(ART)
PALETTE_N = 16
ALPHA_CUT = 140        # 源图 alpha 二值化阈值
MIN_OPAQUE = 2         # 2x2 块内至少几个实心像素才算不透明
EDGE_FACTOR = 0.55


def load_binary(path):
    img = Image.open(path).convert("RGBA")
    px = list(img.getdata())
    out = []
    for (r, g, b, a) in px:
        if a >= ALPHA_CUT:
            out.append((r, g, b, 255))
        else:
            out.append((0, 0, 0, 0))
    return out, img.size


def downscale2(px, w, h):
    nw, nh = w // 2, h // 2
    out = []
    for y in range(nh):
        for x in range(nw):
            block = [px[(y * 2 + dy) * w + (x * 2 + dx)]
                     for dy in range(2) for dx in range(2)]
            solid = [p for p in block if p[3] == 255]
            if len(solid) < MIN_OPAQUE:
                out.append((0, 0, 0, 0))
                continue
            r = sum(p[0] for p in solid) // len(solid)
            g = sum(p[1] for p in solid) // len(solid)
            b = sum(p[2] for p in solid) // len(solid)
            out.append((r, g, b, 255))
    return out, nw, nh


def despeckle(px, w, h, need=2):
    src = px[:]
    out = src[:]
    for y in range(h):
        for x in range(w):
            i = y * w + x
            if src[i][3] == 0:
                continue
            n = 0
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    if dx == 0 and dy == 0:
                        continue
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and src[ny * w + nx][3]:
                        n += 1
            if n < need:
                out[i] = (0, 0, 0, 0)
    return out


def fill_transparent(px, w, h, rounds=2):
    for _ in range(rounds):
        src = px[:]
        for y in range(h):
            for x in range(w):
                i = y * w + x
                if src[i][3] != 0:
                    continue
                acc = [0, 0, 0]
                n = 0
                for dy in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        nx, ny = x + dx, y + dy
                        if 0 <= nx < w and 0 <= ny < h:
                            j = ny * w + nx
                            if src[j][3]:
                                acc[0] += src[j][0]
                                acc[1] += src[j][1]
                                acc[2] += src[j][2]
                                n += 1
                if n:
                    px[i] = (acc[0] // n, acc[1] // n, acc[2] // n, 0)
    return px


def dilate(px, w, h, rounds=1):
    """轮廓外扩 1 像素，让杆身在缩到 16 逻辑像素后仍有 1 像素实体。"""
    for _ in range(rounds):
        src = px[:]
        out = src[:]
        for y in range(h):
            for x in range(w):
                i = y * w + x
                if src[i][3]:
                    continue
                acc = [0, 0, 0]
                n = 0
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and src[ny * w + nx][3]:
                        p = src[ny * w + nx]
                        acc[0] += p[0]
                        acc[1] += p[1]
                        acc[2] += p[2]
                        n += 1
                if n:
                    out[i] = (acc[0] // n, acc[1] // n, acc[2] // n, 255)
        px = out
    return px


def edge_darken(px, w, h, factor=EDGE_FACTOR):
    src = px[:]
    out = src[:]
    for y in range(h):
        for x in range(w):
            i = y * w + x
            if src[i][3] == 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if not (0 <= nx < w and 0 <= ny < h) or src[ny * w + nx][3] == 0:
                    r, g, b, _ = src[i]
                    out[i] = (int(r * factor), int(g * factor), int(b * factor), 255)
                    break
    return out


def quantize(px, w, h, colors):
    opaque = [p for p in px if p[3]]
    if not opaque:
        return px
    tmp = Image.new("RGB", (1, len(opaque)))
    tmp.putdata([(p[0], p[1], p[2]) for p in opaque])
    q = tmp.quantize(colors=colors, method=Image.MEDIANCUT, dither=Image.NONE)
    pal = q.getpalette()[: colors * 3]
    palette = [tuple(pal[i * 3:i * 3 + 3]) for i in range(colors)]
    out = []
    for p in px:
        if not p[3]:
            out.append(p)
            continue
        best, bd = palette[0], 1 << 30
        for c in palette:
            d = (p[0] - c[0]) ** 2 + (p[1] - c[1]) ** 2 + (p[2] - c[2]) ** 2
            if d < bd:
                bd, best = d, c
        out.append((best[0], best[1], best[2], 255))
    return out


def process(name):
    px, (w, h) = load_binary(os.path.join(SRC_DIR, f"{name}_64x64.png"))
    px, nw, nh = downscale2(px, w, h)
    px = despeckle(px, nw, nh, 2)
    px = fill_transparent(px, nw, nh, 2)          # 给透明区填色，量化时边缘才不脏
    px = dilate(px, nw, nh, 1)
    px = edge_darken(px, nw, nh)
    px = quantize(px, nw, nh, PALETTE_N)
    # 透明区恢复为全透明（fill 只影响 RGB，alpha 一直是 0，无需处理）

    img = Image.new("RGBA", (nw, nh))
    img.putdata(px)
    img.save(os.path.join(ART, f"{name}_32.png"))
    img.resize((nw * 8, nh * 8), Image.NEAREST).save(os.path.join(ART, f"{name}_32@8x.png"))
    img.resize((16, 16), Image.BOX).resize((256, 256), Image.NEAREST).save(
        os.path.join(ART, f"{name}_16sim.png"))

    cols = len({(p[0], p[1], p[2]) for p in px if p[3]})
    solid = sum(1 for p in px if p[3])
    print(f"{name}: {cols} colors, opaque {solid}/{nw*nh}")


if __name__ == "__main__":
    for n in ("taiwan_rod", "lure_rod"):
        process(n)
