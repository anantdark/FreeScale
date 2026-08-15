# FreeScale

Android app for the **Dr. Trust USA 532** (SSW532 / ICOMON FG2211WB) 8-electrode smart scale with handbar. Package: `com.anant.freescale`.

FreeScale only targets this model. For other Bluetooth scales, use **[openScale](https://github.com/oliexdev/openScale)**.

## Body composition math

Raw weight + impedances come from the scale. Derived metrics use **Chipsea / ICOMON WLA25**, the same OEM algorithm family as Fitdays 8-electrode scales, ported from **[sacoma-lib](https://github.com/ynsgnr/sacoma-lib)** (MIT). If WLA25 rejects a sample, FreeScale falls back to Sun (2003) FFM/TBW + Janssen SMM literature equations.

## License

GNU General Public License v3 (see [LICENSE](LICENSE)).

## Phase 1

Barebones: scan, connect, Start measurement, live weight, full open metrics + raw Ω / BLE hex (`FreeScale/BLE`).

## Phase 2

Calibrated Instrument UI: Home readout with in-place Scan / Measure controls, bottom tabs (Home / Progress / Settings), profile fields in Settings, Debug mode for advanced BLE/Ω detail, Material You (on by default) + system light/dark, Expressive motion tied to weigh/BIA phases, measuring banners on the reading card.

## Build / release

See [DISTRIBUTION.md](DISTRIBUTION.md) for signing keys, `github` / `fdroid` flavors, and GitHub Actions release pipelines.


