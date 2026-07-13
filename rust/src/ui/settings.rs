use iced::widget::{
    Space, button, checkbox, column, container, mouse_area, pick_list, radio, row, scrollable,
    stack, text, text_input,
};
use iced::{Element, Length};

use super::{App, Message};
use crate::config::{SyncMethod, Theme};

// ── Public entry-point ────────────────────────────────────────────────────────

pub fn view(app: &App) -> Element<'_, Message> {
    let s = &app.settings;
    let ui = &app.settings_ui;
    let t = |k| app.t(k);

    // ── App section ───────────────────────────────────────────────────────────

    let lang_opts: Vec<String> = vec!["en".into(), "zh-TW".into(), "zh-CN".into()];
    let lang_picker = pick_list(
        lang_opts,
        Some(s.ui_language.clone()),
        Message::SettingsUiLanguage,
    );

    let theme_row = row![
        radio(
            t("settings.theme_light"),
            Theme::Light,
            Some(s.theme.clone()),
            Message::SettingsTheme
        ),
        radio(
            t("settings.theme_dark"),
            Theme::Dark,
            Some(s.theme.clone()),
            Message::SettingsTheme
        ),
        radio(
            t("settings.theme_auto"),
            Theme::Auto,
            Some(s.theme.clone()),
            Message::SettingsTheme
        ),
    ]
    .spacing(16);

    let app_section = section(
        t("settings.app"),
        column![
            labeled_row(t("settings.language"), lang_picker),
            labeled_row_el(t("settings.theme"), theme_row.into()),
        ]
        .spacing(10),
    );

    // ── Sync method section ───────────────────────────────────────────────────

    let sync_radios = row![
        radio(
            t("settings.sync_none"),
            SyncMethod::Disabled,
            Some(s.sync_method.clone()),
            Message::SettingsSyncMethod
        ),
        radio(
            t("settings.sync_ftp"),
            SyncMethod::Ftp,
            Some(s.sync_method.clone()),
            Message::SettingsSyncMethod
        ),
        radio(
            t("settings.sync_sftp"),
            SyncMethod::Sftp,
            Some(s.sync_method.clone()),
            Message::SettingsSyncMethod
        ),
        radio(
            t("settings.sync_drive"),
            SyncMethod::GoogleDrive,
            Some(s.sync_method.clone()),
            Message::SettingsSyncMethod
        ),
    ]
    .spacing(12)
    .wrap();

    let method_fields: Element<Message> = match s.sync_method {
        SyncMethod::Disabled => Space::new().into(),
        SyncMethod::Ftp => ftp_fields(app),
        SyncMethod::Sftp => sftp_fields(app),
        SyncMethod::GoogleDrive => drive_fields(app),
    };

    let sync_status_row: Element<Message> = if ui.sync_in_progress {
        let step = ui.sync_message.as_deref().unwrap_or(t("settings.syncing"));
        text(step).into()
    } else {
        let sync_btn = button(text(t("settings.sync_now")))
            .style(button::primary)
            .on_press(Message::SettingsSyncNow);
        if let Some(msg) = &ui.sync_message {
            row![sync_btn, text(msg.as_str())].spacing(12).into()
        } else {
            sync_btn.into()
        }
    };

    let sync_section = section(
        t("settings.sync"),
        column![sync_radios, method_fields, sync_status_row].spacing(12),
    );

    // ── Practice section ──────────────────────────────────────────────────────

    let practice_section: Element<Message> = if ui.clear_stats_confirm {
        section(
            t("settings.practice"),
            column![
                text(t("settings.clear_confirm")),
                row![
                    button(text(t("settings.clear_yes")))
                        .style(button::danger)
                        .on_press(Message::SettingsClearStatsConfirm),
                    button(text(t("words.cancel"))).on_press(Message::SettingsClearStatsCancel),
                ]
                .spacing(8),
            ]
            .spacing(10),
        )
    } else {
        section(
            t("settings.practice"),
            column![
                button(text(t("settings.clear_stats")))
                    .style(button::danger)
                    .on_press(Message::SettingsClearStatsAsk),
            ]
            .spacing(10),
        )
    };

    // ── About section ─────────────────────────────────────────────────────────

    let about_section = section(
        t("settings.about"),
        column![
            row![
                text(t("settings.about_name")).width(Length::Fixed(110.0)),
                text("EasyVocaBook"),
            ]
            .spacing(8),
            row![
                text(t("settings.about_version")).width(Length::Fixed(110.0)),
                text(env!("CARGO_PKG_VERSION")),
            ]
            .spacing(8),
            row![
                text(t("settings.about_author")).width(Length::Fixed(110.0)),
                text("Chien-Hong Chan"),
            ]
            .spacing(8),
        ]
        .spacing(6),
    );

    let body = scrollable(
        column![app_section, sync_section, practice_section, about_section]
            .spacing(20)
            .padding(16),
    )
    .height(Length::Fill);

    let main_content: Element<Message> = container(body).height(Length::Fill).into();

    if let Some(auth_url) = &app.settings_ui.drive_auth_url {
        drive_auth_overlay(main_content, auth_url, app)
    } else {
        main_content
    }
}

// ── FTP fields ────────────────────────────────────────────────────────────────

