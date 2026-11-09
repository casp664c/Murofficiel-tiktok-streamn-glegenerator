# LIVE Key Studio — Android + Altare (0.1-dev)

Selvstændigt open source-udviklingsprojekt til Android og en separat Node.js-backend, udviklet fra bunden.

> **Vigtig status:** Denne version genererer **ikke** en fungerende TikTok LIVE-streamnøgle. TikTok har ikke godkendt eller verificeret en uofficiel nøgleudstedelsesmetode. Ingen falske streamnøgler, påståede TikTok-login eller løfter om adgang.

## Det der er implementeret
- En indfødt Android-app i Kotlin/Jetpack Compose med dansk brugerflade.
- Manuel indtastning og grundlæggende validering af RTMP/RTMPS-server og streamnøgle.
- Kopiering og brugerinitieret deling af gyldige streamingoplysninger til kompatible streamingapps.
- En separat Node.js-server uden tredjepartsafhængigheder: `GET /health`, `GET /api/v1/capabilities`, `POST /api/v1/sessions` (returnerer bevidst `501`).
- Health-check af en brugerangivet **HTTPS**-backend direkte fra Android-appen.
- Node-tests, Android-unit-tests og GitHub Actions-konfiguration til test og APK-build.

## Byg Android
Åbn repositoryets rod i Android Studio med Android SDK 35 og JDK 17, eller brug Gradle 8.7:

```sh
gradle :android-app:assembleDebug
```

APK'en bliver genereret under `android-app/build/outputs/apk/debug/` efter et vellykket build. Workflowet `Android + backend CI` uploader også debug-APK som et GitHub Actions-artefakt efter et vellykket build. En lokal eller cloudbuild er **ikke** det samme som en testet LIVE-integration.

## Kør backend lokalt
```sh
cd altare-backend
npm test
npm start
```

Lokal backend lytter som standard kun på `127.0.0.1:3000`. Brug `HOST` og `PORT` for andre bindingsadresser. Ekstern adgang til backend kræver **HTTPS** via en egnet proxy/tunnel; udgiv aldrig credentials eller tokens via HTTP.

## Altare
Se [docs/altare.md](docs/altare.md). Der er **ikke** foretaget nogen deployment på Altare i denne version.

## Sikkerhed
- **Ingen** adgangskoder, sessioncookies, private API-nøgler eller virkelige streamnøgler må committes.
- Android-appen lagrer ikke den indtastede streamnøgle permanent.
- Deling/kopiering af en streamnøgle er en udtrykkelig brugerhandling; udklipsholderen/andre apps kan derefter få adgang til oplysningerne.
- Den offentlige backend har ikke et TikTok-login- eller genererings-endpoint, som foregiver at virke.
- Kun funktioner for konti/sessioner, som brugeren har ret til at administrere, er omfattet.

Se [docs/architecture.md](docs/architecture.md) og [docs/tiktok-integration.md](docs/tiktok-integration.md) for den videre plan.
