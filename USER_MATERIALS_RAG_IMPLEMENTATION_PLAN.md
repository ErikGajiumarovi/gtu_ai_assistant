# User Materials RAG Implementation Plan

## Goal

Add a private user materials library that allows each authenticated user to upload, list, download, delete, index, and use their own study materials as AI assistant context.

Supported v1 formats:

- `.md`
- `.txt`
- `.pdf`
- `.docx`

Original files should be stored in MinIO when configured, otherwise in local storage. Indexed text chunks should be stored in PostgreSQL with pgvector embeddings. User materials must remain private and must only be searchable by the owning user.

## Status Legend

- `[ ]` Not started
- `[~]` In progress
- `[x]` Completed

## Vertical Slice 1: Source Selection API Contract

Status: `[x]`

Completed:

- Added shared `AgentSourceMode` enum.
- Extended `CreateChatWithAgentRequest` with `sourceMode`, `collectionIds`, and `documentIds`.
- Extended `ContinueChatWithAgentRequest` with `sourceMode`, `collectionIds`, and `documentIds`.
- Kept default values so existing chat calls remain compatible.
- Verified with `./gradlew :shared:api-models:compileKotlinMetadata`.

Purpose: introduce the transport-level contract that lets frontend and backend pass source-selection settings through chat requests without changing retrieval behavior yet.

Tasks:

- Add shared `AgentSourceMode` enum.
- Extend `CreateChatWithAgentRequest` with `sourceMode`, `collectionIds`, and `documentIds`.
- Extend `ContinueChatWithAgentRequest` with `sourceMode`, `collectionIds`, and `documentIds`.
- Keep defaults backward-compatible for existing frontend/backend calls.
- Verify shared model compilation or relevant tests.

Expected result:

- Existing chat calls continue working.
- New clients can send source-selection options.
- Backend may ignore these fields until later slices.

## Vertical Slice 2: Material Domain Contracts

Status: `[x]`

Completed:

- Added `materials` domain package.
- Added material IDs and models: `MaterialDocument`, `MaterialDocumentId`, `MaterialCollection`, `MaterialCollectionId`, `MaterialChunk`, `MaterialIngestionStatus`.
- Added use case input ports for upload, list, download, and delete materials.
- Added use case input ports for create, list, and delete collections.
- Added output ports for saving/finding/deleting material documents, saving/deleting chunks, searching user materials, collections, and material object storage.
- Added owner-scoped material search query/result models.
- Added `USER_MATERIAL` to `MessageCitationSourceType`.
- Verified with `./gradlew :backend:domain:compileKotlin`.

Purpose: add domain language and ports for private user materials without persistence or HTTP implementation yet.

Tasks:

- Add `materials` domain package.
- Add IDs and models: `MaterialDocument`, `MaterialDocumentId`, `MaterialCollection`, `MaterialCollectionId`, `MaterialChunk`, `MaterialIngestionStatus`.
- Add use case input ports: upload, list, download, delete materials; create/list/delete collections.
- Add output ports: save/find/delete material documents, save chunks, search user materials, object storage.
- Add material search query/result models.
- Add `USER_MATERIAL` to `MessageCitationSourceType`.

Expected result:

- Application and infrastructure can implement material flows against explicit ports.
- User ownership is represented at contract level.

## Vertical Slice 3: Persistence Schema And Basic Ports

Status: `[x]`

Completed:

- Added Exposed tables for material collections, documents, chunks, and ingestion jobs.
- Added owner/document/collection/status indexes and a pgvector embedding column for material chunks.
- Added raw SQL HNSW vector index creation for `material_chunks.embedding`.
- Added material persistence record objects and domain mappers.
- Implemented owner-scoped save/find/delete persistence ports for material documents and collections.
- Verified with `./gradlew :backend:infrastructure:compileKotlin`.

Purpose: persist material metadata, chunks, collections, and ingestion jobs.

Tasks:

- Add tables: `material_collections`, `material_documents`, `material_chunks`, `material_ingestion_jobs`.
- Add indexes by `owner_user_id`, `collection_id`, `document_id`, and status.
- Add vector column for `material_chunks.embedding`.
- Add raw SQL vector index creation if needed.
- Add persistence record objects and mappers.
- Implement basic save/find/delete ports for documents and collections.

Expected result:

- Backend can persist private material metadata and chunks.
- All queries are owner-scoped.

