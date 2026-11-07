"""One importer run over a temporary Vault, with real Docling.

Each test builds a Vault, runs the importer once as it would be run by hand
and asserts only on the files it leaves in the Vault.
"""

import datetime
import os
import shutil
import subprocess
import sys
from pathlib import Path

import yaml

SAMPLE_PDF = Path(__file__).parent / "fixtures" / "sample.pdf"


def run_importer(*vaults: Path) -> None:
    args = [arg for vault in vaults for arg in ("--vault", str(vault))]
    subprocess.run([sys.executable, "-m", "pdf_importer", *args], check=True)


def settled(pdf: Path) -> Path:
    """Backdates the PDF so the importer treats it as fully copied."""
    long_ago = pdf.stat().st_mtime - 3600
    os.utime(pdf, (long_ago, long_ago))
    return pdf


def put_in_inbox(vault: Path, name: str, content: bytes) -> Path:
    inbox = vault / "Coaching" / "_inbox"
    inbox.mkdir(parents=True, exist_ok=True)
    pdf = inbox / name
    pdf.write_bytes(content)
    return settled(pdf)


def snapshot(vault: Path) -> dict[Path, bytes]:
    return {f.relative_to(vault): f.read_bytes() for f in vault.rglob("*") if f.is_file()}


def frontmatter(note: Path) -> dict:
    _, header, _ = note.read_text(encoding="utf-8").split("---\n", 2)
    return yaml.safe_load(header)


def test_imports_a_pdf_from_a_topic_inbox_into_a_source_note(tmp_path):
    vault = tmp_path / "Engineering"
    inbox = vault / "Coaching" / "_inbox"
    inbox.mkdir(parents=True)
    settled(Path(shutil.copy(SAMPLE_PDF, inbox / "dependency-rule.pdf")))

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
    put_in_inbox(vault, "untitled.pdf", untitled)

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


def test_importing_the_same_pdf_twice_keeps_the_first_note_and_adds_suffixed_copies(tmp_path):
    vault = tmp_path / "Engineering"
    topic = vault / "Coaching"
    sample = SAMPLE_PDF.read_bytes()
    put_in_inbox(vault, "dependency-rule.pdf", sample)
    run_importer(vault)
    first_note = (topic / "Source-Notes" / "dependency-rule.md").read_bytes()

    put_in_inbox(vault, "dependency-rule.pdf", sample)
    run_importer(vault)

    assert (topic / "Source-Notes" / "dependency-rule.md").read_bytes() == first_note
    second = topic / "Source-Notes" / "dependency-rule-1.md"
    assert frontmatter(second)["source"] == "[[_sources/dependency-rule-1.pdf]]"
    assert (topic / "_sources" / "dependency-rule-1.pdf").read_bytes() == sample
    assert list((topic / "_inbox").iterdir()) == []


def test_a_note_without_a_pdf_in_sources_still_forces_a_suffix_on_both(tmp_path):
    vault = tmp_path / "Engineering"
    topic = vault / "Coaching"
    (topic / "Source-Notes").mkdir(parents=True)
    existing = topic / "Source-Notes" / "dependency-rule.md"
    existing.write_text("my own words", encoding="utf-8")
    put_in_inbox(vault, "dependency-rule.pdf", SAMPLE_PDF.read_bytes())

    run_importer(vault)

    assert existing.read_text(encoding="utf-8") == "my own words"
    assert (topic / "Source-Notes" / "dependency-rule-1.md").exists()
    assert (topic / "_sources" / "dependency-rule-1.pdf").exists()


def test_a_corrupt_pdf_ends_in_failed_with_a_log_and_no_note(tmp_path):
    vault = tmp_path / "Engineering"
    topic = vault / "Coaching"
    corrupt = os.urandom(2048)
    put_in_inbox(vault, "broken.pdf", corrupt)
    other = topic / "Source-Notes" / "mine.md"
    other.parent.mkdir()
    other.write_text("untouched", encoding="utf-8")

    run_importer(vault)

    assert (topic / "_inbox" / "_failed" / "broken.pdf").read_bytes() == corrupt
    assert (topic / "_inbox" / "_failed" / "broken.log").read_text(encoding="utf-8").strip()
    assert sorted(f.name for f in vault.rglob("*") if f.is_file()) == ["broken.log", "broken.pdf", "mine.md"]
    assert other.read_text(encoding="utf-8") == "untouched"


