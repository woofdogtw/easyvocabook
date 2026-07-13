pub mod quiz;
pub mod settings;
pub mod word_edit;
pub mod word_list;

use iced::widget::{button, column, container, row, text};
use iced::{Element, Length, Point, Task};

use crate::config::{Settings, SyncMethod, Theme};
use crate::db::labels;
use crate::db::{
    self, DbTableBase, DbTableMemory, NewWord, SortField, UpdateWord, WordEntry, WordFilter,
};
use crate::network::{
    SyncClient,
    drive::DriveClient,
    ftp::FtpClient,
    sftp::SftpClient,
    sync::{SyncResult, decide, download_and_reload, read_local_last_modified_async},
};
use crate::quiz::engine::{self, QuizMode, QuizQuestion};
use crate::quiz::sampler;
use std::path::PathBuf;

// ── Tabs ─────────────────────────────────────────────────────────────────────

#[derive(Debug, Clone, PartialEq)]
pub enum Tab {
    Quiz,
    WordList,
    Settings,
}

// ── Word list sub-state ───────────────────────────────────────────────────────

#[derive(Default, Clone)]
pub struct WordListState {
    pub search_visible: bool,
    pub more_menu_open: bool,
    pub context_word_id: Option<i64>,
    pub context_menu_pos: Point,
    pub local_cursor_pos: Point,
    pub confirm_delete_id: Option<i64>,
    pub info_message: Option<&'static str>,
    pub scroll_offset: iced::widget::scrollable::AbsoluteOffset,
    pub hovered_row: Option<i64>,
}

// ── Word edit sub-state ────────────────────────────────────────────────────────

#[derive(Clone)]
pub struct WordEditState {
    pub open: bool,
    pub editing_id: Option<i64>,
    pub language: String,
    pub word: String,
    pub reading: String,
    pub primary_meaning: String,
    pub pos: String,
    pub extra_meanings: Vec<String>,
    pub forms: Vec<(String, String)>,
    pub sentences: Vec<(String, String)>,
    pub error: Option<String>,
}

impl Default for WordEditState {
    fn default() -> Self {
        Self {
            open: false,
            editing_id: None,
            language: "en".into(),
            word: String::new(),
            reading: String::new(),
            primary_meaning: String::new(),
            pos: String::new(),
            extra_meanings: Vec::new(),
            forms: Vec::new(),
            sentences: Vec::new(),
            error: None,
        }
    }
}

impl WordEditState {
    pub fn for_new(last_language: &str) -> Self {
        Self {
            language: last_language.to_owned(),
            ..Default::default()
        }
    }

    pub fn from_entry(e: &WordEntry) -> Self {
        Self {
            open: true,
            editing_id: Some(e.id),
            language: e.language.clone(),
            word: e.word.clone(),
            reading: e.reading.clone().unwrap_or_default(),
            primary_meaning: e.meaning.clone(),
            pos: e.part_of_speech.clone().unwrap_or_default(),
            extra_meanings: e.meanings.clone(),
            forms: e
                .forms
                .iter()
                .map(|f| (f.label.clone(), f.value.clone()))
                .collect(),
            sentences: e
                .sentences
                .iter()
                .map(|s| {
                    (
                        s.sentence.clone(),
                        s.translation.clone().unwrap_or_default(),
                    )
                })
                .collect(),
            error: None,
        }
    }

    fn repopulate_forms(&mut self) {
        let suggestions = labels::suggested_labels(&self.language, &self.pos);
        let old: std::collections::HashMap<String, String> = self.forms.iter().cloned().collect();
        self.forms = suggestions
            .iter()
            .map(|&label| {
                let value = old.get(label).cloned().unwrap_or_default();
                (label.to_owned(), value)
            })
            .collect();
    }

    fn to_new_word(&self) -> NewWord {
        NewWord {
            word: self.word.trim().to_owned(),
            reading: if self.reading.trim().is_empty() {
                None
            } else {
                Some(self.reading.trim().to_owned())
            },
            meaning: self.primary_meaning.trim().to_owned(),
            part_of_speech: if self.pos.is_empty() {
                None
            } else {
                Some(self.pos.clone())
            },
            note: None,
            language: self.language.clone(),
            meanings: self
                .extra_meanings
                .iter()
                .filter(|m| !m.trim().is_empty())
                .cloned()
                .collect(),
            forms: self
                .forms
                .iter()
                .filter(|(_, v)| !v.trim().is_empty())
                .map(|(l, v)| (l.clone(), v.trim().to_owned()))
                .collect(),
            sentences: self
                .sentences
                .iter()
                .filter(|(s, _)| !s.trim().is_empty())
                .map(|(s, t)| {
                    (
                        s.trim().to_owned(),
                        if t.trim().is_empty() {
                            None
                        } else {
                            Some(t.trim().to_owned())
                        },
                    )
                })
                .collect(),
        }
    }

    fn to_update_word(&self) -> UpdateWord {
        let nw = self.to_new_word();
        UpdateWord {
            word: nw.word,
            reading: nw.reading,
            meaning: nw.meaning,
            part_of_speech: nw.part_of_speech,
            note: nw.note,
            language: nw.language,
            meanings: nw.meanings,
            forms: nw.forms,
            sentences: nw.sentences,
        }
    }
}

// ── Quiz sub-state ────────────────────────────────────────────────────────────

