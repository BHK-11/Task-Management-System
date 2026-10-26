"""Bounded, local text extraction for uploaded TASK OS knowledge files."""

from __future__ import annotations

from io import BytesIO
import zipfile

from docx import Document
from openpyxl import load_workbook
from pptx import Presentation
from pypdf import PdfReader

MAX_UPLOAD_BYTES = 10 * 1024 * 1024
MAX_UNPACKED_BYTES = 25 * 1024 * 1024
MAX_EXTRACTED_CHARS = 1_000_000
MAX_SEGMENTS = 2_000


def _clean(value: object) -> str:
    return " ".join(str(value).replace("\x00", " ").split()).strip()


def _segment(segments: list[dict[str, str]], locator: str, text: str) -> None:
    text = _clean(text)
    if text and len(segments) < MAX_SEGMENTS:
        segments.append({"locator": locator[:240], "text": text})


def _check_package(data: bytes, expected: str) -> None:
    try:
        with zipfile.ZipFile(BytesIO(data)) as archive:
            entries = archive.infolist()
            if len(entries) > 4_000 or sum(item.file_size for item in entries) > MAX_UNPACKED_BYTES:
                raise ValueError("The document package exceeds the extraction limits.")
            if any(item.flag_bits & 1 for item in entries):
                raise ValueError("Encrypted office documents are not supported.")
            if expected not in archive.namelist():
                raise ValueError("The uploaded file is not a valid document of this type.")
    except (zipfile.BadZipFile, OSError) as exc:
        raise ValueError("The uploaded file is not a valid office document.") from exc


def extract_document(kind: str, data: bytes) -> dict[str, object]:
    if kind not in {"pdf", "docx", "xlsx", "pptx", "txt", "md"}:
        raise ValueError("This document format is not supported.")
    if not data or len(data) > MAX_UPLOAD_BYTES:
        raise ValueError("The document is empty or exceeds the 10 MB limit.")

    segments: list[dict[str, str]] = []
    try:
        if kind == "pdf":
            if not data.startswith(b"%PDF-"):
                raise ValueError("The uploaded file is not a valid PDF.")
            reader = PdfReader(BytesIO(data), strict=True)
            if reader.is_encrypted or len(reader.pages) > 500:
                raise ValueError("Encrypted PDFs and PDFs over 500 pages are not supported.")
            for index, page in enumerate(reader.pages):
                _segment(segments, f"Page {index + 1}", page.extract_text() or "")
        elif kind == "docx":
            _check_package(data, "word/document.xml")
            document = Document(BytesIO(data))
            for index, paragraph in enumerate(document.paragraphs, 1):
                if paragraph.text.strip():
                    title = paragraph.style.name if paragraph.style else "Paragraph"
                    _segment(segments, f"{title} {index}", paragraph.text)
            for table_index, table in enumerate(document.tables, 1):
                for row_index, row in enumerate(table.rows, 1):
                    _segment(segments, f"Table {table_index}, row {row_index}", " | ".join(cell.text for cell in row.cells))
        elif kind == "xlsx":
            _check_package(data, "xl/workbook.xml")
            workbook = load_workbook(BytesIO(data), read_only=True, data_only=True)
            if len(workbook.sheetnames) > 50:
                raise ValueError("The workbook has too many sheets to index.")
            for sheet in workbook.worksheets:
                for row_index, row in enumerate(sheet.iter_rows(values_only=True), 1):
                    values = [_clean(cell)[:256] for cell in row if cell is not None and _clean(cell)]
                    if values:
                        _segment(segments, f"Sheet {sheet.title}, row {row_index}", " | ".join(values))
                    if row_index >= 10_000:
                        break
            workbook.close()
        elif kind == "pptx":
            _check_package(data, "ppt/presentation.xml")
            presentation = Presentation(BytesIO(data))
            if len(presentation.slides) > 500:
                raise ValueError("The presentation has too many slides to index.")
            for slide_index, slide in enumerate(presentation.slides, 1):
                text = "\n".join(shape.text for shape in slide.shapes if getattr(shape, "has_text_frame", False))
                _segment(segments, f"Slide {slide_index}", text)
        else:
            text = data.decode("utf-8-sig", errors="strict")
            if "\x00" in text:
                raise ValueError("Text files containing binary data are not supported.")
            for index, paragraph in enumerate(text.splitlines(), 1):
                _segment(segments, f"Line {index}", paragraph)
    except ValueError:
        raise
    except Exception as exc:
        raise ValueError("The document could not be read. Check that it is a valid, unencrypted file.") from exc

    total = sum(len(item["text"]) for item in segments)
    if total > MAX_EXTRACTED_CHARS:
        raise ValueError("The extracted document text exceeds the 1 MB indexing limit.")
    if not segments:
        raise ValueError("No selectable text was found. Image OCR is not available yet.")
    return {"format": kind, "characterCount": total, "segments": segments}
