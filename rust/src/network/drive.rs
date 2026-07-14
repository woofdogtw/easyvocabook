use aes::Aes128;
use cbc::cipher::{BlockDecryptMut, KeyIvInit, block_padding::Pkcs7};
use oauth2::PkceCodeChallenge;
use reqwest::Client;
use serde_json::Value;
use std::collections::HashMap;
use std::path::Path;
use std::sync::Arc;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpListener;

use crate::config::keychain;
use crate::network::oauth::OAuthTokens;

mod secrets {
    include!(concat!(env!("OUT_DIR"), "/drive_secrets.rs"));
}

const DRIVE_AUTH_URL: &str = "https://accounts.google.com/o/oauth2/v2/auth";

fn percent_encode(s: &str) -> String {
    let mut out = String::with_capacity(s.len() * 3);
    for b in s.bytes() {
        match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                out.push(b as char);
            }
            _ => {
                out.push('%');
                out.push_str(&format!("{b:02X}"));
            }
        }
    }
    out
}
const DRIVE_TOKEN_URL: &str = "https://oauth2.googleapis.com/token";
const DRIVE_API: &str = "https://www.googleapis.com/drive/v3";
const DRIVE_UPLOAD_API: &str = "https://www.googleapis.com/upload/drive/v3";

type Aes128CbcDec = cbc::Decryptor<Aes128>;

fn decrypt_secret(enc: &[u8], key: &[u8; 16], iv: &[u8; 16]) -> String {
    let mut buf = enc.to_vec();
    let dec = Aes128CbcDec::new(key.into(), iv.into());
    let pt = dec.decrypt_padded_mut::<Pkcs7>(&mut buf).unwrap();
    String::from_utf8(pt.to_vec()).unwrap()
}

fn client_id() -> String {
    decrypt_secret(
        secrets::DRIVE_CLIENT_ID_ENC,
        &secrets::DRIVE_AES_KEY,
        &secrets::DRIVE_AES_IV,
    )
}

fn client_secret() -> String {
    decrypt_secret(
        secrets::DRIVE_CLIENT_SECRET_ENC,
        &secrets::DRIVE_AES_KEY,
        &secrets::DRIVE_AES_IV,
    )
}

/// State produced by `DriveClient::prepare_login` and consumed by `complete_login_and_email`.
pub struct DriveAuthPending {
    pub auth_url: String,
    redirect_uri: String,
    listener: TcpListener,
    verifier_secret: String,
}

impl std::fmt::Debug for DriveAuthPending {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("DriveAuthPending")
            .field("auth_url", &self.auth_url)
            .finish_non_exhaustive()
    }
}

fn extract_query_param_from_request(http_request: &str, param: &str) -> Option<String> {
    let first_line = http_request.lines().next()?;
    let path = first_line.split_whitespace().nth(1)?;
    let query = path.split_once('?')?.1;
    let params: HashMap<_, _> = query
        .split('&')
        .filter_map(|kv| kv.split_once('='))
        .collect();
    params.get(param).map(|v| urlencoded_decode(v))
}

fn urlencoded_decode(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut chars = s.chars().peekable();
    while let Some(c) = chars.next() {
        if c == '%' {
            let h1 = chars.next().unwrap_or('0');
            let h2 = chars.next().unwrap_or('0');
            if let Ok(byte) = u8::from_str_radix(&format!("{h1}{h2}"), 16) {
                out.push(byte as char);
            }
        } else {
            out.push(c);
        }
    }
    out
}

pub struct DriveClient {
    folder_name: String,
    http: Client,
}

impl DriveClient {
    pub fn new(folder_name: &str) -> Self {
        Self {
            folder_name: folder_name.to_string(),
            http: Client::new(),
        }
    }

    /// Phase 1: bind loopback listener and return the auth URL + pending state.
    /// Call this first to get the URL to show the user.
    pub async fn prepare_login() -> Result<DriveAuthPending, String> {
        let (pkce_challenge, pkce_verifier) = PkceCodeChallenge::new_random_sha256();
        let listener = TcpListener::bind("127.0.0.1:0")
            .await
            .map_err(|e| format!("Cannot bind loopback port: {e}"))?;
        let port = listener.local_addr().unwrap().port();
        let redirect_uri = format!("http://127.0.0.1:{port}/callback");
        let enc_redirect = percent_encode(&redirect_uri);
        let cid = client_id();
        let auth_url = format!(
            "{DRIVE_AUTH_URL}?client_id={cid}\
             &redirect_uri={enc_redirect}\
             &response_type=code\
             &scope=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fdrive.file\
             &access_type=offline\
             &code_challenge_method=S256\
             &code_challenge={}\
             &prompt=select_account",
            pkce_challenge.as_str()
        );
        Ok(DriveAuthPending {
            auth_url,
            redirect_uri,
            listener,
            verifier_secret: pkce_verifier.secret().to_string(),
        })
    }