fn ftp_fields(app: &App) -> Element<'_, Message> {
    let s = &app.settings;
    let ui = &app.settings_ui;
    let t = |k| app.t(k);

    let mut col = column![
        labeled_row(
            t("settings.ftp_host"),
            text_input("ftp.example.com", &s.ftp_host)
                .on_input(Message::SettingsFtpHost)
                .width(Length::Fill)
        ),
        labeled_row(
            t("settings.ftp_port"),
            text_input("21", &ui.ftp_port_str)
                .on_input(Message::SettingsFtpPort)
                .width(Length::Fixed(80.0))
        ),
        labeled_row(
            t("settings.ftp_user"),
            text_input("", &s.ftp_username)
                .on_input(Message::SettingsFtpUser)
                .width(Length::Fill)
        ),
        labeled_row(
            t("settings.ftp_pass"),
            text_input("", &ui.ftp_password)
                .on_input(Message::SettingsFtpPassword)
                .secure(true)
                .width(Length::Fill)
        ),
        labeled_row(
            t("settings.ftp_dir"),
            text_input("/", &s.ftp_directory)
                .on_input(Message::SettingsFtpDir)
                .width(Length::Fill)
        ),
    ]
    .spacing(8);
    col = col.push(
        checkbox(s.ftp_tls)
            .label(t("settings.ftp_tls"))
            .on_toggle(Message::SettingsFtpTls),
    );
    col = col.push(button(text(t("settings.save_creds"))).on_press(Message::SettingsFtpSave));
    col.into()
}

// ── SFTP fields ───────────────────────────────────────────────────────────────

fn sftp_fields(app: &App) -> Element<'_, Message> {
    let s = &app.settings;
    let ui = &app.settings_ui;
    let t = |k| app.t(k);

    column![
        labeled_row(
            t("settings.ftp_host"),
            text_input("sftp.example.com", &s.sftp_host)
                .on_input(Message::SettingsSftpHost)
                .width(Length::Fill)
        ),
        labeled_row(
            t("settings.ftp_port"),
            text_input("22", &ui.sftp_port_str)
                .on_input(Message::SettingsSftpPort)
                .width(Length::Fixed(80.0))
        ),
        labeled_row(
            t("settings.ftp_user"),
            text_input("", &s.sftp_username)
                .on_input(Message::SettingsSftpUser)
                .width(Length::Fill)
        ),
        labeled_row(
            t("settings.ftp_pass"),
            text_input("", &ui.sftp_password)
                .on_input(Message::SettingsSftpPassword)
                .secure(true)
                .width(Length::Fill)
        ),
        labeled_row(
            t("settings.ftp_dir"),
            text_input("/", &s.sftp_directory)
                .on_input(Message::SettingsSftpDir)
                .width(Length::Fill)
        ),
        button(text(t("settings.save_creds"))).on_press(Message::SettingsSftpSave),
    ]
    .spacing(8)
    .into()
}

// ── Google Drive fields ───────────────────────────────────────────────────────

fn drive_fields(app: &App) -> Element<'_, Message> {
    let s = &app.settings;
    let ui = &app.settings_ui;
    let t = |k| app.t(k);

    let auth_row: Element<Message> = if ui.drive_logged_in {
        row![
            text(t("settings.logged_in")),
            button(text(t("settings.logout")))
                .style(button::danger)
                .on_press(Message::SettingsDriveLogout),
        ]
        .spacing(8)
        .into()
    } else {
        button(text(t("settings.login_google")))
            .style(button::primary)
            .on_press(Message::SettingsDriveLogin)
            .into()
    };

    column![
        labeled_row(
            t("settings.drive_folder"),
            text_input("EasyVocaBook", &s.drive_folder)
                .on_input(Message::SettingsDriveFolder)
                .width(Length::Fill)
        ),
        auth_row,
    ]
    .spacing(8)
    .into()
}

// ── Layout helpers ────────────────────────────────────────────────────────────

fn section<'a>(title: &'a str, content: iced::widget::Column<'a, Message>) -> Element<'a, Message> {
    column![
        text(title).size(16),
        container(content).padding(iced::Padding {
            top: 0.0,
            right: 0.0,
            bottom: 0.0,
            left: 12.0
        }),
    ]
    .spacing(8)
    .into()
}

fn labeled_row<'a>(
    label: &'a str,
    widget: impl Into<Element<'a, Message>>,
) -> Element<'a, Message> {
    row![text(label).width(Length::Fixed(110.0)), widget.into(),]
        .spacing(8)
        .align_y(iced::alignment::Vertical::Center)
        .into()
}

fn labeled_row_el<'a>(label: &'a str, el: Element<'a, Message>) -> Element<'a, Message> {
    row![text(label).width(Length::Fixed(110.0)), el,]
        .spacing(8)
        .align_y(iced::alignment::Vertical::Center)
        .into()
}

// ── Google Drive auth URL dialog ──────────────────────────────────────────────

fn drive_auth_overlay<'a>(
    main_content: Element<'a, Message>,
    auth_url: &'a str,
    app: &'a App,
) -> Element<'a, Message> {
    let t = |k| app.t(k);

    let dialog = column![
        text(t("settings.drive_auth_hint")),
        text(auth_url).size(11),
        row![
            button(text(t("settings.drive_auth_copy")))
                .style(button::primary)
                .on_press(Message::SettingsDriveAuthCopyUrl),
            button(text(t("settings.drive_auth_cancel")))
                .on_press(Message::SettingsDriveAuthCancel),
        ]
        .spacing(8),
    ]
    .spacing(12)
    .padding(20);

    let dialog_box = container(dialog)
        .max_width(480)
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
        container(Space::new())
            .width(Length::Fill)
            .height(Length::Fill),
    )
    .on_press(Message::SettingsDriveAuthCancel);

    stack![
        main_content,
        backdrop,
        container(dialog_box).center(Length::Fill)
    ]
    .into()
}
