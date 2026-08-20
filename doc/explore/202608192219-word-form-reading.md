# 詞類讀音泛化 探索

## 動機

兩個使用者需求，追查後發現共用同一個病根：

1. 日文新增/編輯單字時，「其他詞類」只能填單字、不像原形可以填單字+讀音；
   測驗時原形可以輸入讀音作答，其他詞類不行。希望所有詞類都能有單字與讀音兩種輸入。
2. 測驗答案面（背面）只顯示單字、不顯示讀音，放棄作答時看得到漢字卻不知道怎麼唸。

---

## 根源分析

`word_forms` 資料表沒有 `reading` 欄位，讀音是原形（`words` 表）的專屬待遇：

```
原形 (words)                  其他詞類 (word_forms)
┌──────────────────┐          ┌──────────────────┐
│ word     食べる   │          │ label   masu_form │
│ reading  たべる   │          │ value   食べます  │
└──────────────────┘          └──────────────────┘
      ↑ 兩欄                         ↑ 只有一欄
```

測驗批改把讀音寫死成原形特例：

- Kotlin `QuizEngine.kt:113` — `field.label == "word" && (... input == word.reading ...)`
- Rust `engine.rs grade_typing()` — 原形比對 `word || reading`；詞類欄位只比對 `f.value`

答案面顯示同理：

- Kotlin `QuizScreen.kt:196`（選擇題結果）只印 `word.word`，但問題面 171-172 早就印了讀音
- 填空題結果的 `correctValue = field.value`，沒有讀音可印

---

## 需求 2 可拆成兩半

```
「答案面要顯示讀音」
├── 選擇題答案面顯示原形讀音   → 不需改 schema，可獨立先做
└── 填空題答案面顯示詞類讀音   → 依賴需求 1 的 schema 改動
```

---

## 影響範圍（需求 1）

| 層 | 改動 | 平台 |
|----|------|------|
| Schema | `word_forms` 加 `reading` 欄 → **DB v1 → v2** | 兩邊各寫一次 migration |
| Model | `WordForm{label,value}` → 加 `reading` | Rust + Kotlin |
| 編輯 UI | 每個詞類多一個讀音輸入框 | Rust + Kotlin |
| 測驗批改 | 移除「只有原形吃讀音」的特例 | Rust + Kotlin |
| 測驗答案面 | 顯示 `單字（讀音）` | Rust + Kotlin |

---

## 決策

| # | 議題 | 決策 |
|---|------|------|
| ① | DB v1→v2 升級的同步相容性（舊版 app 會拒開新版 db） | **兩邊（桌面/Android）都會一起更新**，不特別處理過渡期 |
| ② | 讀音欄要不要跨語言 | **所有語言都給讀音欄**，英文可填可不填（保留欄位，不限定 ja） |
| ③ | 既有 `hiragana`/`kanji`/`phonetic` 等「用詞類裝讀音」的變通 label | 既然讀音已泛化，**特規 label 不用保留** |
| ④ | 批改語意：單字與讀音的關係 | **滿足其一即算正確** |
| ⑤ | 兩平台測驗引擎架構分歧 | 確認可接受，實作各自處理 |

### ⑤ 的細節

兩平台原形的處理方式不同，同一功能在兩邊會長得不一樣：

- **Kotlin**：原形被包成 `TypingField("word", ...)` 混在 fields list 裡 →
  改動較小，拿掉 `label == "word"` 特例即可
- **Rust**：原形用 `matched` 另外判斷，與 `conjugation_fields` 分開 → 需動兩處

---

## 佐證：特規 label 的實際用量

使用者現有資料（`~/tmp/easyvocabook.db`，1157 字）的 `word_forms` label 分布：

```
te_form 284 / ta_form 284 / nai_form 284 / masu_form 284 / dictionary_form 284
```

完全沒有用到 `hiragana`/`kanji`/`phonetic`，故 ③ 的移除對既有資料風險極低。

---

## 未決問題

- ③ 移除特規 label 的範圍界定：是只從建議清單（`JA_FORM_LABELS`/`EN_FORM_LABELS`、
  `suggested_labels()`）移除，還是連 locale 字串（`form.hiragana` 等）一併清掉？
  schema.md 註明「非正規 label 也接受」，故既有資料仍可顯示，不會壞掉。
- 是否需要資料遷移：若他人資料已用 `hiragana` label 裝讀音，要不要在 v1→v2 migration
  自動搬進新的 `reading` 欄？（本人資料不受影響，但這是對外相容性考量）
- 編輯 UI 版面：每個詞類多一欄後，欄位密度變高，是否需要調整排版（尤其手機）。
- 答案面顯示格式：沿用既有慣例 `單字（讀音）`（Rust `format_word_display()`
  與 Kotlin 問題面已是此形式），維持一致。
