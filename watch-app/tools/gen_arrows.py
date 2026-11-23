#!/usr/bin/env python3
"""Sinh src/common/arrows/m00..m13.png (108x108, 1:1 cho <image class="arrow"> trên Watch S5)
và src/common/icon.png (160x160 cho app drawer). Chỉ dùng stdlib.

Mọi mũi tên cùng một ngôn ngữ hình học: thân dày đều W (đường gấp khúc bo góc) + đầu tam giác cân bo nhẹ,
căn giữa quang học và vừa khít vòng tròn an toàn bên trong khung dial 132x132.
"""
import math, os, struct, zlib

SIZE = 108    # 1:1 với <image class="arrow"> (108x108 px) — tránh Vela/LVGL crop khi không scale ảnh
ICON_SIZE = 160
SS = 4        # supersample 4x cho viền mượt
W = 20        # độ dày thân (trong hệ tọa độ thiết kế 160x160)
HL = 32       # chiều dài đầu mũi tên
HW = 52       # bề rộng đầu mũi tên
HR = 3        # bo góc đầu


def write_rgba_png(path, size, rgba_rows):
    raw = bytearray()
    for row in rgba_rows:
        raw.append(0)
        raw += row
    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(data)


def png(path, size, alpha_rows):
    rgba_rows = []
    for row in alpha_rows:
        r = bytearray()
        for a in row:
            r += bytes((255, 255, 255, a))
        rgba_rows.append(r)
    write_rgba_png(path, size, rgba_rows)


# ---------- hình học: mỗi hình là danh sách đa giác (list điểm trong không gian 160x160) ----------

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
    if r <= 0 or len(pts) < 3:
        return list(pts)
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
        return stroke(circle(80, 80, 42, 48), 18, closed=True)
    if m == 1:  # đi thẳng
        return arrow([(80, 146), (80, 56)], 0)
    if m == 2:  # chếch trái 45°
        return arrow([(100, 146), (100, 102), (68, 70)], 18)
    if m == 3:  # rẽ trái 90°
        return arrow([(112, 146), (112, 74), (74, 74)], 18)
    if m == 4:  # ngoặt trái gắt 135° (chuẩn Google Maps / Material turn_sharp_left: góc bẻ sắc, tách rõ thân đứng & nhánh chéo)
        return arrow([(116, 144), (116, 44), (56, 104)], 9)
    if m == 5:  # chếch phải
        return mirror(shape(2))
    if m == 6:  # rẽ phải 90°
        return mirror(shape(3))
    if m == 7:  # ngoặt phải gắt 135°
        return mirror(shape(4))
    if m == 8:  # quay đầu: chữ U vòng rộng (khác hẳn góc gắt của m04/m07)
        return arrow([(114, 146), (114, 62), (46, 62), (46, 98)], 34)
    if m == 9:  # vòng xuyến: vòng tròn rõ + chân vào + mũi tên thoát phía trên
        ring = stroke(circle(80, 100, 26, 48), 18, closed=True)
        return ring + stroke([(80, 146), (80, 118)], W) + arrow([(80, 78), (80, 54)], 0)
    if m == 10:  # giữ trái: nhánh thẳng ngắn gọn + mũi tên chính chếch trái rõ
        prong = stroke([(94, 146), (94, 80)], 14) + [circle(94, 80, 7, 16)]
        return prong + arrow([(94, 146), (94, 114), (60, 76)], 14)
    if m == 11:  # giữ phải
        return mirror(shape(10))
    if m == 12:  # đến nơi: cờ
        pole = [(40, 26), (52, 26), (52, 144), (40, 144)]
        flag = [(52, 28), (126, 28), (108, 56), (126, 84), (52, 84)]
        return [pole, flag, circle(46, 26, 6, 12)]
    if m == 13:  # xuất phát: chấm + mũi tên lên
        return [circle(80, 132, 16, 32)] + arrow([(80, 106), (80, 62)], 0)
    raise ValueError(m)


def bbox(polys):
    xs = [x for p in polys for x, _ in p]
    ys = [y for p in polys for _, y in p]
    return min(xs), min(ys), max(xs), max(ys)


def fit_circle(polys, out_size, safe_radius):
    """Căn giữa theo bounding box rồi co đều để mọi đỉnh nằm gọn trong đường tròn safe_radius quanh tâm out_size/2."""
    x0, y0, x1, y1 = bbox(polys)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    max_r = max(math.hypot(x - cx, y - cy) for p in polys for x, y in p) or 1.0
    # Giữ tỷ lệ đồng nhất giữa các mũi tên nhưng đảm bảo không đỉnh nào chạm viền tròn
    base_scale = out_size / 160.0
    k = min(base_scale * 1.12, safe_radius / max_r)
    half = out_size / 2.0
    return [[(half + (x - cx) * k, half + (y - cy) * k) for x, y in p] for p in polys]


def rasterize(polys, size):
    n = size * SS
    mask = [bytearray(n) for _ in range(n)]
    for poly in polys:
        p = [(x * SS, y * SS) for x, y in poly]
        ys = [y for _, y in p]
        for row in range(max(0, int(min(ys))), min(n, int(max(ys)) + 1)):
            yc = row + 0.5
            xs = []
            for i in range(len(p)):
                x1, y1 = p[i]
                x2, y2 = p[(i + 1) % len(p)]
                if (y1 <= yc < y2) or (y2 <= yc < y1):
                    xs.append(x1 + (yc - y1) * (x2 - x1) / (y2 - y1))
            xs.sort()
            for k in range(0, len(xs) - 1, 2):
                a, b = max(0, int(round(xs[k]))), min(n, int(round(xs[k + 1])))
                if b > a:
                    mask[row][a:b] = b'\x01' * (b - a)
    rows = []
    for y in range(size):
        r = []
        for x in range(size):
            t = 0
            for sy in range(SS):
                t += sum(mask[y * SS + sy][x * SS:(x + 1) * SS])
            r.append(round(t * 255 / (SS * SS)))
        rows.append(r)
    return rows


def render(m, size=SIZE, safe_radius=46.0):
    return rasterize(fit_circle(shape(m), size, safe_radius), size)


def render_app_icon(path):
    """Sinh icon ứng dụng 160x160 nền tròn tối + vòng ngọc lam + mũi tên trắng sắc nét."""
    arrow_alpha = render(1, size=ICON_SIZE, safe_radius=54.0)
    cx = cy = ICON_SIZE / 2.0
    rows = []
    for y in range(ICON_SIZE):
        row = bytearray()
        for x in range(ICON_SIZE):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d > 78.5:
                row += bytes((0, 0, 0, 0))
                continue
            edge_a = max(0.0, min(1.0, 78.5 - d))
            if d >= 71.0:
                r, g, b = 45, 212, 191  # #2dd4bf teal ring
            else:
                r, g, b = 15, 23, 42    # #0f172a deep slate
            aa = arrow_alpha[y][x] / 255.0
            r = round(r * (1 - aa) + 255 * aa)
            g = round(g * (1 - aa) + 255 * aa)
            b = round(b * (1 - aa) + 255 * aa)
            row += bytes((r, g, b, round(edge_a * 255)))
        rows.append(row)
    write_rgba_png(path, ICON_SIZE, rows)


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = os.path.join(root, 'src', 'common', 'arrows')
    os.makedirs(out, exist_ok=True)
    for m in range(14):
        png(os.path.join(out, 'm%02d.png' % m), SIZE, render(m))
    render_app_icon(os.path.join(root, 'src', 'common', 'icon.png'))


if __name__ == '__main__':
    main()
