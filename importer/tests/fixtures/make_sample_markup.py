"""Writes sample.pptx, sample.ppt, sample.odp, sample.html, sample.mhtml, sample.adoc, sample.tex and sample.xml (JATS),
the small slide decks and markup files the importer tests convert, and not-jats.xml, an XML file that is not JATS.

Run it with `uv run python tests/fixtures/make_sample_markup.py` only to change the fixtures.
It needs LibreOffice (`soffice`) to derive .ppt and .odp from the .pptx.
"""

import subprocess
import tempfile
from pathlib import Path

from pptx import Presentation

HERE = Path(__file__).parent
TITLE = "Notes on the Dependency Rule"
INTRO = "Source code dependencies must point only inward, toward higher-level policies."
SECTION = "Crossing Boundaries"
BOUNDARY = "We use dynamic polymorphism to invert the source code dependency at that point."


def write_pptx(target: Path) -> None:
    presentation = Presentation()
    first = presentation.slides.add_slide(presentation.slide_layouts[1])
    first.shapes.title.text = TITLE
    first.placeholders[1].text = INTRO
    second = presentation.slides.add_slide(presentation.slide_layouts[1])
    second.shapes.title.text = SECTION
    second.placeholders[1].text = BOUNDARY
    presentation.save(target)


def convert(source: Path, extension: str) -> None:
    with tempfile.TemporaryDirectory() as out:
        subprocess.run(
            ["soffice", "--headless", "--convert-to", extension, "--outdir", out, str(source)],
            check=True, capture_output=True,
        )
        (HERE / f"sample.{extension}").write_bytes((Path(out) / f"sample.{extension}").read_bytes())


HTML = f"""<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>Browser tab</title></head>
<body><h1>{TITLE}</h1><p>{INTRO}</p><h2>{SECTION}</h2><p>{BOUNDARY}</p></body></html>
"""

MHTML = (
    "MIME-Version: 1.0\r\n"
    "Content-Type: multipart/related; boundary=\"----sample\"; type=\"text/html\"\r\n"
    "\r\n"
    "------sample\r\n"
    "Content-Type: text/html; charset=\"utf-8\"\r\n"
    "Content-Location: http://example.com/index.html\r\n"
    "\r\n"
    f"{HTML}\r\n"
    "------sample--\r\n"
)

ADOC = f"""= {TITLE}

{INTRO}

== {SECTION}

{BOUNDARY}
"""

TEX = rf"""\documentclass{{article}}
\title{{{TITLE}}}
\begin{{document}}
\maketitle
\section{{{TITLE}}}
{INTRO}
\section{{{SECTION}}}
{BOUNDARY}
\end{{document}}
"""

JATS = f"""<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE article PUBLIC "-//NLM//DTD JATS (Z39.96) Journal Archiving and Interchange DTD v1.2 20190208//EN" "JATS-archivearticle1.dtd">
<article xmlns:xlink="http://www.w3.org/1999/xlink" article-type="research-article">
<front><article-meta><title-group><article-title>{TITLE}</article-title></title-group></article-meta></front>
<body><sec><title>{SECTION}</title><p>{INTRO}</p><p>{BOUNDARY}</p></sec></body>
</article>
"""

NOT_JATS = '<?xml version="1.0" encoding="UTF-8"?>\n<config><entry key="a">1</entry></config>\n'


if __name__ == "__main__":
    write_pptx(HERE / "sample.pptx")
    for extension in ("ppt", "odp"):
        convert(HERE / "sample.pptx", extension)
    (HERE / "sample.html").write_text(HTML, encoding="utf-8")
    (HERE / "sample.mhtml").write_text(MHTML, encoding="utf-8")
    (HERE / "sample.adoc").write_text(ADOC, encoding="utf-8")
    (HERE / "sample.tex").write_text(TEX, encoding="utf-8")
    (HERE / "sample.xml").write_text(JATS, encoding="utf-8")
    (HERE / "not-jats.xml").write_text(NOT_JATS, encoding="utf-8")
