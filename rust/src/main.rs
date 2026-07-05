#![cfg_attr(target_os = "windows", windows_subsystem = "windows")]

mod config;
mod db;
mod locale;
mod network;
mod quiz;
mod ui;

fn main() -> iced::Result {
    let icon = load_window_icon();

    iced::application(ui::boot, ui::App::update, ui::App::view)
        .title("EasyVocaBook")
        .theme(ui::App::iced_theme)
        .subscription(ui::App::subscription)
        .window(iced::window::Settings {
            icon,
            ..Default::default()
        })
        .run()
}

fn load_window_icon() -> Option<iced::window::Icon> {
    const PNG: &[u8] = include_bytes!("../resources/app.png");
    let img = image::load_from_memory(PNG).ok()?.into_rgba8();
    let (w, h) = img.dimensions();
    iced::window::icon::from_rgba(img.into_raw(), w, h).ok()
}
