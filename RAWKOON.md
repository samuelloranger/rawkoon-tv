# Rawkoon integration

This fork is a Rawkoon client first. The app connects to a Rawkoon server, signs in there,
and Rawkoon hands it everything else: the Jellyfin server URL, a Jellyfin sign-in through
Quick Connect, and the TMDB key. Jellyfin stays the media server for library and playback.

## First-run flow

1. The user enters the Rawkoon server URL.
2. The app signs in to Rawkoon with a device code (default) or email and password (fallback).
3. `GET /api/tv/bootstrap` returns the Jellyfin URL and the TMDB key.
4. The app starts Quick Connect on that Jellyfin server and sends the code to Rawkoon, which
   approves it for the Jellyfin user linked to this Rawkoon account.
5. The app finishes the Jellyfin Quick Connect sign-in. Home opens with nothing left to set up.

All Rawkoon calls after sign-in send `Authorization: Bearer <rawkoon token>`. A 401 on any
call means the Rawkoon session ended: go back to step 2 (keep the server URL).

## 2a. Device code sign-in (default)

Standard OAuth device flow (RFC 8628), served by better-auth.

```
POST {rawkoon}/api/auth/device/code
Content-Type: application/json

{ "client_id": "rawkoon-tv" }
```

200:

```json
{
  "device_code": "…",
  "user_code": "ABCD1234",
  "verification_uri": "https://rawkoon.example/device",
  "verification_uri_complete": "https://rawkoon.example/device?user_code=ABCD1234",
  "expires_in": 600,
  "interval": 5
}
```

Show `user_code` (formatted `ABCD-1234`) and `verification_uri` as text, plus a QR code of
`verification_uri_complete`. The user opens it on a phone where they are signed in to
Rawkoon and approves. Then poll every `interval` seconds:

```
POST {rawkoon}/api/auth/device/token
Content-Type: application/json

{ "grant_type": "urn:ietf:params:oauth:grant-type:device_code", "device_code": "…", "client_id": "rawkoon-tv" }
```

| Status | Body | Meaning |
|---|---|---|
| 200 | `{ "access_token": "…", "token_type": "Bearer", "expires_in": … }` | Signed in. `access_token` is the Rawkoon bearer token |
| 400 | `{ "error": "authorization_pending" }` | Keep polling |
| 400 | `{ "error": "slow_down" }` | Add 5 s to the interval and keep polling |
| 400 | `{ "error": "expired_token" }` | Code expired: request a new one and show it |
| 400 | `{ "error": "access_denied" }` | The user denied it: show that, offer to retry |

## 2b. Email and password sign-in (fallback)

```
POST {rawkoon}/api/auth/sign-in/email
Content-Type: application/json

{ "email": "…", "password": "…" }
```

200 with the bearer token in the `set-auth-token` response header. 401 means wrong email or
password. 429 means too many attempts.

## 3. Bootstrap

```
GET {rawkoon}/api/tv/bootstrap
Authorization: Bearer <token>
```

```json
{
  "user": { "id": "…", "name": "…", "is_admin": false },
  "jellyfin": null | { "url": "https://jellyfin.example", "linked": true },
  "tmdb": null | { "key": "…" }
}
```

- `jellyfin` is null when Rawkoon has no Jellyfin integration. `linked` is false when this
  Rawkoon user has no linked Jellyfin user: show "Ask an admin to link your Jellyfin
  account in Rawkoon" and stop.
- `tmdb.key` is a v3 API key or a v4 read token; the app's TMDB credential handling detects
  which. `tmdb` is null when Rawkoon has no TMDB key: discovery then stays off.

## 4. Jellyfin sign-in through Quick Connect

1. On the Jellyfin server from bootstrap: `POST /QuickConnect/Initiate` (SDK:
   `quickConnectApi.initiateQuickConnect()`), giving `Code` and `Secret`.
2. Ask Rawkoon to approve it:

   ```
   POST {rawkoon}/api/tv/jellyfin/quick-connect
   Authorization: Bearer <token>
   Content-Type: application/json

   { "code": "<Code>" }
   ```

   | Status | Meaning |
   |---|---|
   | 200 `{ "ok": true }` | Approved for the linked Jellyfin user |
   | 403 | Not linked to a Jellyfin user |
   | 404 | Jellyfin does not know the code (expired): initiate again |
   | 503 | Rawkoon has no Jellyfin integration |
   | 502 | Rawkoon could not reach Jellyfin |

3. Poll `GET /QuickConnect/Connect?secret=<Secret>` until `Authenticated` is true (it is
   usually true right away), then `POST /Users/AuthenticateWithQuickConnect` with the secret.
   Save that Jellyfin session the same way a normal Jellyfin sign-in is saved.

## Request status for a title

```
GET {rawkoon}/api/requests/status?tmdb_id=603&type=movie      (type: movie | show)
Authorization: Bearer <token>
```

```json
{
  "library": null | {
    "id": 12,
    "status": "wanted|downloading|downloaded|skipped|returning|in_production|planned|upgrading",
    "episode_count": 10 | null,
    "downloaded_episode_count": 4 | null
  },
  "request": null | {
    "id": 5,
    "status": "pending|approved|denied|available",
    "deny_reason": "…" | null
  }
}
```

`deny_reason` is null for a request another user made.

## Creating a request

```
POST {rawkoon}/api/requests
Authorization: Bearer <token>
Content-Type: application/json

{ "type": "movie" | "show", "tmdb_id": 603, "title": "…", "poster_url": "…" | null, "year": 1999 | null }
```

| Status | Meaning |
|---|---|
| 200 `{ "id": 5 }` | Request created (pending until an admin approves it in Rawkoon) |
| 409 | Already requested |
| 400 | Already in the library |

Requests cover a whole show. Rawkoon has no per-season requests.

## Legacy: Jellyfin token exchange

`POST {rawkoon}/api/jellyfin/session` with `{ "jellyfin_token", "device_name" }` trades a
Jellyfin token for a Rawkoon token. The first-run flow above replaces it; the endpoint stays
on the server for other Jellyfin clients.
