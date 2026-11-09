# Contratto API: battaglie e matchmaking

Prefisso `/api/v1`. Basato su `battle`, `battle_log`, `opponent_list` e `opponent_list_entry`.

## Avversari QUICK/RANKED

| Metodo | Rotta | Auth | Esito |
|---|---|---|---|
| GET | `/matchmaking/opponents?mode=QUICK` | sì | lista corrente (5 voci) |
| POST | `/matchmaking/opponents/refresh` | sì | rigenera la lista se il cooldown lo consente |
| POST | `/matchmaking/opponents/{opponentId}/battle` | sì | avvia QUICK o RANKED contro una voce non ancora attaccata |

Per l'avvio, il body specifica `mode` e `deckId`. La lista è persistita: refresh pagina non la cambia; un avversario nella lista può essere attaccato una sola volta. `trophiesAtGeneration` è solo informativo, la risoluzione usa trofei correnti. RANKED aggiorna i trofei secondo la regola Elo ancora da definire; QUICK non assegna trofei.

## Sfide FRIEND

| Metodo | Rotta | Auth | Esito |
|---|---|---|---|
| POST | `/battles/friend` | sì | invia sfida a un amico online, 201 `PENDING` |
| GET | `/battles/{battleId}` | sì | stato/dettaglio per una delle parti |
| POST | `/battles/{battleId}/accept` | sì | accetta e seleziona mazzo difensivo, |
| POST | `/battles/{battleId}/reject` | sì | rifiuta |
| POST | `/battles/{battleId}/cancel` | sì | annulla il challenger prima dell'accettazione |

Challenge body proposto: `{ "opponentUsername": "star_hunter", "deckId": 12 }`. La sfida scade dopo 30 secondi secondo il contesto; definire se accettazione e avvio partita avvengono via REST o WebSocket.

## Partita, risultati e storico

| Metodo | Rotta | Auth | Esito |
|---|---|---|---|
| POST | `/battles/{battleId}/actions` | sì | invia azione di gioco; schema `BattleActionRequest` da definire |
| GET | `/battles/{battleId}/result` | sì | risultato finale e delta energia/trofei |
| GET | `/users/me/battles` | sì | cronologia paginata; filtri proposti `mode`, `outcome`, date |
| GET | `/battles/{battleId}/replay` | sì | snapshot dei mazzi e log turni, se autorizzato |

Il client non invia seed, outcome, energia finale o delta trofei: sono autoritativi lato server. La partita parte da energia zero; vittoria al raggiungimento di 30. Per RANKED il risultato persistito contiene i delta dei due giocatori. La struttura dei turni e delle azioni dipende dal motore di gioco e va concordata prima di congelare il contratto.

Errori specifici proposti: `BATTLE_NOT_FOUND` 404, `BATTLE_FORBIDDEN` 403, `BATTLE_NOT_PENDING` 409, `BATTLE_EXPIRED` 409, `OPPONENT_NOT_IN_LIST` 409, `OPPONENT_ALREADY_ATTACKED` 409, `MATCHMAKING_COOLDOWN` 429, `DECK_INVALID` 422, `OPPONENT_NOT_ONLINE` 409, `INVALID_BATTLE_ACTION` 400.

## Da decidere

- Range trofei matchmaking e algoritmo di scelta/refresh/cooldown.
- Regole Elo, pareggio e protezione temporanea (`protectedUntil`).
- Forma delle azioni, autorità del server, riconnessione e idempotenza.
- Accesso e retention del replay; visibilità dei log durante la partita.
- Trasporto real-time: WebSocket/STOMP o alternativa e relativi eventi.

