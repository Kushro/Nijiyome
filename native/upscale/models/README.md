# Modelos Real-ESRGAN `realesr-animevideov3`

Pesos oficiales en formato ncnn (`.param` y `.bin`). Licencia BSD-3-Clause de Real-ESRGAN, en `LICENSE-Real-ESRGAN.txt`.

- **Origen:** release `v0.2.5.0` de [xinntao/Real-ESRGAN](https://github.com/xinntao/Real-ESRGAN/releases/tag/v0.2.5.0), archivo `realesrgan-ncnn-vulkan-20220424-ubuntu.zip`, carpeta `models/`.
  - URL: `https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesrgan-ncnn-vulkan-20220424-ubuntu.zip`
  - SHA-256 del zip: `e5aa6eb131234b87c0c51f82b89390f5e3e642b7b70f2b9bbe95b6a285a40c96`
- **Descargado:** 2026-11-27.

| Archivo | Bytes | SHA-256 |
|---|---|---|
| `realesr-animevideov3-x2.param` | 3173 | `b88ff4f00ebf019a7fdac17fdd45a7fd3665d37509efc5baf2e4da2e24420a04` |
| `realesr-animevideov3-x2.bin` | 1247368 | `548a36f9c3f4ab8da56cd3b13badf23968bee207b396dad14d04b830e5f2ab2d` |
| `realesr-animevideov3-x3.param` | 3173 | `d1a5755008791d09b57e3425fc9dd0bd26b00fdf79c606210bc0e693f8230881` |
| `realesr-animevideov3-x3.bin` | 1247368 | `548a36f9c3f4ab8da56cd3b13badf23968bee207b396dad14d04b830e5f2ab2d` |
| `realesr-animevideov3-x4.param` | 3077 | `850a248e7c14c27e5bd8cf7265113a9441036a7db63963bb8aa5169d788a435e` |
| `realesr-animevideov3-x4.bin` | 1247368 | `548a36f9c3f4ab8da56cd3b13badf23968bee207b396dad14d04b830e5f2ab2d` |

Los tres `.bin` son idénticos. La red siempre escala x4 por dentro; los `.param` de x2 y x3 solo agregan al final un `Interp` bicúbico de 0,5 o 0,75. Por eso x2 cuesta en la GPU lo mismo que x4. El motor nativo usa `realesr-animevideov3-x4.bin` cuando falta el `.bin` de la escala pedida, así que el empaquetado puede incluir un solo `.bin` (ahorra unos 2,5 MB).
