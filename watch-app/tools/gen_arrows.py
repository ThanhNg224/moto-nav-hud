#!/usr/bin/env python3
"""Sinh src/common/arrows/m00..m13.png (160x160, trắng trên nền trong suốt). Chỉ dùng stdlib.

Mọi mũi tên cùng một ngôn ngữ hình học: thân dày đều W (đường gấp khúc bo góc) + đầu tam giác cân bo nhẹ,
tất cả là hợp (union) của các đa giác nên liền khối. Sau khi dựng, hình được căn giữa ô 160x160.
"""
import math, os, struct, zlib

SIZE = 160
SS = 4  # supersample
N = SIZE * SS
W = 22        # độ dày thân
HL = 36       # chiều dài đầu mũi tên
HW = 58       # bề rộng đầu mũi tên
HR = 3        # bo góc đầu


def png(path, alpha_rows):
    raw = bytearray()
    for row in alpha_rows:
        raw.append(0)
        for a in row:
            raw += bytes((255, 255, 255, a))
    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', SIZE, SIZE, 8, 6, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(data)


# ---------- hình học: mỗi hình là danh sách đa giác (list điểm) ----------

def circle(cx, cy, r, n=32):
    return [(cx + r * math.cos(2 * math.pi * i / n), cy + r * math.sin(2 * math.pi * i / n)) for i in range(n)]


def stroke(pts, w, closed=False):
    polys = []
    segs = list(zip(pts, pts[1:] + ([pts[0]] if closed else [])))
    for (x1, y1), (x2, y2) in segs:
        dx, dy = x2 - x1, y2 - y1
        l = math.hypot(dx, dy) or 1
        nx, ny = -dy / l * w / 2, dx / l * w / 2
        polys.append([(x1 + nx, y1 + ny), (x2 + nx, y2 + ny), (x2 - nx, y2 - ny), (x1 - nx, y1 - ny)])
    inner = pts if closed else pts[1:-1]
    for x, y in inner:
        polys.append(circle(x, y, w / 2, 16))
    return polys


def fillet(pts, r):
    """Bo tròn các góc của đường gấp khúc bằng cung bán kính r."""
    out = [pts[0]]
    for i in range(1, len(pts) - 1):
        a, b, c = pts[i - 1], pts[i], pts[i + 1]
        u = (a[0] - b[0], a[1] - b[1]); lu = math.hypot(*u); u = (u[0] / lu, u[1] / lu)
        v = (c[0] - b[0], c[1] - b[1]); lv = math.hypot(*v); v = (v[0] / lv, v[1] / lv)
        theta = math.acos(max(-1, min(1, u[0] * v[0] + u[1] * v[1])))
        t = r / math.tan(theta / 2)
        t = min(t, lu / 2 if i == 1 else lu, lv / 2 if i == len(pts) - 2 else lv)
        rr = t * math.tan(theta / 2)
        t1 = (b[0] + u[0] * t, b[1] + u[1] * t)
        t2 = (b[0] + v[0] * t, b[1] + v[1] * t)
        bis = (u[0] + v[0], u[1] + v[1]); lb = math.hypot(*bis); bis = (bis[0] / lb, bis[1] / lb)
        d = rr / math.sin(theta / 2)
        cx, cy = b[0] + bis[0] * d, b[1] + bis[1] * d
        a1 = math.atan2(t1[1] - cy, t1[0] - cx)
        a2 = math.atan2(t2[1] - cy, t2[0] - cx)
        da = (a2 - a1 + math.pi) % (2 * math.pi) - math.pi
        steps = 12
        for k in range(steps + 1):
            ang = a1 + da * k / steps
            out.append((cx + rr * math.cos(ang), cy + rr * math.sin(ang)))
    out.append(pts[-1])
    return out


def head(base, direction):
    """Đầu mũi tên tam giác cân, đáy tại base, hướng direction (vector đơn vị), bo góc HR."""
    dx, dy = direction
    px, py = -dy, dx
    bx, by = base
    a = (bx + px * HW / 2, by + py * HW / 2)
    b = (bx - px * HW / 2, by - py * HW / 2)
    c = (bx + dx * HL, by + dy * HL)
    la, lb, lc = math.dist(b, c), math.dist(a, c), math.dist(a, b)
    per = la + lb + lc
    ix = (a[0] * la + b[0] * lb + c[0] * lc) / per
    iy = (a[1] * la + b[1] * lb + c[1] * lc) / per
    s = per / 2
    area = abs((b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1])) / 2
    rho = area / s
    k = (rho - HR) / rho
    tri = [(ix + (p[0] - ix) * k, iy + (p[1] - iy) * k) for p in (a, b, c)]
    return [tri] + stroke(tri, 2 * HR, closed=True)


