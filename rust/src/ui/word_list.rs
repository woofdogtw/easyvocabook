use std::collections::HashSet;

use iced::alignment;
use iced::widget::{
    Space, button, column, container, mouse_area, pick_list, row, scrollable, stack, text,
    text_input,
};
use iced::{Element, Length};

use super::{App, Message};
use crate::db::{DbTableBase, SortField, WordEntry, WordFilter, labels};

// ── Public entry-point ────────────────────────────────────────────────────────

pub fn view(app: &App) -> Element<'_, Message> {
    let t = |k| app.t(k);
    let filter = &app.word_list_filter;
    let words = app.memory.list_words(filter);
    let has_filter = filter.text.is_some() || filter.language.is_some();

    let all_label = t("words.lang_all");
    let all_langs: HashSet<String> = app
        .memory
        .all_entries()
        .iter()
        .map(|e| e.language.clone())
        .collect();
    let mut sorted_codes: Vec<String> = all_langs.into_iter().collect();
    sorted_codes.sort();

    // Build parallel (code, display) lists so the picker shows translated names.
    let lang_displays: Vec<String> = {
        let mut v = vec![all_label.to_owned()];
        v.extend(
            sorted_codes
                .iter()
                .map(|c| app.t(labels::lang_locale_key(c)).to_owned()),
        );
        v
    };
    let lang_codes: Vec<String> = {
        let mut v = vec![String::new()]; // "" == "All"
        v.extend(sorted_codes.iter().cloned());
        v
    };
    let selected_lang_display = Some(
        filter
            .language
            .as_ref()
            .map(|c| app.t(labels::lang_locale_key(c)).to_owned())
            .unwrap_or_else(|| all_label.to_owned()),
    );
    let lang_codes_clone = lang_codes.clone();
    let lang_displays_clone = lang_displays.clone();

    // ── Filter bar ────────────────────────────────────────────────────────────
    let lang_picker = pick_list(lang_displays, selected_lang_display, move |s: String| {
        let code = lang_codes_clone
            .iter()
            .zip(lang_displays_clone.iter())
            .find(|(_, d)| d.as_str() == s)
            .map(|(c, _)| c.clone())
            .unwrap_or_default();
        Message::WordListLanguage(code)
    });

    let search_bar: Element<Message> = if app.word_list.search_visible {
        let current_text = filter.text.clone().unwrap_or_default();
        text_input(t("words.search_hint"), &current_text)
            .on_input(Message::WordListSearchChanged)
            .width(Length::Fixed(220.0))
            .into()
    } else {
        Space::new().into()
    };

    // ── Action bar ────────────────────────────────────────────────────────────
    let action_bar = row![
        button(text(t("words.new"))).on_press(Message::WordListNew),
        button(text(t("words.search"))).on_press(Message::WordListSearchToggle),
        button(text(t("words.sync"))).on_press(Message::WordListSyncNow),
        button(text(t("words.more"))).on_press(Message::WordListMoreMenu),
    ]
    .spacing(4);

    let filter_bar = row![lang_picker, search_bar].spacing(8).padding(8);

    // ── Table header ──────────────────────────────────────────────────────────
    let header = row![
        sort_header(t("words.col_word"), SortField::Word, filter),
        sort_header(t("words.col_reading"), SortField::Reading, filter),
        sort_header(t("words.col_meaning"), SortField::Meaning, filter),
        sort_header(t("words.col_rate"), SortField::CorrectRate, filter),
    ]
    .spacing(0);

    // ── Table body ────────────────────────────────────────────────────────────
    let body: Element<Message> = if words.is_empty() {
        let msg = if has_filter {
            t("words.empty_filter")
        } else {
            t("words.empty")
        };
        container(text(msg))
            .center(Length::Fill)
            .height(Length::Fill)
            .into()
    } else {
        let rows = words
            .into_iter()
            .fold(column![].spacing(0), |col, entry| col.push(word_row(entry)));
        scrollable(rows).height(Length::Fill).into()
    };

    // ── Assemble ──────────────────────────────────────────────────────────────
    let main_content: Element<Message> = column![filter_bar, action_bar, header, body]
        .spacing(0)
        .into();

    if let Some(ctx_id) = app.word_list.context_word_id {
        context_menu_overlay(main_content, ctx_id, app)
    } else if let Some(del_id) = app.word_list.confirm_delete_id {
        delete_confirm_overlay(main_content, del_id, app)
    } else if let Some(msg) = app.word_list.info_message {
        info_overlay(main_content, msg, app)
    } else if app.word_list.more_menu_open {
        more_menu_overlay(main_content, app)
    } else {
        main_content
    }
}

