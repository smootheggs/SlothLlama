# LocalMind Architecture & Threat Model

## 1. Layered Clean Architecture
- **UI Layer (`ui/`)**: Jetpack Compose Material 3 screens with unidirectional data flow (`StateFlow` + immutable `UiState`), true OLED `#000000` canvas, and accessibility semantics.
- **Domain Layer (`domain/`)**: Pure Kotlin interfaces (`LocalInferenceEngine`, `CloudModelProvider`, `SymbolicMathEngine`) and use cases.
- **Data Layer (`data/`)**:
  - `local/`: Room SQLite database with 13 normalized entities (`InstalledModelEntity`, `ModelDownloadEntity`, `PersonaEntity`, `ConversationEntity`, `MessageEntity`, `KnowledgeCollectionEntity`, `KnowledgeSourceEntity`, `KnowledgeChunkEntity`, `CitationEntity`, `UserMemoryEntity`, `TutorProgressEntity`, `VocabularyItemEntity`, `AppEventEntity`). Giant model weights and vector index files are stored on disk via validated file references rather than SQLite BLOBs.
  - `inference/`: `LiteRtLmInferenceEngine` (Google AI Edge / MediaPipe GenAI) + `FakeInferenceEngine` fallback, `SecurePromptAssembler`, and `DeterministicCalculatorTool`.
  - `retrieval/`: `OnDeviceCosineVectorStore` and `SourcePolicyValidator` (Jsoup HTML sanitization, robots/allowlist enforcement, SHA-256 verification).

## 2. Threat Model & Security Controls
| Threat Vector | Mitigation Implemented |
| :--- | :--- |
| **Arbitrary Code Execution from Downloaded Models** | Disallows dynamic class loading (`DexClassLoader`) or untrusted `.so` native libraries. Only parses declarative weights inside validated `.litertlm` / `.task` containers. |
| **Indirect Prompt Injection via RAG Documents** | All retrieved chunks are sanitized for instruction-override patterns and isolated inside `<untrusted_retrieved_evidence>` blocks with an explicit system directive. |
| **Path Traversal & Zip Bombs during Import** | Canonical path verification inside `context.filesDir/models/<modelId>` and strict byte-size ceiling before extraction. |
| **Tampered / Corrupted Model Downloads** | Mandatory streaming SHA-256 verification against the manifest checksum prior to marking any model `READY`. |
| **Cleartext Network Sniffing & Key Leakage** | `usesCleartextTraffic="false"` in `AndroidManifest.xml` + `network_security_config.xml`. User-supplied cloud keys stored via `AndroidKeyStore` (`EncryptedSharedPreferences`) and redacted from `AppEventEntity` logs. |
| **Fabricated Citations** | Citations are deterministically linked to `KnowledgeChunkEntity` IDs returned by `LocalVectorStore.searchSimilar()`. If similarity falls below threshold, LocalMind explicitly states that local evidence was not found. |
