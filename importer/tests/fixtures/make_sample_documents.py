"""Writes sample.docx, sample.doc, sample.odt, sample.rtf and sample.epub, the small documents the importer tests convert,
and headless.docx, a document without a heading.

Run it with `uv run python tests/fixtures/make_sample_documents.py` only to change the fixtures.
It needs LibreOffice (`soffice`) to derive .doc, .odt and .rtf from the .docx.
"""

import subprocess
import tempfile
import zipfile
from pathlib import Path

import docx

HERE = Path(__file__).parent
TITLE = "Notes on the Dependency Rule"
INTRO = "Source code dependencies must point only inward, toward higher-level policies."
SECTION = "Crossing Boundaries"
BOUNDARY = "We use dynamic polymorphism to invert the source code dependency at that point."
TABLE = [("Circle", "Knows about"), ("Entities", "Nothing"), ("Use cases", "Entities")]


def write_docx(target: Path) -> None:
    document = docx.Document()
    document.add_heading(TITLE, level=1)
    document.add_paragraph(INTRO)
    document.add_heading(SECTION, level=2)
    document.add_paragraph(BOUNDARY)
    table = document.add_table(rows=len(TABLE), cols=2)
    for row, cells in zip(table.rows, TABLE):
        for cell, text in zip(row.cells, cells):
            cell.text = text
    document.save(target)


def convert(source: Path, extension: str) -> None:
    with tempfile.TemporaryDirectory() as out:
        subprocess.run(
            ["soffice", "--headless", "--convert-to", extension, "--outdir", out, str(source)],
            check=True, capture_output=True,
        )
        (HERE / f"sample.{extension}").write_bytes((Path(out) / f"sample.{extension}").read_bytes())


def write_epub(target: Path) -> None:
    chapter = (
        '<?xml version="1.0" encoding="utf-8"?>'
        '<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Chapter</title></head><body>'
        f"<h1>{TITLE}</h1><p>{INTRO}</p><h2>{SECTION}</h2><p>{BOUNDARY}</p>"
        "<table><tr><th>Circle</th><th>Knows about</th></tr>"
        "<tr><td>Entities</td><td>Nothing</td></tr><tr><td>Use cases</td><td>Entities</td></tr></table>"
        "</body></html>"
    )
    package = (
        '<?xml version="1.0" encoding="utf-8"?>'
        '<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">'
        '<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">'
        '<dc:identifier id="id">sample</dc:identifier><dc:title>Sample</dc:title><dc:language>en</dc:language>'
        "</metadata><manifest>"
        '<item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>'
        '<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>'
        '</manifest><spine><itemref idref="chapter"/></spine></package>'
    )
    nav = (
        '<?xml version="1.0" encoding="utf-8"?>'
        '<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Nav</title></head>'
        '<body><nav epub:type="toc"><ol><li><a href="chapter.xhtml">Chapter</a></li></ol></nav></body></html>'
    )
    container = (
        '<?xml version="1.0"?>'
        '<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">'
        '<rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>'
        "</rootfiles></container>"
    )
    with zipfile.ZipFile(target, "w") as epub:
        epub.writestr("mimetype", "application/epub+zip", compress_type=zipfile.ZIP_STORED)
        epub.writestr("META-INF/container.xml", container)
        epub.writestr("OEBPS/content.opf", package)
        epub.writestr("OEBPS/nav.xhtml", nav)
        epub.writestr("OEBPS/chapter.xhtml", chapter)


def write_headless_docx(target: Path) -> None:
    document = docx.Document()
    document.add_paragraph(INTRO)
    document.save(target)


if __name__ == "__main__":
    write_headless_docx(HERE / "headless.docx")
    write_docx(HERE / "sample.docx")
    for extension in ("doc", "odt", "rtf"):
        convert(HERE / "sample.docx", extension)
    write_epub(HERE / "sample.epub")
