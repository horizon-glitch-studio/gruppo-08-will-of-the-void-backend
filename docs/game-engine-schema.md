# Game engine schema

How the server simulates a match. The rules are in [match.md](https://github.com/horizon-glitch-studio/gruppo-08-will-of-the-void-docs/blob/main/rules/match.md); rule IDs (`MATCH-04`) and parameter names (`WIN_THRESHOLD_RANKED`) are the same as in the rules.

## 1. High-level architecture

```mermaid
flowchart LR
    subgraph Input["Match input"]
        I["Deck A<br/>Deck B<br/>Seed (PRNG)<br/>GameConfig"]
    end
    subgraph Engine["Game engine (server)"]
        E["Synchronous game loop<br/>- Shuffle decks (MATCH-02)<br/>- Apply faction advantages (FAC-02)<br/>- Resolve rounds (MATCH-04)<br/>- Award Energy (MATCH-08)"]
        S["Game state<br/>- Summoner A (Energy)<br/>- Summoner B (Energy)<br/>- Cards in the Arena and decks"]
    end
    subgraph Output["Output / client"]
        O["JSON event log<br/>(replay, MATCH-18)"]
    end
    I --> E --> O
    E --> S
```

## 2. Card lifecycle

```mermaid
stateDiagram-v2
    [*] --> Deck
    Deck --> Arena: Deploy (MATCH-03, MATCH-11)
    Arena --> CheckDef: Takes damage (round, MATCH-04)
    state CheckDef <<choice>>
    CheckDef --> Arena: DEF > 0, stays with residual DEF (MATCH-10)
    CheckDef --> Defeated: DEF <= 0 (MATCH-06)
    Defeated --> [*]
```

A defeated card generates Energy only if it is the only one defeated and its DEF is below 0 (MATCH-08, MATCH-09).

## 3. Friend and Quick match flow

```mermaid
flowchart TD
    Start["FRIEND / QUICK MATCH START<br/>- Both Summoners at 0 Energy (MATCH-01)<br/>- Shuffle decks with the seed (MATCH-02)<br/>- Deploy 1st card for A and B (MATCH-03)"]
    Clash["CLASH<br/>Card A vs card B in the Arena"]
    Round["ROUND (MATCH-04)<br/>- Effective ATK = ATK + 3 if the faction prevails (FAC-02)<br/>- Both hit at the same time<br/>- Log ROUND_EXECUTED"]
    Dead{"Is at least one card's<br/>DEF <= 0? (MATCH-06)"}
    Resolution["CLASH RESOLUTION"]
    Both["BOTH DEFEATED (MATCH-09)<br/>- No Energy<br/>- Both deploy the next card"]
    One["ONLY ONE DEFEATED (MATCH-08)<br/>- Survivor keeps its residual DEF (MATCH-10)<br/>- The survivor's Summoner gains Energy = DEF below 0 of the defeated card<br/>- DEF exactly 0: no Energy"]
    Threshold{"Has a Summoner reached<br/>the mode win threshold? (MATCH-12)"}
    EnergyWin["WIN BY THRESHOLD<br/>(match over)"]
    Exhausted{"Does a Summoner have to deploy<br/>with no cards left? (MATCH-13)"}
    Deploy["DEPLOY NEXT CARD<br/>for each defeated card (MATCH-11)"]
    DeckEnd["DECK EXHAUSTION (MATCH-14)<br/>- +4 Energy (END_BONUS_PER_CARD) [TEST] per card left, Arena included<br/>- More Energy wins"]
    Resolver["FRIEND / QUICK RESOLVER<br/>- Trophies unchanged (MODE-02)"]
    WinA["WINNER: A"]
    WinB["WINNER: B"]
    Draw["DRAW (MATCH-15)"]
    Over["MATCH OVER<br/>- Save match and log (MATCH-18)<br/>- Return event log and outcome"]

    Start --> Clash --> Round --> Dead
    Dead -- NO --> Round
    Dead -- YES --> Resolution
    Resolution --> Both
    Resolution --> One
    Both --> Threshold
    One --> Threshold
    Threshold -- YES --> EnergyWin
    Threshold -- NO --> Exhausted
    Exhausted -- NO --> Deploy --> Clash
    Exhausted -- YES --> DeckEnd
    EnergyWin --> Resolver
    DeckEnd --> Resolver
    Resolver --> WinA
    Resolver --> WinB
    Resolver --> Draw
    WinA --> Over
    WinB --> Over
    Draw --> Over
```

## 4. Ranked match flow

```mermaid
flowchart TD
    Start["RANKED MATCH START<br/>- Fetch Trophies of A and B<br/>- Both Summoners at 0 Energy (MATCH-01)<br/>- Shuffle decks with the seed (MATCH-02)<br/>- Deploy 1st card for A and B (MATCH-03)"]
    Clash["CLASH<br/>Card A vs card B in the Arena"]
    Round["ROUND (MATCH-04)<br/>- Effective ATK = ATK + 3 if the faction prevails (FAC-02)<br/>- Both hit at the same time<br/>- Log ROUND_EXECUTED"]
    Dead{"Is at least one card's<br/>DEF <= 0? (MATCH-06)"}
    Resolution["CLASH RESOLUTION"]
    Both["BOTH DEFEATED (MATCH-09)<br/>- No Energy<br/>- Both deploy the next card"]
    One["ONLY ONE DEFEATED (MATCH-08)<br/>- Survivor keeps its residual DEF (MATCH-10)<br/>- The survivor's Summoner gains Energy = DEF below 0 of the defeated card<br/>- DEF exactly 0: no Energy"]
    Threshold{"Has a Summoner reached<br/>the mode win threshold? (MATCH-12)"}
    EnergyWin["WIN BY THRESHOLD<br/>(match over)"]
    Exhausted{"Does a Summoner have to deploy<br/>with no cards left? (MATCH-13)"}
    Deploy["DEPLOY NEXT CARD<br/>for each defeated card (MATCH-11)"]
    DeckEnd["DECK EXHAUSTION (MATCH-14)<br/>- +4 Energy (END_BONUS_PER_CARD) [TEST] per card left, Arena included<br/>- More Energy wins"]
    Resolver["RANKED RESOLVER (RANK-04)<br/>- Expected A = 1 / (1 + 10^((Trophies B - Trophies A) / 400))<br/>- Result A = 1 win, 0.5 draw, 0 defeat<br/>- Delta A = round(32 (ELO_K) x (result A - expected A))<br/>- Delta B = - Delta A"]
    WinA["WINNER: A"]
    WinB["WINNER: B"]
    Draw["DRAW (MATCH-15)"]
    Floor["TROPHY FLOOR (RANK-05)<br/>- New Trophies = max(0, Trophies + Delta)<br/>- The winner gains the full Delta anyway"]
    Over["MATCH OVER (RANKED)<br/>- Save match and log (MATCH-18)<br/>- Update leaderboard and Orbit (RANK-10)<br/>- Return final Trophies and actual Delta"]

    Start --> Clash --> Round --> Dead
    Dead -- NO --> Round
    Dead -- YES --> Resolution
    Resolution --> Both
    Resolution --> One
    Both --> Threshold
    One --> Threshold
    Threshold -- YES --> EnergyWin
    Threshold -- NO --> Exhausted
    Exhausted -- NO --> Deploy --> Clash
    Exhausted -- YES --> DeckEnd
    EnergyWin --> Resolver
    DeckEnd --> Resolver
    Resolver --> WinA
    Resolver --> WinB
    Resolver --> Draw
    WinA --> Floor
    WinB --> Floor
    Draw --> Floor
    Floor --> Over
```

## Notes

- Friend and Quick matches give no rewards and do not change Trophies (MODE-02).
- Win threshold per mode in GameConfig (MATCH-12): Ranked 30 (`WIN_THRESHOLD_RANKED`), Friend 30 (`WIN_THRESHOLD_FRIEND`), Quick 20 (`WIN_THRESHOLD_QUICK`) [TEST].
- Going past the win threshold with the end bonus does not count as a win by threshold (MATCH-16).
- No turn limit (MATCH-17): every clash defeats at least one card, so a match lasts at most 59 clashes.
- Ranked draw: result 0.5 for both. The Summoner with more Trophies loses some, the one with fewer gains some; the change is 0 only when Trophies are equal (RANK-03, RANK-04).
- All values come from [parameters.md](https://github.com/horizon-glitch-studio/gruppo-08-will-of-the-void-docs/blob/main/rules/parameters.md).
