# Android  Attestation Library
[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-brightgreen.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0) 
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.22-blue.svg?logo=kotlin)](http://kotlinlang.org)
![Java](https://img.shields.io/badge/java-11-blue.svg?logo=OPENJDK)
![Build artifacts](https://github.com/a-sit-plus/android-attestation/actions/workflows/gradle-build.yml/badge.svg)
[![Maven Central](https://img.shields.io/maven-central/v/at.asitplus/android-attestation)](https://mvnrepository.com/artifact/at.asitplus/android-attestation/)

This Kotlin library provides a convenient API (a single function, actually) to remotely attest the integrity of an Android device, its OS and a specific application.
It is intended to be integrated into back-end services requiring authentic, unmodified mobile clients (but it also works in other settings, such as peer-to-peer-scenarios).

Full API docs are available [here](https://a-sit-plus.github.io/android-attestation).

This library's core logic is based off [code from Google](https://github.com/google/android-key-attestation) (and actually directly integrates it), such that it can easily keep up with upstream for the lower-level functionality.
Because of this, it only targets the JVM, although a KMP rewrite (also targeting JS/Node) is possible.
This JVM-centricity is also the reason why the function signatures are rather JVM-esque (read: exceptions are thrown on error,
as done by pretty much every verification function of classes form the `java.security` package).

This library is an integral part of the more comprehensive [Attestation Service](https://github.com/a-sit-plus/attestation-service), which also supports iOS clients and provides
more idiomatic kotlin interfaces.
However, if you are only concerned about Android clients, this library provides all functionality needed without unnecessary bloat.

Another useful feature of this library is the possibility to set custom trust anchors and thus use automatically generated 'fake' attestations
for end-to-end-tests, for example.

## Development

See [DEVELOPMENT.md](https://github.com/a-sit-plus/android-attestation/blob/main/DEVELOPMENT.md)

## Background
Android devices with a TEE allow for cryptographic keys to be generated in hardware. These keys can only be used, but not exported and are safe from extraction due protective hardware measures. The Android Keystore API expose this hardware-based management of cryptographic material and also allows for generating certficates for such keys, which contain custom Extension that indicate the location of a key (hardware or software).
<br>
Additional extension (populated by the cryptographic hardware during key generation) further indicate the device's integrity state (bootloader unlocked, system image integrity, …). This certificate is signed in hardware by a manufacturer key (also protected by hardware) which is provisioned during device manufacturing. A certificate corresponding to this manufacurer key is signed by Google, and the public key of this signing key is published by Google.
Hence, verifying this certificate chain against this Google root key makes it possible to assert the authenticity of the leaf certificate. Checking the custom extension of this leaf certificate consequently allows for remotely establishing trust in an Android device and the application which created the underlying key.
A noteworthy property of this attestation concept is that no third party needs to be contacted (except for obtaining certificate revocation information) compared to Apple's AppAttest/DeviceCheck.

## Usage

Written in Kotlin, plays nicely with Java (cf. `@JvmOverloads`), published at maven central.
### Gradle

```kotlin
 dependencies {
     implementation("at.asitplus:android-attestation:$version")
 }
```

Three flavours of attestation are implemented:
* Hardware attestation through [HardwareAttestationChecker](https://github.com/a-sit-plus/android-attestation/blob/main/android-attestation/src/main/kotlin/AndroidAttestationChecker.kt)
  (this is what you typically want)
* Software attestation through [SoftwareAttestationChecker](https://github.com/a-sit-plus/android-attestation/blob/main/android-attestation/src/main/kotlin/SoftwareAttestationChecker.kt)
  (you typically don't want to use this)
* Nougat hybrid attestation through [NougatHybridAttestationChecker](https://github.com/a-sit-plus/android-attestation/blob/main/android-attestation/src/main/kotlin/NougatHybridAttestationChecker.kt)
  (you may require this to support legacy devices originally shipped with Android 7 (Nougat))

All of these extend [AndroidAttestationChecker](https://github.com/a-sit-plus/android-attestation/blob/main/android-attestation/src/main/kotlin/AndroidAttestationChecker.kt)


### Configuration
Configuration is based on the data class `AttestationConfiguration`. Some properties are nullable – if unset, no checks against these properties are made.

**Note:** In order to use anything but the `HardwareAttestationChecker` the corresponding flags need to be set in the configuration.
This serves a dual purpose:
1. It makes shooting yourself in the foot a lot harder (i.e. accidentally enabling software attestation or disabling
   hardware attestation requires more manual effort).
2. It allows for instantiating AttestationCheckers based on these flags, for example, when chaining hardware attestation
   with nougat-style attestation as a fallback just from evaluating an `AndroidAttestationConfiguration` instance.

When using Kotlin, named parameters make configuration straight-forward: 

```kotlin
AndroidAttestationConfiguration(
    applications= listOf(   //REQUIRED: add applications to be attested
        AndroidAttestationConfiguration.AppData(
            packageName = "at.asitplus.attestation_client",
            signatureDigests = listOf("NLl2LE1skNSEMZQMV73nMUJYsmQg7=".encodeToByteArray()),
            appVersion = 5
        ),
        AndroidAttestationConfiguration.AppData( //we have a dedicated app for latest android version
            packageName = "at.asitplus.attestation_client-tiramisu",
            signatureDigests = listOf("NLl2LE1skNSEMZQMV73nMUJYsmQg7=".encodeToByteArray()),
            appVersion = 2, //with a different versioning scheme
            androidVersionOverride = 13000, //so we need to override this
            patchLevelOverride = PatchLevel(2023, 6) //also override patch level
        )
    ),
    androidVersion = 11000,                 //OPTIONAL, null by default
    patchLevel = PatchLevel(2022, 12),      //OPTIONAL, null by default
    requireStrongBox = false,               //OPTIONAL, defaults to false
    allowBootloaderUnlock = false,          //OPTIONAL, defaults to false
    requireRollbackResistance = false,      //OPTIONAL, defaults to false
    ignoreLeafValidity = false,             //OPTIONAL, defaults to false
    hardwareAttestationTrustAnchors = linkedSetOf(*DEFAULT_HARDWARE_TRUST_ANCHORS), //OPTIONAL, defaults  shown here
    softwareAttestationTrustAnchors = linkedSetOf(*DEFAULT_SOFTWARE_TRUST_ANCHORS), //OPTIONAL, defaults  shown here
    verificationSecondsOffset = -300,       //OPTIONAL, defaults to 0
    disableHardwareAttestation = false,     //OPTIONAL, defaults to false
    enableNougatAttestation = false,        //OPTIONAL, defaults to false
    enableSoftwareAttestation = false       //OPTIONAL, defaults to false
)
```

Additionally, a builder is available for smoohter java interoperability:

```java
List<AndroidAttestationConfiguration.AppData> apps = new LinkedList<>();

apps.add(new AndroidAttestationConfiguration.AppData(
        "at.asitplus.example",
        Collections.singletonList(Base64.getDecoder().decode("NLl2LE1skNSEMZQMV73nMUJYsmQg7+Fqx/cnTw0zCtU="))
));
apps.add(new AndroidAttestationConfiguration.AppData(
        "at.asitplus.anotherexample",
        Collections.singletonList(Base64.getDecoder().decode("NLl2LE1skNSEMZQMV73nMUJYsmQg7+Fqx/cnTw0zCtU=")),
        2
));
AndroidAttestationConfiguration config = new AndroidAttestationConfiguration.Builder(apps)
        .androidVersion(11000)
        .ingoreLeafValidity()
        .patchLevel(new PatchLevel(2023, 03))
        .verificationSecondsOffset(-500) //we to account for time drift
        .build();
```

The (nullable) properties like patch level and app version essentially allow for excluding outdated devices and obsolete app releases. If, for example a critical flaw is discovered in an attested app, users can be forced to update by considering only the latest and greatest version trustworthy and configuring the `AndroidAttestationChecker` instance accordingly.

In addition to configuration, it is possible to override the function which verifies the challenge used to verify an attestation when instantiating an `<*>AttestationChecker`
By default, this is simply a `contentEquals` on the provided challenge vs a reference value.

### Obtaining an Attestation Result
1. The general workflow this library caters to assumes a back-end service, sending an attestation challenge to the mobile app. This challenge needs to be kept for future reference
2. The app is assumed to generate a key pair with attestation (passing the received challenge the Android Keystore)
3. The app responds with the certificate chain associated with this key pair
4. On the back-end a single call to `AndroidAttestationChecker.verifyAttestation()` is sufficient to remotely verify the app's integrity and establish trust in the app. This call requires the challenge from step 1.

```kotlin
val checker = HardwareAttestationChecker(config)

//throws an exception if attestation fails, return a ParsedAttestationRecord on success, which can be inspected
val attestationRecord =  checker.verifyAttestation(attestationCertChain, Date(), challengeFromStep1)
```

## Debugging
The module [attestation-diag](https://github.com/a-sit-plus/android-attestation/blob/main/attestation-diag) contains a
(very) simple command-line utility. It can be built using the `shadowJar` gradle task and pretty-prints attestation
information contained in attestation certificates:
```shell
java -jar attestation-diag-0.0.1-all.jar "MIICkDCCAjagAwIBAgIBATAKBggqhkjOPQQDAjCBiDELMAkGA1UEBhMCVVMxEzARBgNVBAgMCkNhbGlmb3JuaWExFTATBgNVBAoMDEdvb2dsZSwgSW5jLjEQMA4GA1UECwwHQW5kcm9pZDE7MDkGA1UEAwwyQW5kcm9pZCBLZXlzdG9yZSBTb2Z0d2FyZSBBdHRlc3RhdGlvbiBJbnRlcm1lZGlhdGUwIBcNNzAwMTAxMDAwMDAwWhgPMjEwNjAyMDcwNjI4MTVaMB8xHTAbBgNVBAMMFEFuZHJvaWQgS2V5c3RvcmUgS2V5MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEoX5eWkxsJOk2z6S5tclt6bOyJhS3b+2+ULx3O3zZAwFNrbWP52YnQzp\/lsexI99lx\/Z5NRzJ9x0aD
LdIcR\/AyqOB9jCB8zALBgNVHQ8EBAMCB4AwgcIGCisGAQQB1nkCAREEgbMwgbACAQIKAQACAQEKAQEEB2Zvb2JkYXIEADBev4U9BwIFAKtq1Vi\/hUVPBE0wSzElMCMEHmNvbS5leGFtcGxlLnRydXN0ZWRhcHBsaWNhdGlvbgIBATEiBCCI5cOT6u82gpgAtB33hqUv8KWCFYUMqKZQc4Wa3PAZDzA3oQgxBgIBAgIBA6IDAgEDowQCAgEApQgxBgIBAAIBBKoDAgEBv4N3AgUAv4U+AwIBAL+FPwIFADAfBgNVHSMEGDAWgBQ\/\/KzWGrE6noEguNUlHMVlux6RqTAKBggqhkjOPQQDAgNIADBFAiBiMBtVeUV4j1VOiRU8DnGzq9\/xtHfl0wra1xnsmxG+LAIhAJAroVhVcxxItgYZEMN1AaWqmZUXFtktQeLXh7u2F3d+"
```

The result from the above call is a pretty-printed JSON:
```json
{
  "attestationVersion": 2,
  "attestationSecurityLevel": "SOFTWARE",
  "keymasterVersion": 1,
  "keymasterSecurityLevel": "TRUSTED_ENVIRONMENT",
  "attestationChallenge": "666F6F62646172",
  "uniqueId": "",
  "softwareEnforced": {
    "rollbackResistance": false,
    "noAuthRequired": false,
    "allowWhileOnBody": false,
    "trustedUserPresenceRequired": false,
    "trustedConfirmationRequired": false,
    "unlockedDeviceRequired": false,
    "allApplications": false,
    "creationDateTime": "1970-02-03T06:51:45.368Z",
    "rollbackResistant": false,
    "attestationApplicationId": {
      "packageInfos": [
        {
          "packageName": "com.example.trustedapplication",
          "version": 1
        }
      ],
      "signatureDigests": [
        "88E5C393EAEF36829800B41DF786A52FF0A58215850CA8A65073859ADCF0190F"
      ]
    },
    "attestationApplicationIdBytes": "304B31253023041E636F6D2E6578616D706C652E747275737465646170706C69636174696F6E0201013122042088E5C393EAEF36829800B41DF786A52FF0A58215850CA8A65073859ADCF0190F",
    "individualAttestation": false,
    "identityCredentialKey": false
  },
  "teeEnforced": {
    "purpose": [
      "SIGN",
      "VERIFY"
    ],
    "algorithm": "EC",
    "keySize": 256,
    "digest": [
      "NONE",
      "SHA_2_256"
    ],
    "ecCurve": "P_256",
    "rollbackResistance": false,
    "noAuthRequired": true,
    "allowWhileOnBody": false,
    "trustedUserPresenceRequired": false,
    "trustedConfirmationRequired": false,
    "unlockedDeviceRequired": false,
    "allApplications": false,
    "origin": "GENERATED",
    "rollbackResistant": true,
    "individualAttestation": false,
    "identityCredentialKey": false
  },
  "attestedKey": {
    "algorithm": "EC",
    "format": "X.509",
    "encoded": "3059301306072A8648CE3D020106082A8648CE3D03010703420004A17E5E5A4C6C24E936CFA4B9B5C96DE9B3B22614B76FEDBE50BC773B7CD903014DADB58FE76627433A7F96C7B123DF65C7F679351CC9F71D1A0CB748711FC0CA"
  }
}

```

Nulls and empty arrays are omitted by default, but can be printed by adding `-v` at the end of the command line.
The example below shows a verbose JSON obtained this way:
```json
{
  "attestationVersion": 2,
  "attestationSecurityLevel": "SOFTWARE",
  "keymasterVersion": 1,
  "keymasterSecurityLevel": "TRUSTED_ENVIRONMENT",
  "attestationChallenge": "666F6F62646172",
  "uniqueId": "",
  "softwareEnforced": {
    "purpose": [],
    "algorithm": null,
    "keySize": null,
    "digest": [],
    "padding": [],
    "ecCurve": null,
    "rsaPublicExponent": null,
    "rollbackResistance": false,
    "activeDateTime": null,
    "originationExpireDateTime": null,
    "usageExpireDateTime": null,
    "noAuthRequired": false,
    "userAuthType": [],
    "authTimeout": null,
    "allowWhileOnBody": false,
    "trustedUserPresenceRequired": false,
    "trustedConfirmationRequired": false,
    "unlockedDeviceRequired": false,
    "allApplications": false,
    "applicationId": null,
    "creationDateTime": "1970-02-03T06:51:45.368Z",
    "origin": null,
    "rollbackResistant": false,
    "rootOfTrust": null,
    "osVersion": null,
    "osPatchLevel": null,
    "attestationApplicationId": {
      "packageInfos": [
        {
          "packageName": "com.example.trustedapplication",
          "version": 1
        }
      ],
      "signatureDigests": [
        "88E5C393EAEF36829800B41DF786A52FF0A58215850CA8A65073859ADCF0190F"
      ]
    },
    "attestationApplicationIdBytes": "304B31253023041E636F6D2E6578616D706C652E747275737465646170706C69636174696F6E0201013122042088E5C393EAEF36829800B41DF786A52FF0A58215850CA8A65073859ADCF0190F",
    "attestationIdBrand": null,
    "attestationIdDevice": null,
    "attestationIdProduct": null,
    "attestationIdSerial": null,
    "attestationIdImei": null,
    "attestationIdSecondImei": null,
    "attestationIdMeid": null,
    "attestationIdManufacturer": null,
    "attestationIdModel": null,
    "vendorPatchLevel": null,
    "bootPatchLevel": null,
    "individualAttestation": false,
    "identityCredentialKey": false
  },
  "teeEnforced": {
    "purpose": [
      "SIGN",
      "VERIFY"
    ],
    "algorithm": "EC",
    "keySize": 256,
    "digest": [
      "NONE",
      "SHA_2_256"
    ],
    "padding": [],
    "ecCurve": "P_256",
    "rsaPublicExponent": null,
    "rollbackResistance": false,
    "activeDateTime": null,
    "originationExpireDateTime": null,
    "usageExpireDateTime": null,
    "noAuthRequired": true,
    "userAuthType": [],
    "authTimeout": null,
    "allowWhileOnBody": false,
    "trustedUserPresenceRequired": false,
    "trustedConfirmationRequired": false,
    "unlockedDeviceRequired": false,
    "allApplications": false,
    "applicationId": null,
    "creationDateTime": null,
    "origin": "GENERATED",
    "rollbackResistant": true,
    "rootOfTrust": null,
    "osVersion": null,
    "osPatchLevel": null,
    "attestationApplicationId": null,
    "attestationApplicationIdBytes": null,
    "attestationIdBrand": null,
    "attestationIdDevice": null,
    "attestationIdProduct": null,
    "attestationIdSerial": null,
    "attestationIdImei": null,
    "attestationIdSecondImei": null,
    "attestationIdMeid": null,
    "attestationIdManufacturer": null,
    "attestationIdModel": null,
    "vendorPatchLevel": null,
    "bootPatchLevel": null,
    "individualAttestation": false,
    "identityCredentialKey": false
  },
  "attestedKey": {
    "algorithm": "EC",
    "format": "X.509",
    "encoded": "3059301306072A8648CE3D020106082A8648CE3D03010703420004A17E5E5A4C6C24E936CFA4B9B5C96DE9B3B22614B76FEDBE50BC773B7CD903014DADB58FE76627433A7F96C7B123DF65C7F679351CC9F71D1A0CB748711FC0CA"
  }
}
```

Attestation certificates can also be read from a file (need to be PEM-encoded, but can also be plain base64 MIME-encoded):
```shell
java -jar attestation-diag-0.0.1-all.jar -f cert.pem
```

(Some) illegal characters are stripped from the base64 input for convenience, which means that dirty base64 also somewhat works.

**Note:** Pretty-printing is done using Gson (in order to leave the upstream code untouched), which also means that it relies
on reflective access to platform types. Hence, this jar will only run on the same Java version it was built with!

<br>

---
<p align="center">
This project has received funding from the European Union’s Horizon 2020 research and innovation
programme under grant agreement No 959072.
</p>
<p align="center">
<img src="eu.svg" alt="EU flag">
</p>
