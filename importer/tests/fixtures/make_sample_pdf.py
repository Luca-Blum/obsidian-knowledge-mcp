"""Writes sample.pdf, the small text PDF the importer tests convert.

Run it with `uv run python tests/fixtures/make_sample_pdf.py` only to change the fixture.
"""

from pathlib import Path

TITLE = "Notes on the Dependency Rule"
LINES = [
    (18, "The Dependency Rule"),
    (11, "Source code dependencies must point only inward, toward higher-level policies."),
    (11, "Nothing in an inner circle can know anything at all about something in an outer circle."),
    (11, "In particular, the name of something declared in an outer circle must not be mentioned"),
    (11, "by the code in an inner circle. That includes functions, classes and variables."),
    (14, "Crossing Boundaries"),
    (11, "When control flow has to cross a boundary against the direction of the dependencies,"),
    (11, "we use dynamic polymorphism to invert the source code dependency at that point."),
]


def content_stream() -> bytes:
    commands = ["BT", "72 740 Td"]
    for size, text in LINES:
        escaped = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
        commands += [f"/F1 {size} Tf", f"0 -{size + 10} Td", f"({escaped}) Tj"]
    commands.append("ET")
    return "\n".join(commands).encode("latin-1")


def pdf() -> bytes:
    stream = content_stream()
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
        b"/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        b"<< /Length %d >>\nstream\n" % len(stream) + stream + b"\nendstream",
        f"<< /Title ({TITLE}) >>".encode("latin-1"),
    ]
    out = bytearray(b"%PDF-1.4\n")
    offsets = []
    for number, body in enumerate(objects, start=1):
        offsets.append(len(out))
        out += b"%d 0 obj\n" % number + body + b"\nendobj\n"
    xref = len(out)
    out += b"xref\n0 %d\n0000000000 65535 f \n" % (len(objects) + 1)
    out += b"".join(b"%010d 00000 n \n" % offset for offset in offsets)
    out += b"trailer\n<< /Size %d /Root 1 0 R /Info 6 0 R >>\n" % (len(objects) + 1)
    out += b"startxref\n%d\n%%%%EOF\n" % xref
    return bytes(out)


if __name__ == "__main__":
    Path(__file__).with_name("sample.pdf").write_bytes(pdf())
