"""One importer run over a temporary Vault, with real Docling.

Each test builds a Vault, runs the importer once as it would be run by hand
and asserts only on the files it leaves in the Vault.
"""

import datetime
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

import pytest
import yaml

FIXTURES = Path(__file__).parent / "fixtures"
SAMPLE_PDF = FIXTURES / "sample.pdf"
DOCUMENT_EXTENSIONS = ["epub", "docx", "doc", "odt", "rtf"]
MARKUP_EXTENSIONS = ["pptx", "ppt", "odp", "html", "mhtml", "adoc", "tex", "xml"]


def run_importer(*vaults: Path) -> str:
    """Runs the importer once and returns what it logged, as launchd would write it to its log file."""
    args = [arg for vault in vaults for arg in ("--vault", str(vault))]
    result = subprocess.run(
        [sys.executable, "-m", "pdf_importer", *args], check=True, capture_output=True, text=True
    )
    return result.stderr


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
        vault / "Coaching" / "_inbox" / "notes.csv",
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


@pytest.mark.parametrize("extension", DOCUMENT_EXTENSIONS)
def test_imports_a_text_document_into_a_source_note_titled_by_its_first_heading(tmp_path, extension):
    vault = tmp_path / "Engineering"
    topic = vault / "Coaching"
    sample = (FIXTURES / f"sample.{extension}").read_bytes()
    put_in_inbox(vault, f"book.{extension}", sample)

    run_importer(vault)

    note = topic / "Source-Notes" / "book.md"
    header = frontmatter(note)
    assert header["type"] == "source"
    assert header["title"] == "Notes on the Dependency Rule"
    assert header["imported"] == datetime.date.today()
    assert header["source"] == f"[[_sources/book.{extension}]]"
    assert "Source code dependencies must point only inward" in note.read_text(encoding="utf-8")
    assert (topic / "_sources" / f"book.{extension}").read_bytes() == sample
    assert list((topic / "_inbox").iterdir()) == []


def test_a_text_document_without_a_heading_is_titled_by_its_file_name(tmp_path):
    vault = tmp_path / "Engineering"
    put_in_inbox(vault, "no-heading.docx", (FIXTURES / "headless.docx").read_bytes())

    run_importer(vault)

    assert frontmatter(vault / "Coaching" / "Source-Notes" / "no-heading.md")["title"] == "no-heading"


@pytest.mark.parametrize("extension", DOCUMENT_EXTENSIONS)
def test_a_corrupt_text_document_ends_in_failed_with_a_log_and_nothing_else_changes(tmp_path, extension):
    vault = tmp_path / "Engineering"
    topic = vault / "Coaching"
    corrupt = os.urandom(2048)
    put_in_inbox(vault, f"broken.{extension}", corrupt)

    run_importer(vault)

    assert (topic / "_inbox" / "_failed" / f"broken.{extension}").read_bytes() == corrupt
    assert (topic / "_inbox" / "_failed" / "broken.log").read_text(encoding="utf-8").strip()
    assert sorted(f.name for f in vault.rglob("*") if f.is_file()) == sorted(["broken.log", f"broken.{extension}"])


@pytest.mark.parametrize("extension", MARKUP_EXTENSIONS)
def test_imports_a_slide_deck_or_markup_file_into_a_source_note_titled_by_its_first_heading(tmp_path, extension):
    vault = tmp_path / "Engineering"
    topic = vault / "Coaching"
    sample = (FIXTURES / f"sample.{extension}").read_bytes()
    put_in_inbox(vault, f"deck.{extension}", sample)

    run_importer(vault)

    note = topic / "Source-Notes" / "deck.md"
    header = frontmatter(note)
    assert header["type"] == "source"
    assert header["title"] == "Notes on the Dependency Rule"
    assert header["imported"] == datetime.date.today()
    assert header["source"] == f"[[_sources/deck.{extension}]]"
    assert "Source code dependencies must point only inward" in note.read_text(encoding="utf-8")
    assert (topic / "_sources" / f"deck.{extension}").read_bytes() == sample
    assert list((topic / "_inbox").iterdir()) == []


