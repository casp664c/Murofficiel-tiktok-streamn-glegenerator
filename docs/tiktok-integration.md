# TikTok-integration

**Status: ingen automatisk nøgleudstedelse.** TikTok har ingen offentlig API, der udleverer streamnøgler, og TikTok afgør selv, om en konto må gå LIVE. En tilfældigt genereret nøgle bliver ikke accepteret, og en Altare-server ændrer ikke dette.

## Sådan bruger projektet TikTok i dag
1. Brugeren henter sin egen serveradresse og streamnøgle fra TikToks officielle værktøjer (fx LIVE Studio), på en konto med adgang til LIVE. Appens fane *Guide* beskriver trinnene.
2. Brugeren indsætter oplysningerne i appen og gemmer dem som en krypteret profil.
3. Appen tester, at serveren svarer (RTMP-handshake), og kan streame fra telefonens kamera eller kopiere oplysningerne til en anden streamingapp.
4. TikTok validerer nøglen, når streamen starter. Afvises den, viser appen en fejl uden at gentage nøglen.

## Hvad der ikke bygges
- Ingen omgåelse af TikToks krav til konti, og ingen opfundne eller gættede nøgler.
- Ingen indsamling af TikTok-adgangskoder eller cookies, hverken i appen eller i backenden.
- Ingen uverificerede signeringstjenester.

## Hvis TikTok senere tilbyder en officiel vej
Provider-grænsefladen `LiveSessionProvider` i appen og `POST /api/v1/sessions` i backenden er forberedt til det. En provider må kun returnere virkeligt udstedte oplysninger for en bruger, der har givet tilladelse, og skal have rate limits, tokenstyring og testkonto, før den aktiveres.

Ingen kode må fremstille et fiktivt resultat som en fungerende TikTok-nøgle.
