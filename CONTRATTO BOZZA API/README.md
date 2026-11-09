# Contratto bozza API — indice

Bozza derivata dal contesto funzionale e dallo schema PostgreSQL in `CONTESTO HORIZON/database/schema.sql`. Non è una descrizione delle rotte attualmente implementate. Le decisioni confermate sono espresse direttamente; i punti ancora aperti sono elencati nelle sezioni finali.

## Convenzioni comuni

- Base URL: `/api/v1`.
- JSON in ingresso e uscita (`application/json`); nomi `camelCase`; date UTC ISO 8601.
- Rotte che agiscono sul giocatore autenticato usano `Authorization: Bearer <accessToken>`.
- Gli errori seguono il formato e i codici comuni definiti in [01-auth-profilo.md](01-auth-profilo.md#0-convenzioni); le aree aggiungono i codici specifici descritti nei rispettivi file.
- Gli ID sono interi (`bigint` nel database); il client li tratta come stringhe se il runtime non rappresenta interi grandi senza perdita.
- Elenchi paginati: `page` (0-based), `size` (default 20, massimo 100), risposta `{ "items": [], "page": 0, "size": 20, "totalItems": 0, "totalPages": 0 }`.

## Contratti per area

1. [Autenticazione e profilo](01-auth-profilo.md)
2. [Catalogo, collezione e mazzi](02-carte-collezione-mazzi.md)
3. [Pacchetti e crediti](03-pacchetti-crediti.md)
4. [Scambi](04-scambi.md)
5. [Battaglie e matchmaking](05-battaglie-matchmaking.md)
6. [Amicizie, notifiche e presenza](06-social-notifiche.md)

## Decisioni trasversali aperte

- Il prefisso `/api/v1` è comune a tutte le aree.
- Errori applicativi hanno codici stabili e non espongono dettagli interni.
- Definire formato e limiti di paginazione, ordinamento e filtri.
- Il rate limiting è rinviato alla fase 2.
- Decidere quali endpoint richiedono email verificata e quali sono pubblici.
- Definire l'API amministrativa separatamente: gestione catalogo, rarità, pacchetti e mazzo iniziale non è inclusa in questa bozza giocatore.
- La presenza online e le sfide in tempo reale richiedono un contratto WebSocket separato; il documento social ne indica gli eventi da concordare.


