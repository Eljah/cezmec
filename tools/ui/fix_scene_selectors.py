"""Scope navigation clicks to scene buttons; the video also records data-scene."""
from pathlib import Path
root = Path(__file__).resolve().parents[2]
for name in ['app.spec.js', 'video.spec.js']:
    path = root/'tests/e2e'/name
    text = path.read_text(encoding='utf-8')
    # Keep strict-mode enabled. Do not change video readiness/pixel assertions.
    text = text.replace('page.locator(`[data-scene=', 'page.locator(`.scene-button[data-scene=')
    text = text.replace("page.locator('[data-scene=", "page.locator('.scene-button[data-scene=")
    path.write_text(text, encoding='utf-8')
