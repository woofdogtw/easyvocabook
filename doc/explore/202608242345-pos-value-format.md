# 詞性欄位值格式不一致 探索

## 動機

`verb-transitivity`（2026-08-23 合併）新增的自他動詞屬性，在 Android 上**自己建立**日文動詞時
完全不會運作：三顆 radio button 不顯示，`transitivity` 與 `verb_group` 也寫不進資料庫。

根因不在昨天那個 change，而在六週前的 Android 移植：`part_of_speech` 這個欄位，
**兩平台存的值格式不同**。

## 現況：同一個「日文動詞」有兩種存法

| 來源 | 存進 `part_of_speech` 的值 |
|---|---|
| Rust 桌面（`JA_POS`） | `verb` |
| `~/tmp/gen_vocab_sql.py` 種子 | `verb` |
| **Android（`POS_JA`）** | **`動詞`** |

`POS_JA` 存的是顯示文字而非 key，而同一個檔案裡的 `POS_EN` 存的是 key：

```kotlin
// WordEditSheet.kt:23-24
private val POS_EN = listOf("", "noun", "verb", "adjective", "adverb", "phrase")   // key
private val POS_JA = listOf("", "名詞", "動詞", "い形容詞", "な形容詞", ...)          // 顯示文字
```

英文正確、日文不正確，所以問題只在日文顯現。

`Labels.posDisplay()` 的 `when` 只認 key（`"verb" -> "動詞 (verb)"`），碰到 `"動詞"`
落到 `else -> pos` 原樣回傳——看起來正常，於是沒人發現。

### 為什麼六週都沒事

移植當時唯一讀 POS 的地方是 `WordFormLabels.forWord`，而它**刻意兩種都收**：

```kotlin
// QuizEngine.kt:55,58
"verb", "動詞" -> listOf(...)
"i-adj", "い形容詞" -> listOf(...)
```

寫的人知道有兩種格式並做了防禦。分歧因此長期無害。

## 昨天的 change 讓分歧變成 bug

`1a48436 implement: verb-transitivity` 新增了 6 處 `== "verb"` 比對，**都沒有沿用上面的防禦慣例**：

| 位置 | 後果 | 嚴重度 |
|---|---|---|
| `DbTableSQLite.kt:352` (新增) | `transitivity`/`verb_group` 寫成 NULL | **靜默資料遺失** |
| `DbTableSQLite.kt:368` (編輯) | 同上 | **靜默資料遺失** |
| `WordEditSheet.kt:130` | 三顆 radio button 不顯示 | 無法輸入 |
| `WordEditViewModel.kt:92` | 切換詞性／語言時清空屬性 | 資料遺失 |
| `QuizEngine.kt:106` | 動詞不會被考自他 | 功能失效 |
| `DbTableMemory.kt:57` | 同 SQLite 的正規化 | 一致性 |

持久層那兩處最嚴重：**即使 UI 讓你填，寫入時仍會被丟掉**。

### 桌面端也會被波及

Rust 自己是一致的（`JA_POS` 用 key），但它有 5 處同樣的閘門：

```
rust/src/db/sqlite.rs:8（verb_only）        rust/src/quiz/engine.rs:129
rust/src/ui/mod.rs:154,162     rust/src/ui/word_edit.rs:128
```

Android 建立的字帶著 `動詞` 經 Drive 同步過去，桌面同樣全部略過，
且 `suggested_labels("ja","動詞")` 落到 `_ => &[]`，建議欄位全空。

**這是跨平台污染，不是單一平台的 bug。**

### 種子資料為什麼測不出來

手動驗證用的是種子 DB（`part_of_speech = "verb"`），所有閘門都成立，功能看起來正常。
只有「在 Android 新建日文動詞」或「動到詞性下拉」才會踩到。

還有一條更隱蔽的路徑：種子的字 `pos = "verb"`，但 `"verb"` **不在 `POS_JA` 選項裡**。
下拉框顯示「動詞 (verb)」，選項列卻是「動詞」——使用者只要點開重選，
值就從 `verb` 變成 `動詞`，存檔後自他資訊消失。

