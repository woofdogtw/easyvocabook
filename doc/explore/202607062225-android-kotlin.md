# Android Kotlin App 探索

## 範圍決策

- **功能完整度**：與 Rust desktop 完整對齊（word list、word edit、quiz、settings、cloud sync）
- **OneDrive**：移除，Android 不做，同時從 desktop 版也拿掉
- **雲端同步提供者**：FTP / FTPS / SFTP / Google Drive（共四個）
- **UI 風格**：Android-native（Jetpack Compose + Material Design 3）
- **書本數**：單一書（固定 `easyvocabook.db`，與 desktop 一致）
- **DB 位置**：`filesDir/easyvocabook.db`（App 內部儲存空間，不需 external storage 權限）

---

## 技術棧決策

| 面向 | 選擇 | 理由 |
|------|------|------|
| UI framework | Jetpack Compose | config.yaml 指定；現代宣告式 UI，動態列表/表單比 XML 省力 |
| Navigation | Bottom Navigation Bar（3 tabs） | 3 個主頁面是 bottom nav 的典型用例 |
| Architecture | MVVM + ViewModel + StateFlow | Compose 官方推薦，狀態管理清晰 |
| DB | 原生 Android SQLite API（SQLiteDatabase） | 沿用 ECA/EHR 模式，不引入 Room；DbTableBase interface 保持一致 |
| FTP/FTPS | commons-net 3.x | 沿用 ECA/EHR，成熟穩定 |
| SFTP | SSHJ（net.schmizz:sshj） | 比 JSch 更現代，active maintained（2024+） |
| Google Drive auth | Google Identity Services — Authorization API | 見下方詳述 |
| Google Drive REST | okhttp3 | 與 EHR 一致，輕量 |
| 設定儲存 | EncryptedSharedPreferences（敏感）+ SharedPreferences（一般） | token/密碼加密，其餘明文 |
| minSdk / compileSdk | 29 / 34 | 沿用 ECA/EHR baseline |

---

## Google Drive Auth 設計

### 選擇：Authorization API（非 Firebase、非 deprecated Sign-In SDK）

```
com.google.android.gms:play-services-auth（Google Identity Services）

AuthorizationRequest.Builder()
  .setRequestedScopes([Scope("https://www.googleapis.com/auth/drive.file")])
  .build()

→ AuthorizationResult
    .accessToken   (短效，直接用)
    .pendingIntent (需要使用者同意時彈出)
```

- 不用 Firebase
- 不用已 deprecated 的 `GoogleSignInClient`
- Play Services 自動處理 silent refresh（access token 過期自動重新取得）
- Refresh token 不需要我們手動管理（Play Services 持有）
- 儲存項目：`SP_SYNC_GOOGLE_FOLDER`（資料夾名稱，只此一項）

### 比較

| 選項 | 狀態 | Firebase 需要？ | 說明 |
|------|------|----------------|------|
| GoogleSignInClient | Deprecated（2024） | 否 | EHR 原計劃但沒完成 |
| Authorization API | 現行推薦 | 否 | **選此** |
| Credential Manager + AuthorizationRequest | 更新版，包含 Authorization API | 否 | 本質一致 |
| Firebase Auth | 現行 | **是** | config.yaml 明確禁止 |

---

## 架構圖

```
tw.idv.woofdog.easyvocabook (Android)
═══════════════════════════════════════════════════════

UI (Jetpack Compose + Material 3, purple+teal, Day/Night)
┌─────────────────────────────────────────────────────┐
│  QuizScreen    WordListScreen   SettingsScreen        │
│  ┌──────────┐  ┌─────────────┐  ┌────────────────┐  │
│  │ FlipCard │  │ LazyColumn  │  │ App section    │  │
│  │ Typing   │  │ sort/filter │  │ Sync section   │  │
│  │ MCQ      │  │ FAB (Add)   │  │ Practice stats │  │
│  └──────────┘  └──────┬──────┘  └────────────────┘  │
│                        │ ModalBottomSheet (WordEditSheet)│
│                        └─ word / reading / meaning   │
│                           + Add Meaning / word_forms / sentences│
└──────────────────────────────────────────────────────┘
NavigationBar: 🎯 Quiz  📖 Word List  ⚙ Settings
(Quiz 為預設啟動 tab，與 desktop 一致)

State (MVVM)
┌──────────────────────────────────────────────────────┐
│  WordListViewModel  QuizViewModel  SettingsViewModel  │
│  StateFlow<UiState>                                  │
└──────────────────────────────────────────────────────┘

Data (same abstract pattern as ECA/EHR)
┌──────────────────────────────────────────────────────┐
│  DbTableBase (interface)                             │
│  ├── DbTableSQLite  ← filesDir/easyvocabook.db       │
│  └── DbTableMemory  ← search results / read-only    │
└──────────────────────────────────────────────────────┘

Sync
┌──────────────────────────────────────────────────────┐
│  SyncClient (interface, mirrors Rust SyncClient)     │
│  ├── NetFtp    (commons-net — FTP/FTPS)              │
│  ├── NetSftp   (SSHJ — SFTP)                        │
│  └── NetDrive  (Authorization API + okhttp3 REST)   │
└──────────────────────────────────────────────────────┘
```