## Vertical Slice 4: Local Object Storage And Material HTTP API

Status: `[x]`

Completed:

- Implemented local filesystem material object storage under `APP_LOCAL_STORAGE_DIR` with server-generated owner-scoped object keys.
- Added application use cases for material upload, list, download, and delete using existing domain ports.
- Added authenticated material routes for upload, list, metadata, download, and delete.
- Added validation for supported `.md`, `.txt`, `.pdf`, and `.docx` extensions, reasonable MIME types, empty files, and `APP_MATERIAL_MAX_FILE_SIZE_BYTES`.
- Wired material ports/use cases/storage for memory and PostgreSQL app modes.
- Verified with backend compile checks.

Purpose: support file upload/list/download/delete with local storage first.

Tasks:

- Add `MaterialObjectStoragePort`.
- Implement local storage adapter using `APP_LOCAL_STORAGE_DIR`.
- Add authenticated routes:
  - `POST /api/materials`
  - `GET /api/materials`
  - `GET /api/materials/{id}`
  - `GET /api/materials/{id}/download`
  - `DELETE /api/materials/{id}`
- Validate extension, mime type, and size.
- Ensure all operations check document owner.

Expected result:

- User can manage original files without AI indexing yet.
- Files are private and downloadable only by owner.

## Vertical Slice 5: Text Extraction For MD/TXT

Status: `[x]`

Completed:

- Added application-level `MaterialTextExtractionService` for `.md` and `.txt` UTF-8 extraction.
- Normalized whitespace and preserved Markdown heading paths in extraction segments where ATX headings are present.
- Integrated synchronous extraction into upload for `.md` and `.txt` only.
- Marked successfully extracted `.md`/`.txt` documents as `READY` and extraction failures as `FAILED` with `ingestionError`.
- Left `.pdf` and `.docx` uploads at `UPLOADED` for later extraction slices.
- Did not add embeddings, vector search, PDF/DOCX extraction, frontend, or ingestion worker.

Purpose: index simple text formats end-to-end before adding heavier document parsers.

Tasks:

- Add `MaterialTextExtractionService`.
- Extract `.md` and `.txt` as UTF-8 text.
- Preserve Markdown heading path where possible.
- Normalize whitespace.
- Add extraction errors to document status.

Expected result:

- Uploaded `.md` and `.txt` files can produce normalized text for chunking.

## Vertical Slice 6: Chunking, Embeddings, And Ingestion Worker

Status: `[x]`

Completed:

- Added application-level `MaterialChunkBuilder` with 400-word chunks and 75-word overlap.
- Added material embedding output port and infrastructure adapter delegating to the existing embedding port.
- Added `MaterialIngestionWorker` and app scheduler polling uploaded `.md`/`.txt` documents asynchronously.
- Changed upload flow so supported materials are saved as `UPLOADED`; worker transitions `.md`/`.txt` through `PROCESSING` to `READY` or `FAILED`.
- Added transactional PostgreSQL chunk replacement plus in-memory chunk adapters.
- Left `.pdf` and `.docx` at `UPLOADED` and ignored by the Slice 6 worker.

Purpose: turn extracted text into searchable vector chunks asynchronously.

Tasks:

- Add `MaterialChunkBuilder`.
- Use chunk size around 250-500 words with 50-100 word overlap.
- Include title and heading/page metadata in embedding input.
- Add `MaterialIngestionWorker` or scheduler similar to existing knowledge ingestion scheduler.
- Update document status: `UPLOADED -> PROCESSING -> READY` or `FAILED`.
- Store chunks transactionally.

Expected result:

- `.md` and `.txt` files become indexed and ready for search.

## Vertical Slice 7: User Material Search

Status: `[x]`

Completed:

- Implemented PostgreSQL `SearchUserMaterialsPortImpl` with query embeddings and pgvector top-N candidate search.
- Enforced owner scoping on both `material_chunks.owner_user_id` and `material_documents.owner_user_id`, and searched only `READY` documents.
- Added optional collection/document filters and bounded candidate/result limits.
- Added deterministic hybrid reranking using vector score plus lexical token overlap.
- Added in-memory owner-scoped material search for MEMORY persistence mode.

Purpose: search indexed private materials efficiently and safely.

Tasks:

