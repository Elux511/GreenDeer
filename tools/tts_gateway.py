#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
GreenDeer - local TTS gateway (sherpa-onnx offline voices).

Bridge between the Spring Boot backend and a local sherpa-onnx VITS model,
so the AI-livestream chat loop can synthesize real Mandarin speech without
calling any cloud service.

HTTP API (matches HttpSherpaTtsProvider in the backend):
  GET  /health        -> {"status":"ok","sherpa":true|false,"voices":[...]}
  POST /tts           -> body {"text":"...","voice":"sherpa-onnx-vits-zh-ll","sample_rate":16000}
                          -> 200 audio/wav binary (mono PCM16 wav)
                          -> 501 {"error":"..."} if sherpa-onnx missing
                          -> 404 {"error":"..."} if the voice model dir not found
                          -> 400 {"error":"..."} invalid request

Usage:
  pip install sherpa-onnx soundfile        # optional; only needed for real voice
  python tools/tts_gateway.py --models-dir models --port 8899

Model layout expected under --models-dir (sherpa-onnx VITS):
  {models-dir}/{voice-name}/
      model.onnx lexicon.txt dict/ tokens.txt
Typical voice name: sherpa-onnx-vits-zh-ll
(see https://github.com/k2-fsa/sherpa-onnx/releases - tts-models)
"""
import argparse
import base64
import json
import sys
import threading

try:
    import numpy as np
    import sherpa_onnx  # type: ignore
    import soundfile as sf
    SHERPA_OK = True
except Exception:  # noqa: BLE001 - report cleanly when models are unavailable
    SHERPA_OK = False
    sherpa_onnx = None
    sf = None
    np = None

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer  # noqa: E402

_lock = threading.Lock()
_engines = {}  # voice -> (engine, sample_rate)


def _load_engine(model_root: str, voice: str):
    """Load (and cache) one sherpa-onnx VITS engine for a voice."""
    with _lock:
        if voice in _engines:
            return _engines[voice]
        model_dir = os.path.join(model_root, voice)
        missing = [f for f in ("model.onnx", "tokens.txt") if not os.path.exists(os.path.join(model_dir, f))]
        if missing:
            raise LookupError(f"voice '{voice}' model dir incomplete at {model_dir}, missing {missing}")
        cfg = {
            "model": os.path.join(model_dir, "model.onnx"),
            "tokens": os.path.join(model_dir, "tokens.txt"),
            "lexicon": os.path.join(model_dir, "lexicon.txt"),
            "dict_dir": os.path.join(model_dir, "dict"),
            "data_dir": os.path.join(model_dir, "espeak-ng-data"),
        }
        vits = sherpa_onnx.OfflineTtsVitsModelConfig(
            model=cfg["model"],
            lexicon=cfg["lexicon"] if os.path.exists(cfg["lexicon"]) else "",
            dict_dir=cfg["dict_dir"] if os.path.isdir(cfg["dict_dir"]) else "",
            tokens=cfg["tokens"],
        )
        model_cfg = sherpa_onnx.OfflineTtsModelConfig(vits=vits, provider="cpu", debug=0, num_threads=2)
        tts_cfg = sherpa_onnx.OfflineTtsConfig(model=model_cfg, max_num_sentences=2)
        if not tts_cfg.validate():
            raise LookupError(f"voice '{voice}' config invalid")
        engine = sherpa_onnx.OfflineTts(tts_cfg)
        _engines[voice] = engine
        return engine


def _synthesize_wav(model_root: str, voice: str, text: str) -> bytes:
    engine = _load_engine(model_root, voice)
    result = engine.generate(text)
    samples = result.samples
    sr = result.sample_rate
    if samples is None or len(samples) == 0:
        raise ValueError("empty synthesis result")
    pcm = np.asarray(samples, dtype=np.float32)
    # sherpa-onnx returns float samples in [-1,1] -> int16
    pcm16 = (pcm * 32767).astype(np.int16)
    import io
    buf = io.BytesIO()
    sf.write(buf, pcm16, sr, format="WAV", subtype="PCM_16")
    return buf.getvalue()


def list_voices(model_root: str) -> list:
    if not os.path.isdir(model_root):
        return []
    return sorted(d for d in os.listdir(model_root)
                  if os.path.isdir(os.path.join(model_root, d))
                  and os.path.exists(os.path.join(model_root, d, "model.onnx")))


def make_handler(model_root: str, default_voice: str):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, fmt, *args):  # quieter logs
            pass

        def _json(self, code, obj):
            body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            if self.path.startswith("/health"):
                self._json(200, {"status": "ok", "sherpa": SHERPA_OK,
                                 "default_voice": default_voice, "voices": list_voices(model_root)})
            else:
                self._json(404, {"error": "not found"})

        def do_POST(self):
            if not self.path.startswith("/tts"):
                self._json(404, {"error": "not found"})
                return
            length = int(self.headers.get("Content-Length") or 0)
            try:
                req = json.loads(self.rfile.read(length).decode("utf-8") or "{}")
            except Exception:
                self._json(400, {"error": "bad json"})
                return
            text = (req.get("text") or "").strip()
            voice = req.get("voice") or default_voice
            if not text:
                self._json(400, {"error": "text is empty"})
                return
            if len(text) > 1000:
                self._json(400, {"error": "text too long (<=1000)"})
                return
            if not SHERPA_OK:
                self._json(501, {"error": "sherpa-onnx not installed: pip install sherpa-onnx soundfile"})
                return
            try:
                wav = _synthesize_wav(model_root, voice, text)
                self.send_response(200)
                self.send_header("Content-Type", "audio/wav")
                self.send_header("Content-Length", str(len(wav)))
                self.end_headers()
                self.wfile.write(wav)
            except LookupError as e:
                self._json(404, {"error": str(e)})
            except Exception as e:  # noqa: BLE001
                self._json(500, {"error": f"{type(e).__name__}: {e}"})

    return Handler


def main() -> None:
    ap = argparse.ArgumentParser(description="GreenDeer local sherpa-onnx TTS gateway")
    ap.add_argument("--models-dir", default="models", help="directory containing voice model dirs")
    ap.add_argument("--default-voice", default="sherpa-onnx-vits-zh-ll", help="default voice name")
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=8899)
    args = ap.parse_args()

    handler = make_handler(args.models_dir, args.default_voice)
    print(f"[tts-gateway] models_dir={args.models_dir} sherpa={SHERPA_OK} "
          f"voices={list_voices(args.models_dir)}")
    if not SHERPA_OK:
        print("[tts-gateway] WARNING: sherpa-onnx missing -> /tts returns 501; "
              "install with: pip install sherpa-onnx soundfile")
    print(f"[tts-gateway] listening on http://{args.host}:{args.port}  (POST /tts, GET /health)")
    server = ThreadingHTTPServer((args.host, args.port), handler)
    server.serve_forever()


if __name__ == "__main__":
    import os
    main()