def test_a_failed_pdf_is_suffixed_when_the_name_is_taken_in_failed(tmp_path):
    vault = tmp_path / "Engineering"
    failed = vault / "Coaching" / "_inbox" / "_failed"
    failed.mkdir(parents=True)
    (failed / "broken.pdf").write_bytes(b"old")
    (failed / "broken.log").write_text("old log", encoding="utf-8")
    put_in_inbox(vault, "broken.pdf", os.urandom(512))

    run_importer(vault)

    assert (failed / "broken.pdf").read_bytes() == b"old"
    assert (failed / "broken.log").read_text(encoding="utf-8") == "old log"
    assert (failed / "broken-1.pdf").exists()
    assert (failed / "broken-1.log").exists()


def test_one_failing_pdf_does_not_stop_the_others(tmp_path):
    vault = tmp_path / "Engineering"
    topic = vault / "Coaching"
    put_in_inbox(vault, "a-broken.pdf", os.urandom(512))
    put_in_inbox(vault, "b-good.pdf", SAMPLE_PDF.read_bytes())

    run_importer(vault)

    assert (topic / "_inbox" / "_failed" / "a-broken.pdf").exists()
    assert (topic / "Source-Notes" / "b-good.md").exists()
    assert (topic / "_sources" / "b-good.pdf").exists()


def test_a_failed_move_to_sources_removes_the_note_and_fails_the_pdf(tmp_path):
    vault = tmp_path / "Engineering"
    topic = vault / "Coaching"
    topic.mkdir(parents=True)
    (topic / "_sources").write_text("a file where the folder should be", encoding="utf-8")
    put_in_inbox(vault, "dependency-rule.pdf", SAMPLE_PDF.read_bytes())

    run_importer(vault)

    assert list((topic / "Source-Notes").iterdir()) == []
    assert (topic / "_inbox" / "_failed" / "dependency-rule.pdf").exists()
    assert (topic / "_inbox" / "_failed" / "dependency-rule.log").exists()


def test_a_pdf_that_changed_a_moment_ago_is_skipped_until_a_later_run(tmp_path):
    vault = tmp_path / "Engineering"
    inbox = vault / "Coaching" / "_inbox"
    inbox.mkdir(parents=True)
    (inbox / "half-copied.pdf").write_bytes(b"%PDF-1.7 still being copied")
    before = snapshot(vault)

    run_importer(vault)

    assert snapshot(vault) == before


def test_a_run_gives_way_while_another_run_holds_the_vault(tmp_path):
    vault = tmp_path / "Engineering"
    put_in_inbox(vault, "dependency-rule.pdf", SAMPLE_PDF.read_bytes())
    before = snapshot(vault)
    holder = subprocess.Popen(
        [sys.executable, "-c",
         "import fcntl,os,sys,time\n"
         "fd=os.open(sys.argv[1],os.O_RDONLY)\nfcntl.flock(fd,fcntl.LOCK_EX)\n"
         "print('locked',flush=True)\ntime.sleep(60)", str(vault)],
        stdout=subprocess.PIPE, text=True,
    )
    try:
        assert holder.stdout.readline().strip() == "locked"
        run_importer(vault)
        assert snapshot(vault) == before
    finally:
        holder.kill()
        holder.wait()


def test_the_original_keeps_its_own_extension_and_a_suffix_goes_before_it(tmp_path):
    vault = tmp_path / "Engineering"
    topic = vault / "Coaching"
    sample = SAMPLE_PDF.read_bytes()
    put_in_inbox(vault, "Paper.PDF", sample)
    run_importer(vault)
    put_in_inbox(vault, "Paper.PDF", sample)
    run_importer(vault)

    assert frontmatter(topic / "Source-Notes" / "Paper.md")["source"] == "[[_sources/Paper.PDF]]"
    assert frontmatter(topic / "Source-Notes" / "Paper-1.md")["source"] == "[[_sources/Paper-1.PDF]]"
    assert sorted(f.name for f in (topic / "_sources").iterdir()) == ["Paper-1.PDF", "Paper.PDF"]