#[derive(Clone, Default)]
pub struct QuizState {
    pub language: Option<String>,
    pub current: Option<QuizQuestion>,
    pub typing_word: String,
    pub typing_fields: Vec<(String, String)>,
    pub mc_selected: Vec<bool>,
    pub submitted: bool,
    pub gave_up: bool,
    pub typing_correct: Option<bool>,
    pub field_results: Vec<(String, bool, String)>,
    pub choice_correct: Option<bool>,
}

impl QuizState {
    fn load_next(&mut self, pool: &[WordEntry]) {
        let filtered: Vec<WordEntry> = if let Some(lang) = &self.language {
            pool.iter()
                .filter(|e| &e.language == lang)
                .cloned()
                .collect()
        } else {
            pool.to_vec()
        };

        if filtered.is_empty() {
            self.current = None;
            return;
        }

        let seed = now_seed();
        if let Some(i) = sampler::weighted_pick(&filtered, seed) {
            let target = &filtered[i];
            let mode = engine::select_mode(target, seed);
            let question = engine::build_question(target, &filtered, mode, seed);
            let mc_len = question.options.len();
            let field_labels: Vec<(String, String)> = question
                .conjugation_fields
                .iter()
                .map(|f| (f.label.clone(), String::new()))
                .collect();
            self.current = Some(question);
            self.typing_word = String::new();
            self.typing_fields = field_labels;
            self.mc_selected = vec![false; mc_len];
            self.submitted = false;
            self.gave_up = false;
            self.typing_correct = None;
            self.field_results = Vec::new();
            self.choice_correct = None;
        } else {
            self.current = None;
        }
    }
}

// ── Settings UI sub-state ─────────────────────────────────────────────────────

#[derive(Clone, Default)]
pub struct SettingsUiState {
    pub ftp_port_str: String, // editable port string (parsed on change)
    pub ftp_password: String,
    pub sftp_port_str: String,
    pub sftp_password: String,
    pub drive_logged_in: bool,
    pub drive_email: Option<String>,
    pub drive_auth_url: Option<String>,
    pub drive_auth_pending: Option<std::sync::Arc<crate::network::drive::DriveAuthPending>>,
    pub sync_in_progress: bool,
    pub sync_message: Option<String>,
    pub clear_stats_confirm: bool,
}

impl SettingsUiState {
    pub fn init(settings: &Settings) -> Self {
        Self {
            ftp_port_str: settings.ftp_port.to_string(),
            sftp_port_str: settings.sftp_port.to_string(),
            drive_logged_in: DriveClient::is_logged_in(),
            ..Default::default()
        }
    }
}

fn now_seed() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_nanos() as u64)
        .unwrap_or(12345)
}

// ── Sync state passed between the two-phase sync tasks ────────────────────────

#[derive(Debug, Clone)]
pub(crate) enum SyncNextStep {
    Done(SyncResult),
    NeedUpload { method: SyncMethod, path: PathBuf, settings: Settings },
    NeedDownload { method: SyncMethod, path: PathBuf, settings: Settings },
}

// ── Messages ──────────────────────────────────────────────────────────────────

#[derive(Debug, Clone)]
pub enum Message {
    TabChanged(Tab),
    DbLoaded(Result<DbTableMemory, String>),
    CursorMoved(Point),
    TabKeyPressed { shift: bool },
    QuizEnterKey,

    // ── Word list ─────────────────────────────────────────────────────────────
    WordListSort(SortField),
    WordListLanguage(String),
    WordListSearchToggle,
    WordListSearchChanged(String),
    WordListContextMenu(i64),
    WordListContextMenuClose,
    WordListMoreMenu,
    WordListImport,
    WordListExport,
    WordListInfoDismiss,
    WordListNew,
    WordListEdit(i64),
    WordListDeleteAsk(i64),
    WordListDeleteConfirm,
    WordListDeleteCancel,
    WordListSyncNow,
    WordDeleted(Result<i64, String>),
    WordListCursorMoved(Point),
    WordListScrolled(iced::widget::scrollable::Viewport),
    WordListHover(Option<i64>),

    // ── Word edit dialog ──────────────────────────────────────────────────────
    WordEditClose,
    WordEditLanguage(String),
    WordEditWord(String),
    WordEditReading(String),
    WordEditMeaning(String),
    WordEditPos(String),
    WordEditAddMeaning,
    WordEditRemoveMeaning(usize),
    WordEditChangeMeaning(usize, String),
    WordEditAddForm,
    WordEditRemoveForm(usize),
    WordEditFormLabel(usize, String),
    WordEditFormValue(usize, String),
    WordEditAddSentence,
    WordEditRemoveSentence(usize),
    WordEditSentence(usize, String),
    WordEditTranslation(usize, String),
    WordEditSave,
    WordEditSaved(Result<WordEntry, String>),

    // ── Quiz ──────────────────────────────────────────────────────────────────
    QuizLanguage(String),
    QuizNextCard,
    QuizTypingWord(String),
    QuizTypingField(usize, String),
    QuizMcToggle(usize),
    QuizSubmit,
    QuizGiveUp,
    QuizSkip,
    QuizStatsUpdated(Result<(i64, bool, i64), String>),

