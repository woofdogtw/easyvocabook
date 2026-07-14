use std::collections::HashSet;

use iced::widget::{
    Space, button, checkbox, column, container, pick_list, row, scrollable, text, text_input,
};
use iced::{Element, Length};

use super::{App, Message};
use crate::db::labels;
use crate::quiz::engine::QuizMode;

// ── Public entry-point ────────────────────────────────────────────────────────

pub fn view(app: &App) -> Element<'_, Message> {
    let t = |k| app.t(k);
    let pool = app.memory.all_entries();

    let all_label = t("words.lang_all");
    let all_langs: HashSet<String> = pool.iter().map(|e| e.language.clone()).collect();
    let mut sorted_codes: Vec<String> = all_langs.into_iter().collect();
    sorted_codes.sort();

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
        let mut v = vec![String::new()];
        v.extend(sorted_codes.iter().cloned());
        v
    };
    let selected_lang_display = Some(
        app.quiz
            .language
            .as_ref()
            .map(|c| app.t(labels::lang_locale_key(c)).to_owned())
            .unwrap_or_else(|| all_label.to_owned()),
    );
    let lang_codes_clone = lang_codes.clone();
    let lang_displays_clone = lang_displays.clone();

    let lang_picker = pick_list(lang_displays, selected_lang_display, move |s: String| {
        let code = lang_codes_clone
            .iter()
            .zip(lang_displays_clone.iter())
            .find(|(_, d)| d.as_str() == s)
            .map(|(c, _)| c.clone())
            .unwrap_or_default();
        Message::QuizLanguage(code)
    });

    let action_bar = row![
        lang_picker,
        Space::new().width(Length::Fill),
        button(text(t("quiz.skip"))).on_press(Message::QuizSkip),
    ]
    .spacing(8)
    .padding(8);

    let filtered_pool: Vec<_> = if let Some(lang) = &app.quiz.language {
        pool.iter().filter(|e| &e.language == lang).collect()
    } else {
        pool.iter().collect()
    };

    if filtered_pool.is_empty() {
        let msg = if pool.is_empty() {
            t("quiz.empty.no_words")
        } else {
            t("quiz.empty.no_filter")
        };
        return column![
            action_bar,
            container(text(msg))
                .center(Length::Fill)
                .height(Length::Fill),
        ]
        .into();
    }

    let card: Element<Message> = match &app.quiz.current {
        None => container(text(t("quiz.no_card")))
            .center(Length::Fill)
            .height(Length::Fill)
            .into(),
        Some(q) => {
            if app.quiz.submitted {
                reveal_view(app)
            } else {
                match &q.mode {
                    QuizMode::Typing => typing_view(app),
                    QuizMode::MultipleChoice => choice_view(app),
                }
            }
        }
    };

    column![action_bar, card].height(Length::Fill).into()
}

// ── Typing quiz ───────────────────────────────────────────────────────────────

fn typing_view(app: &App) -> Element<'_, Message> {
    let t = |k| app.t(k);
    let q = app.quiz.current.as_ref().unwrap();

    let prompt = text(format!("{} {}", t("quiz.prompt_meaning"), q.prompt_meaning)).size(20);

    let word_input = row![
        text(t("quiz.word_label")).width(Length::Fixed(120.0)),
        text_input(t("edit.hint_optional"), &app.quiz.typing_word)
            .on_input(Message::QuizTypingWord)
            .on_submit(Message::QuizSubmit)
            .width(Length::Fill),
    ]
    .spacing(8);

    let field_inputs = app.quiz.typing_fields.iter().enumerate().fold(
        column![].spacing(6),
        |col, (i, (label, value))| {
            let label_display = app.t_label(label);
            col.push(
                row![
                    text(format!("{label_display}:")).width(Length::Fixed(120.0)),
                    text_input("", value)
                        .on_input(move |s| Message::QuizTypingField(i, s))
                        .on_submit(Message::QuizSubmit)
                        .width(Length::Fill),
                ]
                .spacing(8),
            )
        },
    );

    let buttons = row![
        button(text(t("quiz.submit")))
            .style(button::primary)
            .on_press(Message::QuizSubmit),
        button(text(t("quiz.give_up"))).on_press(Message::QuizGiveUp),
    ]
    .spacing(8);

    container(
        column![prompt, word_input, field_inputs, buttons]
            .spacing(16)
            .padding(24),
    )
    .height(Length::Fill)
    .into()
}