def arrow(pts, r):
    """Thân gấp khúc bo góc r kết thúc tại tâm đáy đầu mũi tên (pts[-1]); đầu mũi tên theo hướng đoạn cuối."""
    path = fillet(pts, r)
    (x1, y1), (x2, y2) = path[-2], path[-1]
    l = math.hypot(x2 - x1, y2 - y1)
    d = ((x2 - x1) / l, (y2 - y1) / l)
    ext = (x2 + d[0] * 2, y2 + d[1] * 2)  # lấn 2px vào đầu để không có khe
    return stroke(path[:-1] + [ext], W) + head(pts[-1], d)


def mirror(polys):
    return [[(2 * 80 - x, y) for x, y in p] for p in polys]


def shape(m):
    if m == 0:  # không rõ: vòng tròn
        return stroke(circle(80, 80, 44, 48)[:], 20, closed=True)
    if m == 1:
        return arrow([(80, 150), (80, 54)], 0)
    if m == 3:
        return arrow([(114, 150), (114, 74), (78, 74)], 18)
    if m == 6:
        return mirror(shape(3))
    if m == 2:
        return arrow([(100, 150), (100, 104), (72, 76)], 18)
    if m == 5:
        return mirror(shape(2))
    if m == 4:
        return arrow([(126, 148), (126, 44), (62, 108)], 14)
    if m == 7:
        return mirror(shape(4))
    if m == 8:  # quay đầu: chữ U
        return arrow([(112, 150), (112, 60), (48, 60), (48, 96)], 32)
    if m == 9:  # vòng xuyến: vòng + đường vào + lối ra phía trên
        ring = stroke(circle(80, 104, 28, 48), 20, closed=True)
        return ring + stroke([(80, 152), (80, 118)], W) + arrow([(80, 80), (80, 52)], 0)
    if m == 10:  # giữ trái: thân thẳng ngắn + nhánh chếch 30 độ liền khối
        prong = stroke([(86, 150), (86, 84)], W) + [circle(86, 84, W / 2, 16)]
        return prong + arrow([(86, 150), (86, 114), (56, 78)], 14)
    if m == 11:
        return mirror(shape(10))
    if m == 12:  # đến nơi: cờ
        pole = [(38, 24), (50, 24), (50, 148), (38, 148)]
        flag = [(50, 26), (128, 26), (110, 54), (128, 82), (50, 82)]
        return [pole, flag, circle(44, 24, 6, 12)]
    if m == 13:  # xuất phát: chấm + mũi tên lên
        return [circle(80, 134, 18, 32)] + arrow([(80, 108), (80, 62)], 0)
    raise ValueError(m)


def bbox(polys):
    xs = [x for p in polys for x, _ in p]
    ys = [y for p in polys for _, y in p]
    return min(xs), min(ys), max(xs), max(ys)


def center(polys):
    x0, y0, x1, y1 = bbox(polys)
    k = min(1.0, 148 / max(x1 - x0, y1 - y0))
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    return [[(80 + (x - cx) * k, 80 + (y - cy) * k) for x, y in p] for p in polys]


def rasterize(polys):
    mask = [bytearray(N) for _ in range(N)]
    for poly in polys:
        p = [(x * SS, y * SS) for x, y in poly]
        ys = [y for _, y in p]
        for row in range(max(0, int(min(ys))), min(N, int(max(ys)) + 1)):
            yc = row + 0.5
            xs = []
            for i in range(len(p)):
                x1, y1 = p[i]
                x2, y2 = p[(i + 1) % len(p)]
                if (y1 <= yc < y2) or (y2 <= yc < y1):
                    xs.append(x1 + (yc - y1) * (x2 - x1) / (y2 - y1))
            xs.sort()
            for k in range(0, len(xs) - 1, 2):
                a, b = max(0, int(round(xs[k]))), min(N, int(round(xs[k + 1])))
                if b > a:
                    mask[row][a:b] = b'\x01' * (b - a)
    rows = []
    for y in range(SIZE):
        r = []
        for x in range(SIZE):
            t = 0
            for sy in range(SS):
                t += sum(mask[y * SS + sy][x * SS:(x + 1) * SS])
            r.append(round(t * 255 / (SS * SS)))
        rows.append(r)
    return rows


def render(m):
    return rasterize(center(shape(m)))


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = os.path.join(root, 'src', 'common', 'arrows')
    os.makedirs(out, exist_ok=True)
    for m in range(14):
        png(os.path.join(out, 'm%02d.png' % m), render(m))
    with open(os.path.join(out, 'm01.png'), 'rb') as f, open(os.path.join(root, 'src', 'common', 'icon.png'), 'wb') as g:
        g.write(f.read())


if __name__ == '__main__':
    main()
