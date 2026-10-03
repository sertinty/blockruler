from PIL import Image, ImageDraw
import math

S = 128
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

# 背景：深色圆角方块 + 蓝色描边
pad = 4
d.rounded_rectangle([pad, pad, S - pad - 1, S - pad - 1], radius=22, fill=(20, 26, 36, 255),
                    outline=(63, 169, 245, 255), width=3)

# 一条斜向的“尺子”条带
band = Image.new("RGBA", (S, S), (0, 0, 0, 0))
bd = ImageDraw.Draw(band)
bd.rounded_rectangle([10, 52, S - 10, 78], radius=6, fill=(63, 169, 245, 255))
band = band.rotate(-28, resample=Image.BICUBIC, center=(S / 2, S / 2))
img.alpha_composite(band)
d = ImageDraw.Draw(img)

# 尺子上的刻度线（同样角度）
angle = math.radians(-28)
cx, cy = S / 2, S / 2
for i in range(-5, 6):
    px = cx + i * 9 * math.cos(angle)
    py = cy + i * 9 * math.sin(angle)
    for k in range(5):
        t = -7 + k * 3.5
        x1 = px + t * math.sin(angle)
        y1 = py - t * math.cos(angle)
        x2 = x1 + 4 * math.cos(angle)
        y2 = y1 + 4 * math.sin(angle)
        d.line([x1, y1, x2, y2], fill=(20, 26, 36, 220), width=1)


def dot(x, y, r, fill):
    d.ellipse([x - r, y - r, x + r, y + r], fill=fill, outline=(255, 255, 255, 255), width=2)


# 两点 + 中间的测量线
a = (32, 98)
b = (96, 34)
for i in range(9):
    t0 = i / 9
    t1 = t0 + 0.55 / 9
    x0 = a[0] + (b[0] - a[0]) * t0
    y0 = a[1] + (b[1] - a[1]) * t0
    x1 = a[0] + (b[0] - a[0]) * t1
    y1 = a[1] + (b[1] - a[1]) * t1
    d.line([x0, y0, x1, y1], fill=(255, 255, 255, 235), width=3)

dot(a[0], a[1], 9, (74, 222, 128, 255))
dot(b[0], b[1], 9, (248, 113, 113, 255))

img.save(r"D:\sertinty\Documents\dsh\blockruler\src\main\resources\assets\blockruler\icon.png")
print("icon written")
