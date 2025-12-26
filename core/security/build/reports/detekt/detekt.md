# detekt

## Metrics

* 243 number of properties

* 112 number of functions

* 94 number of classes

* 9 number of packages

* 23 number of kt files

## Complexity Report

* 3,280 lines of code (loc)

* 2,108 source lines of code (sloc)

* 1,562 logical lines of code (lloc)

* 704 comment lines of code (cloc)

* 350 cyclomatic complexity (mcc)

* 182 cognitive complexity

* 8 number of total code smells

* 33% comment source ratio

* 224 mcc per 1,000 lloc

* 5 code smells per 1,000 lloc

## Findings (8)

### style, MagicNumber (1)

Report magic numbers. Magic number is a numeric literal that is not defined as a constant and hence it's unclear what the purpose of this number is. It's better to declare such numbers as constants and give them a proper name. By default, -1, 0, 1, and 2 are not considered to be magic numbers.

[Documentation](https://detekt.dev/docs/rules/style#magicnumber)

* /home/yungblud/Desktop/SpaceTec/core/security/src/main/java/com/spacetec/core/security/SecurityManager.kt:26:73
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
23         return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
24             getOrCreateKeyApi23()
25         } else {
26             fallbackKey ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { fallbackKey = it }
!!                                                                         ^ error
27         }
28     }
29 

```

### style, MaxLineLength (6)

Line detected, which is longer than the defined maximum line length in the code style.

[Documentation](https://detekt.dev/docs/rules/style#maxlinelength)

* /home/yungblud/Desktop/SpaceTec/core/security/src/main/java/com/spacetec/core/security/encryption/KeyStoreManager.kt:50:1
```
Line detected, which is longer than the defined maximum line length in the code style.
```
```kotlin
47      */
48     suspend fun storeKey(keyId: String, key: SecretKey): Result<Unit, Throwable> {
49         try {
50             if (isHardwareBackedKeystoreAvailable() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
!! ^ error
51                 storeKeyInHardwareKeystore(keyId, key)
52             } else {
53                 // Fallback to software storage (less secure)

```

* /home/yungblud/Desktop/SpaceTec/core/security/src/main/java/com/spacetec/core/security/encryption/KeyStoreManager.kt:72:1
```
Line detected, which is longer than the defined maximum line length in the code style.
```
```kotlin
69      */
70     suspend fun getKey(keyId: String): SecretKey? {
71         return try {
72             if (isHardwareBackedKeystoreAvailable() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
!! ^ error
73                 getKeyFromHardwareKeystore(keyId)
74             } else {
75                 softwareKeyCache[keyId]

```

* /home/yungblud/Desktop/SpaceTec/core/security/src/main/java/com/spacetec/core/security/encryption/KeyStoreManager.kt:90:1
```
Line detected, which is longer than the defined maximum line length in the code style.
```
```kotlin
87      */
88     suspend fun deleteKey(keyId: String): Result<Unit, Throwable> {
89         return try {
90             if (isHardwareBackedKeystoreAvailable() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
!! ^ error
91                 keyStore.deleteEntry(keyId)
92             } else {
93                 softwareKeyCache.remove(keyId)

```

* /home/yungblud/Desktop/SpaceTec/core/security/src/main/java/com/spacetec/core/security/encryption/KeyStoreManager.kt:134:1
```
Line detected, which is longer than the defined maximum line length in the code style.
```
```kotlin
131      */
132     suspend fun keyExists(keyId: String): Boolean {
133         return try {
134             if (isHardwareBackedKeystoreAvailable() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
!!! ^ error
135                 keyStore.containsAlias(keyId)
136             } else {
137                 softwareKeyCache.containsKey(keyId)

```

* /home/yungblud/Desktop/SpaceTec/core/security/src/main/java/com/spacetec/core/security/encryption/KeyStoreManager.kt:151:1
```
Line detected, which is longer than the defined maximum line length in the code style.
```
```kotlin
148      */
149     suspend fun listKeys(): List<String> {
150         return try {
151             if (isHardwareBackedKeystoreAvailable() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
!!! ^ error
152                 keyStore.aliases().toList()
153             } else {
154                 softwareKeyCache.keys.toList()

```

* /home/yungblud/Desktop/SpaceTec/core/security/src/main/java/com/spacetec/core/security/encryption/KeyStoreManager.kt:168:1
```
Line detected, which is longer than the defined maximum line length in the code style.
```
```kotlin
165      */
166     suspend fun clearAllKeys(): Result<Unit, Throwable> {
167         return try {
168             if (isHardwareBackedKeystoreAvailable() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
!!! ^ error
169                 val aliases = keyStore.aliases().toList()
170                 aliases.forEach { alias ->
171                     keyStore.deleteEntry(alias)

```

### style, UnusedPrivateMember (1)

Private function is unused and should be removed.

[Documentation](https://detekt.dev/docs/rules/style#unusedprivatemember)

* /home/yungblud/Desktop/SpaceTec/core/security/src/test/java/com/spacetec/core/security/SecurityVerificationPropertyTest.kt:239:13
```
Private function `calculateExpectedTrustLevel` is unused.
```
```kotlin
236     )
237 }
238 
239 private fun calculateExpectedTrustLevel(deviceInfo: ScannerDeviceInfo): TrustLevel {
!!!             ^ error
240     var score = 0
241     
242     val trustedManufacturers = setOf("ELM Electronics", "OBDLink", "Veepeak")

```

generated with [detekt version 1.23.4](https://detekt.dev/) on 2025-12-25 22:55:52 UTC
