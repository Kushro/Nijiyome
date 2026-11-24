#!/usr/bin/env python3
"""Pasa un módulo Android del proyecto a la estructura multiplataforma (Android + escritorio).

  modulo_kmp.py feature/home            mueve fuentes y recursos y reescribe los usos de recursos del módulo
  modulo_kmp.py feature/home --check    solo informa qué quedaría para revisar a mano

Qué hace:
  - src/main/kotlin  -> src/commonMain/kotlin        src/test/kotlin -> src/commonTest/kotlin
  - src/main/res/values*/ (cadenas y plurales)  -> src/commonMain/composeResources/values*/
  - el resto de src/main (manifest, otros recursos, assets) -> src/androidMain
  - en el código: R.string / R.plurals -> Res.string / Res.plurals, con los imports de Compose Multiplatform
  - en las cadenas movidas: los escapes de Android que Compose Resources no entiende (%% -> %, \' -> ', \" -> ")
    y los marcadores sin posición (%s, %d -> %1$s, %2$d...)

Qué NO hace (queda en la lista que imprime al final, para resolver a mano y con el compilador):
  - el build.gradle.kts del módulo
  - APIs de Android en el código común (LocalContext, permisos, Intents...): van detrás de expect/actual o de :core:ui
  - funciones que devuelven el id de un recurso como Int: pasan a StringResource
  - lecturas fuera de un composable (context.getString): pasan a org.jetbrains.compose.resources.getString (suspend)
  - una misma llamada que mezcla un texto propio con uno de :core:ui
  - cadenas con formatos que Compose Resources no soporta (%1$.1f, %1$+d...): el número se formatea en el código
    y la cadena pasa a %1$s
  - recursos Android de OTRO módulo usados desde código común (FgR.string.x, QualityR.string.x)
"""
import pathlib
import re
import subprocess
import sys

STRING_FILE = re.compile(r"<(string|plurals|string-array)\b")
ANDROID_HINTS = re.compile(
    r"^import (android\.|androidx\.(core|activity|work|browser|documentfile|camera|media3)\b)|LocalContext\.current|"
    r"LocalConfiguration\.current|LocalView\.current|rememberLauncherForActivityResult|Build\.VERSION|\bAndroidView\b|"
    r"\bBackHandler\b|\bR\.(drawable|font|raw|id|color)\.",
    re.M,
)
INT_RES = re.compile(r"\): Int\??\s*=\s*when|@StringRes|@PluralsRes|\.getString\(|\.getQuantityString\(")
# Android resources of another module (an alias such as FgR / QualityR, or a full package): not readable from common code.
FOREIGN_R = re.compile(r"\b(?!Ui)[A-Z]\w*R\.(string|plurals)\.|\bapp\.nijiyome\.[\w.]+\.R\.(string|plurals)\.")
# What Compose Resources substitutes: %1$s and %1$d only.
SUPPORTED_ARG = re.compile(r"%\d+\$[sd]")
ANY_ARG = re.compile(r"%(\d+\$)?[-+ #,0(]*\d*(\.\d+)?[a-zA-Z]")


def convert_android_string(text: str) -> str:
    """Android string escapes -> what Compose Resources expects, for the text of one <string> or <item>."""
    counter = [0]

    def positional(match: re.Match) -> str:
        counter[0] += 1
        return f"%{counter[0]}${match.group(1)}"

    if "%" in text and not re.search(r"%\d+\$", text):
        text = re.sub(r"(?<!%)%([sd])", positional, text)  # %s %d -> %1$s %2$d, in order
    text = text.replace("%%", "%")
    for esc, plain in (("\\'", "'"), ('\\"', '"'), ("\\@", "@"), ("\\?", "?")):
        text = text.replace(esc, plain)
    return text


def convert_strings_file(xml: pathlib.Path) -> list[str]:
    """Rewrites the strings of a file already moved to composeResources. Returns what needs a person."""
    s = xml.read_text()
    problems = []

    def element(match: re.Match) -> str:
        opening, body, closing = match.group(1), match.group(2), match.group(3)
        new_body = convert_android_string(body)
        for arg in ANY_ARG.finditer(new_body):
            if not SUPPORTED_ARG.fullmatch(arg.group(0)):
                name = re.search(r'name="([^"]+)"', opening)
                problems.append(f"{name.group(1) if name else '?'}: formato no soportado '{arg.group(0)}'")
        if re.search(r"<(b|i|u|a|font|xliff:g)\b", new_body):
            name = re.search(r'name="([^"]+)"', opening)
            problems.append(f"{name.group(1) if name else '?'}: contiene etiquetas")
        return opening + new_body + closing

    out = re.sub(r"(<string\b[^>]*>)(.*?)(</string>)", element, s, flags=re.S)
    out = re.sub(r"(<item\b[^>]*>)(.*?)(</item>)", element, out, flags=re.S)
    if out != s:
        xml.write_text(out)
    return problems


