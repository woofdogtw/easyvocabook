## Context

EasyVocaBook is a greenfield personal-series app. This design covers the Rust + iced PC desktop
implementation only. The codebase lives under `rust/` as a single Cargo crate. All design decisions
must remain consistent with the Android Kotlin implementation (to be built in a future change) at
the schema and domain logic level.

Key constraints inherited from the Easy-series architecture:
- `DbTableBase` trait pattern (two implementations: SQLite + Memory)
- Whole-file sync model (no record-level merge)
- `db_info.version` integer for schema migration
- All timestamps as Unix epoch `i64` (seconds)

## Goals / Non-Goals

**Goals:**
- Complete working Rust PC desktop app: DB, quiz engine, word management UI, settings, cloud sync
- Schema v1 that is forward-compatible with the future Android implementation
- Full test coverage of DB layer (both implementations) and quiz engine logic
- All credentials stored securely in OS keychain; no plaintext secrets on disk

**Non-Goals:**
- Android / Kotlin implementation
- Furigana ruby-text rendering (fallback: `漢字（かな）` side-by-side)
- Full SRS scheduling (count-based weighting is sufficient for v1)
- Import/export file format (UI entry points exist but no format is specified yet)
- OpenAPI server sync

## Decisions

### D1: Single crate, module-based split

**Decision**: One Cargo crate with modules `db`, `ui`, `network`, `quiz`, `config`.
**Rationale**: App is not large enough to justify workspace overhead. Module boundaries provide
sufficient separation. All sub-systems share the same `tokio` runtime provided by iced.
**Alternative rejected**: Cargo workspace — adds build complexity with no benefit at this scale.

### D2: rusqlite + spawn_blocking (not sqlx)

**Decision**: `rusqlite 0.40.1` wrapped in `tokio::task::spawn_blocking` for all DB calls.
**Rationale**: sqlx requires compile-time query cache (`.sqlx/`) or a live DB during build —
complex CI setup. rusqlite is synchronous and simple; `spawn_blocking` prevents UI blocking with
zero async-driver overhead. Single local SQLite file needs no connection pool.
**Alternative rejected**: `sqlx` — compile-time overhead, bundling conflicts with iced's tokio.

### D3: DbTableMemory as full-aggregate in-memory cache

**Decision**: On startup, load all words with their complete sub-records (word_meanings,
word_forms, sentences) into `DbTableMemory`. All UI reads go to the cache; all writes go to
SQLite first, then update the cache.
**Rationale**: 5000 words × ~500 bytes ≈ 2.5 MB — trivially fits in RAM. Enables instant
search (including secondary meanings), zero-latency UI rendering, and quiz sampling with no
additional SQL. Edit dialog loads from cache with no extra DB round-trip.
**Alternative rejected**: On-demand SQL for search/quiz — adds latency, requires debounce logic.

### D4: word_meanings as a separate table (1:N)

**Decision**: `words.meaning TEXT NOT NULL` stores the primary (display) meaning. Additional
meanings live in `word_meanings(word_id, meaning)` with `UNIQUE(word_id, meaning)`.
**Rationale**: A word like "bank" can have multiple unrelated meanings (銀行, 河岸). Storing them
as separate `words` rows would split a single vocabulary concept. The 1:N table follows the same
pattern as `sentences` and `word_forms`, keeping the schema consistent.
**Alternative rejected**: Pipe-delimited text in `words.meaning` — not queryable, parsing fragile.

### D5: part_of_speech as language-keyed enum stored as neutral key

**Decision**: `words.part_of_speech` stores a language-neutral key (e.g., `noun`, `verb`,
`i-adj`). The UI renders it via i18n lookup per locale. Dropdown options switch based on
`words.language`.
**Rationale**: Storing display text (「名詞」) would break cross-platform comparison and i18n
switching. A neutral key is stable across locales and Rust/Kotlin implementations.
**Keys (EN)**: `noun`, `verb`, `adjective`, `adverb`, `pronoun`, `preposition`, `conjunction`,
`interjection`, `other`
**Keys (JA)**: `noun`, `verb`, `i-adj`, `na-adj`, `adverb`, `particle`, `aux-verb`,
`conjunction`, `other`

### D6: Three-layer storage

| Layer | Location | Contents |
|-------|----------|----------|
| DB (synced) | `{data_local_dir}/easyvocabook/easyvocabook.db` | words, word_meanings, word_forms, sentences, db_info |
| Keychain | OS keychain (`keyring` crate) | FTP password, SFTP password, Google/OneDrive OAuth tokens |
| Local config | `{config_dir}/easyvocabook/settings.toml` | ui_language, theme, sync_method, FTP host/port/user/dir, Drive/OneDrive folder name, last_word_language, last_synced timestamp |

`settings.toml` is never synced. DB is synced. Keychain is device-local.

### D7: OAuth2 PKCE loopback flow (PC)

