#!/usr/bin/env python3
"""Prints a Markdown size breakdown of one or more APKs (compressed size per top-level group).

Usage: apk-composition.py LABEL=PATH [LABEL=PATH ...]

Used by the Minified Release Check workflow to compare the unminified and R8-minified release APKs
of the same commit. Reads only zip metadata; it never extracts or records APK contents.
"""
import sys
import zipfile
from collections import Counter


def group(name: str) -> str:
    if name.startswith("classes") and name.endswith(".dex"):
        return "classes*.dex"
    if name.startswith("res/"):
        return "res/"
    for prefix in ("lib/", "assets/", "META-INF/", "kotlin/"):
        if name.startswith(prefix):
            return prefix
    return name if name in ("resources.arsc", "AndroidManifest.xml") else "other"


def measure(path: str) -> tuple[Counter, int, int, int]:
    sizes: Counter = Counter()
    with zipfile.ZipFile(path) as apk:
        infos = apk.infolist()
        for info in infos:
            sizes[group(info.filename)] += info.compress_size
        dex = [i for i in infos if i.filename.startswith("classes") and i.filename.endswith(".dex")]
        stored = sum(1 for i in dex if i.compress_type == zipfile.ZIP_STORED)
        return sizes, sum(i.file_size for i in dex), stored, len(dex)


def mib(value: int) -> str:
    return f"{value / 1048576:.2f}"


def main(argv: list[str]) -> int:
    items = [arg.split("=", 1) for arg in argv]
    if not items or any(len(item) != 2 for item in items):
        print(__doc__, file=sys.stderr)
        return 2
    results = {label: measure(path) for label, path in items}
    groups = sorted({g for sizes, *_ in results.values() for g in sizes})
    print("| Entry group (MiB) | " + " | ".join(results) + " |")
    print("| --- | " + " | ".join("---:" for _ in results) + " |")
    for g in groups:
        print(f"| {g} | " + " | ".join(mib(r[0][g]) for r in results.values()) + " |")
    print("| **Total entries** | " + " | ".join(f"**{mib(sum(r[0].values()))}**" for r in results.values()) + " |")
    print()
    for label, (_, dex_bytes, stored, count) in results.items():
        print(f"- {label}: dex {mib(dex_bytes)} MiB across {count} file(s), {stored} stored uncompressed")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