    // ── Settings ──────────────────────────────────────────────────────────────
    SettingsUiLanguage(String),
    SettingsTheme(Theme),
    SettingsSyncMethod(SyncMethod),
    SettingsFtpHost(String),
    SettingsFtpPort(String),
    SettingsFtpUser(String),
    SettingsFtpPassword(String),
    SettingsFtpDir(String),
    SettingsFtpTls(bool),
    SettingsFtpSave,
    SettingsSftpHost(String),
    SettingsSftpPort(String),
    SettingsSftpUser(String),
    SettingsSftpPassword(String),
    SettingsSftpDir(String),
    SettingsSftpSave,
    SettingsDriveFolder(String),
    SettingsDriveLogin,
    SettingsDriveAuthReady(Result<std::sync::Arc<crate::network::drive::DriveAuthPending>, String>),
    SettingsDriveAuthCopyUrl,
    SettingsDriveAuthCancel,
    SettingsDriveLogout,
    SettingsDriveLoginDone(Result<(), String>),
    SettingsSyncNow,
    SettingsSyncPhase2(Result<SyncNextStep, String>),
    SettingsSyncDone(Result<SyncResult, String>),
    SettingsClearStatsAsk,
    SettingsClearStatsConfirm,
    SettingsClearStatsCancel,
    SettingsClearStatsDone(Result<(), String>),
}

// ── Application state ─────────────────────────────────────────────────────────

pub struct App {
    pub tab: Tab,
    pub db_path: std::path::PathBuf,
    pub memory: DbTableMemory,
    pub settings: Settings,
    pub word_list_filter: WordFilter,
    pub word_list: WordListState,
    pub word_edit: WordEditState,
    pub quiz: QuizState,
    pub settings_ui: SettingsUiState,
    loading: bool,
    startup_error: Option<String>,
    cursor_pos: Point,
}

// ── Boot ──────────────────────────────────────────────────────────────────────

pub fn boot() -> (App, Task<Message>) {
    let settings = Settings::load();
    let db_path = db::db_path();
    let path = db_path.clone();

    let task = Task::perform(
        async move {
            tokio::task::spawn_blocking(move || -> Result<DbTableMemory, String> {
                let sqlite = db::DbTableSQLite::open(&path)?;
                Ok(db::DbTableMemory::load_from(&sqlite))
            })
            .await
            .map_err(|e| format!("Thread error: {e}"))?
        },
        Message::DbLoaded,
    );

    let settings_ui = SettingsUiState::init(&settings);
    let app = App {
        tab: Tab::Quiz,
        db_path,
        memory: DbTableMemory::new(),
        settings,
        word_list_filter: WordFilter::default(),
        word_list: WordListState::default(),
        word_edit: WordEditState::default(),
        quiz: QuizState::default(),
        settings_ui,
        loading: true,
        startup_error: None,
        cursor_pos: Point::ORIGIN,
    };

    (app, task)
}

// ── Update ────────────────────────────────────────────────────────────────────