@pytest.mark.parametrize("extension", MARKUP_EXTENSIONS)
def test_a_corrupt_slide_deck_or_markup_file_ends_in_failed_with_a_log_and_nothing_else_changes(tmp_path, extension):
    vault = tmp_path / "Engineering"
    topic = vault / "Coaching"
    corrupt = os.urandom(2048)
    put_in_inbox(vault, f"broken.{extension}", corrupt)

    run_importer(vault)

    assert (topic / "_inbox" / "_failed" / f"broken.{extension}").read_bytes() == corrupt
    assert (topic / "_inbox" / "_failed" / "broken.log").read_text(encoding="utf-8").strip()
    assert sorted(f.name for f in vault.rglob("*") if f.is_file()) == sorted(["broken.log", f"broken.{extension}"])


def test_an_xml_file_that_is_not_jats_ends_in_failed_with_a_log_and_nothing_else_changes(tmp_path):
    vault = tmp_path / "Engineering"
    topic = vault / "Coaching"
    config = (FIXTURES / "not-jats.xml").read_bytes()
    put_in_inbox(vault, "config.xml", config)

    run_importer(vault)

    assert (topic / "_inbox" / "_failed" / "config.xml").read_bytes() == config
    assert (topic / "_inbox" / "_failed" / "config.log").read_text(encoding="utf-8").strip()
    assert sorted(f.name for f in vault.rglob("*") if f.is_file()) == ["config.log", "config.xml"]


ENGLISH = "Source code dependencies must point only inward, toward higher-level policies. "


def test_a_markdown_file_without_frontmatter_gets_the_importer_fields(tmp_path):
    vault = tmp_path / "Engineering"
    topic = vault / "Coaching"
    original = f"Intro line.\n\n# Dependency Rule\n\n{ENGLISH * 3}\n".encode()
    put_in_inbox(vault, "rule.md", original)

    run_importer(vault)

    note = topic / "Source-Notes" / "rule.md"
    assert frontmatter(note) == {
        "type": "source",
        "title": "Dependency Rule",
        "lang": "en",
        "source": "[[_sources/rule.md]]",
        "imported": datetime.date.today(),
    }
    assert note.read_text(encoding="utf-8").endswith(original.decode())
    assert (topic / "_sources" / "rule.md").read_bytes() == original
    assert list((topic / "_inbox").iterdir()) == []


def test_a_markdown_file_keeps_its_own_fields_and_gains_only_the_missing_ones(tmp_path):
    vault = tmp_path / "Engineering"
    topic = vault / "Coaching"
    put_in_inbox(
        vault,
        "own.md",
        f"---\ntype: article\ntitle: My Title\nauthor: Ada\nlang: de\ntags: [a, b]\n---\n# Other Heading\n\n{ENGLISH}\n".encode(),
    )

    run_importer(vault)

    note = topic / "Source-Notes" / "own.md"
    assert frontmatter(note) == {
        "type": "source",
        "title": "My Title",
        "author": "Ada",
        "lang": "de",
        "tags": ["a", "b"],
        "source": "[[_sources/own.md]]",
        "imported": datetime.date.today(),
    }
    text = note.read_text(encoding="utf-8")
    assert text.count("---\n") == 2
    assert text.endswith(f"# Other Heading\n\n{ENGLISH}\n")


def test_a_markdown_file_without_title_or_heading_is_titled_by_its_file_name(tmp_path):
    vault = tmp_path / "Engineering"
    put_in_inbox(vault, "plain-name.md", b"---\nauthor: Ada\n---\nJust words.\n")

    run_importer(vault)

    assert frontmatter(vault / "Coaching" / "Source-Notes" / "plain-name.md")["title"] == "plain-name"


def test_a_heading_inside_a_code_fence_is_not_the_title(tmp_path):
    vault = tmp_path / "Engineering"
    put_in_inbox(vault, "fenced.md", b"```sh\n# a comment\n```\n\n## Real\n")

    run_importer(vault)

    assert frontmatter(vault / "Coaching" / "Source-Notes" / "fenced.md")["title"] == "Real"


