# PlayZone — Sports Equipment Rental & GameBond

PlayZone is a Java-based sports equipment rental management application with a browser-based dashboard and a GameBond social matchmaking feature.

## Features

- Equipment inventory and availability
- Student and staff rentals
- Automatic rental billing, discounts and 18% GST
- Rental history and reports
- Smart Insights: rule-based stock and demand summaries
- Reality Rift: scenario-based inventory simulator
- GameBond: game-group discovery, compatibility ranking and estimated expense sharing
- 50 fictional GameBond demo member profiles and 50 seeded demo game circles

## Technology

- **Backend:** Java 17, built-in `com.sun.net.httpserver.HttpServer`
- **Frontend:** HTML, CSS and vanilla JavaScript
- **Deployment:** Docker-based Render Web Service + Render Static Site
- **Storage:** In-memory demo data (no database currently)

## Architecture

```text
                         Browser
                            |
                            v
              Render Static Site (Frontend)
                  HTML / CSS / JavaScript
                            |
                   HTTPS JSON requests
                            |
                            v
               Render Web Service (Backend)
                     Java 17 / Docker
                            |
             --------------------------------
             |              |               |
             v              v               v
        Rental APIs     Insights APIs   GameBond APIs
             |              |               |
             --------------------------------
                            |
                     In-memory arrays
```

The frontend and backend are deployed as separate Render services. The frontend API URL is configured in `web/config.js`. The backend accepts the Render-provided `PORT` and reads `CORS_ALLOWED_ORIGIN` to allow the deployed frontend origin.

## Run locally

Requirements: JDK 17 or later.

From the repository root:

```bash
javac -encoding UTF-8 -d out src/playzone/Main.java src/playzone/model/*.java src/playzone/people/*.java src/playzone/exceptions/*.java src/playzone/service/*.java src/playzone/web/*.java
java -cp out playzone.web.PlayZoneWebServer
```

Open <http://localhost:8080>.

The local frontend config uses `/api`, so it works when the Java server serves both the frontend and backend from the same origin.

## API endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/health` | Backend health check |
| GET | `/api/equipment` | Equipment inventory |
| GET | `/api/insights` | Rule-based stock insights |
| GET | `/api/report` | Rental summary |
| GET | `/api/rentals` | Rental history |
| POST | `/api/rentals` | Create a rental |
| GET | `/api/rentals/{id}` | Retrieve a rental bill |
| GET | `/api/gamebond/members` | 50 fictional demo profiles |
| GET | `/api/gamebond/lobbies` | Open game circles |
| POST | `/api/gamebond/lobbies` | Create a game circle |
| POST | `/api/gamebond/recommend` | Find compatible groups |
| POST | `/api/gamebond/join` | Join a group |

## Deploy on Render

### 1. Backend — Render Web Service

- Push this repository to GitHub.
- In Render, create **New → Web Service** and connect the repository.
- Set the root directory to `PlayZone_Web` only if the repository contains this project in a subfolder. If the repository root itself contains `Dockerfile`, leave root directory blank.
- Choose **Docker** as the runtime.
- Set the Dockerfile path to `./Dockerfile`.
- Set health check path to `/api/health`.
- Add environment variable `CORS_ALLOWED_ORIGIN` with the exact public URL of the frontend Static Site after it has been created.
- Deploy and copy the backend URL.

Render supplies `PORT`; the Java server reads it automatically.

### 2. Frontend — Render Static Site

- In Render, create **New → Static Site** using the same GitHub repository.
- Set the root directory to `PlayZone_Web/web` if the repository has the project in a subfolder. If the repository root itself is `PlayZone_Web`, set root directory to `web`.
- Leave Build Command empty.
- Set Publish Directory to `.`.
- Before deploying, edit `web/config.js` and set:

```javascript
window.PLAYZONE_API_BASE = "https://YOUR-BACKEND.onrender.com/api";
```

- Deploy the Static Site and copy its public URL.
- Go back to the backend Web Service and set `CORS_ALLOWED_ORIGIN` to the exact frontend URL, then redeploy the backend if required.

### 3. Verify

- Open `https://YOUR-BACKEND.onrender.com/api/health` and confirm it returns `{"status":"ok","service":"PlayZone API"}`.
- Open `https://YOUR-BACKEND.onrender.com/api/gamebond/members` and confirm 50 demo profiles are returned.
- Open the frontend Static Site URL and test inventory, rental creation, reports, Smart Insights, Reality Rift and GameBond.
- Check browser Developer Tools → Console and Network if requests fail. Confirm `web/config.js` points to the correct backend URL and `CORS_ALLOWED_ORIGIN` exactly matches the frontend origin.

## Important prototype limitations

- Rental records, inventory changes, and GameBond lobbies are stored in memory and reset when the backend restarts or is redeployed.
- The 50 GameBond profiles are fictional sample data, not real students.
- Smart Insights and GameBond compatibility are rule-based; they are not trained machine-learning models.
- Reality Rift uses illustrative scenario assumptions.
- There is no login, database persistence, chat, payment processing, identity verification, live location, or real-time notifications.
- This is a hackathon/demo prototype, not yet a production-ready rental platform.

## Project structure

```text
PlayZone_Web/
├── src/playzone/           # Java source
│   ├── model/
│   ├── people/
│   ├── service/
│   ├── exceptions/
│   └── web/                # HTTP server and API routes
├── web/                    # Frontend
│   ├── index.html
│   ├── styles.css
│   ├── config.js           # Frontend API base URL
│   └── app.js
├── Dockerfile
├── .dockerignore
├── .gitignore
├── .env.example
├── README.md
└── AI_LOG.txt
```

## Team

- Sambhram N
- Hemavati
- Kavya
- Akshay
- Shrihari