    /// Phase 2: wait for the OAuth callback, exchange the code, store tokens,
    /// and return the user email. The listener in `pending` stays alive until
    /// the redirect arrives, so the user can open the auth URL in any browser.
    pub async fn complete_login_and_email(pending: Arc<DriveAuthPending>) -> Result<(), String> {
        let code = loop {
            let (mut stream, _) = pending
                .listener
                .accept()
                .await
                .map_err(|e| format!("Callback accept failed: {e}"))?;
            let mut buf = vec![0u8; 8192];
            let n = stream
                .read(&mut buf)
                .await
                .map_err(|e| format!("Callback read failed: {e}"))?;
            let request = String::from_utf8_lossy(&buf[..n]).to_string();
            stream
                .write_all(
                    b"HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n\
                    <html><body><h2>Authorization complete. You may close this tab.</h2></body></html>",
                )
                .await
                .ok();
            drop(stream);
            if let Some(c) = extract_query_param_from_request(&request, "code") {
                break c;
            }
            if let Some(err) = extract_query_param_from_request(&request, "error") {
                return Err(format!("Authorization denied: {err}"));
            }
        };

        let tokens =
            exchange_code_drive(&code, &pending.redirect_uri, &pending.verifier_secret).await?;
        keychain::store(keychain::DRIVE_ACCESS_TOKEN, &tokens.access_token)?;
        if let Some(rt) = tokens.refresh_token {
            keychain::store(keychain::DRIVE_REFRESH_TOKEN, &rt)?;
        }
        Ok(())
    }

    pub async fn logout() -> Result<(), String> {
        keychain::delete(keychain::DRIVE_ACCESS_TOKEN)?;
        keychain::delete(keychain::DRIVE_REFRESH_TOKEN)?;
        Ok(())
    }

    pub fn is_logged_in() -> bool {
        keychain::load(keychain::DRIVE_ACCESS_TOKEN)
            .ok()
            .flatten()
            .is_some()
    }

    /// Refresh the access token using the stored refresh token.
    async fn refresh_access_token(&self) -> Result<String, String> {
        let refresh_token = keychain::load(keychain::DRIVE_REFRESH_TOKEN)?
            .ok_or("No refresh token; please log in again")?;
        let cid = client_id();
        let csecret = client_secret();
        let resp: Value = self
            .http
            .post(DRIVE_TOKEN_URL)
            .form(&[
                ("grant_type", "refresh_token"),
                ("client_id", cid.as_str()),
                ("client_secret", csecret.as_str()),
                ("refresh_token", refresh_token.as_str()),
            ])
            .send()
            .await
            .map_err(|e| format!("Token refresh request failed: {e}"))?
            .json()
            .await
            .map_err(|e| format!("Token refresh parse failed: {e}"))?;

        let new_token = resp["access_token"]
            .as_str()
            .ok_or_else(|| format!("Token refresh failed: {resp}"))?
            .to_string();
        keychain::store(keychain::DRIVE_ACCESS_TOKEN, &new_token)?;
        Ok(new_token)
    }

    /// Send an authenticated request. If the server returns 401, refresh the
    /// access token once and retry. The `build` closure is called with the
    /// bearer token and must return a ready-to-send `RequestBuilder`.
    async fn send_authed<F>(&self, build: F) -> Result<reqwest::Response, String>
    where
        F: Fn(&str) -> reqwest::RequestBuilder,
    {
        let token =
            keychain::load(keychain::DRIVE_ACCESS_TOKEN)?.ok_or("Not logged in to Google Drive")?;
        let resp = build(&token)
            .send()
            .await
            .map_err(|e| format!("Request failed: {e}"))?;
        if resp.status() == reqwest::StatusCode::UNAUTHORIZED {
            let new_token = self.refresh_access_token().await?;
            return build(&new_token)
                .send()
                .await
                .map_err(|e| format!("Retry failed: {e}"));
        }
        Ok(resp)
    }

    async fn find_or_create_folder(&self) -> Result<String, String> {
        let name = self.folder_name.replace('\'', "\\'");
        let http = self.http.clone();
        let query = format!(
            "name='{name}' and mimeType='application/vnd.google-apps.folder' and trashed=false"
        );

        let resp: Value = self
            .send_authed(|t| {
                http.get(format!("{DRIVE_API}/files"))
                    .bearer_auth(t)
                    .query(&[("q", query.as_str()), ("fields", "files(id)")])
            })
            .await?
            .json()
            .await
            .map_err(|e| format!("Drive list parse failed: {e}"))?;

        if let Some(id) = resp["files"]
            .as_array()
            .and_then(|a| a.first())
            .and_then(|f| f["id"].as_str())
        {
            return Ok(id.to_string());
        }

        // Create folder.
        let body = serde_json::json!({
            "name": name,
            "mimeType": "application/vnd.google-apps.folder"
        });
        let http2 = http.clone();

        let resp: Value = self
            .send_authed(|t| {
                http2
                    .post(format!("{DRIVE_API}/files"))
                    .bearer_auth(t)
                    .json(&body)
            })
            .await?
            .json()
            .await
            .map_err(|e| format!("Drive mkdir parse failed: {e}"))?;

        resp["id"]
            .as_str()
            .map(|s| s.to_string())
            .ok_or_else(|| format!("Drive folder creation failed: {resp}"))
    }