---

## 對稱性：Rust ↔ Kotlin

| 面向 | Rust (Desktop) | Kotlin (Android) |
|------|---------------|------------------|
| DB interface | `DbTableBase` trait | `DbTableBase` interface |
| DB SQLite impl | `DbTableSQLite` | `DbTableSQLite` |
| DB memory impl | `DbTableMemory` | `DbTableMemory` |
| Sync interface | `SyncClient` trait | `SyncClient` interface |
| FTP client | `FtpClient` (suppaftp) | `NetFtp` (commons-net) |
| SFTP client | `SftpClient` (russh) | `NetSftp` (SSHJ) |
| Drive client | `DriveClient` (PKCE loop) | `NetDrive` (Auth API) |
| Settings | `settings.toml` | SharedPreferences |
| Token store | OS keychain (keyring) | EncryptedSharedPreferences |
| DB path | `data_local_dir()/easyvocabook/` | `filesDir/` |

Schema（`db_info` / `words` / `word_meanings` / `word_forms` / `sentences`）兩平台完全相同，
migration SQL 各自實作（不共用工具），以 `db_info.version` 作為版本標記。

---

## Word Edit UI 設計

桌面版是 modal dialog；Android 選 `ModalBottomSheet`：

```
[Word List] ─ tap FAB / long-press row ─→

┌──────────────────────────────┐  ← ModalBottomSheet
│  Add Word                    │
│  ─────────────────────────── │
│  Word *          [__________]│
│  Reading         [__________]│
│  Language        [en ▼      ]│
│  Part of speech  [noun ▼    ]│
│  ─────────────────────────── │
│  Meaning (primary) *         │  ← words.meaning（必填，NOT NULL）
│  [___________________________]│
│  ─────────────────────────── │
│  Additional Meanings         │  ← word_meanings（0..N）
│  1. [___________]   [－]     │
│  [＋ Add meaning]            │
│  ─────────────────────────── │
│  Note                        │  ← words.note（補充說明，選填）
│  [___________________________]│
│  ─────────────────────────── │
│  Word Forms  (動態，依語言)  │
│  past  [__________]          │
│  past_p[__________]          │
│  ─────────────────────────── │
│  Sentences                   │
│  [+ Add sentence]            │
│  ─────────────────────────── │
│       [Cancel]    [Save]     │
└──────────────────────────────┘
```

欄位說明：
- **Meaning (primary)**：對應 `words.meaning`，必填，列表顯示用
- **Additional Meanings**：對應 `word_meanings`，0..N，構成完整意思集合（quiz MCQ 用）
- **Note**：對應 `words.note`，自由文字，選填
- BottomSheet 可 scroll，欄位多時自然往下延伸；FAB 和 long-press 都觸發同一個 Sheet

---

## Quiz UI 設計

**無翻牌卡模式、無自評**。quiz-engine 是跨平台契約，Android 必須與 Rust desktop 完全一致：
- 只有 **Typing**（中翻目標語、打字填答）與 **Multiple-Choice**（目標語翻中、選全部正確意思）
- 每題都有 **[Give Up]**（顯示答案＋**自動記錯**，practice+1，correct 不動）
- **⏭ Skip**（不記分、換下一題，不同於 Give Up）
- 答對：practice+1、correct+1；答錯：practice+1；Skip：兩者皆不動

```
Typing 模式（中翻英 / 中翻日）
┌──────────────────────────────┐
│  Quiz                  ⏭ Skip│
│                              │
│  abandon                     │  ← 顯示中文意思作為提示
│                              │
│  word      [______________]  │
│  past      [______________]  │  ← word_forms（若有）
│  past_p    [______________]  │
│                              │
│  [Give Up]       [Submit]    │
└──────────────────────────────┘

Multiple-Choice 模式（英翻中 / 日翻中）
┌──────────────────────────────┐
│  Quiz                  ⏭ Skip│
│                              │
│  abandon (v.)                │  ← 顯示單字（＋讀音若有）
│                              │
│  ☑ 放棄                     │  ← 可多選，需全選對才算對
│  ☐ 捨棄                     │
│  ☐ 丟棄                     │
│  ☑ 棄置                     │
│                              │
│  [Give Up]       [Submit]    │
└──────────────────────────────┘

答案揭曉後（Give Up 或答錯）
┌──────────────────────────────┐
│  ✗  Incorrect                │
│                              │
│  abandon → 放棄、捨棄、棄置  │  ← 全部正確意思
│  past: abandoned             │
│                              │
│               [Next →]       │
└──────────────────────────────┘
```

Compose 動畫可用於題目卡片的入場／答案揭曉過渡，但**不是翻牌卡互動模式**。

---

## 設定頁結構

