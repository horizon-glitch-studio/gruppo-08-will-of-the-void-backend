# Game engine schema

## 1. High-level architecture

```mermaid
flowchart LR
    subgraph Input["Match input"]
        I["Deck A<br/>Deck B<br/>Seed (PRNG)<br/>GameConfig"]
    end
    subgraph Engine["Game engine (server)"]
        E["Synchronous game loop<br/>- Apply factions<br/>- Resolve rounds<br/>- Award energy"]
        S["Game state<br/>- Player A (Energy)<br/>- Player B (Energy)<br/>- Active cards & decks"]
    end
    subgraph Output["Output / client"]
        O["JSON event log<br/>(client replay)"]
    end
    I --> E --> O
    E --> S
```

## 2. Card lifecycle

```mermaid
stateDiagram-v2
    [*] --> Deck
    Deck --> OnBoard: Draw & deploy
    OnBoard --> CheckDef: Takes damage (round)
    state CheckDef <<choice>>
    CheckDef --> OnBoard: DEF > 0 (residual DEF)
    CheckDef --> Defeated: DEF <= 0
    Defeated --> Graveyard: Generates energy
    Graveyard --> [*]

    OnBoard: On board
```

## 3. Casual match flow (non-ranked)

```mermaid
flowchart TD
    Start["CASUAL MATCH START<br/>- Initialize seed & decks<br/>- Player A & B trophies unchanged<br/>- Deploy 1st card for A & B"]
    Clash["ACTIVE CLASH<br/>Card A on board vs card B"]
    Round["ROUND CALCULATION<br/>- Apply modified ATK simultaneously to DEF<br/>- Log ROUND_EXECUTED"]
    Dead{"Is at least one card's<br/>DEF <= 0?"}
    Resolution["CLASH RESOLUTION"]
    Both["BOTH DEAD<br/>- No energy awarded<br/>- Both discard cards"]
    One["ONLY ONE DEAD<br/>- Winner retains DEF<br/>- Loser converts negative DEF into winner energy<br/>- Loser discards card"]
    Threshold{"Has any player reached<br/>>= mode win threshold?"}
    EnergyWin["ENERGY WIN<br/>(match over)"]
    Deploy["DEPLOY NEW CARDS<br/>- Replace defeated cards"]
    Exhausted{"Is a deck exhausted with no<br/>cards available to deploy?"}
    DeckWin["DECK EXHAUSTION WIN<br/>- Award bonus per surviving card (+? energy)<br/>- Highest energy wins"]
    Resolver["CASUAL MATCH RESOLVER<br/>- Trophy delta A: +0<br/>- Trophy delta B: +0"]
    WinA["WINNER: A"]
    WinB["WINNER: B"]
    Draw["DRAW"]
    Over["MATCH OVER (CASUAL)<br/>- Retain current trophies<br/>- Return event log & outcome"]

    Start --> Clash --> Round --> Dead
    Dead -- NO --> Round
    Dead -- YES --> Resolution
    Resolution --> Both
    Resolution --> One
    Both --> Threshold
    One --> Threshold
    Threshold -- YES --> EnergyWin
    Threshold -- NO --> Deploy --> Exhausted
    Exhausted -- NO --> Clash
    Exhausted -- YES --> DeckWin
    EnergyWin --> Resolver
    DeckWin --> Resolver
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
    Start["RANKED MATCH START<br/>- Fetch player A & B trophies<br/>- Initialize seed & decks<br/>- Deploy 1st card for A & B"]
    Clash["ACTIVE CLASH<br/>Card A on board vs card B"]
    Round["ROUND CALCULATION<br/>- Apply modified ATK simultaneously to DEF<br/>- Log ROUND_EXECUTED"]
    Dead{"Is at least one card's<br/>DEF <= 0?"}
    Resolution["CLASH RESOLUTION"]
    Both["BOTH DEAD<br/>- No energy awarded<br/>- Both discard cards"]
    One["ONLY ONE DEAD<br/>- Winner retains DEF<br/>- Loser converts negative DEF into winner energy<br/>- Loser discards card"]
    Threshold{"Has any player reached<br/>>= mode win threshold?"}
    EnergyWin["ENERGY WIN<br/>(match over)"]
    Deploy["DEPLOY NEW CARDS<br/>- Replace defeated cards"]
    Exhausted{"Is a deck exhausted with no<br/>cards available to deploy?"}
    DeckWin["DECK EXHAUSTION WIN<br/>- Award bonus per surviving card (+? energy)<br/>- Highest energy wins"]
    Resolver["RANKED MATCH RESOLVER"]
    WinA["WINNER: A<br/>- Raw delta A: +?<br/>- Raw delta B: -?"]
    WinB["WINNER: B<br/>- Raw delta A: -?<br/>- Raw delta B: +?"]
    Draw["DRAW<br/>- Raw delta A: ±?<br/>- Raw delta B: ±?"]
    Floor["TROPHY FLOOR GUARD<br/>- New trophies A = max(0, A + ΔA)<br/>- New trophies B = max(0, B + ΔB)<br/>- Actual Δ = new trophies - current"]
    Over["MATCH OVER (RANKED)<br/>- Save match history to DB<br/>- Update leaderboard & orbit<br/>- Return final trophies & actual Δ"]

    Start --> Clash --> Round --> Dead
    Dead -- NO --> Round
    Dead -- YES --> Resolution
    Resolution --> Both
    Resolution --> One
    Both --> Threshold
    One --> Threshold
    Threshold -- YES --> EnergyWin
    Threshold -- NO --> Deploy --> Exhausted
    Exhausted -- NO --> Clash
    Exhausted -- YES --> DeckWin
    EnergyWin --> Resolver
    DeckWin --> Resolver
    Resolver --> WinA
    Resolver --> WinB
    Resolver --> Draw
    WinA --> Floor
    WinB --> Floor
    Draw --> Floor
    Floor --> Over
```

## Notes

- Casual matches (Friend, Quick) give no rewards and do not change trophies.
- Win threshold is set per mode in GameConfig: Ranked 30, Friend 30, Quick 20? (to be tested).
- Ranked draw: Elo with score 0.5 for both. The player with more trophies loses some, the player with fewer gains some; the delta is 0 only when trophies are equal.