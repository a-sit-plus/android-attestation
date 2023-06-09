/* Copyright 2019, The Android Open Source Project, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.attestation;

import com.google.common.collect.ImmutableMap;
import org.bouncycastle.asn1.*;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.List;

import static com.google.android.attestation.Constants.*;

/** Java representation of Key Attestation extension data. */
public class ParsedAttestationRecord {

  public final int attestationVersion;
  public final SecurityLevel attestationSecurityLevel;
  public final int keymasterVersion;
  public final SecurityLevel keymasterSecurityLevel;
  public final byte[] attestationChallenge;
  public final byte[] uniqueId;
  public final AuthorizationList softwareEnforced;
  public final AuthorizationList teeEnforced;

  private ParsedAttestationRecord(ASN1Sequence extensionData) {
    this.attestationVersion =
        ASN1Parsing.getIntegerFromAsn1(extensionData.getObjectAt(ATTESTATION_VERSION_INDEX));
    this.attestationSecurityLevel =
        securityLevelToEnum(
            ASN1Parsing.getIntegerFromAsn1(
                extensionData.getObjectAt(ATTESTATION_SECURITY_LEVEL_INDEX)));
    this.keymasterVersion =
        ASN1Parsing.getIntegerFromAsn1(extensionData.getObjectAt(KEYMASTER_VERSION_INDEX));
    this.keymasterSecurityLevel =
        securityLevelToEnum(
            ASN1Parsing.getIntegerFromAsn1(
                extensionData.getObjectAt(KEYMASTER_SECURITY_LEVEL_INDEX)));
    this.attestationChallenge =
        ((ASN1OctetString) extensionData.getObjectAt(ATTESTATION_CHALLENGE_INDEX)).getOctets();
    this.uniqueId = ((ASN1OctetString) extensionData.getObjectAt(UNIQUE_ID_INDEX)).getOctets();
    this.softwareEnforced =
        AuthorizationList.createAuthorizationList(
            ((ASN1Sequence) extensionData.getObjectAt(SW_ENFORCED_INDEX)).toArray(),
            attestationVersion);
    this.teeEnforced =
        AuthorizationList.createAuthorizationList(
            ((ASN1Sequence) extensionData.getObjectAt(TEE_ENFORCED_INDEX)).toArray(),
            attestationVersion);
  }

  private ParsedAttestationRecord(
      int attestationVersion,
      SecurityLevel attestationSecurityLevel,
      int keymasterVersion,
      SecurityLevel keymasterSecurityLevel,
      byte[] attestationChallenge,
      byte[] uniqueId,
      AuthorizationList softwareEnforced,
      AuthorizationList teeEnforced) {
    this.attestationVersion = attestationVersion;
    this.attestationSecurityLevel = attestationSecurityLevel;
    this.keymasterVersion = keymasterVersion;
    this.keymasterSecurityLevel = keymasterSecurityLevel;
    this.attestationChallenge = attestationChallenge;
    this.uniqueId = uniqueId;
    this.softwareEnforced = softwareEnforced;
    this.teeEnforced = teeEnforced;
  }

  public static ParsedAttestationRecord createParsedAttestationRecord(List<X509Certificate> certs)
      throws IOException {

    // Parse the attestation record that is closest to the root. This prevents an adversary from
    // attesting an attestation record of their choice with an otherwise trusted chain using the
    // following attack:
    // 1) having the TEE attest a key under the adversary's control,
    // 2) using that key to sign a new leaf certificate with an attestation extension that has their chosen attestation record, then
    // 3) appending that certificate to the original certificate chain.
    for (int i = certs.size() - 1; i >= 0; i--) {
      byte[] attestationExtensionBytes = certs.get(i).getExtensionValue(KEY_DESCRIPTION_OID);
      if (attestationExtensionBytes != null && attestationExtensionBytes.length != 0) {
        return new ParsedAttestationRecord(extractAttestationSequence(attestationExtensionBytes));
      }
    }

    throw new IllegalArgumentException("Couldn't find the keystore attestation extension data.");
  }

  public static ParsedAttestationRecord create(ASN1Sequence extensionData) {
    return new ParsedAttestationRecord(extensionData);
  }

  public static ParsedAttestationRecord create(
      int attestationVersion,
      SecurityLevel attestationSecurityLevel,
      int keymasterVersion,
      SecurityLevel keymasterSecurityLevel,
      byte[] attestationChallenge,
      byte[] uniqueId,
      AuthorizationList softwareEnforced,
      AuthorizationList teeEnforced) {
    return new ParsedAttestationRecord(
        attestationVersion,
        attestationSecurityLevel,
        keymasterVersion,
        keymasterSecurityLevel,
        attestationChallenge,
        uniqueId,
        softwareEnforced,
        teeEnforced);
  }

  private static SecurityLevel securityLevelToEnum(int securityLevel) {
    switch (securityLevel) {
      case KM_SECURITY_LEVEL_SOFTWARE:
        return SecurityLevel.SOFTWARE;
      case KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT:
        return SecurityLevel.TRUSTED_ENVIRONMENT;
      case KM_SECURITY_LEVEL_STRONG_BOX:
        return SecurityLevel.STRONG_BOX;
      default:
        throw new IllegalArgumentException("Invalid security level.");
    }
  }

  private static int securityLevelToInt(SecurityLevel securityLevel) {
    switch (securityLevel) {
      case SOFTWARE:
        return KM_SECURITY_LEVEL_SOFTWARE;
      case TRUSTED_ENVIRONMENT:
        return KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT;
      case STRONG_BOX:
        return KM_SECURITY_LEVEL_STRONG_BOX;
    }
    throw new IllegalArgumentException("Invalid security level.");
  }

