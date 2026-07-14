# EasyVocaBook

![CI](https://github.com/woofdogtw/easyvocabook/actions/workflows/build-test.yaml/badge.svg)
[![Coverage](https://img.shields.io/endpoint?url=https://woofdogtw.github.io/easyvocabook/badge.json)](https://woofdogtw.github.io/easyvocabook/html/)
[![Android Coverage](https://img.shields.io/endpoint?url=https://woofdogtw.github.io/easyvocabook/android/badge.json)](https://woofdogtw.github.io/easyvocabook/android/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

A simple vocabulary practice notebook app — like a physical flashcard book where each page is one word.
Supports practicing English, Japanese, and other foreign languages.

Part of the [Easy series](https://github.com/woofdogtw) of personal apps.

## Platforms

| Platform | Technology |
|----------|-----------|
| Desktop (Windows / Linux / macOS) | Rust + [iced](https://github.com/iced-rs/iced) |
| Android | Kotlin + Jetpack Compose |

## Features

- Create and manage multiple vocabulary books (one SQLite file per book)
- Each word entry: word, reading/pronunciation, meaning, example sentences, note
- Practice mode with familiarity tracking
- Cloud sync: FTP/FTPS/SFTP, Google Drive, OneDrive
- Multilingual UI: English, Traditional Chinese, Simplified Chinese

## Project Structure

```
easyvocabook/
├── openspec/       # API specs and change proposals
├── rust/           # Desktop app (Rust + iced)
├── kotlin/         # Android app (Kotlin + Compose)
├── doc/            # Documentation and database schema
└── tools/          # Helper scripts
```

## Explore (pending discussion)

- Categories / tags for organizing vocabulary books

## License

MIT License — Copyright 2026 Chien-Hong Chan
