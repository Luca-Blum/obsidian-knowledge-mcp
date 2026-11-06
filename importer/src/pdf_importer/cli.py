import argparse
import logging
from pathlib import Path

from pdf_importer.run import run


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(
        prog="pdf-importer",
        description="Imports the PDFs in every Topic's _inbox/ into Source Notes.",
    )
    parser.add_argument(
        "--vault",
        dest="vaults",
        action="append",
        type=Path,
        required=True,
        help="a Vault to scan; repeat for several Vaults",
    )
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    run(args.vaults)
