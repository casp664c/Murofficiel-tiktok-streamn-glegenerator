# Arkitektur og sikkerhedsgrænser

```text
Android-app (Kotlin / Jetpack Compose)
  Profiler  ── krypteret lager (AES-GCM, nøgle i Android Keystore)
  Eksport   ── validering, kopiering, deling efter brugerens tryk
  Test      ── RTMP/RTMPS-handshake (sender ikke nøglen)
  Stream    ── kamera + mikrofon -> RTMP (RootEncoder, kun i Broadcaster.kt)
  Altare    ── HTTPS-klient (BackendClient) til egen backend
          |
          | HTTPS, Bearer-token
          v
Altare/Node.js-backend (ingen deployment endnu)
  /health, /api/v1/capabilities
  /api/v1/auth/*        konti (scrypt, tokens gemt som hash, rate limits)
  /api/v1/profiles/*    profiler pr. bruger, nøgle krypteret med VAULT_KEY
  POST /api/v1/sessions -> 501, provider er ikke installeret
          |
          X  Ingen TikTok-integration
```

## Sikkerhedsbeslutninger
- Ingen hardcodede kontooplysninger, API-nøgler, adgangstokens eller streamnøgler.
- Det indtastede i Eksport holdes kun i hukommelsen. Gemte profiler krypteres med AES-256-GCM; krypteringsnøglen kan ikke forlade Android Keystore.
- Android-backup er slået fra, så krypterede profiler ikke kan gendannes på en anden enhed. Brug Altare-synkronisering til det.
- Login-token på telefonen gemmes krypteret. Adgangskoden gemmes aldrig.
- Backend: scrypt til adgangskoder, tokens gemmes kun som SHA-256, rate limits på login/oprettelse/afsløring af nøgle, 16 KB grænse på request-bodies, ingen CORS, ingen logning af bodies.
- Registrering er lukket, medmindre administratoren sætter `REGISTRATION_CODE` eller `ALLOW_REGISTRATION=true`.
- Nøgler gemmes kun på serveren, hvis `VAULT_KEY` er sat. Krypteringen er bundet til ejer og profil (AAD), så poster ikke kan flyttes mellem brugere.
- Nøglen returneres aldrig af liste- eller læse-endpoints, kun af `reveal-key` til ejeren.
- Android kan kun tale med HTTPS-backends. `rtmp://` uden kryptering giver en advarsel.
- Streaming kører kun, mens skærmen er synlig (ingen foreground service), så en stream ikke fortsætter uset i baggrunden.

## Kendte grænser
- RTMP-testen viser kun, at serveren svarer. Om TikTok accepterer nøglen, ser man først ved publicering.
- Backend gemmer data i en JSON-fil. Det er nok til en enkelt instans og få brugere, men ikke til skalering.
- Rate limits ligger i hukommelsen og nulstilles ved genstart.

## Senere faser
- Foreground service, så streaming kan fortsætte med slukket skærm.
- Valgfri kompatibilitet med øvrige streamingapps og streamopsætning (opløsning, bitrate).
- En provider-grænseflade, som kun svarer med virkeligt udstedte, verificerede streamingoplysninger, hvis TikTok engang tilbyder en officiel vej.
