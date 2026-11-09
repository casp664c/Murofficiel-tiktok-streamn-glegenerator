# Altare backend — deploymentforberedelse

Dette dokument beskriver planlagt deployment. Intet er installeret på brugerens Altare-konto endnu.

## Uafhængig service
Deploy **altare-backend/** som separat Node.js-service (Node >= 20). Genbrug ikke en eksisterende Minecraft-server, databaser eller hemmelige tokens.

## Miljø
- `PORT`: serverens interne port, standard 3000.
- `HOST`: som standard `127.0.0.1`; på en container-platform kan `0.0.0.0` være nødvendig for at nå serviceporten.

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
3. Lad ikke apps sende tokens, TikTok-cookies eller streamnøgler til denne prototype.
4. Overvåg health, begræns adgang og læg ikke credentials i offentlige logs.
5. Hvis en reel autoriseret TikTok-integration senere tilføjes: implementér ordentlig brugergodkendelse, endpoint-autorisation, rate limits og sikker hemmelighedshåndtering først.

Den nuværende backend har kun ikke-følsomme status-endpoints og et eksplicit ikke-implementeret session-endpoint.