- Implement `SearchUserMaterialsPortImpl`.
- Calculate query embedding.
- Use pgvector SQL top-N search with strict `owner_user_id = currentUserId` filter.
- Support optional `collectionIds` and `documentIds` filters.
- Rerank candidates with hybrid vector + lexical scoring.
- Return snippets, document IDs, titles, and page/heading metadata.

Expected result:

- Backend can retrieve relevant chunks from only the current user's materials.

## Vertical Slice 8: Agent Integration

Status: `[x]`

Completed:

- Added domain-level chat source selection and `GenerateMessageCommand` carrying messages, user id, source mode, collection ids, and document ids.
- Updated create/continue chat use cases and HTTP mapping to pass source settings with domain validation for material filters.
- Added `UserMaterialSearchTool` backed by `SearchUserMaterialsPort` and current authenticated user id.
- Integrated source-mode behavior in agent generation for GTU-only, user-material-only, GTU+materials, and GTU+materials+web fallback.
- Added prompt rules for material-only and mixed source modes.
- Wired user material search tool in app DI.

Purpose: make uploaded materials influence assistant answers.

Tasks:

- Add `UserMaterialSearchTool`.
- Replace `GenerateMessagePort` input with a command containing messages, userId, source mode, collection IDs, and document IDs.
- Update create/continue chat use cases to pass source settings.
- Implement source behavior:
  - `GTU_ONLY`: current GTU RAG only.
  - `MY_MATERIALS_ONLY`: user materials only.
  - `GTU_AND_MY_MATERIALS`: both, no web.
  - `GTU_MY_MATERIALS_AND_WEB`: both plus web fallback.
- Add prompt rules for material-only mode.

Expected result:

- User can choose whether the assistant uses GTU sources, private materials, web, or combinations.

## Vertical Slice 9: Citations For User Materials

Status: `[x]`

Completed:

- Added nullable material citation metadata (`documentId`, `pageStart`, `pageEnd`) to domain `MessageCitation` and shared `CitationResponse`.
- Persisted material citation metadata in `chat_message_citations` with nullable columns and save/load mapping.
- Propagated document/page metadata from user material search hits through `AgentSource` into assistant message citations.
- Mapped `USER_MATERIAL` citation URLs to the authenticated material download route in API responses.
- Updated frontend citation rendering to label private file citations and open material downloads through an authenticated fetch.

Purpose: make answers traceable to uploaded files.

Tasks:

- Add `documentId`, `pageStart`, and `pageEnd` to `MessageCitation` or equivalent citation metadata.
- Update chat citation table and mapper.
- Update shared `CitationResponse`.
- Render `USER_MATERIAL` citations in frontend.
- Link material citations to download/view endpoint.

Expected result:

- Assistant answers can cite private documents with page or section references.

## Vertical Slice 10: Frontend Materials UI

Status: `[x]`

Completed:

- Added shared material response DTOs for typed frontend parsing.
- Added frontend material API client methods for list, upload, and delete.
- Added a sidebar Materials section with upload, list, status, size, upload date, ingestion errors, download, delete, and document selection.
- Added a source mode selector near the chat composer.
- Chat create/continue requests now pass selected source mode and selected document IDs.
- Did not add collection UI/API, material search HTTP API, PDF/DOCX extraction, MinIO, or Slice 11+ features.

Purpose: let users manage files and choose source mode from the UI.

Tasks:

- Add `ApiClient` methods for material endpoints.
- Add Materials screen or sidebar section.
- Add upload form accepting `.md,.txt,.pdf,.docx`.
- Show file name, status, size, upload date, and ingestion errors.
- Add delete and download actions.
- Add source mode selector near chat composer.
- Add optional document/collection selector.

Expected result:

- User can manage materials and control assistant context from the browser.

## Vertical Slice 11: PDF And DOCX Extraction

Status: `[x]`

Completed:

- Added application-level PDFBox and Apache POI parser dependencies.
- Extended `MaterialTextExtractionService` to extract PDF text page by page and attach `pageStart`/`pageEnd` metadata to extracted segments.
- Extended chunk building so PDF page metadata is preserved on stored material chunks.
- Extended `MaterialTextExtractionService` to extract DOCX paragraph text and maintain a basic heading path from Word `Heading1`-`Heading6` paragraph styles.
- Updated ingestion eligibility so the worker processes `.pdf` and `.docx` documents through the existing `UPLOADED -> PROCESSING -> READY/FAILED` flow.
- Added in-memory generated PDF/DOCX extraction tests, including page metadata preservation through chunk building.
- Did not add OCR, MinIO, material search HTTP API, collection API, frontend changes, or Slice 12+ features.

