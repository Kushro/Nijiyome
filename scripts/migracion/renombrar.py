#!/usr/bin/env python3
"""Renombra la identidad de Tatakai Next a Nijiyome.

Dos usos:

  renombrar.py --repo            reescribe el contenido y las rutas de los archivos versionados de este repositorio
  renombrar.py --patch < a > b   convierte un parche hecho contra Kushro/Tatakai-own (git format-patch / git diff)
                                 para aplicarlo acá con `git apply` o `git am`

El segundo sirve para traer arreglos posteriores de Tatakai Next sin rehacerlos a mano.

No se reescribe el código de terceros vendorizado ni los documentos que hablan del origen. Los alias de
compatibilidad que el host de extensiones mantiene para los `.kai` existentes se agregaron a mano
(ver contracts/extension-host/globals.v1.md); este script no los crea.

Límites de --patch: un parche que toque líneas editadas a mano en Nijiyome (los alias del host de extensiones,
la guarda de contratos, las notas de terceros) no va a aplicar limpio y hay que resolverlo a mano. Las rutas se
reubican solo en las cabeceras del parche; el contenido de los archivos vendorizados no se toca.
"""
import os
import re
import subprocess
import sys

# Orden importa: de lo más específico a lo más general.
CONTENT_RULES = [
    (re.compile(r"app\.tatakai\.own\.next"), "app.nijiyome"),
    (re.compile(r"app\.tatakai\.next"), "app.nijiyome"),
    (re.compile(r"app/tatakai/own/next"), "app/nijiyome"),
    (re.compile(r"app/tatakai/next"), "app/nijiyome"),
    (re.compile(r"app_tatakai_own_next"), "app_nijiyome"),
    (re.compile(r"app_tatakai_next"), "app_nijiyome"),  # nombres JNI
    (re.compile(r"Tatakai[ _-]?Next"), "Nijiyome"),
    (re.compile(r"tatakai[ _-]?[Nn]ext"), "nijiyome"),
    (re.compile(r"TATAKAI[_-]NEXT"), "NIJIYOME"),
    (re.compile(r"Tatakai-own"), "Nijiyome"),
    (re.compile(r"tatakai-own"), "nijiyome"),
    (re.compile(r"TATAKAI"), "NIJIYOME"),
    (re.compile(r"Tatakai"), "Nijiyome"),
    (re.compile(r"tatakai"), "nijiyome"),
]

# Solo para las cabeceras de --patch: el proyecto Gradle pasó a la raíz del repositorio.
LAYOUT_RULES = [
    (re.compile(r"apps/android-native/"), ""),
    (re.compile(r"packages/tokens/"), "tokens/"),
    (re.compile(r"apps/desktop-native/native/temple-lsfg/"), "native/temple-lsfg/"),
    (re.compile(r"apps/desktop-native/packaging/"), "packaging/"),
]

# Rutas (ya en el layout de Nijiyome) cuyo contenido no se toca.
SKIP_PREFIXES = ("docs/", "scripts/migracion/")
SKIP_FRAGMENTS = ("/third_party/", "tokens/vendor/")

LEFTOVER = re.compile(r"tatakai", re.IGNORECASE)
HEADER_PREFIXES = (
    "diff --git ", "--- ", "+++ ", "rename from ", "rename to ", "copy from ", "copy to ", "Binary files ",
)


def rewrite(text: str) -> str:
    for pattern, replacement in CONTENT_RULES:
        text = pattern.sub(replacement, text)
    return text


def relocate(text: str) -> str:
    for pattern, replacement in LAYOUT_RULES:
        text = pattern.sub(replacement, text)
    return text


def skipped(path: str) -> bool:
    return path.startswith(SKIP_PREFIXES) or any(f in "/" + path for f in SKIP_FRAGMENTS)


def has_old_name(raw: bytes) -> bool:
    if LEFTOVER.search(raw.decode("latin-1")):
        return True
    for encoding in ("utf-16-le", "utf-16-be"):
        if LEFTOVER.search(raw.decode(encoding, errors="ignore")):
            return True
    return False


def run_repo() -> int:
    root = subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip()
    os.chdir(root)
    files = subprocess.check_output(["git", "ls-files", "-z"], text=True).split("\0")
    files = [f for f in files if f and not skipped(f)]

    changed = renamed = 0
    binary_hits = []
    emptied = set()  # directorios de los que salió algún archivo
    for path in files:
        try:
            with open(path, "rb") as fh:
                raw = fh.read()
        except FileNotFoundError:
            continue
        text = None
        if b"\0" not in raw:
            try:
                text = raw.decode("utf-8")
            except UnicodeDecodeError:
                text = None
        if text is None:
            if has_old_name(raw):
                binary_hits.append(path)
        else:
            new = rewrite(text)
            if new != text:
                with open(path, "wb") as fh:
                    fh.write(new.encode("utf-8"))
                changed += 1
        new_path = rewrite(path)
        if new_path != path:
            os.makedirs(os.path.dirname(new_path) or ".", exist_ok=True)
            if os.path.exists(new_path):
                print(f"COLISIÓN: {path} -> {new_path} ya existe", file=sys.stderr)
                return 1
            os.rename(path, new_path)
            renamed += 1
            emptied.add(os.path.dirname(path))

    # Solo se borran los directorios que quedaron vacíos por un renombrado, subiendo hacia la raíz.
    for start in sorted(emptied, key=len, reverse=True):
        current = start
        while current and os.path.isdir(current) and not os.listdir(current):
            os.rmdir(current)
            current = os.path.dirname(current)

    print(f"contenido reescrito: {changed} archivos; rutas renombradas: {renamed}")
    if binary_hits:
        print("binarios o no UTF-8 con el nombre viejo adentro (revisar a mano):")
        for p in binary_hits:
            print("  " + p)
    return 0


def run_patch() -> int:
    """Cabeceras: se reubican y se renombran. Contenido: solo se renombra, y no en archivos vendorizados."""
    out = []
    in_hunks = False       # False = preámbulo del correo o cabecera de un archivo
    skip_content = False   # el archivo actual está en SKIP_*
    for line in sys.stdin.read().splitlines(keepends=True):
        if line.startswith("diff --git "):
            in_hunks = False
            header = rewrite(relocate(line))
            match = re.match(r"diff --git a/(.*?) b/", header)
            skip_content = bool(match and skipped(match.group(1)))
            out.append(header)
        elif not in_hunks:
            if line.startswith("@@"):
                in_hunks = True
                out.append(line if skip_content else rewrite(line))
            else:
                # preámbulo de format-patch (asunto, lista de archivos) y cabeceras ---/+++/rename
                out.append(rewrite(relocate(line)))
        else:
            out.append(line if skip_content else rewrite(line))
    sys.stdout.write("".join(out))
    return 0


if __name__ == "__main__":
    if sys.argv[1:] == ["--repo"]:
        sys.exit(run_repo())
    if sys.argv[1:] == ["--patch"]:
        sys.exit(run_patch())
    print(__doc__, file=sys.stderr)
    sys.exit(2)
