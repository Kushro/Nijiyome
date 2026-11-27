#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Paper textures for the tanzaku skins (docs/redesign/pieles).

Every file is a seamless, tileable grayscale "shading map" centred on mid-gray (128): lighter than 128 lifts the paper, darker
sinks it. The page and the app lay it over the paper colour with an overlay / soft-light blend, so one map serves every status
colour and every theme, and the file carries no colour of its own.

    python3 -I docs/redesign/herramientas/papeles.py [out_dir]       # page textures (webp + png)
    python3 -I docs/redesign/herramientas/papeles.py --app          # app tiles: rd_skin_<name>.png in :core:designsystem

Deterministic (fixed seeds). Needs numpy and Pillow. All filtering is done on a torus (np.roll, FFT), so the edges continue.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

SIZE = 512
DEFAULT_OUT = Path(__file__).resolve().parents[1] / "pieles" / "tex"
APP_OUT = Path(__file__).resolve().parents[3] / "core/designsystem/src/commonMain/composeResources/drawable"
APP_LEVELS = 32  # gray levels kept in the app tiles: drawn at partial strength, 32 steps are invisible and the PNGs stay small


def blur(img, sx, sy=None):
    """Anisotropic Gaussian blur on a torus (FFT)."""
    sy = sx if sy is None else sy
    fy = np.fft.fftfreq(img.shape[0])[:, None]
    fx = np.fft.fftfreq(img.shape[1])[None, :]
    k = np.exp(-2.0 * np.pi ** 2 * ((sx * fx) ** 2 + (sy * fy) ** 2))
    return np.real(np.fft.ifft2(np.fft.fft2(img) * k))


def norm(a):
    a = a - a.min()
    return a / max(a.max(), 1e-9)


def emboss(height, strength, light=(-0.6, -0.8)):
    """Lambert-ish shading of a height map lit from the top left, centred on 0."""
    dx = (np.roll(height, -1, 1) - np.roll(height, 1, 1)) * 0.5
    dy = (np.roll(height, -1, 0) - np.roll(height, 1, 0)) * 0.5
    return -(dx * light[0] + dy * light[1]) * strength


def splat(canvas, xs, ys, w):
    x0 = np.floor(xs).astype(np.int64)
    y0 = np.floor(ys).astype(np.int64)
    fx, fy = xs - x0, ys - y0
    for ox, oy, ww in ((0, 0, (1 - fx) * (1 - fy)), (1, 0, fx * (1 - fy)), (0, 1, (1 - fx) * fy), (1, 1, fx * fy)):
        np.add.at(canvas, ((y0 + oy) % SIZE, (x0 + ox) % SIZE), ww * w)


def strands(rng, count, length, bend, step=0.6, angle_bias=None):
    """Curving fibres as anti-aliased random walks; returns a coverage map."""
    canvas = np.zeros((SIZE, SIZE))
    for _ in range(count):
        n = int(rng.uniform(*length) / step)
        a0 = rng.uniform(0, np.pi) if angle_bias is None else angle_bias + rng.normal(0, 0.35)
        da = np.cumsum(rng.normal(0, bend, n))
        a = a0 + da
        x = rng.uniform(0, SIZE) + np.cumsum(np.cos(a) * step)
        y = rng.uniform(0, SIZE) + np.cumsum(np.sin(a) * step)
        taper = np.sin(np.linspace(0, np.pi, n)) ** 0.6
        splat(canvas, x % SIZE, y % SIZE, taper * rng.uniform(0.4, 1.0))
    return canvas


def to_img(shade, contrast=1.0):
    """Shade (centred on 0) to an 8-bit gray image centred on 128."""
    s = shade - shade.mean()
    s = s / max(np.percentile(np.abs(s), 99.5), 1e-9) * 0.5 * contrast
    return Image.fromarray(np.clip(128 + s * 255, 0, 255).astype(np.uint8), "L")


def crepe(rng):
    """Papel crepé: fine vertical crinkles that wander, with soft horizontal stretch bands (reference: crepe paper photo)."""
    n = rng.standard_normal((SIZE, SIZE))
    h = blur(n, 1.5, 15.0)  # long vertical ridges
    # sideways wobble that changes slowly down the sheet, so ridges meander instead of running ruler-straight
    wob = blur(rng.standard_normal((SIZE, SIZE)), 30, 30)
    wob = (norm(wob) - 0.5) * 16
    ys, xs = np.mgrid[0:SIZE, 0:SIZE]
    h = h[ys, (xs + wob.astype(int)) % SIZE]
    h += blur(rng.standard_normal((SIZE, SIZE)), 0.9, 3.0) * 0.12  # fine grit
    shade = emboss(h, 3.0)
    # horizontal stretch bands: two per tile, a lit crest with a soft shadow below
    y = np.arange(SIZE)[:, None]
    for c in (SIZE * 0.25, SIZE * 0.75):
        d = ((y - c + SIZE / 2) % SIZE) - SIZE / 2
        shade = shade + (np.exp(-(d / 4.5) ** 2) * 0.7 - np.exp(-((d - 8) / 7.0) ** 2) * 0.45) * shade.std() * 2.0
    return to_img(shade, 1.1)


