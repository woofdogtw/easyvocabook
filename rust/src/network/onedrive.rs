use reqwest::Client;
use serde_json::Value;
use std::path::Path;

use crate::config::keychain;
use crate::network::oauth::{OAuthTokens, pkce_flow};

const MS_AUTH_URL: &str = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize";
const MS_TOKEN_URL: &str = "https://login.microsoftonline.com/common/oauth2/v2.0/token";
const GRAPH_API: &str = "https://graph.microsoft.com/v1.0";
// Client ID must be registered in Azure App Registration (public client).
const CLIENT_ID: &str = "YOUR_MICROSOFT_CLIENT_ID";

pub struct OneDriveClient {
    folder_name: String,
    http: Client,
}

impl OneDriveClient {
    pub fn new(folder_name: &str) -> Self {
        Self {
            folder_name: folder_name.to_string(),
            http: Client::new(),
        }
    }

    pub async fn login(&self) -> Result<(), String> {
        let tokens = pkce_flow(
            |redirect_uri, code_challenge| {
                format!(
                    "{MS_AUTH_URL}?client_id={CLIENT_ID}\
                     &redirect_uri={redirect_uri}\
                     &response_type=code\
                     &scope=Files.ReadWrite+offline_access\
                     &code_challenge_method=S256\
                     &code_challenge={code_challenge}"
                )
            },
            |code, redirect_uri, code_verifier| async move {
                exchange_code_ms(&code, &redirect_uri, &code_verifier).await
            },
        )
        .await?;

        keychain::store(keychain::ONEDRIVE_ACCESS_TOKEN, &tokens.access_token)?;
        if let Some(rt) = tokens.refresh_token {
            keychain::store(keychain::ONEDRIVE_REFRESH_TOKEN, &rt)?;
        }
        Ok(())
    }

    pub async fn logout() -> Result<(), String> {
        keychain::delete(keychain::ONEDRIVE_ACCESS_TOKEN)?;
        keychain::delete(keychain::ONEDRIVE_REFRESH_TOKEN)?;
        Ok(())
    }

    pub fn is_logged_in() -> bool {
        keychain::load(keychain::ONEDRIVE_ACCESS_TOKEN)
            .ok()
            .flatten()
            .is_some()
    }

    async fn access_token(&self) -> Result<String, String> {
        keychain::load(keychain::ONEDRIVE_ACCESS_TOKEN)?.ok_or("Not logged in to OneDrive".into())
    }
}

async fn exchange_code_ms(
    code: &str,
    redirect_uri: &str,
    code_verifier: &str,
) -> Result<OAuthTokens, String> {
    let client = Client::new();
    let resp: Value = client
        .post(MS_TOKEN_URL)
        .form(&[
            ("code", code),
            ("client_id", CLIENT_ID),
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
        .ok_or("No access_token")?
        .to_string();
    let refresh = resp["refresh_token"].as_str().map(|s| s.to_string());
    Ok(OAuthTokens {
        access_token: access,
        refresh_token: refresh,
    })
}

impl crate::network::SyncClient for OneDriveClient {
    fn upload<'a>(
        &'a self,
        local_path: &'a Path,
    ) -> std::pin::Pin<Box<dyn std::future::Future<Output = Result<(), String>> + Send + 'a>> {
        Box::pin(async move {
            let token = self.access_token().await?;
            let data = tokio::fs::read(local_path)
                .await
                .map_err(|e| format!("Cannot read local file: {e}"))?;
            let url = format!(
                "{GRAPH_API}/me/drive/root:/{}/easyvocabook.db:/content",
                self.folder_name
            );
            self.http
                .put(&url)
                .bearer_auth(&token)
                .header("Content-Type", "application/octet-stream")
                .body(data)
                .send()
                .await
                .map_err(|e| format!("OneDrive upload failed: {e}"))?;
            Ok(())
        })
    }

    fn download<'a>(
        &'a self,
        dest_path: &'a Path,
    ) -> std::pin::Pin<Box<dyn std::future::Future<Output = Result<(), String>> + Send + 'a>> {
        Box::pin(async move {
            let token = self.access_token().await?;
            let url = format!(
                "{GRAPH_API}/me/drive/root:/{}/easyvocabook.db:/content",
                self.folder_name
            );
            let bytes = self
                .http
                .get(&url)
                .bearer_auth(&token)
                .send()
                .await
                .map_err(|e| format!("OneDrive download failed: {e}"))?
                .bytes()
                .await
                .map_err(|e| format!("OneDrive read failed: {e}"))?;

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
                .join(format!("easyvocabook_onedrive_{}.db", std::process::id()));
            match self.download(&tmp).await {
                Ok(()) => {
                    let v = crate::network::ftp::read_last_modified_from_pub(
                        &tmp.to_string_lossy(),
                    );
                    let _ = std::fs::remove_file(&tmp);
                    v
                }
                Err(_) => Ok(None),
            }
        })
    }
}
