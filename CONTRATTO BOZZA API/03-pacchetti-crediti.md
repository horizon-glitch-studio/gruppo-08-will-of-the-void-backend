# Contratto API: pacchetti e crediti

Prefisso `/api/v1`. Basato su `pack_type`, `pack_slot`, `pack_opening`, `pack_opening_card`, `credit_transaction`.

## Rotte

| Metodo | Rotta | Auth | Esito |
|---|---|---|---|
| GET | `/packs` | sì | pacchetti acquistabili in questo momento |
| GET | `/packs/{packTypeId}` | sì | dettaglio e costo |
| POST | `/packs/{packTypeId}/open` | sì | acquista e apre un pacchetto in una singola operazione atomica, 201 |
| GET | `/users/me/pack-openings` | sì | storico paginato delle aperture |
| GET | `/users/me/pack-openings/{openingId}` | sì | dettaglio delle carte ottenute |
| GET | `/users/me/credit-transactions` | sì | storico paginato dei movimenti |

L'elenco pubblico al giocatore non espone configurazioni interne degli slot o le probabilità dettagliate, a meno che il team non decida di renderle visibili. Per trasparenza, includere nel pack un `odds` esplicito calcolato dalle regole degli slot.

Esempio apertura:

```json
{
  "id": 901,
  "pack": { "id": 3, "name": "Event: Eclipse" },
  "creditsSpent": 100,
  "creditBalance": 420,
  "cards": [
    { "card": { "id": 42, "name": "Aster" }, "quantity": 1, "expiresAt": null, "isNew": true }
  ],
  "createdAt": "2026-11-08T16:00:00Z"
}
```

`POST .../open` non accetta carte o rarità dal client: il server verifica disponibilità temporale, addebita crediti e sorteggia le carte in transazione. Se un Wanderer già posseduto viene trovato di nuovo, il server applica la regola di estensione della permanenza prevista dal gioco.

Tipi movimento crediti: `REGISTRATION_BONUS`, `DAILY_BONUS`, `INVITE_BONUS`, `PACK_PURCHASE`. Il saldo va restituito anche nel profilo e aggiornato atomicamente; il client non invia mai un saldo da salvare.

Errori specifici proposti: `PACK_NOT_FOUND` 404, `PACK_NOT_AVAILABLE` 409, `INSUFFICIENT_CREDITS` 409 (includere `creditBalance` e `creditCost`), `PACK_OPENING_FAILED` 500. La stessa richiesta ripetuta potrebbe addebitare due volte: supportare `Idempotency-Key` sugli acquisti.

## Da decidere

- Se le probabilità sono pubbliche e come rappresentare gli upgrade di rarità.
- Numero e disponibilità dei pack, limite di acquisti, eventuali finestre evento.
- Formato e retention dello storico.
- Bonus giornaliero: il contratto auth lo assegna al login; decidere se prevedere anche una rotta dedicata per il claim manuale.