    async fn find_file_id(&self, folder_id: &str) -> Result<Option<String>, String> {
        let folder_id = folder_id.to_string();
        let http = self.http.clone();
        let query =
            format!("name='easyvocabook.db' and '{folder_id}' in parents and trashed=false");

        let resp: Value = self
            .send_authed(|t| {
                http.get(format!("{DRIVE_API}/files"))
                    .bearer_auth(t)
                    .query(&[("q", query.as_str()), ("fields", "files(id)")])
            })
            .await?
            .json()
            .await
            .map_err(|e| format!("Drive search parse failed: {e}"))?;

        Ok(resp["files"]
            .as_array()
            .and_then(|a| a.first())
            .and_then(|f| f["id"].as_str())
            .map(|s| s.to_string()))
    }
}

async fn exchange_code_drive(
    code: &str,
    redirect_uri: &str,
    code_verifier: &str,
) -> Result<OAuthTokens, String> {
    let cid = client_id();
    let csecret = client_secret();
    let client = Client::new();
    let resp: Value = client
        .post(DRIVE_TOKEN_URL)
        .form(&[
            ("code", code),
            ("client_id", cid.as_str()),
            ("client_secret", csecret.as_str()),
            ("redirect_uri", redirect_uri),
            ("grant_type", "authorization_code"),
            ("code_verifier", code_verifier),
        ])
        .send()
        .await
        .map_err(|e| format!("Token exchange failed: {e}"))?
        .json()
        .await
        .map_err(|e| format!("Token parse failed: {e}"))?;

    let access = resp["access_token"]
        .as_str()
        .ok_or("No access_token in response")?
        .to_string();
    let refresh = resp["refresh_token"].as_str().map(|s| s.to_string());
    Ok(OAuthTokens {
        access_token: access,
        refresh_token: refresh,
    })
}

impl crate::network::SyncClient for DriveClient {
    fn upload<'a>(
        &'a self,
        local_path: &'a Path,
    ) -> std::pin::Pin<Box<dyn std::future::Future<Output = Result<(), String>> + Send + 'a>> {
        Box::pin(async move {
            let folder_id = self.find_or_create_folder().await?;
            let data = tokio::fs::read(local_path)
                .await
                .map_err(|e| format!("Cannot read local file: {e}"))?;
            let existing = self.find_file_id(&folder_id).await?;
            let http = self.http.clone();

            if let Some(file_id) = existing {
                // Update existing file.
                let url = format!("{DRIVE_UPLOAD_API}/files/{file_id}?uploadType=media");
                self.send_authed(|t| {
                    http.patch(&url)
                        .bearer_auth(t)
                        .header("Content-Type", "application/octet-stream")
                        .body(data.clone())
                })
                .await?;
            } else {
                // Create new file with multipart.
                let meta_str = serde_json::json!({
                    "name": "easyvocabook.db",
                    "parents": [folder_id]
                })
                .to_string();
                let url = format!("{DRIVE_UPLOAD_API}/files?uploadType=multipart");
                self.send_authed(|t| {
                    let form = reqwest::multipart::Form::new()
                        .part(
                            "metadata",
                            reqwest::multipart::Part::text(meta_str.clone())
                                .mime_str("application/json")
                                .unwrap(),
                        )
                        .part(
                            "file",
                            reqwest::multipart::Part::bytes(data.clone())
                                .mime_str("application/octet-stream")
                                .unwrap(),
                        );
                    http.post(&url).bearer_auth(t).multipart(form)
                })
                .await?;
            }
            Ok(())
        })
    }

    fn download<'a>(
        &'a self,
        dest_path: &'a Path,
    ) -> std::pin::Pin<Box<dyn std::future::Future<Output = Result<(), String>> + Send + 'a>> {
        Box::pin(async move {
            let folder_id = self.find_or_create_folder().await?;
            let file_id = self
                .find_file_id(&folder_id)
                .await?
                .ok_or("Remote file does not exist")?;

            let http = self.http.clone();
            let url = format!("{DRIVE_API}/files/{file_id}?alt=media");
            let bytes = self
                .send_authed(|t| http.get(&url).bearer_auth(t))
                .await?
                .bytes()
                .await
                .map_err(|e| format!("Drive read failed: {e}"))?;

            tokio::fs::write(dest_path, bytes)
                .await
                .map_err(|e| format!("Cannot write local file: {e}"))
        })
    }

    fn remote_last_modified<'a>(
        &'a self,
    ) -> std::pin::Pin<Box<dyn std::future::Future<Output = Result<Option<i64>, String>> + Send + 'a>>
    {
        Box::pin(async move {
            let tmp = std::env::temp_dir()
                .join(format!("easyvocabook_drive_{}.db", std::process::id()));
            match self.download(&tmp).await {
                Ok(()) => {
                    let v = crate::network::ftp::read_last_modified_from_pub(
                        &tmp.to_string_lossy(),
                    );
                    let _ = std::fs::remove_file(&tmp);
                    v
                }
                Err(e) if e.contains("Remote file does not exist") => Ok(None),
                Err(e) => Err(e),
            }
        })
    }
}
