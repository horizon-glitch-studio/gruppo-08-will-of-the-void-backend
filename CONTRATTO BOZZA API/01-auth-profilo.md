# Contratto API: Auth e Profilo

Tutte le rotte hanno prefisso `/api/v1`. Formato dati: `application/json`, campi in camelCase, date in ISO 8601 UTC.
I testi che vedono i giocatori sono solo in inglese: il backend restituisce `code` stabili e messaggi in inglese per gli sviluppatori, la traduzione dei testi in UI Ã¨ del frontend.

Le decisioni confermate sono riportate come regole del contratto. Restano aperti solo i punti elencati in fondo.

---

## 0. Convenzioni

**Autenticazione.** Le rotte protette richiedono `Authorization: Bearer <accessToken>`.
**Ruoli.** Nel DB il ruolo Ã¨ `USER` o `ADMIN`. Spring Security aggiunge il prefisso `ROLE_` internamente, nell'API si usa `USER` / `ADMIN`.
**UnicitÃ .** Username ed email sono unici senza distinguere maiuscole e minuscole (`uq_player_username_lower`, `uq_player_email_lower`).
**Account eliminati.** L'eliminazione Ã¨ un soft delete (`deleted_at`): username ed email vengono anonimizzati. Un account eliminato non puÃ² fare login e il suo profilo pubblico risponde 404.

### Formato degli errori

```json
{
  "timestamp": "2026-11-08T16:22:00Z",
  "status": 409,
  "error": "Conflict",
  "code": "EMAIL_ALREADY_USED",
  "message": "This email is already registered.",
  "path": "/api/v1/auth/register",
  "fieldErrors": []
}
```

| Campo         | Tipo              | Descrizione                                                                                         |
| :------------ | :---------------- | :-------------------------------------------------------------------------------------------------- |
| `timestamp`   | String (ISO 8601) | Data e ora dell'errore                                                                              |
| `status`      | Integer           | Codice HTTP                                                                                         |
| `error`       | String            | Nome sintetico dell'errore HTTP                                                                     |
| `code`        | String            | Codice stabile in MAIUSCOLO, il frontend lo usa per scegliere il testo da mostrare                  |
| `message`     | String            | Descrizione in inglese per gli sviluppatori, non da mostrare ai giocatori                           |
| `path`        | String            | Rotta chiamata                                                                                      |
| `fieldErrors` | Array             | Solo per i 400 di validazione. Ogni elemento Ã¨ `{ "field": "...", "code": "..." }`, altrimenti `[]` |

Esempio di 400 di validazione:

```json
{
  "timestamp": "2026-11-08T16:22:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "One or more fields are invalid.",
  "path": "/api/v1/auth/register",
  "fieldErrors": [
    { "field": "username", "code": "USERNAME_INVALID_CHARACTERS" },
    { "field": "password", "code": "PASSWORD_TOO_SHORT" }
  ]
}
```

### Codici di errore

| Codice                  | HTTP | Quando                                                                  |
| :---------------------- | :--- | :---------------------------------------------------------------------- |
| `VALIDATION_FAILED`     | 400  | Uno o piÃ¹ campi non validi (dettaglio in `fieldErrors`)                 |
| `INVALID_CREDENTIALS`   | 401  | Username/email o password errati (stessa risposta nei due casi)         |
| `TOKEN_INVALID`         | 401  | JWT o refresh token mancante, malformato o scaduto                      |
| `FORBIDDEN`             | 403  | Ruolo insufficiente                                                     |
| `PLAYER_NOT_FOUND`      | 404  | Profilo inesistente o account eliminato                                 |
| `USERNAME_ALREADY_USED` | 409  | Username giÃ  in uso                                                     |
| `EMAIL_ALREADY_USED`    | 409  | Email giÃ  in uso                                                        |
| `INVITE_CODE_INVALID`   | 400  | Codice invito inesistente                                               |
| `ACTION_TOKEN_INVALID`  | 400  | Token di verifica email o reset password sbagliato, scaduto o giÃ  usato |
| `TOO_MANY_REQUESTS`     | 429  | Codice previsto per il rate limiting, rinviato alla fase 2; non fa parte degli esiti v1. |
| `INTERNAL_ERROR`        | 500  | Errore del server                                                       |

Valori di `fieldErrors[].code` per username e password: `USERNAME_TOO_SHORT`, `USERNAME_TOO_LONG`, `USERNAME_INVALID_CHARACTERS`, `USERNAME_RESERVED`, `PASSWORD_TOO_SHORT`, `PASSWORD_TOO_LONG`, `EMAIL_INVALID`, `TIMEZONE_INVALID`.

---

## 1. Autenticazione

### 1.1 Registrazione

Crea un account. Alla registrazione il giocatore riceve il bonus crediti di registrazione (transazione `REGISTRATION_BONUS`, una sola per giocatore) e il backend invia un'email di verifica.

- **URL:** `/auth/register`
- **Metodo:** `POST`
- **Autenticazione:** nessuna

Request:

```json
{
  "username": "void_walker",
  "email": "player@willofthevoid.com",
  "password": "Password123!",
  "timezone": "Europe/Rome",
  "inviteCode": "VOID-4F7K"
}
```