impl App {
    pub fn update(&mut self, message: Message) -> Task<Message> {
        match message {
            Message::DbLoaded(Ok(memory)) => {
                self.memory = memory;
                self.loading = false;
                self.quiz.load_next(self.memory.all_entries());
            }
            Message::DbLoaded(Err(e)) => {
                self.startup_error = Some(e);
                self.loading = false;
            }
            Message::TabChanged(tab) => {
                self.tab = tab;
                self.word_list.context_word_id = None;
            }

            // ── Word list ─────────────────────────────────────────────────────
            Message::WordListSort(field) => {
                if self.word_list_filter.sort == field {
                    self.word_list_filter.sort_desc = !self.word_list_filter.sort_desc;
                } else {
                    self.word_list_filter.sort = field;
                    self.word_list_filter.sort_desc = false;
                }
            }
            Message::WordListLanguage(lang) => {
                self.word_list_filter.language = if lang.is_empty() { None } else { Some(lang) };
            }
            Message::WordListSearchToggle => {
                self.word_list.search_visible = !self.word_list.search_visible;
                if !self.word_list.search_visible {
                    self.word_list_filter.text = None;
                }
            }
            Message::WordListSearchChanged(t) => {
                self.word_list_filter.text = if t.is_empty() { None } else { Some(t) };
            }
            Message::CursorMoved(pos) => {
                self.cursor_pos = pos;
            }
            Message::WordListCursorMoved(pos) => {
                self.word_list.local_cursor_pos = pos;
            }
            Message::WordListScrolled(viewport) => {
                self.word_list.scroll_offset = viewport.absolute_offset();
            }
            Message::WordListHover(id) => {
                self.word_list.hovered_row = id;
            }
            Message::TabKeyPressed { shift } => {
                return if shift {
                    iced::widget::operation::focus_previous()
                } else {
                    iced::widget::operation::focus_next()
                };
            }
            Message::WordListContextMenu(id) => {
                self.word_list.context_word_id = Some(id);
                // Use view-local cursor pos (relative to word_list area, not window top)
                // so the overlay padding matches the stack's coordinate origin.
                self.word_list.context_menu_pos = self.word_list.local_cursor_pos;
                self.word_list.more_menu_open = false;
            }
            Message::WordListContextMenuClose => {
                self.word_list.context_word_id = None;
                self.word_list.more_menu_open = false;
            }
            Message::WordListMoreMenu => {
                self.word_list.more_menu_open = !self.word_list.more_menu_open;
                self.word_list.context_word_id = None;
            }
            Message::WordListImport | Message::WordListExport => {
                self.word_list.more_menu_open = false;
                self.word_list.info_message = Some(self.t("words.not_implemented"));
            }
            Message::WordListInfoDismiss => {
                self.word_list.info_message = None;
            }
            Message::WordListNew => {
                self.word_list.context_word_id = None;
                let lang = &self.settings.last_word_language;
                self.word_edit = WordEditState::for_new(lang);
                self.word_edit.open = true;
            }
            Message::WordListEdit(id) => {
                self.word_list.context_word_id = None;
                if let Some(entry) = self.memory.get_word(id) {
                    self.word_edit = WordEditState::from_entry(&entry);
                }
            }
            Message::WordListDeleteAsk(id) => {
                self.word_list.context_word_id = None;
                self.word_list.confirm_delete_id = Some(id);
            }
            Message::WordListDeleteCancel => {
                self.word_list.confirm_delete_id = None;
            }
            Message::WordListDeleteConfirm => {
                if let Some(id) = self.word_list.confirm_delete_id.take() {
                    let path = self.db_path.clone();
                    return Task::perform(
                        async move {
                            tokio::task::spawn_blocking(move || -> Result<i64, String> {
                                let sqlite = db::DbTableSQLite::open(&path)?;
                                sqlite.delete_word(id).map_err(|e| e.to_string())?;
                                Ok(id)
                            })
                            .await
                            .map_err(|e| format!("Thread: {e}"))?
                        },
                        Message::WordDeleted,
                    );
                }
            }
            Message::WordListSyncNow => {
                return self.update(Message::SettingsSyncNow);
            }
            Message::WordDeleted(Ok(id)) => {
                self.memory.remove_entry(id);
            }
            Message::WordDeleted(Err(e)) => {
                eprintln!("Delete error: {e}");
            }

            // ── Word edit ─────────────────────────────────────────────────────
            Message::WordEditClose => {
                self.word_edit.open = false;
                return iced::widget::operation::scroll_to(
                    "word_list_body",
                    self.word_list.scroll_offset,
                );
            }
            Message::WordEditLanguage(lang) => {
                self.word_edit.language = lang;
                self.word_edit.repopulate_forms();
            }
            Message::WordEditWord(s) => self.word_edit.word = s,
            Message::WordEditReading(s) => self.word_edit.reading = s,
            Message::WordEditMeaning(s) => self.word_edit.primary_meaning = s,
            Message::WordEditPos(pos) => {
                self.word_edit.pos = pos;
                self.word_edit.repopulate_forms();
            }
            Message::WordEditAddMeaning => {
                self.word_edit.extra_meanings.push(String::new());
            }
            Message::WordEditRemoveMeaning(i) => {
                if i < self.word_edit.extra_meanings.len() {
                    self.word_edit.extra_meanings.remove(i);
                }
            }
            Message::WordEditChangeMeaning(i, s) => {
                if let Some(m) = self.word_edit.extra_meanings.get_mut(i) {
                    *m = s;
                }
            }
            Message::WordEditAddForm => {
                let default_label = match self.word_edit.language.as_str() {
                    "ja" => labels::JA_FORM_LABELS,
                    _ => labels::EN_FORM_LABELS,
                }
                .first()
                .copied()
                .unwrap_or("")
                .to_owned();
                self.word_edit.forms.push((default_label, String::new()));
            }
            Message::WordEditRemoveForm(i) => {
                if i < self.word_edit.forms.len() {
                    self.word_edit.forms.remove(i);
                }
            }
            Message::WordEditFormLabel(i, s) => {
                if let Some(f) = self.word_edit.forms.get_mut(i) {
                    f.0 = s;
                }
            }
            Message::WordEditFormValue(i, s) => {
                if let Some(f) = self.word_edit.forms.get_mut(i) {
                    f.1 = s;
                }
            }
            Message::WordEditAddSentence => {
                self.word_edit
                    .sentences
                    .push((String::new(), String::new()));
            }
            Message::WordEditRemoveSentence(i) => {
                if i < self.word_edit.sentences.len() {
                    self.word_edit.sentences.remove(i);
                }
            }
            Message::WordEditSentence(i, s) => {
                if let Some(sent) = self.word_edit.sentences.get_mut(i) {
                    sent.0 = s;
                }
            }
            Message::WordEditTranslation(i, s) => {
                if let Some(sent) = self.word_edit.sentences.get_mut(i) {
                    sent.1 = s;
                }
            }
            Message::WordEditSave => {
                if self.word_edit.word.trim().is_empty() {
                    self.word_edit.error = Some("Word is required.".into());
                    return Task::none();
                }
                if self.word_edit.primary_meaning.trim().is_empty() {
                    self.word_edit.error = Some("Primary meaning is required.".into());
                    return Task::none();
                }
                let path = self.db_path.clone();
                let editing_id = self.word_edit.editing_id;
                if let Some(id) = editing_id {
                    let upd = self.word_edit.to_update_word();
                    return Task::perform(
                        async move {
                            tokio::task::spawn_blocking(move || -> Result<WordEntry, String> {
                                let sqlite = db::DbTableSQLite::open(&path)?;
                                sqlite.update_word(id, &upd).map_err(|e| e.to_string())?;
                                sqlite
                                    .get_word(id)
                                    .ok_or("Word not found after update".into())
                            })
                            .await
                            .map_err(|e| format!("Thread: {e}"))?
                        },
                        Message::WordEditSaved,
                    );
                } else {
                    let nw = self.word_edit.to_new_word();
                    return Task::perform(
                        async move {
                            tokio::task::spawn_blocking(move || -> Result<WordEntry, String> {
                                let sqlite = db::DbTableSQLite::open(&path)?;
                                let id = sqlite.create_word(&nw).map_err(|e| e.to_string())?;
                                sqlite
                                    .get_word(id)
                                    .ok_or("Word not found after create".into())
                            })
                            .await
                            .map_err(|e| format!("Thread: {e}"))?
                        },
                        Message::WordEditSaved,
                    );
                }
            }
            Message::WordEditSaved(Ok(entry)) => {
                if self.word_edit.editing_id.is_some() {
                    self.memory.replace_entry(entry.clone());
                } else {
                    self.memory.insert_entry(entry.clone());
                }
                self.settings.last_word_language = entry.language.clone();
                let _ = self.settings.save();
                self.word_edit.open = false;
                return iced::widget::operation::scroll_to(
                    "word_list_body",
                    self.word_list.scroll_offset,
                );
            }
            Message::WordEditSaved(Err(e)) => {
                self.word_edit.error = Some(format!("Save failed: {e}"));
            }

            // ── Quiz ──────────────────────────────────────────────────────────
            Message::QuizLanguage(lang) => {
                self.quiz.language = if lang.is_empty() { None } else { Some(lang) };
                self.quiz.load_next(self.memory.all_entries());
            }
            Message::QuizEnterKey => {
                if self.quiz.submitted {
                    self.quiz.load_next(self.memory.all_entries());
                }
            }
            Message::QuizNextCard => {
                self.quiz.load_next(self.memory.all_entries());
            }
            Message::QuizSkip => {
                self.quiz.load_next(self.memory.all_entries());
            }
            Message::QuizTypingWord(s) => {
                self.quiz.typing_word = s;
            }
            Message::QuizTypingField(i, s) => {
                if let Some(f) = self.quiz.typing_fields.get_mut(i) {
                    f.1 = s;
                }
            }
            Message::QuizMcToggle(i) => {
                if let Some(v) = self.quiz.mc_selected.get_mut(i) {
                    *v = !*v;
                }
            }
            Message::QuizSubmit => {
                let q = match &self.quiz.current {
                    Some(q) => q.clone(),
                    None => return Task::none(),
                };
                let pool: Vec<WordEntry> = self.memory.all_entries().to_vec();

                let (correct, field_res) = match &q.mode {
                    QuizMode::Typing => {
                        let typed_fields = self.quiz.typing_fields.clone();
                        let typed_word = self.quiz.typing_word.clone();
                        engine::grade_typing(&q, &typed_word, &typed_fields, &pool)
                    }
                    QuizMode::MultipleChoice => {
                        let selected: Vec<String> = q
                            .options
                            .iter()
                            .enumerate()
                            .filter_map(|(i, opt)| {
                                if self.quiz.mc_selected.get(i).copied().unwrap_or(false) {
                                    Some(opt.clone())
                                } else {
                                    None
                                }
                            })
                            .collect();
                        let ok = engine::grade_choice(&q, &selected);
                        (ok, vec![])
                    }
                };

                match &q.mode {
                    QuizMode::Typing => {
                        self.quiz.typing_correct = Some(
                            pool.iter()
                                .any(|e| e.word.eq_ignore_ascii_case(&self.quiz.typing_word)),
                        );
                        self.quiz.field_results = field_res;
                    }
                    QuizMode::MultipleChoice => {
                        self.quiz.choice_correct = Some(correct);
                    }
                }
                self.quiz.submitted = true;

                let word_id = q.word_id;
                let practiced_at = crate::db::schema::now_epoch();
                self.memory
                    .apply_practice_update(word_id, correct, practiced_at);

                let path = self.db_path.clone();
                return Task::perform(
                    async move {
                        tokio::task::spawn_blocking(move || -> Result<(i64, bool, i64), String> {
                            let sqlite = db::DbTableSQLite::open(&path)?;
                            sqlite
                                .update_practice_stats(word_id, correct, practiced_at)
                                .map_err(|e| e.to_string())?;
                            Ok((word_id, correct, practiced_at))
                        })
                        .await
                        .map_err(|e| format!("Thread: {e}"))?
                    },
                    Message::QuizStatsUpdated,
                );
            }
            Message::QuizGiveUp => {
                let q = match &self.quiz.current {
                    Some(q) => q.clone(),
                    None => return Task::none(),
                };
                self.quiz.gave_up = true;
                self.quiz.submitted = true;

                let word_id = q.word_id;
                let practiced_at = crate::db::schema::now_epoch();
                self.memory
                    .apply_practice_update(word_id, false, practiced_at);

                let path = self.db_path.clone();
                return Task::perform(
                    async move {
                        tokio::task::spawn_blocking(move || -> Result<(i64, bool, i64), String> {
                            let sqlite = db::DbTableSQLite::open(&path)?;
                            sqlite
                                .update_practice_stats(word_id, false, practiced_at)
                                .map_err(|e| e.to_string())?;
                            Ok((word_id, false, practiced_at))
                        })
                        .await
                        .map_err(|e| format!("Thread: {e}"))?
                    },
                    Message::QuizStatsUpdated,
                );
            }
            Message::QuizStatsUpdated(Err(e)) => {
                eprintln!("Quiz stats write error: {e}");
            }
            Message::QuizStatsUpdated(Ok(_)) => {}

            // ── Settings ──────────────────────────────────────────────────────
            Message::SettingsUiLanguage(lang) => {
                self.settings.ui_language = lang;
                let _ = self.settings.save();
            }
            Message::SettingsTheme(t) => {
                self.settings.theme = t;
                let _ = self.settings.save();
            }
            Message::SettingsSyncMethod(m) => {
                self.settings.sync_method = m;
                let _ = self.settings.save();
            }
            Message::SettingsFtpHost(v) => {
                self.settings.ftp_host = v;
                let _ = self.settings.save();
            }
            Message::SettingsFtpPort(v) => {
                self.settings_ui.ftp_port_str = v.clone();
                if let Ok(p) = v.parse::<u16>() {
                    self.settings.ftp_port = p;
                    let _ = self.settings.save();
                }
            }
            Message::SettingsFtpUser(v) => {
                self.settings.ftp_username = v;
                let _ = self.settings.save();
            }
            Message::SettingsFtpPassword(v) => {
                self.settings_ui.ftp_password = v;
            }
            Message::SettingsFtpDir(v) => {
                self.settings.ftp_directory = v;
                let _ = self.settings.save();
            }
            Message::SettingsFtpTls(v) => {
                self.settings.ftp_tls = v;
                let _ = self.settings.save();
            }
            Message::SettingsFtpSave => {
                let password = self.settings_ui.ftp_password.clone();
                if !password.is_empty() {
                    if let Err(e) = crate::config::keychain::store(
                        crate::config::keychain::FTP_PASSWORD,
                        &password,
                    ) {
                        eprintln!("Keychain write error: {e}");
                    }
                }
            }
            Message::SettingsSftpHost(v) => {
                self.settings.sftp_host = v;
                let _ = self.settings.save();
            }
            Message::SettingsSftpPort(v) => {
                self.settings_ui.sftp_port_str = v.clone();
                if let Ok(p) = v.parse::<u16>() {
                    self.settings.sftp_port = p;
                    let _ = self.settings.save();
                }
            }
            Message::SettingsSftpUser(v) => {
                self.settings.sftp_username = v;
                let _ = self.settings.save();
            }
            Message::SettingsSftpPassword(v) => {
                self.settings_ui.sftp_password = v;
            }
            Message::SettingsSftpDir(v) => {
                self.settings.sftp_directory = v;
                let _ = self.settings.save();
            }
            Message::SettingsSftpSave => {
                let password = self.settings_ui.sftp_password.clone();
                if !password.is_empty() {
                    if let Err(e) = crate::config::keychain::store(
                        crate::config::keychain::SFTP_PASSWORD,
                        &password,
                    ) {
                        eprintln!("Keychain write error: {e}");
                    }
                }
            }
            Message::SettingsDriveFolder(v) => {
                self.settings.drive_folder = v;
                let _ = self.settings.save();
            }
            Message::SettingsDriveLogin => {
                return Task::perform(
                    async { DriveClient::prepare_login().await.map(std::sync::Arc::new) },
                    Message::SettingsDriveAuthReady,
                );
            }
            Message::SettingsDriveAuthReady(Ok(pending)) => {
                self.settings_ui.drive_auth_url = Some(pending.auth_url.clone());
                self.settings_ui.drive_auth_pending = Some(pending.clone());
                return Task::perform(
                    DriveClient::complete_login_and_email(pending),
                    Message::SettingsDriveLoginDone,
                );
            }
            Message::SettingsDriveAuthReady(Err(e)) => {
                eprintln!("Drive prepare failed: {e}");
            }
            Message::SettingsDriveAuthCopyUrl => {
                if let Some(url) = self.settings_ui.drive_auth_url.clone() {
                    return iced::clipboard::write(url);
                }
            }
            Message::SettingsDriveAuthCancel => {
                self.settings_ui.drive_auth_url = None;
                self.settings_ui.drive_auth_pending = None;
            }
            Message::SettingsDriveLogout => {
                self.settings_ui.drive_logged_in = false;
                self.settings_ui.drive_email = None;
                return Task::perform(async { DriveClient::logout().await }, |_| {
                    Message::SettingsDriveLoginDone(Err("logged out".into()))
                });
            }
            Message::SettingsDriveLoginDone(Ok(())) => {
                self.settings_ui.drive_auth_url = None;
                self.settings_ui.drive_auth_pending = None;
                self.settings_ui.drive_logged_in = true;
            }
            Message::SettingsDriveLoginDone(Err(e)) => {
                eprintln!("Drive login error: {e}");
                self.settings_ui.drive_auth_url = None;
                self.settings_ui.drive_auth_pending = None;
                self.settings_ui.drive_logged_in = false;
            }
            Message::SettingsSyncNow => {
                self.settings_ui.sync_in_progress = true;
                self.settings_ui.sync_message =
                    Some(sync_step_msg(self.t("settings.sync_checking"), 0, 2));
                let path = self.db_path.clone();
                let settings_clone = self.settings.clone();
                let method = self.settings.sync_method.clone();
                return Task::perform(
                    sync_phase1(method, path, settings_clone),
                    Message::SettingsSyncPhase2,
                );
            }
            Message::SettingsSyncPhase2(Ok(SyncNextStep::Done(result))) => {
                return self.update(Message::SettingsSyncDone(Ok(result)));
            }
            Message::SettingsSyncPhase2(Ok(SyncNextStep::NeedUpload { method, path, settings })) => {
                self.settings_ui.sync_message =
                    Some(sync_step_msg(self.t("settings.sync_uploading"), 1, 2));
                return Task::perform(
                    sync_phase2_upload(method, path, settings),
                    Message::SettingsSyncDone,
                );
            }
            Message::SettingsSyncPhase2(Ok(SyncNextStep::NeedDownload { method, path, settings })) => {
                self.settings_ui.sync_message =
                    Some(sync_step_msg(self.t("settings.sync_downloading"), 1, 2));
                return Task::perform(
                    sync_phase2_download(method, path, settings),
                    Message::SettingsSyncDone,
                );
            }
            Message::SettingsSyncPhase2(Err(e)) => {
                self.settings_ui.sync_in_progress = false;
                self.settings_ui.sync_message = Some(format!("Sync error: {e}"));
            }
            Message::SettingsSyncDone(Ok(result)) => {
                self.settings_ui.sync_in_progress = false;
                let msg = match &result {
                    SyncResult::NoOp => "Already up to date.".to_string(),
                    SyncResult::Uploaded => "Uploaded to remote.".to_string(),
                    SyncResult::Downloaded => "Downloaded from remote.".to_string(),
                    SyncResult::Error(e) => format!("Sync error: {e}"),
                };
                self.settings_ui.sync_message = Some(msg);
                if matches!(result, SyncResult::Downloaded) {
                    let path = self.db_path.clone();
                    return Task::perform(
                        async move {
                            tokio::task::spawn_blocking(move || -> Result<DbTableMemory, String> {
                                let sqlite = db::DbTableSQLite::open(&path)?;
                                Ok(db::DbTableMemory::load_from(&sqlite))
                            })
                            .await
                            .map_err(|e| format!("Thread error: {e}"))?
                        },
                        Message::DbLoaded,
                    );
                }
            }
            Message::SettingsSyncDone(Err(e)) => {
                self.settings_ui.sync_in_progress = false;
                self.settings_ui.sync_message = Some(format!("Sync error: {e}"));
            }
            Message::SettingsClearStatsAsk => {
                self.settings_ui.clear_stats_confirm = true;
            }
            Message::SettingsClearStatsCancel => {
                self.settings_ui.clear_stats_confirm = false;
            }
            Message::SettingsClearStatsConfirm => {
                self.settings_ui.clear_stats_confirm = false;
                let path = self.db_path.clone();
                return Task::perform(
                    async move {
                        tokio::task::spawn_blocking(move || -> Result<(), String> {
                            let sqlite = db::DbTableSQLite::open(&path)?;
                            sqlite.clear_practice_stats().map_err(|e| e.to_string())
                        })
                        .await
                        .map_err(|e| format!("Thread: {e}"))?
                    },
                    Message::SettingsClearStatsDone,
                );
            }
            Message::SettingsClearStatsDone(Ok(())) => {
                self.memory.apply_clear_stats();
            }
            Message::SettingsClearStatsDone(Err(e)) => {
                eprintln!("Clear stats error: {e}");
            }
        }
        Task::none()
    }