def kozo(rng):
    """Washi de kozo: cloudy pulp with long pale fibres floating in it and a few darker inclusions."""
    pulp = blur(rng.standard_normal((SIZE, SIZE)), 18) * 1.0 + blur(rng.standard_normal((SIZE, SIZE)), 4) * 0.35
    pulp = (norm(pulp) - 0.5) * 0.8
    long_f = blur(strands(rng, 260, (60, 220), 0.035), 0.6)
    short_f = blur(strands(rng, 900, (8, 40), 0.06), 0.55)
    dark = blur(strands(rng, 60, (4, 16), 0.15), 0.6)
    shade = pulp + np.sqrt(norm(long_f)) * 0.55 + norm(short_f) * 0.45 - norm(dark) * 0.6
    shade += emboss(blur(long_f, 1.2), 0.25)
    return to_img(shade, 1.0)


def kraft(rng):
    """Kraft reciclado: mottled pulp, short brown fibres and dark flecks."""
    pulp = blur(rng.standard_normal((SIZE, SIZE)), 10) + blur(rng.standard_normal((SIZE, SIZE)), 2.2) * 0.5
    pulp = (norm(pulp) - 0.5) * 0.7
    fib = blur(strands(rng, 2200, (6, 22), 0.08), 0.5)
    flecks = np.zeros((SIZE, SIZE))
    idx = rng.integers(0, SIZE, (420, 2))
    flecks[idx[:, 0], idx[:, 1]] = rng.uniform(0.2, 1.0, 420) ** 2
    flecks = blur(flecks, 0.6) + blur(strands(rng, 120, (3, 9), 0.3), 0.5) * 0.6
    shade = pulp - norm(fib) * 0.5 - norm(flecks) * 0.9
    return to_img(shade, 1.0)


def cotton(rng):
    """Cartulina de algodón (hanafuda, cold press): soft rounded bumps, matte."""
    h = blur(rng.standard_normal((SIZE, SIZE)), 3.2) + blur(rng.standard_normal((SIZE, SIZE)), 9) * 1.5
    shade = emboss(h, 6.0) + blur(rng.standard_normal((SIZE, SIZE)), 0.7) * 0.004
    return to_img(shade, 0.9)


def laid(rng):
    """Papel verjurado: the laid lines of a mould (dense horizontal chain, sparse vertical), plus light pulp."""
    y = np.arange(SIZE)[:, None] * np.ones((1, SIZE))
    x = np.ones((SIZE, 1)) * np.arange(SIZE)[None, :]
    lines = np.cos(2 * np.pi * y / (SIZE / 64)) * 0.25
    chains = np.exp(-(((x % (SIZE / 4)) - SIZE / 8) / 1.6) ** 2) * 1.2
    pulp = blur(rng.standard_normal((SIZE, SIZE)), 12) * 0.9
    h = lines + chains + pulp + blur(rng.standard_normal((SIZE, SIZE)), 1.2) * 0.4
    return to_img(emboss(blur(h, 0.8), 2.0) + (norm(pulp) - 0.5) * 0.2, 0.9)


MAKERS = {"crepe": crepe, "kozo": kozo, "kraft": kraft, "algodon": cotton, "verjurado": laid}


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "--app":
        for i, (name, fn) in enumerate(MAKERS.items()):
            img = fn(np.random.default_rng(7072026 + i))
            step = 256 // APP_LEVELS
            q = (np.asarray(img).astype(np.int32) // step) * step + step // 2
            out = APP_OUT / f"rd_skin_{name}.png"
            Image.fromarray(np.clip(q, 0, 255).astype(np.uint8), "L").save(out, optimize=True)
            print(name, out.stat().st_size)
        return
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_OUT
    out.mkdir(parents=True, exist_ok=True)
    for i, (name, fn) in enumerate(MAKERS.items()):
        img = fn(np.random.default_rng(7072026 + i))
        img.save(out / f"{name}.png", optimize=True)
        img.save(out / f"{name}.webp", quality=88, method=6)
        print(name, (out / f"{name}.webp").stat().st_size)


if __name__ == "__main__":
    main()
