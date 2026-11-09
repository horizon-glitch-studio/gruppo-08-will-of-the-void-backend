# Decisioni aperte — Contratto bozza API

Questo documento raccoglie i punti ancora da decidere emersi nei contratti per area. Le scelte già confermate non sono ripetute qui. Quando una decisione viene presa, aggiornare sia questo elenco sia il contratto dell'area corrispondente.

## Autenticazione e profilo

Riferimento: [01-auth-profilo.md](01-auth-profilo.md)

-  **Verifica email:** il login è consentito anche prima della verifica. Quali funzioni devono restare bloccate fino alla verifica (per esempio Ranked o acquisto pacchetti)?
-  **Avatar:** definire il set/versione degli avatar predefiniti e aggiungere il campo `avatar` allo schema tramite migrazione.
-  **Leghe:** definire nomi e soglie; il livello è calcolato dai trofei.
-  **Rate limiting — fase 2:** definire soglie e implementazione. Non includere `429 TOO_MANY_REQUESTS` nel contratto v1 delle rotte auth.

## Catalogo, collezione e mazzi

Riferimento: [02-carte-collezione-mazzi.md](02-carte-collezione-mazzi.md)

-  **Carte inattive:** Decidere se gli endpoint del catalogo accessibili ai giocatori devono mostrare solo carte attive e come gli amministratori possono consultare le carte disattivate.
-  **Ricerca e liste:** definire ordinamento, filtri e paginazione per catalogo, collezione e mazzi.
-  **Limiti dei mazzi:** definire il numero massimo di mazzi per giocatore e la lunghezza ammessa per il nome.
-  **Salvataggio mazzi non validi:** confermare il salvataggio di mazzi incompleti/non validi; il modello prevede gli stati `INCOMPLETE`, `VALID` e `INVALID`. Solo i mazzi validi possono essere usati in partita.
-  **Mazzo difensivo:** decidere se si può eliminare il mazzo difensivo senza sceglierne prima un altro.

## Pacchetti e crediti

Riferimento: [03-pacchetti-crediti.md](03-pacchetti-crediti.md)

-  **Probabilità:** decidere se esporle al giocatore e come rappresentare gli upgrade di rarità.
-  **Offerta dei pacchetti:** definire quantità, disponibilità, finestre evento ed eventuali limiti di acquisto.
-  **Storico:** definire formato, ordinamento e retention delle aperture e dei movimenti di credito.
-  **Bonus giornaliero:** il bonus viene assegnato al login. Decidere se aggiungere anche una rotta per il claim manuale.

## Scambi

Riferimento: [04-scambi.md](04-scambi.md)

-  **Composizione:** definire quantità minima e massima di carte/righe per scambio e confermare il divieto di scambiare con sé stessi.
-  **Possesso delle carte:** il contesto prevede il controllo all'accettazione. Confermare se mantenere questo comportamento o richiedere il possesso già alla proposta.
-  **Input duplicati e destinatario:** definire il comportamento per carte ripetute nella richiesta e username del destinatario inesistente.
-  **Liste e notifiche:** definire filtri/paginazione e quali transizioni di stato generano notifiche.

## Battaglie e matchmaking

Riferimento: [05-battaglie-matchmaking.md](05-battaglie-matchmaking.md)

-  **Matchmaking:** definire intervallo di trofei e algoritmo di selezione, aggiornamento e cooldown della lista.
-  **Classifica:** definire formula Elo, gestione dei pareggi e durata/regole della protezione (`protectedUntil`).
-  **Azioni di gioco:** definire schema delle azioni, autorità del server, riconnessione e idempotenza.
-  **Replay:** definire chi può accedervi, per quanto tempo conservarlo e quando rendere visibili i log.
-  **Tempo reale:** scegliere WebSocket/STOMP o alternativa e definire eventi e payload.

## Amicizie, notifiche e presenza

Riferimento: [06-social-notifiche.md](06-social-notifiche.md)

-  **Richieste amicizia:** decidere se si possono inviare a utenti offline e stabilire un eventuale limite di amici.
-  **Accettazione:** chiarire se accettare una richiesta avvia anche una sfida o crea soltanto l'amicizia.
-  **Presenza:** definire visibilità (per esempio solo agli amici), heartbeat e timeout per dichiarare un utente offline.
-  **Notifiche:** definire retention, ordinamento, limiti e quali eventi sono push oltre a essere persistiti.
-  **Rimozione relazione:** stabilire il comportamento di `DELETE /friends/{friendshipId}` per amicizia accettata e richiesta inviata; le richieste ricevute si rifiutano tramite l'endpoint dedicato.