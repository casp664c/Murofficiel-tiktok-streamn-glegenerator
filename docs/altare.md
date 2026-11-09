# Altare backend — deploymentforberedelse

Dette dokument beskriver planlagt deployment. Intet er installeret på brugerens Altare-konto endnu.

## Uafhængig service
Deploy **altare-backend/** som separat Node.js-service (Node >= 20). Genbrug ikke en eksisterende Minecraft-server, databaser eller hemmelige tokens.

## Miljø
- `PORT`: serverens interne port, standard 3000.
- `HOST`: som standard `127.0.0.1`; på en container-platform kan `0.0.0.0` være nødvendig for at nå serviceporten.
- `DATA_DIR`: mappe til `store.json` (konti og profiler). Brug en vedvarende disk; uden den forsvinder alt ved genstart.
- `VAULT_KEY`: `openssl rand -base64 32`. Uden den afviser serveren at gemme streamnøgler. Mister du den, kan gemte nøgler ikke læses mere.
- `REGISTRATION_CODE` eller `ALLOW_REGISTRATION=true`: uden en af dem er oprettelse af konti lukket.
- `TRUST_PROXY=true`: kun hvis proxyen foran overskriver `X-Forwarded-For`.
- `SESSION_DAYS`: loginets levetid, standard 30.

## Start
```sh
cd altare-backend
npm test
npm start
```

Test lokalt med `GET /health` og `GET /api/v1/capabilities`.

## Obligatorisk før offentlig adgang
1. Afgør om Altare-planen reelt understøtter en Node.js-app, port og stabil tilgængelighed.
2. Sæt en kontrolleret HTTPS-reverse proxy foran backend. Der er intet TLS i Node-processen selv.
3. Send aldrig TikTok-login eller TikTok-cookies til backenden. Streamnøgler må kun sendes, hvis `VAULT_KEY` er sat, og kun over HTTPS.
4. Overvåg health, tag backup af `DATA_DIR` og `VAULT_KEY` (hver for sig), og læg ikke credentials i offentlige logs.
5. Hvis en reel autoriseret TikTok-integration senere tilføjes: implementér ordentlig brugergodkendelse, endpoint-autorisation, rate limits og sikker hemmelighedshåndtering først.

Backenden har konti, profiler og status-endpoints samt et eksplicit ikke-implementeret session-endpoint (`501`).
