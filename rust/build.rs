use aes::Aes128;
use cbc::cipher::{BlockEncryptMut as _, KeyIvInit, block_padding::Pkcs7};
use rand::RngCore;

type Aes128CbcEnc = cbc::Encryptor<Aes128>;

fn main() {
    let out_dir = std::env::var("OUT_DIR").expect("OUT_DIR not set");

    generate_drive_secrets(&out_dir);

    let target_os = std::env::var("CARGO_CFG_TARGET_OS").unwrap_or_default();
    if target_os == "windows" {
        generate_windows_icon(&out_dir);
    }

    println!("cargo:rerun-if-changed=resources/app.ico");
    println!("cargo:rerun-if-changed=build.rs");
}

fn generate_drive_secrets(out_dir: &str) {
    println!("cargo:rerun-if-env-changed=DRIVE_CLIENT_ID");
    println!("cargo:rerun-if-env-changed=DRIVE_CLIENT_SECRET");

    let client_id =
        std::env::var("DRIVE_CLIENT_ID").unwrap_or_else(|_| "YOUR_GOOGLE_CLIENT_ID".to_string());
    let client_secret = std::env::var("DRIVE_CLIENT_SECRET")
        .unwrap_or_else(|_| "YOUR_GOOGLE_CLIENT_SECRET".to_string());

    let mut key = [0u8; 16];
    let mut iv = [0u8; 16];
    rand::thread_rng().fill_bytes(&mut key);
    rand::thread_rng().fill_bytes(&mut iv);

    let enc_id = encrypt_aes(client_id.as_bytes(), &key, &iv);
    let enc_secret = encrypt_aes(client_secret.as_bytes(), &key, &iv);

    let code = format!(
        "pub const DRIVE_AES_KEY: [u8; 16] = {};\n\
         pub const DRIVE_AES_IV: [u8; 16] = {};\n\
         pub const DRIVE_CLIENT_ID_ENC: &[u8] = &{};\n\
         pub const DRIVE_CLIENT_SECRET_ENC: &[u8] = &{};\n",
        bytes_literal(&key),
        bytes_literal(&iv),
        bytes_literal(&enc_id),
        bytes_literal(&enc_secret),
    );

    std::fs::write(format!("{out_dir}/drive_secrets.rs"), code)
        .expect("Cannot write drive_secrets.rs");
}

fn encrypt_aes(data: &[u8], key: &[u8; 16], iv: &[u8; 16]) -> Vec<u8> {
    // Allocate buffer with space for PKCS7 padding (up to one extra block).
    let padded_len = (data.len() / 16 + 1) * 16;
    let mut buf = vec![0u8; padded_len];
    buf[..data.len()].copy_from_slice(data);
    let ct = Aes128CbcEnc::new(key.into(), iv.into())
        .encrypt_padded_mut::<Pkcs7>(&mut buf, data.len())
        .unwrap();
    ct.to_vec()
}

fn bytes_literal(b: &[u8]) -> String {
    let items: Vec<String> = b.iter().map(|x| x.to_string()).collect();
    format!("[{}]", items.join(", "))
}

fn generate_windows_icon(out_dir: &str) {
    let rc_path = format!("{out_dir}/resource.rc");
    let obj_path = format!("{out_dir}/resource.o");

    std::fs::write(&rc_path, "1 ICON \"resources/app.ico\"\n").expect("Cannot write resource.rc");

    let target = std::env::var("TARGET").unwrap_or_default();
    let windres = if cfg!(target_os = "linux") && target.starts_with("x86_64") {
        "x86_64-w64-mingw32-windres"
    } else if cfg!(target_os = "linux") && target.starts_with("i686") {
        "i686-w64-mingw32-windres"
    } else {
        "windres"
    };

    let status = std::process::Command::new(windres)
        .args(["-i", &rc_path, "-o", &obj_path])
        .status()
        .expect("Failed to run windres");
    assert!(status.success(), "windres failed");

    println!("cargo:rustc-link-arg={obj_path}");
}
