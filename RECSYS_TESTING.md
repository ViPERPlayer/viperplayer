# Recommender (CLAP) — testing

The on-device audio recommender uses a CLAP (`laion/larger_clap_music`) audio tower to turn a song's
PCM into a 512-d embedding. Wave P1a ships the runtime (`com.viperplayer.data.rec`) + Room v6 storage
+ the parity tests described here.

## What runs in CI (no device, no model)

`:app:testDebugUnitTest` runs pure-JVM tests under `app/src/test/java/com/viperplayer/data/rec/`:

- **`MelSpectrogramParityTest`** — the CI mel-parity gate. It reads the small golden PCM + golden mel
  `.bin`s from `app/src/test/resources/clap_golden/` (committed, ~6 MB total) and asserts the Kotlin
  `MelSpectrogram` log-mel matches the Python/transformers reference to cosine ≥ 0.99 (the P0 spike
  measured bit-exact 1.0 off-device). Also checks the periodic-Hann window sum (512.0) and the fixed
  `(1001, 64)` output shape.
- **`ResamplerTest`** — the canonical 48kHz windowed-sinc resampler + stereo→mono downmix: output
  length obeys the sample-rate ratio, a resampled sine keeps its frequency + amplitude, and channel
  averaging is correct.
- **`EmbeddingCodecTest`** — the `audioEmbedding` BLOB codec (`embeddingToBytes` / `bytesToEmbedding`,
  float32 little-endian, 2048 bytes) round-trips exactly, plus the `ClapModel` handshake constants and
  L2-normalization.

No ONNX model or emulator is required for any of the above.

## Full embedding parity (needs the ONNX model, not committed)

`app/src/androidTest/java/com/viperplayer/data/rec/ClapParityTest.kt` is an instrumented test with
two methods:

- `melParity_matchesGoldenMel` — same mel check as CI, on-device. Always runs (golden `.bin`s are
  committed under `app/src/androidTest/assets/clap_golden/`).
- `embeddingParity_matchesGoldenEmbedding` — runs the ONNX audio tower and asserts the L2-normalized
  512-d embedding matches the golden reference to cosine ≥ 0.98. **This needs the ONNX model, which is
  NOT committed to git** (73 MB int8 / 277 MB fp32). When the model asset is absent the test **skips
  gracefully** (JUnit `assumeTrue` + a logged skip message) rather than failing.

### Dropping the model in locally

The CLAP audio-tower ONNX is produced by the P0 spike (`step4_manual_export.py` / `step8_quantize.py`).
Copy it into the androidTest assets dir (it is `.gitignore`d, so it stays out of the repo):

```bash
# int8 (73 MB, cos ≈ 0.999 vs fp32 — preferred, keeps the test APK small):
cp /path/to/p0-clap-spike/onnx/clap_audio_tower.int8.onnx \
   app/src/androidTest/assets/clap_golden/clap_audio_tower.int8.onnx

# or fp32 (277 MB, exact):
cp /path/to/p0-clap-spike/onnx/clap_audio_tower.onnx \
   app/src/androidTest/assets/clap_golden/clap_audio_tower.onnx
```

The test prefers `clap_audio_tower.int8.onnx` if present, otherwise `clap_audio_tower.onnx`.

### Running on the Pixel emulator (parity only)

Per project memory, device-test on the **Pixel_9** AVD (x86_64 — validates correctness, NOT latency):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.viperplayer.data.rec.ClapParityTest
adb logcat -s ClapParityTest:*
```

Any millisecond timing on an emulator is meaningless; real latency requires a physical arm64 Pixel.

## Golden vectors

`app/src/{test,androidTest}/assets|resources/clap_golden/` hold, per clip (`sine_440_5s`,
`white_noise_10s`, `long_mixed_14s`): the 48kHz mono PCM (`*.pcm_f32le.bin`), the reference log-mel
(`*.mel_f32le.bin`), and the reference 512-d embedding (`*.ref_embedding.bin`, androidTest only). All
are raw float32 little-endian. `long_mixed_14s` (>10 s) was generated with crop offset `43567`; the
tests pass that offset for that clip only. A shipping build pins `cropIdx = 0` (crop the first 10 s).

## Parity/version handshake

`ClapModel.MODEL_VERSION` stamps every stored embedding (`songs.embeddingModelVersion`). Bump it on
ANY change to the checkpoint, the mel contract, the resampler, or quantization — the indexer treats a
row with a differing version as stale and recomputes it, and the server ingest must stamp rows with
the identical string for cross-device vector comparability.