  private static ASN1Sequence extractAttestationSequence(byte[] attestationExtensionBytes)
      throws IOException {
    ASN1Sequence decodedSequence;
    try (ASN1InputStream asn1InputStream = new ASN1InputStream(attestationExtensionBytes)) {
      // The extension contains one object, a sequence, in the
      // Distinguished Encoding Rules (DER)-encoded form. Get the DER
      // bytes.
      byte[] derSequenceBytes = ((ASN1OctetString) asn1InputStream.readObject()).getOctets();
      // Decode the bytes as an ASN1 sequence object.
      try (ASN1InputStream seqInputStream = new ASN1InputStream(derSequenceBytes)) {
        decodedSequence = (ASN1Sequence) seqInputStream.readObject();
      }
    }
    return decodedSequence;
  }

  public ASN1Sequence toAsn1Sequence() {
    ASN1Encodable[] vector = new ASN1Encodable[8];
    vector[ATTESTATION_VERSION_INDEX] = new ASN1Integer(this.attestationVersion);
    vector[ATTESTATION_SECURITY_LEVEL_INDEX] =
        new ASN1Enumerated(securityLevelToInt(this.attestationSecurityLevel));
    vector[KEYMASTER_VERSION_INDEX] = new ASN1Integer(this.keymasterVersion);
    vector[KEYMASTER_SECURITY_LEVEL_INDEX] =
        new ASN1Enumerated(securityLevelToInt(this.keymasterSecurityLevel));
    vector[ATTESTATION_CHALLENGE_INDEX] = new DEROctetString(this.attestationChallenge);
    vector[UNIQUE_ID_INDEX] = new DEROctetString(this.uniqueId);
    if (this.softwareEnforced != null) {
      vector[SW_ENFORCED_INDEX] = this.softwareEnforced.toAsn1Sequence();
    }
    if (this.teeEnforced != null) {
      vector[TEE_ENFORCED_INDEX] = this.teeEnforced.toAsn1Sequence();
    }
    return new DERSequence(vector);
  }

  /**
   * This indicates the extent to which a software feature, such as a key pair, is protected based
   * on its location within the device.
   */
  public enum SecurityLevel {
    SOFTWARE,
    TRUSTED_ENVIRONMENT,
    STRONG_BOX
  }

  /** This indicates the version of the key attestation feature. */
  public enum AttestationVersion {
    KEYMASTER_2_0,
    KEYMASTER_3_0,
    KEYMASTER_4_0,
    KEYMASTER_4_1,
    KEYMINT_1_0,
    KEYMINT_2_0,
  }

  public static final ImmutableMap<AttestationVersion, Integer> ATTESTATION_VERSION_TO_ASN1 =
      ImmutableMap.of(
          AttestationVersion.KEYMASTER_2_0,
          1,
          AttestationVersion.KEYMASTER_3_0,
          2,
          AttestationVersion.KEYMASTER_4_0,
          3,
          AttestationVersion.KEYMASTER_4_1,
          4,
          AttestationVersion.KEYMINT_1_0,
          100,
          AttestationVersion.KEYMINT_2_0,
          200);
  public static final ImmutableMap<Integer, AttestationVersion> ASN1_TO_ATTESTATION_VERSION =
      ImmutableMap.of(
          1, AttestationVersion.KEYMASTER_2_0,
          2, AttestationVersion.KEYMASTER_3_0,
          3, AttestationVersion.KEYMASTER_4_0,
          4, AttestationVersion.KEYMASTER_4_1,
          100, AttestationVersion.KEYMINT_1_0,
          200, AttestationVersion.KEYMINT_2_0);

  /** This indicates the version of the key mint or key master feature. */
  public enum KeyMintVersion {
    KEYMASTER_0_2_OR_3,
    KEYMASTER_1_0,
    KEYMASTER_2_0,
    KEYMASTER_3_0,
    KEYMASTER_4_0,
    KEYMASTER_4_1,
    KEYMINT_1_0,
    KEYMINT_2_0,
  }

  public static final ImmutableMap<KeyMintVersion, Integer> KEYMINT_VERSION_TO_ASN1 =
      ImmutableMap.of(
          KeyMintVersion.KEYMASTER_0_2_OR_3,
          0,
          KeyMintVersion.KEYMASTER_1_0,
          1,
          KeyMintVersion.KEYMASTER_2_0,
          2,
          KeyMintVersion.KEYMASTER_3_0,
          3,
          KeyMintVersion.KEYMASTER_4_0,
          4,
          KeyMintVersion.KEYMASTER_4_1,
          41,
          KeyMintVersion.KEYMINT_1_0,
          100,
          KeyMintVersion.KEYMINT_2_0,
          200);

  public static final ImmutableMap<Integer, KeyMintVersion> ASN1_TO_KEYMINT_VERSION =
      ImmutableMap.of(
          0, KeyMintVersion.KEYMASTER_0_2_OR_3,
          1, KeyMintVersion.KEYMASTER_1_0,
          2, KeyMintVersion.KEYMASTER_2_0,
          3, KeyMintVersion.KEYMASTER_3_0,
          4, KeyMintVersion.KEYMASTER_4_0,
          41, KeyMintVersion.KEYMASTER_4_1,
          100, KeyMintVersion.KEYMINT_1_0,
          200, KeyMintVersion.KEYMINT_2_0);
}
