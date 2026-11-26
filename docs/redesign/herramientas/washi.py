#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Washi fibre texture for the Tanabata direction (docs/redesign/CONTRATO-COMPOSE.md, "Textura washi").

Writes a seamless, tileable grayscale PNG: dark ground, light fibres. The app reads it as a mask (luminance = coverage) and tints
it with the palette's paper color, so the file carries no color of its own.

    python3 docs/redesign/herramientas/washi.py [output.png]

Deterministic (fixed seed). Needs numpy and Pillow. Every step works on a torus (coordinates wrap, blur and cloud noise are
done in the frequency domain), so the left edge continues into the right one and the top into the bottom.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

SIZE = 512
SEED = 7_7_2026  # Tanabata, 7/7
LEVELS = 16  # gray levels kept in the file: the tint is applied at low alpha, so 16 steps are invisible and the PNG stays small

DEFAULT_OUT = (
    Path(__file__).resolve().parents[3]
    / "core/designsystem/src/commonMain/composeResources/drawable/rd_tanabata_washi.png"
)


def periodic_blur(img: np.ndarray, sigma: float) -> np.ndarray:
    """Gaussian blur on a torus (FFT), so the result still tiles."""
    fy = np.fft.fftfreq(img.shape[0])[:, None]
    fx = np.fft.fftfreq(img.shape[1])[None, :]
    kernel = np.exp(-2.0 * (np.pi * sigma) ** 2 * (fx * fx + fy * fy))
    return np.real(np.fft.ifft2(np.fft.fft2(img) * kernel))


def cloud(rng: np.random.Generator, scale: float) -> np.ndarray:
    """Soft, tileable unevenness of the pulp: white noise low-passed in the frequency domain, normalised to 0..1."""
    noise = rng.standard_normal((SIZE, SIZE))
    out = periodic_blur(noise, scale)
    out -= out.min()
    return out / max(out.max(), 1e-9)


def splat(canvas: np.ndarray, xs: np.ndarray, ys: np.ndarray, weight: np.ndarray) -> None:
    """Adds bilinear-weighted points to the canvas with wrap-around, so strokes are anti-aliased and seamless."""
    x0 = np.floor(xs).astype(np.int64)
    y0 = np.floor(ys).astype(np.int64)
    fx = xs - x0
    fy = ys - y0
    for dx, dy, w in (
        (0, 0, (1 - fx) * (1 - fy)),
        (1, 0, fx * (1 - fy)),
        (0, 1, (1 - fx) * fy),
        (1, 1, fx * fy),
    ):
        np.add.at(canvas, ((y0 + dy) % SIZE, (x0 + dx) % SIZE), w * weight)


def fibres(rng: np.random.Generator, count: int, length: tuple, bend: float, strength: tuple, step: float = 0.5) -> np.ndarray:
    """Thin curved fibres: each one a smooth random walk whose heading drifts a little at every step."""
    canvas = np.zeros((SIZE, SIZE))
    for _ in range(count):
        n = int(rng.uniform(*length) / step)
        heading = rng.uniform(0, 2 * np.pi)
        # Curvature changes slowly along the fibre: integrate a low-passed random signal.
        drift = np.cumsum(rng.standard_normal(n)) * bend / np.sqrt(n)
        drift = np.convolve(drift, np.ones(9) / 9, mode="same")
        angles = heading + drift
        xs = rng.uniform(0, SIZE) + np.cumsum(np.cos(angles)) * step
        ys = rng.uniform(0, SIZE) + np.cumsum(np.sin(angles)) * step
        # Fibres fade in and out at their ends and vary in density along their length.
        t = np.linspace(0, np.pi, n)
        taper = np.sin(t) ** 0.6
        wobble = 0.75 + 0.25 * np.sin(t * rng.uniform(2, 6) + rng.uniform(0, 6.3))
        weight = taper * wobble * rng.uniform(*strength) * step
        splat(canvas, xs % SIZE, ys % SIZE, weight)
    return canvas


def main() -> None:
    out_path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_OUT
    rng = np.random.default_rng(SEED)

    # Uneven pulp: two scales of cloud, very soft.
    pulp = 0.65 * cloud(rng, 22.0) + 0.35 * cloud(rng, 6.0)

    # Long kozo fibres (few, wavy), medium fibres, and many short fine ones.
    long_f = fibres(rng, 60, (180, 460), 0.7, (0.25, 1.0))
    mid_f = fibres(rng, 240, (40, 150), 0.5, (0.15, 0.7))
    short_f = fibres(rng, 700, (6, 30), 0.4, (0.1, 0.5))
    strands = periodic_blur(long_f, 0.55) + periodic_blur(mid_f, 0.5) + periodic_blur(short_f, 0.45)
    # Soft compression: fibres that cross do not burn out to white.
    strands = 1.0 - np.exp(-strands * 1.8)

    # Fine grain of the sheet.
    grain = periodic_blur(rng.standard_normal((SIZE, SIZE)), 0.6)
    grain = (grain - grain.mean()) / (grain.std() + 1e-9)

    img = 0.30 * pulp + 0.70 * strands + 0.03 * grain
    img -= np.percentile(img, 1)
    img /= np.percentile(img, 99.7)
    img = np.clip(img, 0.0, 1.0)

    q = np.round(img * (LEVELS - 1)) * (255 // (LEVELS - 1))
    out_path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(q.astype(np.uint8), mode="L").save(out_path, optimize=True)
    print(f"{out_path} {out_path.stat().st_size} bytes")


if __name__ == "__main__":
    main()