    // ── View ──────────────────────────────────────────────────────────────────

    pub fn view(&self) -> Element<'_, Message> {
        if self.loading {
            return container(text(self.t("loading")))
                .center(Length::Fill)
                .into();
        }

        if let Some(err) = &self.startup_error {
            return container(text(format!("{}{err}", self.t("startup_error"))))
                .center(Length::Fill)
                .into();
        }

        let tab_bar = row![
            tab_btn(self.t("tab.quiz"), self.tab == Tab::Quiz, Tab::Quiz),
            tab_btn(
                self.t("tab.words"),
                self.tab == Tab::WordList,
                Tab::WordList
            ),
            tab_btn(
                self.t("tab.settings"),
                self.tab == Tab::Settings,
                Tab::Settings
            ),
        ]
        .spacing(4)
        .padding(8);

        let content: Element<Message> = match self.tab {
            Tab::Quiz => quiz::view(self),
            Tab::WordList => word_list::view(self),
            Tab::Settings => settings::view(self),
        };

        let base: Element<Message> = column![tab_bar, content].into();

        if self.word_edit.open {
            word_edit::view(self, base)
        } else {
            base
        }
    }
}

// ── Theme & locale helpers ────────────────────────────────────────────────────

impl App {
    /// Map Settings.theme → iced::Theme, including custom purple+teal palette.
    pub fn subscription(&self) -> iced::Subscription<Message> {
        iced::event::listen_with(|event, status, _id| match event {
            iced::Event::Mouse(iced::mouse::Event::CursorMoved { position }) => {
                Some(Message::CursorMoved(position))
            }
            iced::Event::Keyboard(iced::keyboard::Event::KeyPressed {
                key: iced::keyboard::Key::Named(iced::keyboard::key::Named::Tab),
                modifiers,
                ..
            }) => Some(Message::TabKeyPressed { shift: modifiers.shift() }),
            iced::Event::Keyboard(iced::keyboard::Event::KeyPressed {
                key: iced::keyboard::Key::Named(iced::keyboard::key::Named::Enter),
                ..
            }) if status != iced::event::Status::Captured => Some(Message::QuizEnterKey),
            _ => None,
        })
    }

