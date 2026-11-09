# Contratto API: scambi

Prefisso `/api/v1`. Basato su `trade` e `trade_card`. Uno scambio ha proponente e destinatario, stato `PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED`, `EXPIRED` o `FAILED`; durata prevista 48 ore.

## Rotte

| Metodo | Rotta | Auth | Esito |
|---|---|---|---|
| GET | `/trades` | sì | scambi dell'utente; filtri proposti `status`, `direction` (`incoming`/`outgoing`) |
| POST | `/trades` | sì | propone uno scambio, 201 |
| GET | `/trades/{tradeId}` | sì | dettaglio se l'utente è una delle parti |
| POST | `/trades/{tradeId}/accept` | sì | accetta come destinatario, 200 |
| POST | `/trades/{tradeId}/reject` | sì | rifiuta come destinatario, 200 |
| POST | `/trades/{tradeId}/cancel` | sì | annulla come proponente, 200 |

Creazione:

```json
{
  "recipientUsername": "star_hunter",
  "offeredCards": [{ "cardId": 42, "quantity": 1 }],
  "requestedCards": [{ "cardId": 51, "quantity": 2 }]
}
```

Proposta di risposta: trade con `id`, `status`, `proposer`, `recipient`, `offeredCards`, `requestedCards`, `createdAt`, `expiresAt`, `closedAt`. L'accettazione trasferisce le carte atomicamente e ricalcola lo stato dei mazzi di entrambi. Il server ricontrolla possesso e quantità all'accettazione con lock; carte non scambiabili (Wanderer) non sono ammesse. Il destinatario non può accettare dopo la scadenza.

Errori specifici proposti: `TRADE_NOT_FOUND` 404, `TRADE_FORBIDDEN` 403, `TRADE_NOT_PENDING` 409, `TRADE_EXPIRED` 409, `TRADE_CARD_NOT_TRADEABLE` 422, `TRADE_INVALID_CARDS` 400, `TRADE_NOT_ALLOWED` 409, `TRADE_FAILED` 409. Se al momento dell'accettazione le carte offerte non sono più disponibili, lo scambio termina `FAILED`; definire se la rotta restituisce 409 con lo stato aggiornato.

## Da decidere

- Quantità minima/massima di righe e carte per proposta, auto-scambio vietato.
- Se il proponente deve possedere le carte già alla creazione oppure solo all'accettazione (schema e contesto descrivono il controllo all'accettazione).
- Politica per richieste multiple di carte duplicate e destinatari non esistenti.
- Filtri e paginazione; notifiche generate dai cambi di stato.