## 決策

### ① 只除根，不做相容

`POS_JA` 改存 key，靠詞性顯示函式呈現，全專案只留一種格式。

**不採用「兩種格式都接受」的相容作法。** 既然資料庫重建（見 ②），
舊值不復存在，相容層沒有保護對象，只會讓格式錯誤在某些地方靜默通過。
6 處 `== "verb"` 閘門維持原樣即可——它們本來就是對的，錯的是餵給它們的值。

### ② 既有資料：砍掉重練，因此不需遷移

作者的 DB 全部重建。種子資料一律是 key，重建後不存在任何 `動詞` 這類值，因此：

- **不升 schema 版本**
- **不做讀取容錯**

前提是所有副本都清除，包含手機上的 DB 與 Drive 上的那份；
留一份舊的之後同步回來，壞值會復活。

### ③ 移除既有的雙格式防禦

既然統一成 key，`forWord` 現有的

```kotlin
"verb", "動詞" -> listOf(...)
"i-adj", "い形容詞" -> listOf(...)
```

**應一併移除**。留著的話，壞值會在這一處靜默正常、在其他六處失效——正是本次 bug 的成因。
移除後任何格式錯誤會在所有地方一致地失敗，容易發現。

### ④ 詞性顯示：維持現狀（原決策已撤回）

原本決定「Kotlin `posDisplay` 加 `language` 參數，讓日文顯示 `動詞` 而非 `動詞 (verb)`」。
**此決策在 review 時撤回**，三個理由：

1. `rust/src/db/labels.rs:175` 的 `pos_display()` **是死程式碼**，全 repo 沒有呼叫端；
   `ui/word_edit.rs:62` 的 `pos_displays` 是同名區域變數，由 `pos_locale_key()` + `t()` 組成。
   `labels.rs` 開頭的 `#![allow(dead_code)]` 讓它從不發出警告。
2. 桌面實際顯示的就是 `動詞 (verb)`（`rust/src/locale/mod.rs:241`），
   與 Android 現況**完全一致**——原本以為的「Kotlin 缺陷」並不存在。
3. `openspec/specs/word-edit-ui/spec.md:24-25` 明文要求中文語系顯示
   「中文 (english_key)」，改掉會**違反規格**，也與本 change 宣告的 `skip_specs` 矛盾。

`POS_JA` 改存 key 之後，`posDisplay("verb")` 自然回傳「動詞 (verb)」，兩平台與規格三者一致，
不需要任何額外處理。若日後真想要不帶括號的日文顯示，那是獨立的 UI 決策，
需要 `word-edit-ui` delta 並同時修改 Rust 的 locale 表。

### ⑤ 防止復發：兩邊都要加測試

`POS_EN` 正確而 `POS_JA` 不正確，本質是**同一份清單被寫了兩次**且彼此無連結。

原本以為「Rust 有一致性測試守著，Kotlin 缺」——**這是錯的，兩邊都沒有**。
`labels.rs:196` 的 `every_suggested_label_is_canonical` 雖然遍歷 `EN_POS`/`JA_POS`，
但只斷言「這些詞性所建議的標籤是 canonical」。若 `JA_POS` 被寫成 `["動詞", ...]`，
`suggested_labels("ja","動詞")` 回空、內層迴圈不執行，**測試照樣綠燈**。
Rust 沒出事只是因為當初寫對了，不是因為有守門。

因此兩平台都要加斷言：清單內容等於規格清單，且不含非 ASCII 字元。
後者正是能在寫下當天就攔住這個 bug 的檢查。

另外，Kotlin 兩份清單目前是 `WordEditSheet.kt` 裡的 top-level `private`，測試看不到，
必須先搬到 `ui/Labels.kt`（`SUPPORTED_LANGUAGES`、`EN_FORM_LABELS` 已在那裡）才可能實作。

## 規格早已定案：本 change 沒有設計空間

這不是設計分歧，而是 Android 實作偏離了既有規格。