    pub fn iced_theme(&self) -> iced::Theme {
        use crate::config::Theme as T;
        match self.settings.theme {
            T::Light => iced_theme_light(),
            T::Dark => iced_theme_dark(),
            T::Auto => {
                // Detect system preference via dark mode heuristic:
                // iced doesn't expose system theme yet, so default to light.
                iced_theme_light()
            }
        }
    }

    pub fn t(&self, key: &'static str) -> &'static str {
        crate::locale::t(&self.settings.ui_language, key)
    }

    /// Translate a form/pos label that may be a canonical key or user-entered text.
    /// Returns an owned String so unknown labels fall back to themselves.
    pub fn t_label(&self, label: &str) -> String {
        let key = crate::db::labels::form_locale_key(label);
        if key.is_empty() {
            label.to_owned()
        } else {
            crate::locale::t(&self.settings.ui_language, key).to_owned()
        }
    }
}

fn iced_theme_light() -> iced::Theme {
    iced::Theme::custom(
        std::borrow::Cow::Borrowed("EasyVocaBook Light"),
        iced::theme::Palette {
            background: iced::Color::from_rgb8(0xF9, 0xF7, 0xFF),
            text: iced::Color::from_rgb8(0x1A, 0x11, 0x2E),
            primary: iced::Color::from_rgb8(0x7C, 0x3A, 0xED), // purple-600
            success: iced::Color::from_rgb8(0x14, 0xB8, 0xA6), // teal-500
            warning: iced::Color::from_rgb8(0xF5, 0x9E, 0x0B),
            danger: iced::Color::from_rgb8(0xDC, 0x26, 0x26),
        },
    )
}

fn iced_theme_dark() -> iced::Theme {
    iced::Theme::custom(
        std::borrow::Cow::Borrowed("EasyVocaBook Dark"),
        iced::theme::Palette {
            background: iced::Color::from_rgb8(0x12, 0x0E, 0x1E),
            text: iced::Color::from_rgb8(0xED, 0xE9, 0xFE),
            primary: iced::Color::from_rgb8(0xA7, 0x8B, 0xFA), // violet-400
            success: iced::Color::from_rgb8(0x2D, 0xD4, 0xBF), // teal-400
            warning: iced::Color::from_rgb8(0xFB, 0xBF, 0x24),
            danger: iced::Color::from_rgb8(0xF8, 0x71, 0x71),
        },
    )
}

fn tab_btn(label: &'static str, active: bool, target: Tab) -> Element<'static, Message> {
    if active {
        button(text(label))
            .style(button::primary)
            .on_press(Message::TabChanged(target))
            .into()
    } else {
        button(text(label))
            .style(button::secondary)
            .on_press(Message::TabChanged(target))
            .into()
    }
}

// ── Sync helpers ─────────────────────────────────────────────────────────────

fn sync_step_msg(step_text: &str, done: usize, total: usize) -> String {
    let pct = done * 100 / total;
    format!("{step_text}  {pct}%")
}

// ── Async helpers ─────────────────────────────────────────────────────────────

/// Phase 1: read timestamps + decide what to do. Latest last_modified wins.
async fn sync_phase1(
    method: SyncMethod,
    path: PathBuf,
    settings: Settings,
) -> Result<SyncNextStep, String> {
    let local_lm = read_local_last_modified_async(path.clone()).await?;
    let remote_lm = match &method {
        SyncMethod::Ftp => FtpClient::from_settings(&settings).remote_last_modified().await?,
        SyncMethod::Sftp => SftpClient::from_settings(&settings).remote_last_modified().await?,
        SyncMethod::GoogleDrive => {
            DriveClient::new(&settings.drive_folder).remote_last_modified().await?
        }
        SyncMethod::Disabled => return Err("Sync method is disabled.".into()),
    };

    Ok(match decide(local_lm, remote_lm) {
        crate::network::sync::SyncDecision::NoOp => SyncNextStep::Done(SyncResult::NoOp),
        crate::network::sync::SyncDecision::Upload => SyncNextStep::NeedUpload { method, path, settings },
        crate::network::sync::SyncDecision::Download => SyncNextStep::NeedDownload { method, path, settings },
    })
}

/// Phase 2a: upload the local DB.
async fn sync_phase2_upload(
    method: SyncMethod,
    path: PathBuf,
    settings: Settings,
) -> Result<SyncResult, String> {
    match &method {
        SyncMethod::Ftp => FtpClient::from_settings(&settings).upload(&path).await,
        SyncMethod::Sftp => SftpClient::from_settings(&settings).upload(&path).await,
        SyncMethod::GoogleDrive => DriveClient::new(&settings.drive_folder).upload(&path).await,
        SyncMethod::Disabled => return Err("Sync method is disabled.".into()),
    }?;
    Ok(SyncResult::Uploaded)
}

/// Phase 2b: download remote DB, validate schema, replace local.
async fn sync_phase2_download(
    method: SyncMethod,
    path: PathBuf,
    settings: Settings,
) -> Result<SyncResult, String> {
    match &method {
        SyncMethod::Ftp => download_and_reload(&FtpClient::from_settings(&settings), &path).await,
        SyncMethod::Sftp => download_and_reload(&SftpClient::from_settings(&settings), &path).await,
        SyncMethod::GoogleDrive => {
            download_and_reload(&DriveClient::new(&settings.drive_folder), &path).await
        }
        SyncMethod::Disabled => return Err("Sync method is disabled.".into()),
    }?;
    Ok(SyncResult::Downloaded)
}

