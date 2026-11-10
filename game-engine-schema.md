1. High-Level Architecture
 [ Match Input ]               [ Game Engine (Server) ]               [ Output / Client ]
+-----------------+           +--------------------------+           +------------------+
| - Deck A        |           |  Synchronous Game Loop   |           |                  |
| - Deck B        |  -------> |  - Apply Factions        |  -------> |  JSON Event Log  |
| - Seed (PRNG)   |           |  - Resolve Rounds        |           |  (Client Replay) |
| - GameConfig    |           |  - Award Energy          |           |                  |
+-----------------+           +--------------------------+           +------------------+
                                           |
                                           v
                              +--------------------------+
                              |        Game State        |
                              |  - Player A (Energy)     |
                              |  - Player B (Energy)     |
                              |  - Active Cards & Decks  |
                              +--------------------------+

2. Card State Diagram (Lifecycle)
 [ Deck ] ---> ( Draw & Deploy ) ---> [ On Board ]
                                           |
                                           | <-- Takes Damage (Round)
                                           v
                                   /---------------\
                                  /    DEF <= 0?    \
                                  \-----------------/
                                     /           \
                                NO  /             \ YES
                                   /               \
                                  v                 v
                         [ Remains On Board ]  [ DEFEATED ] ---> Generates Energy
                         (Residual DEF)             |
                                                    v
                                              [ Graveyard ]

3. Casual Match Flow Diagram (Non-Ranked)
                       +----------------------------------+
                       |        CASUAL MATCH START        |
                       | - Initialize Seed & Decks        |
                       | - Player A & B Trophies Unchanged|
                       | - Deploy 1st Card for A & B      |
                       +----------------------------------+
                                        |
                                        v
                       +----------------------------------+
                       |           ACTIVE CLASH           |
                       | Card A on board vs Card B        |
                       +----------------------------------+
                                        |
                                        v
                       +----------------------------------+
                       |          ROUND CALCULATION       |
                       | - Apply modified ATK             |
                       |   simultaneously to DEF          |
                       | - Log ROUND_EXECUTED             |
                       +----------------------------------+
                                        |
                                        v
                          /-----------------------------\
                         /   Is at least one card's      \
                         \        DEF <= 0?              /
                          \-----------------------------/
                                /                \
                          NO   /                  \ YES
                              /                    \
                             v                      v
                [ Continue Round ]         +----------------------------------+
                                           |         CLASH RESOLUTION         |
                                           +----------------------------------+
                                                            |
                             +------------------------------+------------------------------+
                             |                                                             |
                             v                                                             v
               +---------------------------+                                 +---------------------------+
               |        BOTH DEAD          |                                 |       ONLY ONE DEAD       |
               | - No energy awarded       |                                 | - Winner retains DEF      |
               | - Both discard cards      |                                 | - Loser converts negative |
               +---------------------------+                                 |   DEF into Winner Energy  |
                             |                                               | - Loser discards card     |
                             |                                               +---------------------------+
                             |                                                             |
                             +------------------------------+------------------------------+
                                                            |
                                                            v
                                            /-------------------------------\
                                           /    Has any player reached       \
                                           \      >= Mode Win Threshold?     /
                                            \-------------------------------/
                                                   /                 \
                                             YES  /                   \ NO
                                                 /                     \
                                                v                       v
                                  +-------------------+   +---------------------------+
                                  |    ENERGY WIN     |   |    DEPLOY NEW CARDS       |
                                  |   (Match Over)    |   | - Replace defeated cards  |
                                  +-------------------+   +---------------------------+
                                            |                           |
                                            |                           v
                                            |           /-------------------------------\
                                            |          /   Is a deck exhausted with no   \
                                            |          \   cards available to deploy?    /
                                            |           \-------------------------------/
                                            |                  /                 \
                                            |            YES  /                   \ NO
                                            |                /                     \
                                            |               v                       v
                                            | +---------------------------+  [ Return to ACTIVE CLASH ]
                                            | |  DECK EXHAUSTION WIN      |
                                            | | - Award Bonus per         |
                                            | |   surviving card (+? Eng) |
                                            | | - Highest Energy wins     |
                                            | +---------------------------+
                                            |               |
                                            +-------+-------+
                                                    |
                                                    v
                                  +-----------------------------------+
                                  |       CASUAL MATCH RESOLVER       |
                                  | - Trophy Delta A: +0              |
                                  | - Trophy Delta B: +0              |
                                  +-----------------------------------+
                                                    |
                                    +---------------+---------------+
                                    |               |               |
                                    v               v               v
                              [ WINNER: A ]   [ WINNER: B ]     [ DRAW ]
                                    |               |               |
                                    +---------------+---------------+
                                                    |
                                                    v
                                  +-----------------------------------+
                                  |        MATCH OVER (CASUAL)        |
                                  | - Retain current Trophies         |
                                  | - Return Event Log & Outcome      |
                                  +-----------------------------------+


