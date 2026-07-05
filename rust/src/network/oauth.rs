use oauth2::PkceCodeChallenge;
use std::collections::HashMap;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpListener;

pub struct OAuthTokens {
    pub access_token: String,
    pub refresh_token: Option<String>,
}

/// Perform the OAuth2 PKCE loopback flow.
///
/// `auth_url_fn(redirect_uri, code_challenge)` must return the provider's
/// authorization URL with `code_challenge` and `code_challenge_method=S256`
/// already embedded.
///
/// `token_exchange_fn(code, redirect_uri, code_verifier)` must POST to the
/// provider's token endpoint and return the resulting tokens.
pub async fn pkce_flow<F, G, Fut>(
    auth_url_fn: F,
    token_exchange_fn: G,
) -> Result<OAuthTokens, String>
where
    F: FnOnce(String, String) -> String,
    G: FnOnce(String, String, String) -> Fut,
    Fut: std::future::Future<Output = Result<OAuthTokens, String>>,
{
    // Generate a fresh PKCE pair for this login attempt.
    let (pkce_challenge, pkce_verifier) = PkceCodeChallenge::new_random_sha256();

    // Bind to a random port.
    let listener = TcpListener::bind("127.0.0.1:0")
        .await
        .map_err(|e| format!("Cannot bind loopback port: {e}"))?;
    let port = listener.local_addr().unwrap().port();
    let redirect_uri = format!("http://127.0.0.1:{port}/callback");

    // Build the authorization URL and open system browser.
    let auth_url = auth_url_fn(redirect_uri.clone(), pkce_challenge.as_str().to_string());
    webbrowser::open(&auth_url).map_err(|e| format!("Cannot open browser: {e}"))?;

    // Wait for the OAuth callback.  Loop so that browser pre-connect probes
    // (which arrive before the real redirect and carry no "code" param) are
    // silently discarded and we keep listening for the real request.
    let code = loop {
        let (mut stream, _) = listener
            .accept()
            .await
            .map_err(|e| format!("Callback accept failed: {e}"))?;

        let mut buf = vec![0u8; 8192];
        let n = stream
            .read(&mut buf)
            .await
            .map_err(|e| format!("Callback read failed: {e}"))?;
        let request = String::from_utf8_lossy(&buf[..n]).to_string();

        // Always reply so the browser tab does not hang.
        let response = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n\
            <html><body><h2>Authorization complete. You may close this tab.</h2></body></html>";
        stream.write_all(response.as_bytes()).await.ok();
        drop(stream);

        if let Some(c) = extract_query_param(&request, "code") {
            break c;
        }
        if let Some(err) = extract_query_param(&request, "error") {
            return Err(format!("Authorization denied: {err}"));
        }
        // Pre-connect probe or empty connection — keep waiting.
    };

    let code_verifier = pkce_verifier.secret().to_string();
    token_exchange_fn(code, redirect_uri, code_verifier).await
}

fn extract_query_param(http_request: &str, param: &str) -> Option<String> {
    let first_line = http_request.lines().next()?;
    let path = first_line.split_whitespace().nth(1)?;
    let query = path.split_once('?')?.1;
    let params: HashMap<_, _> = query
        .split('&')
        .filter_map(|kv| kv.split_once('='))
        .collect();
    params.get(param).map(|v| {
        let decoded = v.replace('+', " ");
        urlencoded_decode(&decoded)
    })
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
