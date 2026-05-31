# Altio Android

An Android application that runs a local on-device AI inference server, exposing a REST/SSE API
over the loopback interface. Other apps on the same device can use the API to perform text
generation and audio transcription without sending data off-device.

---

## Modules

| Module           | Description                                                     |
|------------------|-----------------------------------------------------------------|
| `app`            | Main application, DI wiring (Metro), notification setup         |
| `core/domain`    | Domain models, repository interfaces, error code definitions    |
| `core/data`      | Room database, download worker, repository implementations      |
| `ui`             | Compose screens (model manager, download, settings, debug logs) |
| `server`         | Ktor-based HTTP server exposing the loopback API                |
| `runtime/litert` | LiteRT-LM inference engine integration                          |
| `demo`           | Self-contained reference client app for the loopback API        |

---

## Model Download & Install — Error Code Definitions

The following files define all error codes and lifecycle states used in the model download and
installation flow. Refer to these when diagnosing failures or extending error handling.

| File                                                                                                                                                                             | Contents                                                              |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------|
| [`core/domain/src/main/kotlin/app/altio/service/domain/model/DownloadFailureReason.kt`](core/domain/src/main/kotlin/app/altio/service/domain/model/DownloadFailureReason.kt) | Terminal failure reasons reported when a download cannot be recovered |
| [`core/domain/src/main/kotlin/app/altio/service/domain/model/DownloadStatus.kt`](core/domain/src/main/kotlin/app/altio/service/domain/model/DownloadStatus.kt)               | Download lifecycle states                                             |
| [`core/domain/src/main/kotlin/app/altio/service/domain/model/ModelStatus.kt`](core/domain/src/main/kotlin/app/altio/service/domain/model/ModelStatus.kt)                     | Overall model lifecycle states                                        |

Human-readable UI strings for `DownloadFailureReason` are mapped in
[`ui/src/main/kotlin/app/altio/service/ui/model/DownloadErrorStrings.kt`](ui/src/main/kotlin/app/altio/service/ui/model/DownloadErrorStrings.kt).

---

## Model Source

When adding or updating bundled model metadata, use the LiteRT community Hugging Face page as the
canonical source for model references:

- https://huggingface.co/litert-community

This is the source we currently use, and should continue using going forward when populating
`models.json`, `ModelCatalog`, version strings, file names, and checksums.

---

## Further Reading

- **Demo app** — see [`demo/README.md`](demo/README.md) for setup instructions and API coverage.
- **API reference** — see [`docs/API.md`](docs/API.md).
- **Client integration guide** — see [`docs/CLIENT_INTEGRATION.md`](docs/CLIENT_INTEGRATION.md).

---

## Licensing

This project is licensed under the terms of the GNU Affero General Public License v3.0 or later (AGPL-3.0-or-later). For details, please see:
* [LICENSE](LICENSE) — Full license text.
* [LICENSING.md](LICENSING.md) — Plain language overview of the copyleft obligations, future commercial options, and FAQ.

## Contributing

We welcome contributions! Please read our [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a pull request. Note that contributors are required to sign our Contributor License Agreement ([CLA.md](CLA.md)) before their changes can be merged.