def test_a_text_file_is_taken_over_as_the_note_body(tmp_path):
    vault = tmp_path / "Engineering"
    topic = vault / "Coaching"
    original = f"{ENGLISH * 3}\nSecond line.\n".encode()
    put_in_inbox(vault, "memo.txt", original)

    run_importer(vault)

    note = topic / "Source-Notes" / "memo.md"
    header = frontmatter(note)
    assert header["type"] == "source"
    assert header["title"] == "memo"
    assert header["source"] == "[[_sources/memo.txt]]"
    assert note.read_text(encoding="utf-8").endswith(f"---\n\n{original.decode()}")
    assert (topic / "_sources" / "memo.txt").read_bytes() == original


def test_a_text_file_with_frontmatter_lookalike_is_not_parsed(tmp_path):
    vault = tmp_path / "Engineering"
    put_in_inbox(vault, "dashes.txt", b"---\ntitle: Nope\n---\nBody\n")

    run_importer(vault)

    note = vault / "Coaching" / "Source-Notes" / "dashes.md"
    assert frontmatter(note)["title"] == "dashes"
    assert note.read_text(encoding="utf-8").endswith("---\ntitle: Nope\n---\nBody\n")


def test_a_markdown_file_never_overwrites_an_existing_note(tmp_path):
    vault = tmp_path / "Engineering"
    topic = vault / "Coaching"
    (topic / "Source-Notes").mkdir(parents=True)
    (topic / "Source-Notes" / "rule.md").write_text("mine", encoding="utf-8")
    put_in_inbox(vault, "rule.md", b"# Rule\n")

    run_importer(vault)

    assert (topic / "Source-Notes" / "rule.md").read_text(encoding="utf-8") == "mine"
    assert frontmatter(topic / "Source-Notes" / "rule-1.md")["source"] == "[[_sources/rule-1.md]]"
    assert (topic / "_sources" / "rule-1.md").read_bytes() == b"# Rule\n"


@pytest.mark.parametrize("name", ["bin.md", "bin.txt"])
def test_binary_data_in_a_markdown_or_text_file_ends_in_failed(tmp_path, name):
    vault = tmp_path / "Engineering"
    topic = vault / "Coaching"
    corrupt = os.urandom(2048) + b"\x00"
    put_in_inbox(vault, name, corrupt)

    run_importer(vault)

    assert (topic / "_inbox" / "_failed" / name).read_bytes() == corrupt
    assert sorted(f.name for f in vault.rglob("*") if f.is_file()) == sorted([name, "bin.log"])


def test_a_markdown_file_with_broken_frontmatter_ends_in_failed(tmp_path):
    vault = tmp_path / "Engineering"
    topic = vault / "Coaching"
    broken = b"---\ntitle: [unclosed\n---\nBody\n"
    put_in_inbox(vault, "broken.md", broken)

    run_importer(vault)

    assert (topic / "_inbox" / "_failed" / "broken.md").read_bytes() == broken
    assert not (topic / "Source-Notes").exists()


def test_the_log_names_each_file_as_converted_with_its_duration(tmp_path):
    vault = tmp_path / "Engineering"
    put_in_inbox(vault, "dependency-rule.pdf", SAMPLE_PDF.read_bytes())

    log = run_importer(vault)

    assert re.search(r"converted .*dependency-rule\.pdf .* in \d+\.\d+s", log)


def test_the_log_names_each_file_as_failed_with_its_duration(tmp_path):
    vault = tmp_path / "Engineering"
    put_in_inbox(vault, "broken.pdf", os.urandom(2048))

    log = run_importer(vault)

    assert re.search(r"failed .*broken\.pdf in \d+\.\d+s", log)


def test_the_log_names_a_file_that_changed_a_moment_ago_as_skipped(tmp_path):
    vault = tmp_path / "Engineering"
    inbox = vault / "Coaching" / "_inbox"
    inbox.mkdir(parents=True)
    (inbox / "half-copied.pdf").write_bytes(b"%PDF-1.7 still being copied")

    log = run_importer(vault)

    assert re.search(r"skipped .*half-copied\.pdf", log)
