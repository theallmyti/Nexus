"""Builds LEGAL_AGREEMENT.rtf (formatted, for the installer) from LEGAL_AGREEMENT.txt.

The .txt is the source of truth for the wording. This only changes presentation:
- the title and "PART n:" lines become headings,
- "Key: value" lines at the top stay one per line,
- numbered items ("1. LABEL: text") get a bold label and a hanging indent,
- bullets ("- text") become indented bullet points,
- hard-wrapped lines are joined back into paragraphs; URLs are shown as links.
Run after editing the .txt:  python installer/make_legal_rtf.py
"""
import re
from pathlib import Path

here = Path(__file__).parent
lines = (here / "LEGAL_AGREEMENT.txt").read_text(encoding="utf-8").splitlines()


def esc(s: str) -> str:
    s = s.replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}")
    return "".join(c if ord(c) < 128 else f"\\u{ord(c) - 65536 if ord(c) > 32767 else ord(c)}?" for c in s)


def fmt(s: str) -> str:
    return re.sub(r"(https?://\S+)", r"{\\cf2\\ul \1}", esc(s))


out: list[str] = []
block: dict | None = None  # {"kind": ..., "head": str, "text": [str]}


def flush():
    global block
    if not block:
        return
    text = fmt(" ".join(block["text"]))
    k = block["kind"]
    if k == "item":
        num, label = block["head"]
        out.append(r"{\pard\fi-300\li300\sb80\sa60\sl276\slmult1 " + esc(num) + r"{\b " + esc(label) + "} " + text + r"\par}")
    elif k == "bullet":
        out.append(r"{\pard\fi-180\li560\sa30\sl276\slmult1 \bullet  " + text + r"\par}")
    elif k == "meta":
        out.append(r"{\pard\sa0\sl276\slmult1 " + text + r"\par}")
    else:
        out.append(r"{\pard\sb60\sa100\sl276\slmult1 " + text + r"\par}")
    block = None


title_done = False
for raw in lines:
    line = raw.rstrip()
    s = line.strip()
    if re.fullmatch(r"[=\-]{10,}", s):
        continue
    if not s:
        flush()
        continue
    indent = len(line) - len(line.lstrip())

    if not title_done and s.isupper() and "AGREEMENT" in s:
        flush()
        out.append(r"{\pard\sa120\b\fs32 " + esc(s.title()) + r"\par}")
        title_done = True
        continue
    if re.match(r"PART \d+:", s):
        flush()
        out.append(r"{\pard\sb280\sa100\b\fs24\cf2 " + esc(s) + r"\cf1\par}")
        continue
    m = re.match(r"(\d+\. )(.*)", s)
    if m and indent == 0:
        flush()
        label_m = re.match(r"([A-Z0-9 &()\-/']+:)\s*(.*)", m.group(2))
        if label_m:
            block = {"kind": "item", "head": (m.group(1), label_m.group(1)), "text": [label_m.group(2)] if label_m.group(2) else []}
        else:
            block = {"kind": "item", "head": (m.group(1), ""), "text": [m.group(2)]}
        continue
    if s.startswith("- "):
        flush()
        block = {"kind": "bullet", "head": None, "text": [s[2:]]}
        continue
    if indent == 0 and re.match(r"[A-Z][A-Za-z &]+: \S", s) and (block is None or block["kind"] == "meta"):
        flush()  # header facts: one per line
        block = {"kind": "meta", "head": None, "text": [s]}
        continue
    if block is None:
        block = {"kind": "para", "head": None, "text": [s]}
    elif block["kind"] in ("item", "bullet", "para"):
        block["text"].append(s)  # wrapped continuation line
    else:
        flush()
        block = {"kind": "para", "head": None, "text": [s]}
flush()

rtf = (
    r"{\rtf1\ansi\ansicpg1252\deff0"
    r"{\fonttbl{\f0\fswiss\fcharset0 Segoe UI;}}"
    r"{\colortbl;\red62\green39\blue35;\red21\green101\blue192;}"
    r"\viewkind4\uc1\f0\fs19\cf1 " + "\n".join(out) + "}"
)
(here / "LEGAL_AGREEMENT.rtf").write_text(rtf, encoding="ascii")
print("wrote", here / "LEGAL_AGREEMENT.rtf")
