use iced::widget::{
    Space, button, column, container, mouse_area, pick_list, row, scrollable, stack, text,
    text_input,
};
use iced::{Element, Length};

use super::{App, Message};
use crate::db::labels;

// ── Modal scaffold ────────────────────────────────────────────────────────────

pub fn view<'a>(app: &'a App, background: Element<'a, Message>) -> Element<'a, Message> {
    let dialog = dialog_content(app);

    let backdrop = mouse_area(
        container(Space::new().width(Length::Fill).height(Length::Fill))
            .width(Length::Fill)
            .height(Length::Fill),
    )
    .on_press(Message::WordEditClose);

    let dialog_centered = container(dialog)
        .center(Length::Fill)
        .style(|_: &iced::Theme| iced::widget::container::Style::default());

    stack![background, backdrop, dialog_centered].into()
}

// ── Dialog content ────────────────────────────────────────────────────────────

fn dialog_content(app: &App) -> Element<'_, Message> {
    let t = |k| app.t(k);
    let state = &app.word_edit;
    let is_edit = state.editing_id.is_some();

    // ── Fixed fields ──────────────────────────────────────────────────────────

    // Language picker: display translated names, send canonical codes.
    const LANG_CODES: &[&str] = &["en", "ja"];
    let lang_displays: Vec<String> = LANG_CODES
        .iter()
        .map(|&c| app.t(labels::lang_locale_key(c)).to_owned())
        .collect();
    let selected_lang_display = Some(app.t(labels::lang_locale_key(&state.language)).to_owned());
    let lang_displays_clone = lang_displays.clone();
    let lang_picker = pick_list(lang_displays, selected_lang_display, move |s: String| {
        let code = LANG_CODES
            .iter()
            .zip(lang_displays_clone.iter())
            .find(|(_, d)| d.as_str() == s)
            .map(|(&c, _)| c.to_owned())
            .unwrap_or(s);
        Message::WordEditLanguage(code)
    });

    // POS picker: display translated names, send canonical keys.
    let pos_none = t("edit.pos_none");
    let pos_canonical: &[&str] = match state.language.as_str() {
        "ja" => labels::JA_POS,
        _ => labels::EN_POS,
    };
    let pos_displays: Vec<String> = pos_canonical
        .iter()
        .map(|&k| app.t(labels::pos_locale_key(k)).to_owned())
        .collect();
    let pos_options: Vec<String> = {
        let mut v = vec![pos_none.to_owned()];
        v.extend(pos_displays.iter().cloned());
        v
    };
    let selected_pos = Some(if state.pos.is_empty() {
        pos_none.to_owned()
    } else {
        app.t(labels::pos_locale_key(&state.pos)).to_owned()
    });
    let pos_canonical_owned: Vec<&'static str> = pos_canonical.to_vec();
    let pos_displays_clone = pos_displays.clone();
    let pos_picker = pick_list(pos_options, selected_pos, move |s: String| {
        if s.as_str() == pos_none {
            Message::WordEditPos(String::new())
        } else {
            let canonical = pos_canonical_owned
                .iter()
                .zip(pos_displays_clone.iter())
                .find(|(_, d)| d.as_str() == s)
                .map(|(&k, _)| k.to_owned())
                .unwrap_or(s);
            Message::WordEditPos(canonical)
        }
    });

    let fixed_fields = column![
        row![
            text(t("edit.language")).width(Length::Fixed(110.0)),
            lang_picker,
        ]
        .spacing(8)
        .align_y(iced::alignment::Vertical::Center),
        row![
            text(t("edit.word")).width(Length::Fixed(110.0)),
            text_input(t("edit.hint_required"), &state.word)
                .on_input(Message::WordEditWord)
                .width(Length::Fill),
        ]
        .spacing(8),
        row![
            text(t("edit.reading")).width(Length::Fixed(110.0)),
            text_input(t("edit.hint_optional"), &state.reading)
                .on_input(Message::WordEditReading)
                .width(Length::Fill),
        ]
        .spacing(8),
        row![
            text(t("edit.meaning")).width(Length::Fixed(110.0)),
            text_input(t("edit.hint_required"), &state.primary_meaning)
                .on_input(Message::WordEditMeaning)
                .width(Length::Fill),
        ]
        .spacing(8),
        row![text(t("edit.pos")).width(Length::Fixed(110.0)), pos_picker,].spacing(8),
    ]
    .spacing(8);

    // ── Additional meanings ───────────────────────────────────────────────────

    let meanings_section =
        {
            let rows = state.extra_meanings.iter().enumerate().fold(
                column![].spacing(4),
                |col, (i, m)| {
                    col.push(
                        row![
                            text_input(t("edit.hint_optional"), m)
                                .on_input(move |s| Message::WordEditChangeMeaning(i, s))
                                .width(Length::Fill),
                            button(text("✕"))
                                .style(button::danger)
                                .on_press(Message::WordEditRemoveMeaning(i)),
                        ]
                        .spacing(4),
                    )
                },
            );

            column![
                text(t("edit.meanings")),
                rows,
                button(text(t("edit.add_meaning"))).on_press(Message::WordEditAddMeaning),
            ]
            .spacing(6)
        };

    // ── Word forms ────────────────────────────────────────────────────────────

    let forms_section = {
        let rows = state.forms.iter().enumerate().fold(
            column![].spacing(4),
            |col, (i, (label, value))| {
                col.push(
                    row![
                        text_input("", label)
                            .on_input(move |s| Message::WordEditFormLabel(i, s))
                            .width(Length::Fixed(150.0)),
                        text_input("", value)
                            .on_input(move |s| Message::WordEditFormValue(i, s))
                            .width(Length::Fill),
                        button(text("✕"))
                            .style(button::danger)
                            .on_press(Message::WordEditRemoveForm(i)),
                    ]
                    .spacing(4),
                )
            },
        );

        column![
            text(t("edit.forms")),
            rows,
            button(text(t("edit.add_form"))).on_press(Message::WordEditAddForm),
        ]
        .spacing(6)
    };

    // ── Sentences ─────────────────────────────────────────────────────────────

    let sentences_section = {
        let rows = state.sentences.iter().enumerate().fold(
            column![].spacing(6),
            |col, (i, (sentence, translation))| {
                col.push(
                    column![
                        row![
                            text_input("", sentence)
                                .on_input(move |s| Message::WordEditSentence(i, s))
                                .width(Length::Fill),
                            button(text("✕"))
                                .style(button::danger)
                                .on_press(Message::WordEditRemoveSentence(i)),
                        ]
                        .spacing(4),
                        text_input("", translation)
                            .on_input(move |s| Message::WordEditTranslation(i, s))
                            .width(Length::Fill),
                    ]
                    .spacing(2),
                )
            },
        );

        column![
            text(t("edit.sentences")),
            rows,
            button(text(t("edit.add_sentence"))).on_press(Message::WordEditAddSentence),
        ]
        .spacing(6)
    };

    // ── Error banner ──────────────────────────────────────────────────────────

    let error_banner: Element<Message> = if let Some(err) = &state.error {
        text(err.as_str()).into()
    } else {
        Space::new().into()
    };

    // ── Footer ────────────────────────────────────────────────────────────────

    let title_str = if is_edit {
        t("edit.edit_word")
    } else {
        t("edit.new_word")
    };
    let footer = row![
        button(text(t("edit.cancel"))).on_press(Message::WordEditClose),
        Space::new().width(Length::Fill),
        button(text(t("edit.save")))
            .style(button::primary)
            .on_press(Message::WordEditSave),
    ]
    .spacing(8);

    let body = scrollable(
        column![
            fixed_fields,
            meanings_section,
            forms_section,
            sentences_section,
            error_banner
        ]
        .spacing(16)
        .padding(16),
    )
    .height(Length::Fill);

    let dialog_inner = column![text(title_str), body, footer]
        .spacing(8)
        .padding(16);

    container(dialog_inner)
        .width(Length::Fixed(520.0))
        .height(Length::Fixed(600.0))
        .style(|theme: &iced::Theme| {
            let palette = theme.palette();
            iced::widget::container::Style {
                background: Some(palette.background.into()),
                border: iced::Border {
                    color: palette.primary,
                    width: 1.5,
                    radius: 8.0.into(),
                },
                shadow: iced::Shadow {
                    color: iced::Color {
                        r: 0.0,
                        g: 0.0,
                        b: 0.0,
                        a: 0.2,
                    },
                    offset: iced::Vector::new(0.0, 4.0),
                    blur_radius: 12.0,
                },
                ..Default::default()
            }
        })
        .into()
}