4. Ranked Match Flow Diagram (Classified)
                       +----------------------------------+
                       |        RANKED MATCH START        |
                       | - Fetch Player A & B Trophies    |
                       | - Initialize Seed & Decks        |
                       | - Deploy 1st Card for A & B      |
                       +----------------------------------+
                                        |
                                        v
                       +----------------------------------+
                       |           ACTIVE CLASH           |
                       | Card A on board vs Card B        |
                       +----------------------------------+
                                        |
                                        v
                       +----------------------------------+
                       |          ROUND CALCULATION       |
                       | - Apply modified ATK             |
                       |   simultaneously to DEF          |
                       | - Log ROUND_EXECUTED             |
                       +----------------------------------+
                                        |
                                        v
                          /-----------------------------\
                         /   Is at least one card's      \
                         \        DEF <= 0?              /
                          \-----------------------------/
                                /                \
                          NO   /                  \ YES
                              /                    \
                             v                      v
                [ Continue Round ]         +----------------------------------+
                                           |         CLASH RESOLUTION         |
                                           +----------------------------------+
                                                            |
                             +------------------------------+------------------------------+
                             |                                                             |
                             v                                                             v
               +---------------------------+                                 +---------------------------+
               |        BOTH DEAD          |                                 |       ONLY ONE DEAD       |
               | - No energy awarded       |                                 | - Winner retains DEF      |
               | - Both discard cards      |                                 | - Loser converts negative |
               +---------------------------+                                 |   DEF into Winner Energy  |
                             |                                               | - Loser discards card     |
                             |                                               +---------------------------+
                             |                                                             |
                             +------------------------------+------------------------------+
                                                            |
                                                            v
                                            /-------------------------------\
                                           /    Has any player reached       \
                                           \      >= Mode Win Threshold?     /
                                            \-------------------------------/
                                                   /                 \
                                             YES  /                   \ NO
                                                 /                     \
                                                v                       v
                                  +-------------------+   +---------------------------+
                                  |    ENERGY WIN     |   |    DEPLOY NEW CARDS       |
                                  |   (Match Over)    |   | - Replace defeated cards  |
                                  +-------------------+   +---------------------------+
                                            |                           |
                                            |                           v
                                            |           /-------------------------------\
                                            |          /   Is a deck exhausted with no   \
                                            |          \   cards available to deploy?    /
                                            |           \-------------------------------/
                                            |                  /                 \
                                            |            YES  /                   \ NO
                                            |                /                     \
                                            |               v                       v
                                            | +---------------------------+  [ Return to ACTIVE CLASH ]
                                            | |  DECK EXHAUSTION WIN      |
                                            | | - Award Bonus per         |
                                            | |   surviving card (+? Eng) |
                                            | | - Highest Energy wins     |
                                            | +---------------------------+
                                            |               |
                                            +-------+-------+
                                                    |
                                                    v
                                  +-----------------------------------+
                                  |       RANKED MATCH RESOLVER       |
                                  +-----------------------------------+
                                                    |
                             +----------------------+----------------------+
                             |                      |                      |
                             v                      v                      v
                       [ WINNER: A ]          [ WINNER: B ]             [ DRAW ]
                       - Raw Delta A: +?      - Raw Delta A: -?      - Raw Delta A: ±?
                       - Raw Delta B: -?      - Raw Delta B: +?      - Raw Delta B: ±?
                             |                      |                      |
                             +----------------------+----------------------+
                                                    |
                                                    v
                                  +-----------------------------------+
                                  |       TROPHY FLOOR GUARD          |
                                  | - New Trophies A = max(0, A + ΔA) |
                                  | - New Trophies B = max(0, B + ΔB) |
                                  | - Actual Δ = NewTrophies - Current|
                                  +-----------------------------------+
                                                    |
                                                    v
                                  +-----------------------------------+
                                  |        MATCH OVER (RANKED)        |
                                  | - Save match history to DB        |
                                  | - Update Leaderboard & Orbit      |
                                  | - Return Final Trophies & Actual Δ|
                                  +-----------------------------------+

Notes:
- Casual matches (Friend, Quick) give no rewards and do not change trophies.
- Win threshold is set per mode in GameConfig: Ranked 30, Friend 30, Quick 20? (to be tested).
- Ranked draw: Elo with score 0.5 for both. The player with more trophies loses some, the player with fewer gains some; the delta is 0 only when trophies are equal.