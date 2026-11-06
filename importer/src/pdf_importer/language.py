"""Detects the Note Language of converted text, offline."""

from lingua import Language, LanguageDetectorBuilder

_LANGS = {Language.GERMAN: "de", Language.ENGLISH: "en"}
_MIN_CONFIDENCE = 0.8

_detector = None


def detect_language(text: str) -> str | None:
    """`de` or `en` when the detector is confident, else None."""
    global _detector
    if _detector is None:
        _detector = LanguageDetectorBuilder.from_all_languages().build()
    confidences = _detector.compute_language_confidence_values(text)
    if not confidences:
        return None
    best = confidences[0]
    if best.value < _MIN_CONFIDENCE:
        return None
    return _LANGS.get(best.language)