// ── Multiple-choice quiz ──────────────────────────────────────────────────────

fn choice_view(app: &App) -> Element<'_, Message> {
    let t = |k| app.t(k);
    let q = app.quiz.current.as_ref().unwrap();

    let prompt = text(format!("{} {}", t("quiz.word_prompt"), q.word_display)).size(20);

    let options = q
        .options
        .iter()
        .enumerate()
        .fold(column![].spacing(8), |col, (i, opt)| {
            let checked = app.quiz.mc_selected.get(i).copied().unwrap_or(false);
            let label = opt.clone();
            col.push(
                checkbox(checked)
                    .label(label)
                    .on_toggle(move |_| Message::QuizMcToggle(i)),
            )
        });

    let buttons = row![
        button(text(t("quiz.submit")))
            .style(button::primary)
            .on_press(Message::QuizSubmit),
        button(text(t("quiz.give_up"))).on_press(Message::QuizGiveUp),
    ]
    .spacing(8);

    container(column![prompt, options, buttons].spacing(16).padding(24))
        .height(Length::Fill)
        .into()
}

// ── Reveal view (post-submit) ─────────────────────────────────────────────────

fn reveal_view(app: &App) -> Element<'_, Message> {
    let t = |k| app.t(k);
    let q = app.quiz.current.as_ref().unwrap();

    let header = if app.quiz.gave_up {
        t("quiz.gave_up")
    } else {
        t("quiz.result")
    };

    let result_section: Element<Message> = match &q.mode {
        QuizMode::Typing => {
            let word_ok = app.quiz.typing_correct.unwrap_or(false);
            let mut col = column![
                row![
                    text(if word_ok { "✓" } else { "✗" }).width(Length::Fixed(24.0)),
                    text(format!(
                        "{} {} ({}: {})",
                        t("quiz.word_label"),
                        q.word_display,
                        t("quiz.your_answer"),
                        app.quiz.typing_word
                    )),
                ]
                .spacing(4),
            ]
            .spacing(6);
            for (label, correct, expected) in &app.quiz.field_results {
                let label_display = app.t_label(label);
                col = col.push(
                    row![
                        text(if *correct { "✓" } else { "✗" }).width(Length::Fixed(24.0)),
                        text(format!("{label_display}: {expected}")),
                    ]
                    .spacing(4),
                );
            }
            col.into()
        }
        QuizMode::MultipleChoice => {
            let overall = app.quiz.choice_correct.unwrap_or(false);
            let verdict = if overall {
                t("quiz.correct")
            } else {
                t("quiz.incorrect")
            };

            let mut opts_col = column![].spacing(4);
            for (i, opt) in q.options.iter().enumerate() {
                let is_correct_answer = q.correct_meanings.contains(opt);
                let was_selected = app.quiz.mc_selected.get(i).copied().unwrap_or(false);
                let mark = match (is_correct_answer, was_selected) {
                    (true, true) => "✓",
                    (true, false) => "○",
                    (false, true) => "✗",
                    (false, false) => "  ",
                };
                opts_col = opts_col.push(text(format!("{mark}  {opt}")));
            }
            column![text(verdict), opts_col].spacing(8).into()
        }
    };

    let synonyms_section: Element<Message> = if q.synonym_words.is_empty() {
        Space::new().into()
    } else {
        let mut col = column![text(t("quiz.synonyms")).size(14)].spacing(4);
        for syn in &q.synonym_words {
            let forms_str: String = syn
                .forms
                .iter()
                .map(|(l, v)| format!("{l}: {v}"))
                .collect::<Vec<_>>()
                .join(", ");
            let label = if forms_str.is_empty() {
                syn.word.clone()
            } else {
                format!("{} ({})", syn.word, forms_str)
            };
            col = col.push(text(format!("  • {label}")));
        }
        col.into()
    };

    let next_btn = button(text(t("quiz.next")))
        .style(button::primary)
        .on_press(Message::QuizNextCard);

    let header_row = row![
        text(header).size(16),
        Space::new().width(Length::Fill),
        next_btn,
    ]
    .spacing(8)
    .align_y(iced::alignment::Vertical::Center);

    let body = scrollable(
        column![header_row, result_section, synonyms_section]
            .spacing(16)
            .padding(24),
    )
    .height(Length::Fill);

    body.into()
}
