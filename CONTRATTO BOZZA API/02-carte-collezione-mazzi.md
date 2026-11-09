# Contratto API: catalogo, collezione e mazzi

Prefisso `/api/v1`. Modelli basati su `card`, `faction`, `rarity`, `collection_card`, `memory_archive`, `deck` e `deck_card`.

## Modello carta

La carta esposta include `id`, `name`, `description`, `imageUrl`, `faction`, `rarity`, `attack`, `defense`. Il catalogo pubblico restituisce solo carte attive. `faction` contiene identificativo e nome; `rarity` contiene nome, ordine e limiti di copie rilevanti per il client. Le probabilità dei pacchetti non sono parte della carta.

## Catalogo

| Metodo | Rotta | Auth | Esito |
|---|---|---|---|
| GET | `/cards` | no | elenco paginato; filtri proposti `factionId`, `rarityId`, `search`, `sort` |
| GET | `/cards/{cardId}` | no | dettaglio carta attiva, 404 `CARD_NOT_FOUND` |
| GET | `/game-data` | no | dati di riferimento: fazioni, rarità e vantaggi di fazione |

Vantaggi di fazione possono influenzare attacco o difesa. Il catalogo deve esporre valore e direzione (`ATTACK`/`DEFENSE`) senza duplicare la logica di calcolo del server.

## Collezione giocatore

| Metodo | Rotta | Auth | Esito |
|---|---|---|---|
| GET | `/users/me/collection` | sì | collezione paginata; filtro `factionId`, `rarityId`, `availableInDeck` |
| GET | `/users/me/collection/{cardId}` | sì | quantità posseduta, date acquisizione/scadenza e carta |
| GET | `/users/me/archive` | sì | archivio paginato dei Wanderer partiti |

Esempio elemento collezione:

```json
{
  "card": { "id": 42, "name": "Aster", "imageUrl": "/cards/42.png", "faction": { "id": 2, "name": "Solar" }, "rarity": { "id": 4, "name": "Wanderer" }, "attack": 8, "defense": 6 },
  "quantity": 1,
  "acquiredAt": "2026-11-08T16:00:00Z",
  "expiresAt": "2026-11-15T16:00:00Z"
}
```

`expiresAt: null` indica una carta permanente. Un Wanderer ha quantità uno; la scadenza e l'archivio sono gestiti dal server, non modificabili tramite API.

## Mazzi

| Metodo | Rotta | Auth | Esito |
|---|---|---|---|
| GET | `/users/me/decks` | sì | mazzi del giocatore |
| POST | `/users/me/decks` | sì | crea mazzo, 201 |
| GET | `/users/me/decks/{deckId}` | sì | dettaglio |
| PUT | `/users/me/decks/{deckId}` | sì | sostituisce nome e lista carte |
| DELETE | `/users/me/decks/{deckId}` | sì | 204 |
| PUT | `/users/me/decks/{deckId}/defense` | sì | imposta come mazzo difensivo |
| DELETE | `/users/me/decks/{deckId}/defense` | sì | rimuove la scelta difensiva, se consentito |
| POST | `/users/me/decks/validate` | sì | valida una composizione senza salvarla |

Creazione/sostituzione:

```json
{
  "name": "Solar control",
  "cards": [{ "cardId": 42, "quantity": 2 }, { "cardId": 51, "quantity": 3 }]
}
```

Risposta mazzo: `id`, `name`, `status` (`INCOMPLETE`, `VALID`, `INVALID`), `cards` con carta e quantità, `totalCards`, `isDefenseDeck`, `updatedAt`, `validationErrors`.

Vincoli dal contesto: mazzo valido di 30 carte, limiti di copie per rarità/carta, massimo 15 carte per fazione; quantità non superiori al posseduto; applicare i limiti vigenti anche dopo scambi e scadenza Wanderer. I numeri/regole che non risultano fissati vanno confermati.

Errori specifici proposti: `DECK_NOT_FOUND` 404, `DECK_INVALID` 422, `CARD_NOT_OWNED` 409, `DECK_NAME_ALREADY_USED` 409, `DEFENSE_DECK_INVALID` 409, `CARD_NOT_FOUND` 404. Le risposte di validazione dovrebbero riportare errori per carta/campo, non solo una stringa.

## Da decidere

- Se catalogo e profilo delle carte includono carte inattive per amministratori (fuori da questo contratto giocatore).
- Ordinamento, filtri, limite massimo di mazzi e lunghezza del nome.
- Se il salvataggio permette mazzi incompleti/non validi (lo schema prevede uno stato, quindi sì; solo i mazzi validi si usano in partita).
- Se cancellare il mazzo difensivo lascia il giocatore senza difesa o richiede prima di sceglierne un altro.