| Campo        | Obbligatorio | Note                                                                                                                   |
| :----------- | :----------- | :--------------------------------------------------------------------------------------------------------------------- |
| `username`   | sÃ¬           | 3 a 20 caratteri, solo lettere, numeri e `_`. Non puÃ² essere una parola riservata (`me`, `admin`, ...). |
| `email`      | sÃ¬           | Formato email valido, massimo 255 caratteri                                                                            |
| `password`   | sÃ¬           | Da 8 a 72 caratteri.                                                                                    |
| `timezone`   | sÃ¬           | Fuso IANA letto dal browser (es. `Europe/Rome`), validato dal backend. Serve per il bonus giornaliero                  |
| `inviteCode` | no           | Codice invito di un altro giocatore                                                                                    |

Risposta **201 Created**:

```json
{
  "id": 1,
  "username": "void_walker",
  "email": "player@willofthevoid.com",
  "emailVerified": false
}
```

Errori: 400 `VALIDATION_FAILED`, 400 `INVITE_CODE_INVALID`, 409 `USERNAME_ALREADY_USED`, 409 `EMAIL_ALREADY_USED`.

---

### 1.2 Login

Accetta username oppure email nello stesso campo. Se Ã¨ il primo accesso della giornata (nel fuso del giocatore) assegna il bonus giornaliero.

- **URL:** `/auth/login`
- **Metodo:** `POST`
- **Autenticazione:** nessuna

Request:

```json
{
  "identifier": "void_walker",
  "password": "Password123!"
}
```

Risposta **200 OK**:

```json
{
  "tokenType": "Bearer",
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "accessTokenExpiresIn": 900,
  "refreshToken": "r8Gk2...",
  "refreshTokenExpiresIn": 2592000,
  "player": {
    "id": 1,
    "username": "void_walker",
    "role": "USER",
    "emailVerified": false
  },
  "dailyBonus": {
    "granted": true,
    "amount": 10
  }
}
```

`expiresIn` Ã¨ in secondi. Se il bonus Ã¨ giÃ  stato preso oggi: `"dailyBonus": { "granted": false, "amount": 0 }`. I valori numerici nell'esempio sono illustrativi.

Errori: 400 `VALIDATION_FAILED`, 401 `INVALID_CREDENTIALS`. Il limite di tentativi e il relativo 429 sono rinviati alla fase 2.

---

### 1.3 Rinnovo del token

Scambia un refresh token valido con una nuova coppia di token. Il refresh token usato viene invalidato (`used_at` in `player_token`) e se ne emette uno nuovo (rotazione).

- **URL:** `/auth/refresh`
- **Metodo:** `POST`
- **Autenticazione:** nessuna

Request:

```json
{ "refreshToken": "r8Gk2..." }
```

Risposta **200 OK**: stesso formato di `tokenType`, `accessToken`, `accessTokenExpiresIn`, `refreshToken`, `refreshTokenExpiresIn` del login (senza `player` e `dailyBonus`).

Errori: 401 `TOKEN_INVALID`.

---

### 1.4 Logout

Invalida il refresh token. L'access token, essendo stateless, resta valido fino alla scadenza.

- **URL:** `/auth/logout`
- **Metodo:** `POST`
- **Autenticazione:** richiesta

Request:

```json
{ "refreshToken": "r8Gk2..." }
```

Risposta **204 No Content**.

---

### 1.5 Verifica email

- **URL:** `/auth/verify-email`
- **Metodo:** `POST`
- **Autenticazione:** nessuna

Request:

```json
{ "token": "b7d1..." }
```

Risposta **204 No Content**. Errori: 400 `ACTION_TOKEN_INVALID`.

### 1.6 Reinvio dell'email di verifica

- **URL:** `/auth/resend-verification`
- **Metodo:** `POST`
- **Autenticazione:** richiesta

Nessun body. Risposta **204 No Content**. Se l'email Ã¨ giÃ  verificata risponde comunque 204.

---

### 1.7 Password dimenticata

Risponde sempre allo stesso modo, che l'email esista o no, per non rivelare quali email sono registrate.

- **URL:** `/auth/forgot-password`
- **Metodo:** `POST`
- **Autenticazione:** nessuna

Request:

```json
{ "email": "player@willofthevoid.com" }
```

Risposta **202 Accepted** (nessun body). Errori: 400 `VALIDATION_FAILED`. Il limite di tentativi e il relativo 429 sono rinviati alla fase 2.

### 1.8 Reimpostazione della password

- **URL:** `/auth/reset-password`
- **Metodo:** `POST`
- **Autenticazione:** nessuna

Request:

```json
{
  "token": "c91a...",
  "newPassword": "NewPassword456!"
}
```

Risposta **204 No Content**. Tutti i refresh token del giocatore vengono invalidati. Errori: 400 `ACTION_TOKEN_INVALID`, 400 `VALIDATION_FAILED`.

---

## 2. Profilo del giocatore

### 2.1 Profilo corrente (dati privati)

- **URL:** `/users/me`
- **Metodo:** `GET`
- **Autenticazione:** richiesta