// ── Sort column header ────────────────────────────────────────────────────────

fn sort_header(
    label: &'static str,
    field: SortField,
    filter: &WordFilter,
) -> Element<'static, Message> {
    let indicator = if filter.sort == field {
        if filter.sort_desc { " ▼" } else { " ▲" }
    } else {
        ""
    };
    button(text(format!("{label}{indicator}")))
        .style(button::secondary)
        .on_press(Message::WordListSort(field))
        .width(if field == SortField::CorrectRate {
            Length::Fixed(130.0)
        } else {
            Length::Fill
        })
        .into()
}

// ── Word row ──────────────────────────────────────────────────────────────────

fn word_row(entry: WordEntry) -> Element<'static, Message> {
    let id = entry.id;
    let rate = if entry.practice_count == 0 {
        "—".to_owned()
    } else {
        format!(
            "{:.0}%",
            100.0 * entry.correct_count as f64 / entry.practice_count as f64
        )
    };

    let row_content = row![
        container(text(entry.word))
            .padding([4, 8])
            .width(Length::Fill),
        container(text(entry.reading.unwrap_or_default()))
            .padding([4, 8])
            .width(Length::Fill),
        container(text(entry.meaning))
            .padding([4, 8])
            .width(Length::Fill),
        container(text(rate))
            .padding([4, 8])
            .width(Length::Fixed(130.0)),
    ]
    .spacing(0);

    mouse_area(row_content)
        .on_right_press(Message::WordListContextMenu(id))
        .into()
}

// ── Info dialog overlay ───────────────────────────────────────────────────────

fn info_overlay<'a>(
    main_content: Element<'a, Message>,
    msg: &'a str,
    app: &'a App,
) -> Element<'a, Message> {
    let dialog = column![
        text(msg),
        button(text(app.t("words.ok"))).on_press(Message::WordListInfoDismiss),
    ]
    .spacing(12)
    .padding(20);

    let dialog_box = container(dialog)
        .width(Length::Fixed(300.0))
        .style(|theme: &iced::Theme| {
            let palette = theme.palette();
            iced::widget::container::Style {
                background: Some(palette.background.into()),
                border: iced::Border {
                    color: palette.primary,
                    width: 1.0,
                    radius: 8.0.into(),
                },
                ..Default::default()
            }
        });

    let backdrop = mouse_area(
        container(Space::new().width(Length::Fill).height(Length::Fill))
            .width(Length::Fill)
            .height(Length::Fill),
    )
    .on_press(Message::WordListInfoDismiss);

    stack![
        main_content,
        backdrop,
        container(dialog_box).center(Length::Fill)
    ]
    .into()
}

// ── More menu overlay (action bar "···") ─────────────────────────────────────

fn more_menu_overlay<'a>(main_content: Element<'a, Message>, app: &'a App) -> Element<'a, Message> {
    let t = |k| app.t(k);

    let menu = column![
        button(text(t("words.import")))
            .width(Length::Fill)
            .on_press(Message::WordListImport),
        button(text(t("words.export")))
            .width(Length::Fill)
            .on_press(Message::WordListExport),
    ]
    .spacing(2)
    .padding(4);

    let menu_box = container(menu)
        .padding(4)
        .width(Length::Fixed(160.0))
        .style(|theme: &iced::Theme| {
            let palette = theme.palette();
            iced::widget::container::Style {
                background: Some(palette.background.into()),
                border: iced::Border {
                    color: palette.primary,
                    width: 1.0,
                    radius: 4.0.into(),
                },
                ..Default::default()
            }
        });

    let backdrop = mouse_area(
        container(Space::new().width(Length::Fill).height(Length::Fill))
            .width(Length::Fill)
            .height(Length::Fill),
    )
    .on_press(Message::WordListContextMenuClose);

    // Position below the action bar, flush right
    let menu_positioned = container(menu_box)
        .align_x(alignment::Horizontal::Right)
        .align_y(alignment::Vertical::Top)
        .width(Length::Fill)
        .height(Length::Fill)
        .padding(iced::Padding {
            top: 88.0,
            right: 8.0,
            bottom: 0.0,
            left: 0.0,
        });

    stack![main_content, backdrop, menu_positioned].into()
}