**Decision**: tokio spawns a temporary HTTP server on a random localhost port. `webbrowser` crate
opens the system browser to the OAuth URL. After the user authenticates, the provider redirects to
`http://127.0.0.1:<port>/callback?code=…`. The app exchanges the code for tokens via the `oauth2`
crate, then stores tokens in OS keychain.
**Rationale**: Google and Microsoft no longer support embedded WebView OAuth. Loopback PKCE is
the current recommended approach for desktop native apps (same mechanism used by tools like
Claude Code). No custom scheme registration required on Windows/Linux.
**Alternative rejected**: WebView — deprecated by both providers for OAuth.

### D8: Whole-file sync with three-way conflict detection

**Decision**: Sync uploads/downloads the entire `easyvocabook.db` file. Conflict detection
compares `db_info.last_modified` against a `last_synced` baseline stored in `settings.toml`.
- `local.last_modified == last_synced` → remote is newer, safe download (fast-forward)
- `remote.last_modified == last_synced` → local is newer, safe upload (fast-forward)
- Both differ → true conflict → prompt user: keep local / use remote
**Rationale**: Consistent with other Easy-series apps. AUTOINCREMENT IDs are safe under
whole-file replace (no record-level merge needed). Epoch-second timestamps are sufficient;
clock-skew is acceptable given manual user resolution of conflicts.
**Alternative rejected**: Record-level merge — incompatible with AUTOINCREMENT IDs across devices.

### D9: Quiz weighting — unified pool with count-based weights

**Decision**: All words share one weighted random pool:
- New word (practice_count == 0): weight = `NEW_WEIGHT` (default 3.0)
- Practiced word: weight = `base` + `incorrect_rate × multiplier` (defaults: base=1.0, multiplier=3.0)

Result: 0% error → 1.0 | new → 3.0 | 50% error → 2.5 | 100% error → 4.0

Small-sample amplification (1 attempt, wrong → rate=1.0 → weight=4.0) is intentional: words
just failed should resurface quickly. No Laplace smoothing in v1.
**Alternative rejected**: Separate new/practiced buckets — starves error correction when many
new words are added.

### D10: Multi-meaning quiz logic

**Decision**:
- **英翻中 / 日翻中** (multiple-choice): correct set = all meanings of the target word; user must
  select all of them. Distractors are drawn from other words, excluding any word whose meaning set
  intersects with the correct set.
- **中翻英 / 中翻日** (typing): a random meaning from the word is shown as prompt. Any word in DB
  whose meaning set contains that prompt meaning is an accepted answer (synonym acceptance).
  After reveal: user's answer + all valid synonyms are shown.

## Risks / Trade-offs

**[Risk] iced 0.14 `table` widget right-click support unclear**
→ Mitigation: spike early. If `table` does not support secondary mouse events, implement a custom
overlay using `iced::widget::stack` to simulate a context menu on right-click coordinates.

**[Risk] Furigana rendering above kanji not supported natively in iced**
→ Mitigation: deferred; v1 displays `reading` field as `漢字（かな）` beside the word. Proper
ruby layout can be added when iced provides a suitable widget or a custom layout is prototyped.

**[Risk] Google Drive / OneDrive API surface is large; only file upload/download/folder-create needed**
→ Mitigation: implement only the minimum API surface (find/create folder by name → get folder ID →
upload/download file). Tokens stored in keychain; refresh handled by `oauth2` crate.

**[Risk] OAuth loopback port conflict (another process holds the port)**
→ Mitigation: bind to port 0 (OS assigns a free port), pass the actual bound port in the redirect URI.

**[Trade-off] DbTableMemory loads all words at startup**
→ Acceptable: vocabulary books rarely exceed a few thousand entries. 5000 words ≈ 2.5 MB.
If a user somehow reaches tens of thousands of words, startup time may increase; pagination
can be introduced then.

**[Trade-off] Language-keyed part_of_speech has no FK validation**
→ Acceptable: the key list is small and maintained as a shared constant (Rust enum + Kotlin sealed
class). Invalid keys are rejected at the UI dropdown level, not the DB level.

## Migration Plan

This is a new application; there is no existing data to migrate.

Schema versioning is established from v1. Future schema changes will add migration steps to both
the Rust and Kotlin implementations following the additive-first policy.

## Open Questions

- **Exact OAuth client IDs**: Google Drive and OneDrive OAuth apps must be registered; client IDs
  are needed before implementing the OAuth flow. (PKCE = public client; no client secret required.)
- **Import/export format**: CSV? JSON? Custom? Deferred to a future change; the `…` menu entry
  exists but the handler is a no-op in v1.
- **Word-forms label vocabulary**: The canonical list of word_forms labels (e.g., `past_tense`,
  `plural`, `te-form`) must be finalized as a shared constant used by both platforms. To be
  defined in the `db-layer` spec.