Purpose: support the remaining v1 file formats.

Tasks:

- Add PDFBox dependency.
- Extract PDF text per page.
- Preserve page ranges in chunks.
- Add Apache Tika or Apache POI dependency for DOCX.
- Extract DOCX text with paragraphs/headings where possible.
- Add tests with small fixture documents.

Expected result:

- `.pdf` and `.docx` files can be indexed and searched.

## Vertical Slice 12: MinIO Storage

Status: `[x]`

Completed:

- Added MinIO Java SDK dependency to `backend/infrastructure`.
- Added file storage config with local default and MinIO mode selected by `APP_FILE_STORAGE_MODE=minio`.
- Implemented `MinioMaterialObjectStoragePortImpl` for owner-scoped original file save/read/delete through the existing `MaterialObjectStoragePort` abstraction.
- Added startup bucket ensure through the MinIO storage factory; startup fails with a clear error if bucket validation/creation fails.
- Added MinIO service, volume, healthcheck, and backend MinIO env wiring to `docker-compose.yml`.
- Kept local filesystem storage as the default fallback/dev mode.
- Did not add Slice 13+, OCR, material search HTTP API, collection API, frontend changes, or parser dependency movement.

Purpose: store original files in MinIO when configured.

Tasks:

- Add MinIO Java SDK dependency.
- Add config:
  - `APP_FILE_STORAGE_MODE`
  - `APP_MINIO_ENDPOINT`
  - `APP_MINIO_ACCESS_KEY`
  - `APP_MINIO_SECRET_KEY`
  - `APP_MINIO_BUCKET`
  - `APP_MINIO_REGION`
- Implement `MinioMaterialObjectStoragePortImpl`.
- Add MinIO service to `docker-compose.yml`.
- Ensure bucket exists on startup or fail with clear error.
- Keep local storage as fallback/dev mode.

Expected result:

- Original files are stored in MinIO in production-like setups.

## Vertical Slice 13: OCR For Scanned PDFs

Status: `[x]`

Completed:

- Added OCR port abstraction and Tesseract-based infrastructure implementation using `eng+rus` by default.
- Added OCR runtime config: `APP_MATERIAL_OCR_ENABLED`, `APP_TESSERACT_COMMAND`, `APP_TESSERACT_LANGUAGES`, `APP_TESSERACT_TIMEOUT_SECONDS`, `APP_MATERIAL_OCR_MIN_TEXT_CHARS`, and `APP_MATERIAL_OCR_RENDER_DPI`.
- Extended PDF extraction to detect weak/missing text layers and render pages to PNG for OCR when enabled.
- Stored OCR usage/confidence/error metadata on material documents after ingestion and exposed it through materials API/UI.
- Installed Tesseract and English/Russian language packs in the backend runtime image.
- Enabled OCR in `docker-compose.yml` with `eng+rus` languages for the backend container.
- Added tests covering PDF text extraction, OCR fallback for scanned/blank PDFs, DOCX extraction, and page metadata preservation.
- OCR runs in the existing asynchronous ingestion worker, so upload requests still only persist the original file and document record.

Purpose: support Russian and English scanned PDFs after the basic text pipeline works.

Tasks:

- Detect PDFs with weak or missing text layer.
- Add OCR strategy for `eng+rus`.
- Decide between local Tesseract, external OCR service, or separate worker container.
- Store OCR confidence/error metadata.
- Avoid blocking upload request while OCR runs.

Expected result:

- Scanned PDFs can eventually become searchable.

## Validation Checklist

- Upload requires JWT.
- List shows only current user's documents.
- Download checks owner.
- Delete checks owner and removes chunks plus original object.
- Search always filters by owner before ranking.
- `MY_MATERIALS_ONLY` does not call GTU/web search.
- User material citations point to the correct file/page or section.
- Existing chat functionality remains compatible with old requests.

## Implementation Notes

- Prefer small changes per slice.
- Do not mix public GTU crawler tables with private user materials tables.
- Keep `HashingEmbeddingPort` for local/dev only; real material search should use multilingual embeddings.
- MinIO integration can come after local storage because the storage port should hide implementation details.
- Collection deletion should only delete the collection and leave documents unassigned, unless a separate destructive delete is explicitly requested.