`openspec/specs/db-schema/spec.md:144` 明文要求，且用的正是 Android 現在做錯的那個例子：

```
### Requirement: part_of_speech stored as language-neutral key
#### Scenario: Japanese word with i-adj part of speech
- WHEN 一個日文單字類型為「い形容詞」被儲存
- THEN part_of_speech 存的是 i-adj，不是「い形容詞」
```

canonical 清單也已寫定（`openspec/specs/word-edit-ui/spec.md:62`）：

```
English (en): noun, verb, adjective, adverb, pronoun, preposition, conjunction, interjection, other
Japanese (ja): noun, verb, i-adj, na-adj, adverb, particle, aux-verb, conjunction, other
```

**兩邊都沒有 `phrase` / `句`。**

### 三種偏差的來源

| 時間 | 事件 |
|---|---|
| 2026-07-05 | Rust 桌面實作，照規格寫出 `EN_POS`／`JA_POS`（完全相符） |
| 2026-07-13 | Android 移植，下拉選單重寫一份，未回頭對規格 |

Android 那份因此同時有三種偏差：

1. 日文存顯示文字——違反 `db-schema` 明文要求
2. 憑空多出 `phrase` / `句`——不在 canonical 清單
3. 漏掉 `pronoun`、`preposition`、`interjection`、`aux-verb`、`conjunction`、`other`

英文那份看似正確，只是因為它剛好是規格清單的**子集**；它同樣漏了五個選項。

### 機械比對結果

對三份清單逐項比對（規格 / Rust / Kotlin）：

| | 規格 | Rust | Kotlin |
|---|---|---|---|
| en | 9 項 | 9 項，**完全相符** | **5 項** |
| ja | 9 項 | 9 項，**完全相符** | **7 項**，且存顯示文字 |

- **Kotlin 英文缺 5 項**：`pronoun`、`preposition`、`conjunction`、`interjection`、`other`
- **Kotlin 日文缺 3 項**：`aux-verb`、`conjunction`、`other`
- **兩邊都多出** `phrase` / `句`

因此 Android 上選不到代名詞、介系詞、連接詞、感嘆詞、助動詞。

### 其他平行清單並未分歧

同樣被複製兩份的欄位標籤表經比對**內容一致**（僅宣告順序不同），本 change 不需觸碰：

| 清單 | Rust | Kotlin |
|---|---|---|
| `EN_FORM_LABELS` | 9 | 9 ✓ |
| `JA_FORM_LABELS` | 13 | 13 ✓ |
| 建議欄位表 | 5 組 | 5 組 ✓ |

（`project_word_form_reading` 記錄的「suggestion table 分歧」指的是桌面版
對 ja/noun 與形容詞顯示空的恆對欄位，屬測驗欄位選取邏輯，與本表內容無關。）

### 結論

`phrase`／`句` **移除**，兩邊清單完全對齊規格。本 change 沒有取捨要做，
只是把偏離規格的實作拉回來——因此也不需要修改任何 spec，只有實作要改。

## 資料現況：未必需要重置

種子 DB（`~/tmp/easyvocabook.db`）實際內容：

```
ja/noun 773    ja/verb 289    ja/i-adj 65    ja/na-adj 35
```

四種值**全是 key、全為 ASCII**，沒有 `phrase`，且**完全沒有英文單字**——
所以補上 5 個英文選項也不影響任何既有資料。

壞值只可能出現在「曾在 Android 新建或重選過詞性」的字。因此重置改為**條件式**：
先對三份副本各跑 `SELECT DISTINCT part_of_speech FROM words;`，
只有出現非 key 值才需要重置，否則直接裝修正版即可保留練習統計。

（`verb-transitivity` 兩天前才全部重置過一次，不必要的第二次會再丟一次統計。）

## 與後續 change 的關係

單字列表要加的「詞性徽章 + 對照欄」直接讀 `part_of_speech`。
本 fix 未完成前，徽章在 Android 建立的字上會顯示錯誤內容。
**應先完成本 fix，再開徽章的 explore。**