def namespace(module: str) -> str:
    seg = module.strip("/").split("/")
    seg = [s.replace("-", "_") for s in seg]
    return "app.nijiyome." + ".".join(seg[1:] if seg[0] == "core" else seg)


def git_mv(src: pathlib.Path, dst: pathlib.Path) -> None:
    dst.parent.mkdir(parents=True, exist_ok=True)
    subprocess.check_call(["git", "mv", str(src), str(dst)])


def swap_import(s: str, old: str, new: str) -> str:
    if old not in s:
        return s
    return s.replace(old, "" if new in s else new)


def rewrite_kotlin(path: pathlib.Path, ns: str) -> bool:
    s = path.read_text()
    o = s
    uses = bool(re.search(r"(?<![\w.])R\.(string|plurals)\.", s))
    s = re.sub(r"(?<![\w.])R\.(string|plurals)\.", r"Res.\1.", s)
    if not FOREIGN_R.search(s):
        # A file that still passes Android resource ids of another module keeps the Android functions (and is reported).
        s = swap_import(s, "import androidx.compose.ui.res.stringResource\n", "import org.jetbrains.compose.resources.stringResource\n")
        s = swap_import(s, "import androidx.compose.ui.res.pluralStringResource\n", "import org.jetbrains.compose.resources.pluralStringResource\n")
    if not re.search(r"(?<![\w.])R\.\w+\.", s):
        s = s.replace(f"import {ns}.R\n", "")
    if uses and f"import {ns}.resources.Res\n" not in s:
        first = re.search(r"^import .*\n", s, re.M)
        pkg = re.search(r"^package .*\n", s, re.M)
        pos = first.start() if first else (pkg.end() if pkg else 0)
        s = s[:pos] + ("" if first else "\n") + f"import {ns}.resources.Res\nimport {ns}.resources.*\n" + s[pos:]
    if s != o:
        path.write_text(s)
    return s != o


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    check = "--check" in sys.argv
    if len(args) != 1:
        print(__doc__, file=sys.stderr)
        return 2
    root = pathlib.Path(subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip())
    module = args[0].strip("/")
    m = root / module
    ns = namespace(module)
    main_dir = m / "src/main"
    string_problems: list[str] = []
    if not check:
        if not main_dir.is_dir() and not (m / "src/commonMain").is_dir():
            print(f"{module}: no tiene src/main ni src/commonMain", file=sys.stderr)
            return 1
        if (main_dir / "kotlin").is_dir():
            git_mv(main_dir / "kotlin", m / "src/commonMain/kotlin")
        if (m / "src/test/kotlin").is_dir():
            git_mv(m / "src/test/kotlin", m / "src/commonTest/kotlin")
        if (m / "src/test/resources").is_dir():
            git_mv(m / "src/test/resources", m / "src/commonTest/resources")
        res = main_dir / "res"
        if res.is_dir():
            for values in sorted(res.glob("values*")):
                for xml in sorted(values.glob("*.xml")):
                    if STRING_FILE.search(xml.read_text()):
                        target = m / "src/commonMain/composeResources" / values.name / xml.name
                        git_mv(xml, target)
                        for problem in convert_strings_file(target):
                            string_problems.append(f"{target.relative_to(root)}: {problem}")
        for rest in sorted(main_dir.iterdir()) if main_dir.is_dir() else []:
            target = m / "src/androidMain" / rest.name
            if rest.is_file():
                git_mv(rest, target)
            elif target.exists():
                for f in sorted(x for x in rest.rglob("*") if x.is_file()):  # merge into an existing folder, file by file
                    git_mv(f, target / f.relative_to(rest))
            elif any(f.is_file() for f in rest.rglob("*")):
                git_mv(rest, target)
        for d in sorted((m / "src").rglob("*"), reverse=True):
            if d.is_dir() and not any(d.iterdir()):
                d.rmdir()
        changed = sum(rewrite_kotlin(f, ns) for f in (m / "src").rglob("*.kt"))
        print(f"{module}: fuentes movidas; {changed} archivos reescritos; Res en {ns}.resources")

    print("\nPara revisar a mano:")
    found = False
    for f in sorted((m / "src").rglob("*.kt")):
        if "/androidMain/" in str(f):
            continue
        s = f.read_text()
        rel = f.relative_to(root)
        for name, rx in (
            ("API de Android en código común", ANDROID_HINTS),
            ("id de recurso como Int / getString / getQuantityString", INT_RES),
            ("recurso Android de otro módulo", FOREIGN_R),
        ):
            lines = [str(s.count("\n", 0, mm.start()) + 1) for mm in rx.finditer(s)]
            if lines:
                found = True
                print(f"  {rel}: {name} (líneas {', '.join(lines[:12])}{'…' if len(lines) > 12 else ''})")
    for problem in string_problems:
        found = True
        print(f"  {problem}")
    if not found:
        print("  nada detectado; compilar los dos destinos igual.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
