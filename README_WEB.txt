PLAYZONE LOCAL WEBSITE
======================

REQUIREMENTS
------------
- Java 17 or later
- VS Code (recommended)

RUN ON WINDOWS / VS CODE
------------------------
1. Extract the ZIP.
2. Open the extracted PlayZone_Web folder in VS Code.
3. Open Terminal > New Terminal.
4. Compile all Java files:

   javac -encoding UTF-8 -d out src/playzone/Main.java src/playzone/model/*.java src/playzone/people/*.java src/playzone/exceptions/*.java src/playzone/service/*.java src/playzone/web/*.java

5. Start the web server:

   java -cp out playzone.web.PlayZoneWebServer

6. Open this address in your browser:

   http://localhost:8080

IMPORTANT
---------
- Keep the terminal running while using the website.
- Stop the server with Ctrl+C.
- Data is held in memory and resets when the server stops.
- The original console program remains available:
  java -cp out playzone.Main
- This is a local demo server. Do not expose it to the public internet as-is.

FEATURES
--------
Dashboard, equipment inventory, Student/Staff rental, draft undo,
automatic billing and GST, rental history, bill lookup, token collection
room, and daily report.


REALITY RIFT – COUNTERFACTUAL INVENTORY SIMULATOR
-------------------------------------------------
Open the "Reality Rift" page in the sidebar.
- Adjust expected participants (10–120).
- Adjust the chaos multiplier (0.8x–2.5x).
- Optionally include the unsaved rental draft in the stock calculation.
- Run three scenarios: Calm Campus, Tournament Surge and Monsoon Flip.
- Each scenario compares projected item demand against live available stock,
  reports coverage and identifies exact quantity gaps.

The scenario profiles are illustrative assumptions for a 30-person event.
The simulator is deterministic decision support, not machine-learning prediction.
Simulations do not reserve stock or create rentals.


GAMEBOND – CAMPUS MULTIPLAYER MATCHMAKER
----------------------------------------
Open GameBond in the sidebar.
- Enter a nickname, game, skill level, preferred time and per-person budget.
- Find open groups ranked by game/time/skill/budget compatibility.
- Create a group with player capacity and estimated total expense.
- Join a group; the estimated per-person share is recalculated.
Supported games: Chess, Carrom, Badminton, Football, Basketball,
Table Tennis, Volleyball and Cricket.

This local prototype stores lobbies in memory (restart clears them).
Nicknames only; no chat, real-time notifications, location sharing,
identity verification or payment processing. Agree venue and expenses
with players before meeting.


GAMEBOND – MULTI-DEVICE DEMO
---------------------------
Start the Java server on one laptop. Connect other phones/laptops to the same
Wi-Fi network and open http://<HOST-LAPTOP-LAN-IP>:8080 in their browsers.
The same server shares game groups among all devices while it is running.
If access fails, allow Java through the host laptop firewall on a private network.
This is a classroom prototype: nickname-only groups, no identity verification,
chat, notifications, payment collection or real-time geolocation.

GAMEBOND DEMO FLOW
------------------
1. Open the website from one browser/device and enter a nickname.
2. Use Create your own group to post (for example) Chess, Evening, 2 players,
   estimated total expense ₹100.
3. On a second browser/device connected to the same Wi-Fi, open the host laptop's
   IPv4 address on port 8080, enter another nickname, choose Chess/Evening and
   set a budget of ₹50 or more.
4. Find compatible players. The group should appear with a compatibility score
   and estimated share if joining now.
5. Choose “I'm in” to join. The group shows its participants and recalculates
   current expense share per person. “Pass for now” hides a group from that
   browser's current discovery deck.

DEMO LIMITATIONS
----------------
The matching score is a transparent rule-based score (game/time/skill/budget),
not an AI-personality score. It matches open game groups, not verified identities.
Group records are held in server memory and disappear when Java stops.
No chat, identity verification, precise location, payment processing, reservation
or background notifications are implemented. Confirm the meeting place and final
cost directly with people you know and use a public campus venue.

RUN SCRIPT
----------
On Windows, double-click run_playzone.bat to compile and start the website.


GAMEBOND DEMO MEMBER SEED DATA
------------------------------
On server startup, GameBond automatically creates 50 fictional sample member profiles.
Each profile has a nickname, preferred game, skill level, preferred time slot and
per-person budget. The profiles are loaded into the matchmaking engine and each
sample member hosts an open demo game circle. This makes the recommendation deck
populated immediately for a hackathon demonstration.

GET /api/gamebond/members returns the 50 fictional demo profiles.
GET /api/gamebond/lobbies returns the seeded open game circles.
These are synthetic demo records, not real students or collected personal data.
They are held in memory and reset to the same demo set whenever the server restarts.