Risposta **200 OK**:

```json
{
  "id": 1,
  "username": "void_walker",
  "email": "player@willofthevoid.com",
  "emailVerified": false,
  "role": "USER",
  "avatar": "avatar_07",
  "creditBalance": 120,
  "trophies": 340,
  "league": "ORBIT_3",
  "defenseDeckId": 12,
  "protectedUntil": null,
  "timezone": "Europe/Rome",
  "inviteCode": "VOID-4F7K",
  "invitedBy": null,
  "stats": {
    "matchesPlayed": 120,
    "wins": 70,
    "losses": 42,
    "draws": 8,
    "winRate": 58.3
  },
  "createdAt": "2026-11-08T16:00:00Z"
}
```

Note sui campi:

- `avatar`: chiave di un avatar predefinito (non c'Ã¨ upload di immagini). Il campo **non esiste ancora nel DB**, va aggiunto con una migrazione V2.
- `league`: calcolata dai trofei, non salvata. Soglie e nomi delle orbite sono da definire con Marco. L'esempio Ã¨ illustrativo.
- `defenseDeckId`: `null` se il giocatore non ha un mazzo di difesa. Il frontend deve controllare anche la validitÃ  del mazzo (vedi contratto dei mazzi).
- `protectedUntil`: `null` se non protetto.
- `stats`: ricavate dalla tabella `battle` (solo partite con stato `PLAYED`). `winRate` = `wins / matchesPlayed * 100`, arrotondato a un decimale, `0` se non ha giocato. I pareggi contano come partite giocate e non come vittorie.
- `invitedBy`: username di chi ha invitato il giocatore, oppure `null`.

Errori: 401 `TOKEN_INVALID`.

---

### 2.2 Modifica del profilo

- **URL:** `/users/me`
- **Metodo:** `PATCH`
- **Autenticazione:** richiesta

Request (tutti i campi opzionali, si inviano solo quelli da cambiare):

```json
{
  "avatar": "avatar_12",
  "timezone": "Europe/London"
}
```

Risposta **200 OK**: stesso oggetto di `GET /users/me`.
Errori: 400 `VALIDATION_FAILED` (avatar inesistente o fuso non valido), 401 `TOKEN_INVALID`.
Username ed email **non** si modificano in v1.

---

### 2.3 Impostazioni di accessibilitÃ 

Corrispondono alla tabella `player_settings`.

- **URL:** `/users/me/settings`
- **Metodi:** `GET` e `PATCH`
- **Autenticazione:** richiesta

Risposta **200 OK** (GET) e request/risposta del PATCH (nel PATCH tutti i campi sono opzionali):

```json
{
  "reduceMotion": false,
  "reduceTransparency": false,
  "showTypeLabels": false
}
```

Errori: 400 `VALIDATION_FAILED`, 401 `TOKEN_INVALID`.

---

### 2.4 Cambio password

- **URL:** `/users/me/password`
- **Metodo:** `PUT`
- **Autenticazione:** richiesta

Request:

```json
{
  "currentPassword": "Password123!",
  "newPassword": "NewPassword456!"
}
```

Risposta **204 No Content**. Gli altri refresh token del giocatore vengono invalidati.
Errori: 400 `VALIDATION_FAILED`, 401 `INVALID_CREDENTIALS` (password attuale errata).

---

### 2.5 Eliminazione dell'account

Soft delete: `deleted_at` impostato, username ed email anonimizzati, tutti i token invalidati.

- **URL:** `/users/me`
- **Metodo:** `DELETE`
- **Autenticazione:** richiesta

Request:

```json
{ "password": "Password123!" }
```

Risposta **204 No Content**. Errori: 401 `INVALID_CREDENTIALS`.

---

### 2.6 Profilo pubblico

- **URL:** `/users/{username}`
- **Metodo:** `GET`
- **Autenticazione:** nessuna (se serve limitarla ai soli giocatori loggati, diventa "richiesta")

Risposta **200 OK**:

```json
{
  "id": 2,
  "username": "star_hunter",
  "avatar": "avatar_03",
  "trophies": 520,
  "league": "ORBIT_4",
  "stats": {
    "matchesPlayed": 80,
    "wins": 50,
    "losses": 28,
    "draws": 2,
    "winRate": 62.5
  },
  "createdAt": "2026-10-20T10:00:00Z"
}
```

Non vengono mai esposti: email, crediti, ruolo, mazzi, fuso orario, codice invito.
Errori: 404 `PLAYER_NOT_FOUND`.

---

## 3. Punti ancora aperti

- **Verifica email:** il login è consentito anche prima della verifica. Va definito quali funzioni restano bloccate fino alla verifica (per esempio Ranked o acquisto pacchetti).
- **Avatar:** il set di avatar predefiniti è confermato; il campo `avatar` manca ancora dallo schema attuale e richiede una migrazione.
- **Leghe:** nomi e soglie restano da definire; la lega è calcolata dai trofei.
- **Rate limiting:** soglie e implementazione sono rinviate alla fase 2. In v1 non promettere `429 TOO_MANY_REQUESTS` sulle rotte auth.
