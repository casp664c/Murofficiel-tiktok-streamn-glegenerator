# LIVE Key Studio — Android + Altare (0.2-dev)

Selvstændigt open source-udviklingsprojekt til Android og en separat Node.js-backend, udviklet fra bunden.

> **Vigtig status:** Projektet genererer **ikke** en TikTok LIVE-streamnøgle. TikTok har ingen offentlig API til at udstede nøgler, og en nøgle kan kun komme fra TikToks egne værktøjer til en konto, der har adgang til LIVE. Appen opfinder aldrig nøgler og lover ikke adgang. Se fanen *Guide* i appen.

## Det der er implementeret

**Android (Kotlin/Jetpack Compose, dansk brugerflade)**
- **Profiler:** gem server og streamnøgle på telefonen. Nøglen krypteres med AES-256-GCM, og krypteringsnøglen ligger i Android Keystore.
- **Forbindelsestest:** RTMP/RTMPS-handshake mod serveren (med certifikatkontrol for rtmps). Viser, at serveren svarer som en RTMP-server. Nøglen sendes ikke og kontrolleres først, når du går live.
- **Guide:** trin for trin til at hente din egen server og nøgle fra TikToks officielle værktøjer, med knapper til at åbne TikTok.
- **Eksport:** validering, kopiering og brugerinitieret deling til andre streamingapps. Advarsel ved `rtmp://` uden kryptering.
- **Stream:** kamera og mikrofon sendes som RTMP direkte fra telefonen (RootEncoder). Streamen stopper, når appen forlader skærmen.
- **Altare:** HTTPS health-check, login/oprettelse af konto, og synkronisering af profiler med din egen backend.

**Backend (Node.js ≥ 20, ingen tredjepartsafhængigheder)**
- `GET /health`, `GET /api/v1/capabilities`
- Konti: `POST /api/v1/auth/register|login|logout|password`, `GET /api/v1/me` (adgangskoder hashes med scrypt, tokens gemmes kun som hash)
- Profiler: `GET|POST /api/v1/profiles`, `GET|PUT|DELETE /api/v1/profiles/:id`, `POST /api/v1/profiles/:id/reveal-key`
- `POST /api/v1/sessions` returnerer bevidst `501`: der er ingen TikTok-provider.
- Streamnøgler gemmes kun krypteret (AES-256-GCM) og kun, hvis `VAULT_KEY` er sat. Listen over profiler indeholder aldrig nøglen.

## Byg Android
Åbn repositoryets rod i Android Studio med Android SDK 35 og JDK 17, eller brug Gradle 8.7:

```sh
gradle :android-app:testDebugUnitTest :android-app:assembleDebug
```

APK'en bliver genereret under `android-app/build/outputs/apk/debug/`. Workflowet `Android + backend CI` kører testene og uploader debug-APK som artefakt. En build er **ikke** det samme som en testet LIVE-forbindelse; test streamingen på en rigtig telefon med en nøgle fra din egen konto.

## Kør backend lokalt
```sh
cd altare-backend
npm test
npm start
```

Backend lytter som standard kun på `127.0.0.1:3000`. Konfiguration via miljøvariabler:

| Variabel | Betydning |
|---|---|
| `HOST`, `PORT` | Bindingsadresse og port (standard `127.0.0.1:3000`) |
| `DATA_DIR` | Mappe til `store.json`. Uden den ligger alt kun i hukommelsen |
| `VAULT_KEY` | 32 bytes i base64 (`openssl rand -base64 32`). Slår krypteret nøglelagring til |
| `REGISTRATION_CODE` | Invitationskode, der kræves for at oprette en konto |
| `ALLOW_REGISTRATION` | `true` åbner for oprettelse uden kode. Uden begge er oprettelse lukket |
| `TRUST_PROXY` | `true`, hvis en proxy foran sætter `X-Forwarded-For` (bruges til rate limits) |
| `SESSION_DAYS` | Loginets levetid (standard 30) |

Ekstern adgang kræver **HTTPS** via en egnet proxy/tunnel. Android-appen afviser alt andet end HTTPS.

## Altare
Se [docs/altare.md](docs/altare.md). Der er **ikke** foretaget nogen deployment på Altare i denne version.

## Sikkerhed
- **Ingen** adgangskoder, sessioncookies, private API-nøgler eller virkelige streamnøgler må committes.
- Nøgler gemmes på telefonen kun som krypterede profiler, du selv har oprettet. Det du indtaster i Eksport, ligger kun i hukommelsen.
- Skærmbilleder og Android-backup er slået fra.
- Deling/kopiering af en streamnøgle er en udtrykkelig brugerhandling; udklipsholderen/andre apps kan derefter få adgang til oplysningerne.
- Backenden indsamler ikke TikTok-login og kontakter ikke TikTok.

Se [docs/architecture.md](docs/architecture.md) og [docs/tiktok-integration.md](docs/tiktok-integration.md).
