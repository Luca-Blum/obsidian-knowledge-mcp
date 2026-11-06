"""One importer run over a temporary Vault, with real Docling.

Each test builds a Vault, runs the importer once as it would be run by hand
and asserts only on the files it leaves in the Vault.
"""

import datetime
import shutil
import subprocess
import sys
from pathlib import Path

import yaml

SAMPLE_PDF = Path(__file__).parent / "fixtures" / "sample.pdf"


def run_importer(*vaults: Path) -> None:
    args = [arg for vault in vaults for arg in ("--vault", str(vault))]
    subprocess.run([sys.executable, "-m", "pdf_importer", *args], check=True)


def frontmatter(note: Path) -> dict:
    _, header, _ = note.read_text(encoding="utf-8").split("---\n", 2)
    return yaml.safe_load(header)


def test_imports_a_pdf_from_a_topic_inbox_into_a_source_note(tmp_path):
    vault = tmp_path / "Engineering"
    inbox = vault / "Coaching" / "_inbox"
    inbox.mkdir(parents=True)
    shutil.copy(SAMPLE_PDF, inbox / "dependency-rule.pdf")

    run_importer(vault)

    note = vault / "Coaching" / "Source-Notes" / "dependency-rule.md"
    assert frontmatter(note) == {
        "type": "source",
        "title": "Notes on the Dependency Rule",
        "lang": "en",
        "imported": datetime.date.today(),
        "source": "[[_sources/dependency-rule.pdf]]",
    }
    body = note.read_text(encoding="utf-8")
    assert "## The Dependency Rule" in body
    assert "Source code dependencies must point only inward" in body
    assert (vault / "Coaching" / "_sources" / "dependency-rule.pdf").read_bytes() == SAMPLE_PDF.read_bytes()
    assert list(inbox.iterdir()) == []


def test_takes_the_title_from_the_first_heading_when_the_pdf_has_no_metadata_title(tmp_path):
    vault = tmp_path / "Engineering"
    inbox = vault / "Coaching" / "_inbox"
    inbox.mkdir(parents=True)
    # Replacements of equal length keep every byte offset, so the PDF stays valid.
    untitled = (
        SAMPLE_PDF.read_bytes()
        .replace(b"/Title (", b"/Xitle (")
        .replace(b"(The Dependency Rule)", b"(The Dependency_Rule)")
    )
    (inbox / "untitled.pdf").write_bytes(untitled)

    run_importer(vault)

    assert frontmatter(vault / "Coaching" / "Source-Notes" / "untitled.md")["title"] == "The Dependency_Rule"


def test_leaves_everything_alone_that_is_not_a_pdf_directly_inside_a_topic_inbox(tmp_path):
    vault = tmp_path / "Engineering"
    untouched = [
        vault / "Coaching" / "_inbox" / "notes.txt",
        vault / "Coaching" / "_inbox" / "_failed" / "broken.pdf",
        vault / "_templates" / "_inbox" / "template.pdf",
        vault / ".obsidian" / "_inbox" / "plugin.pdf",
        vault / "_inbox" / "root.pdf",
    ]
    for file in untouched:
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_bytes(b"not converted")
    files_before = sorted(vault.rglob("*"))

    run_importer(vault)

    assert sorted(vault.rglob("*")) == files_before
