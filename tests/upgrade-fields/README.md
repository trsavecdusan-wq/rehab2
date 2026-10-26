# Upgrade field and effective-image checks

Run `assembleDebug` after the final source edits, then run:

```powershell
./tests/upgrade-fields/run.ps1 -JsonJar '<absolute path to json-20240303.jar>'
```

The JVM-only `org.json:json:20240303` dependency is available from Maven Central. Keep it outside the repository; it is not an Android dependency. The runner uses the Kotlin 1.9.24 standard library already cached by the build and compiled application classes.

The tests exercise all nine editing/locking flags, preserved language fields, repeat merges, personal images, repairable system images, actual starter mappings and the fixed first row. They do not simulate Android storage, rendering or a physical tablet.
