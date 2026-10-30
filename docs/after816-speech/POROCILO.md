# Popravek življenjskega cikla govora po 816

Osnova je objavljena v1.2.816, commit 2466ddc0b352ce515606880de248328297a32909.
To je pripravljena izvorna koda za naslednjo izdajo, brez novega APK ali javne objave. VersionName/versionCode nista spremenjena.

## Potrjene napake in popravki

1. Uspešen zagon Android TTS je bil vezan na privzeto slovenščino. Če je na napravi manjkala slovenščina, se je pogon označil kot nedelujoč tudi za razpoložljivo ukrajinščino. Pripravljenost pogona je zdaj ločena od izbire jezika konkretne zahteve.
2. Čakajoče besedilo je po zagonu preskočilo neuspešno izbiro jezika. Zdaj gre skozi isto preverjanje kot običajni nadomestni govor. Manjkajoča ukrajinščina se ne predvaja skozi drug jezik; če noben dovoljen nadomestni jezik ni podprt, se poroča napaka.
3. Nova zahteva ni vedno ustavila starega Android TTS in počistila čakajočega besedila. Enoten začetek zahteve najprej spremeni serijsko številko, počisti staro čakanje in ustavi prejšnji govor.
4. TTS callbacks niso preverjali ID-ja zahteve. Zakasnjeni start/done/error starega govora so lahko vplivali na nov govor. Vsi dogodki se zdaj ob izvedbi na glavni niti preverijo proti trenutni zahtevi. Tudi že odložena obvestila poslušalcu se preverijo ponovno.
5. MediaPlayer dogodki starega predvajalnika so lahko sprostili trenutno novo instanco. Dogodka completion/error zdaj veljata samo za trenutno instanco.
6. Zaprtje predvajalnika zdaj razveljavi čakajoče zahteve in pozno inicializacijo TTS. Napaka inicializacije počisti in prijavi čakajoči govor.

Pregled osveževanja ikon: obstoječa koda AacCommunicatorActivity.onResume že primerja osebni stamp, ki vsebuje spol in vezave slik, ter ponovno prikaže elemente. V tem delu ni bilo sprememb. Preizkus na tablici ostaja potreben.

## Izvedeni testi

- 27 ponovljivih testov Kotlin, ki sestavijo in izvajajo dejanska telesa funkcij aplikacije s ponarejenim TTS, predvajalnikom in razporejevalnikom. Preverjeni ukrajinščina brez slovenščine, nedosegljivi jeziki, neuspešen zagon, zamenjava čakajočega besedila, oba error callbacka, zastareli dogodki, zaprtje in star MediaPlayer.
- 12 testov dejanskega razreda AacSpeechRequestPolicy.java, tudi null ID, napačen/stari ID, zaprt predvajalnik in robni serijski številki.
- 15 obstoječih regresijskih testov datuma/vremena uspešnih.
- 26 obstoječih runtime testov predpomnilnika SL/UK, obeh glasov in vrste za zajetega pacienta uspešnih.
- Skupaj 80 uspešnih preverjanj. Kotlin compiler 1.9.24, Java 17. git diff --check brez napak.

To ni celotna Android gradnja. Namestitev, pravi Android TTS/MediaPlayer, slišno predvajanje in obnašanje naprave niso preverjeni. Lokalni Codex mora pred podpisom/objavo izvesti Android gradnjo in preverjanje končnega APK. Spletna storitev osebnih ikon ostaja ločena, nenastavljena naloga.

Ni sprememb ikon, pacientove hrambe, slovnice, MainActivity, mreže, radia/GPS/status kode, updaterja, workflowa ali podpisovanja.