```
Settings
├── App
│   ├── UI Language  (English / 繁體中文 / 简体中文)
│   └── Theme        (Light / Dark / Auto)
├── Sync
│   ├── Method ○ Disabled ○ FTP ○ FTPS ○ SFTP ○ Google Drive
│   ├── [FTP fields 展開]
│   ├── [SFTP fields 展開]
│   └── [Google Drive fields 展開]  ← 含 Login / Logout
├── Practice
│   └── [Clear Practice Stats]
└── About
    └── version, license
```

無 OneDrive（移除，桌面版也同步移除）。

---

## OneDrive 移除範圍

需要動到的地方：

**Rust desktop：**
- `rust/src/network/onedrive.rs` → 刪除
- `rust/src/network/mod.rs` → 移除 `pub mod onedrive`
- `rust/src/config/mod.rs` → 移除 `onedrive_folder` 欄位
- Settings UI → 移除 OneDrive radio + 欄位
- Sync orchestrator → 移除 OneDrive branch

**Specs：**
- `cloud-sync` spec → 移除 OneDrive requirement
- `settings-ui` spec → 移除 OneDrive configuration requirement
- `openspec/config.yaml` → 移除 OneDrive 提及

**機制決策**：`rust-desktop` 目前狀態是 83/83 complete、**尚未 archive**。
因此最乾淨的做法是：先在 rust-desktop change 裡完成 OneDrive 移除（程式 + spec）再 archive，
android-kotlin change 從乾淨的 specs 出發，不需要開 REMOVED/MODIFIED delta。

執行順序：
1. 在 rust-desktop change 追加 OneDrive 移除任務並實作
2. Archive rust-desktop（此時 specs 已無 OneDrive）
3. 開 android-kotlin change（specs 乾淨，只需 ADDED capabilities）

---

## 多 DB 討論（暫緩）

曾考慮仿 ECA/EHR 的多 DB 管理風格（book list 畫面、可切換書本）。
決定：**v0.1 先維持單 DB**，以 Compose UI + 單一書本的感覺為優先。
後續若要轉多 DB，再開獨立 change，且同時調整 rust-desktop，等 Android v0.1 完成後再評估。

---

## 跨平台 Sync 契約（Android 必須對齊的三條）

以下三條是 Rust 已實作、Android 必須完全一致的行為。propose 時寫進 cloud-sync spec delta：

1. **latest-wins decide()**：比較 `local.last_modified` vs `remote.last_modified`，大的贏；
   無 `last_synced`、無衝突對話框。
2. **fresh install 種 `db_info.last_modified = 0`**：新建 DB 時設 0（非當下時間），
   確保第一次同步必定下載遠端，不會用空 DB 覆蓋雲端已有的資料。
3. **`remote_last_modified` 錯誤區分**：
   - 遠端檔案不存在 → `Ok(null)`（視為無遠端，可上傳）
   - 網路/認證錯誤 → 拋出例外，**中止 sync，不繼續任何動作**（防止誤覆蓋）

---

## 日文 furigana 顯示

與 desktop 一致：**暫緩 ruby 渲染，退化為 `漢字（かな）` 並排**。
Compose 的 `AnnotatedString` + `SpanStyle` 雖然可以做上方小字，但需要自定義 layout，
v0.1 不做，留待未來版本。

---

## 附帶清理

- `doc/schema.md` 最後兩行（`last_synced` / three-way conflict detection 的說明）是過期描述，
  已改為 latest-wins，需要刪除或改寫。列為 rust-desktop 追加任務（在 archive 前處理）。

---

## Google Drive Auth 補充說明

每次同步前呼叫 `Authorization.getClient(context).authorize(request)`：
- 已同意且 token 未過期 → Play Services **silent return**，立即拿到 `accessToken`
- token 過期 → Play Services **自動 silent refresh**，仍無 UI
- 尚未同意或 refresh token 失效 → 回傳 `pendingIntent`，需要跳出 consent 畫面

**我們不自行儲存 access token**（短效、Play Services 管理）；
儲存的只有 `SP_SYNC_GOOGLE_FOLDER`（資料夾名稱）。
Refresh token 由 Play Services 在裝置帳號系統持有，app 無需碰。

---

## EncryptedSharedPreferences 說明

`androidx.security:security-crypto` 目前處於**維護模式**（功能凍結，bug fix only）。
Google 尚未提供正式替代方案（DataStore 加密仍在實驗階段）。
對 EVB 的 FTP/SFTP 密碼儲存而言，繼續使用是可接受的，沿用 ECA/EHR 做法即可。
長期留意 Google 動向，若有 stable 替代方案再遷移。

---

## 待確認事項（開 change 前）

- SSHJ 的 Maven 坐標確認（`com.hierynomus:sshj:x.x.x`）
- Authorization API 最低 Play Services 版本確認
- `compileSdk` 是否升到 35（2025 年底 Google 要求）

---

## Change 名稱建議

`android-kotlin`

下一步：`/opsx:propose android-kotlin`
