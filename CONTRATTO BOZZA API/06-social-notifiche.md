# Contratto API: amicizie, notifiche e presenza

Prefisso `/api/v1`. Basato su `friendship` e `notification`. La presenza online è volatile e nel contesto è prevista in memoria/WebSocket, non nel database.

## Amicizie

| Metodo | Rotta | Auth | Esito |
|---|---|---|---|
| GET | `/friends` | sì | amicizie accettate |
| GET | `/friend-requests` | sì | richieste ricevute e inviate, filtrabili per `direction` |
| POST | `/friend-requests` | sì | invia richiesta con `{ "username": "star_hunter" }`, 201 |
| POST | `/friend-requests/{friendshipId}/accept` | sì | accetta richiesta ricevuta |
| POST | `/friend-requests/{friendshipId}/reject` | sì | rifiuta e rimuove richiesta |
| DELETE | `/friends/{friendshipId}` | sì | rimuove amicizia accettata o annulla richiesta inviata, |

La stessa coppia di giocatori non può avere due relazioni, anche invertendo requester/addressee. Non è possibile inviare richiesta a sé stessi. La rimozione cancella la relazione; una nuova richiesta può essere inviata in seguito.

## Presenza e sfide in tempo reale

Da definire separatamente: Un WebSocket autenticato espone stato online e notifiche/sfide live. Eventi minimi da concordare:

- `PRESENCE_CHANGED`: player id/username e stato online/offline, visibile agli amici.
- `FRIEND_REQUEST_RECEIVED` e `FRIEND_REQUEST_ACCEPTED`.
- `BATTLE_CHALLENGE_RECEIVED`, `BATTLE_CHALLENGE_UPDATED` (accepted/rejected/cancelled/expired).

Definire handshake JWT, destinazioni, payload, riconnessione e consegna degli eventi mentre il destinatario è offline. Le notifiche persistenti REST restano la fonte per recuperare eventi persi.

## Notifiche

| Metodo | Rotta | Auth | Esito |
|---|---|---|---|
| GET | `/users/me/notifications` | sì | elenco paginato, filtri `unreadOnly` e `type` |
| GET | `/users/me/notifications/unread-count` | sì | `{ "count": 3 }` |
| PATCH | `/users/me/notifications/{notificationId}` | sì | `{ "isRead": true }` |
| POST | `/users/me/notifications/read-all` | sì | segna tutte come lette, 204 |
| DELETE | `/users/me/notifications/{notificationId}` | sì | elimina notifica, |

Tipi previsti dallo schema: `ATTACK_RECEIVED`, `TRADE_RECEIVED`, `TRADE_ACCEPTED`, `TRADE_REJECTED`, `TRADE_CANCELLED`, `TRADE_EXPIRED`, `TRADE_FAILED`, `WANDERER_DEPARTED`, `DECK_INVALID`, `INVITE_BONUS`, `FRIEND_REQUEST_RECEIVED`, `FRIEND_REQUEST_ACCEPTED`. Oggetto risposta proposto: `id`, `type`, `isRead`, `createdAt`, riferimenti opzionali `battle`, `trade`, `card`, `friendship`; i dettagli mostrati dal client si recuperano dalla relativa rotta e non dal testo localizzato server-side.

Errori specifici proposti: `FRIENDSHIP_NOT_FOUND` 404, `FRIENDSHIP_FORBIDDEN` 403, `FRIENDSHIP_ALREADY_EXISTS` 409, `FRIEND_REQUEST_NOT_PENDING` 409, `NOTIFICATION_NOT_FOUND` 404.

## Da decidere

- Se le richieste amicizia sono consentite anche senza presenza online e se c'è un limite numerico amici.
- Se l'accettazione richiesta produce subito la sfida o solo aggiunge l'amicizia.
- Visibilità della presenza (solo amici) e heartbeat/timeout offline.
- Retention, ordinamento e limiti delle notifiche; quali eventi sono push oltre che persistiti.
- Se `DELETE /friends/{id}` distingue amicizia, richiesta inviata e richiesta ricevuta (in quest'ultimo caso usare reject).