// ── Context menu overlay ──────────────────────────────────────────────────────

fn context_menu_overlay<'a>(
    main_content: Element<'a, Message>,
    ctx_id: i64,
    app: &'a App,
) -> Element<'a, Message> {
    let t = |k| app.t(k);

    let menu = column![
        button(text(t("words.menu_edit")))
            .width(Length::Fill)
            .on_press(Message::WordListEdit(ctx_id)),
        button(text(t("words.menu_delete")))
            .width(Length::Fill)
            .on_press(Message::WordListDeleteAsk(ctx_id)),
        button(text(t("words.menu_info")))
            .width(Length::Fill)
            .on_press(Message::WordListContextMenuClose),
        button(text(t("words.menu_homophone")))
            .width(Length::Fill)
            .on_press(Message::WordListContextMenuClose),
    ]
    .spacing(2)
    .padding(4);

    let menu_box = container(menu)
        .padding(4)
        .width(Length::Fixed(160.0))
        .style(|theme: &iced::Theme| {
            let palette = theme.palette();
            iced::widget::container::Style {
                background: Some(palette.background.into()),
                border: iced::Border {
                    color: palette.primary,
                    width: 1.0,
                    radius: 4.0.into(),
                },
                ..Default::default()
            }
        });

    let backdrop = mouse_area(
        container(Space::new().width(Length::Fill).height(Length::Fill))
            .width(Length::Fill)
            .height(Length::Fill),
    )
    .on_press(Message::WordListContextMenuClose);

    let pos = app.word_list.context_menu_pos;
    let menu_positioned = container(menu_box)
        .align_x(alignment::Horizontal::Left)
        .align_y(alignment::Vertical::Top)
        .width(Length::Fill)
        .height(Length::Fill)
        .padding(iced::Padding {
            top: pos.y,
            left: pos.x,
            right: 0.0,
            bottom: 0.0,
        });

    stack![main_content, backdrop, menu_positioned].into()
}

// ── Delete confirmation overlay ───────────────────────────────────────────────

fn delete_confirm_overlay<'a>(
    main_content: Element<'a, Message>,
    del_id: i64,
    app: &'a App,
) -> Element<'a, Message> {
    let t = |k| app.t(k);

    let word_name = app
        .memory
        .get_word(del_id)
        .map(|e| e.word.clone())
        .unwrap_or_else(|| format!("#{del_id}"));

    let dialog = column![
        text(format!("\"{}\"?", word_name)),
        text(t("words.delete_confirm")),
        row![
            button(text(t("words.delete_button")))
                .style(button::danger)
                .on_press(Message::WordListDeleteConfirm),
            button(text(t("words.cancel"))).on_press(Message::WordListDeleteCancel),
        ]
        .spacing(8),
    ]
    .spacing(12)
    .padding(20);

    let dialog_box = container(dialog)
        .width(Length::Fixed(320.0))
        .style(|theme: &iced::Theme| {
            let palette = theme.palette();
            iced::widget::container::Style {
                background: Some(palette.background.into()),
                border: iced::Border {
                    color: palette.primary,
                    width: 1.0,
                    radius: 8.0.into(),
                },
                ..Default::default()
            }
        });

    let backdrop = mouse_area(
        container(Space::new().width(Length::Fill).height(Length::Fill))
            .width(Length::Fill)
            .height(Length::Fill),
    )
    .on_press(Message::WordListDeleteCancel);

    stack![
        main_content,
        backdrop,
        container(dialog_box).center(Length::Fill)
    ]
    .into()
}
