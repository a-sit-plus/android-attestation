package at.asitplus.attestation.android

import at.asitplus.attestation.android.exceptions.AndroidAttestationException
import at.asitplus.attestation.android.exceptions.AttestationValueException
import com.google.android.attestation.ParsedAttestationRecord
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import java.util.*

class HardwareAttestationChecker @JvmOverloads constructor(
    attestationConfiguration: AndroidAttestationConfiguration,
    verifyChallenge: (expected: ByteArray, actual: ByteArray) -> Boolean = { expected, actual -> expected contentEquals actual }
) : AndroidAttestationChecker(attestationConfiguration, verifyChallenge) {

    init {
        if (attestationConfiguration.disableHardwareAttestation) throw object :
            AndroidAttestationException("Hardware attestation is disabled!", null) {}
        if (attestationConfiguration.hardwareAttestationTrustAnchors.isEmpty()) throw object :
            AndroidAttestationException("No hardware attestation trust anchors configured", null) {}
    }

    @Throws(AttestationValueException::class)
    override fun ParsedAttestationRecord.verifySecurityLevel() {
        if (attestationConfiguration.requireStrongBox) {
            if (attestationSecurityLevel() != ParsedAttestationRecord.SecurityLevel.STRONG_BOX)
                throw AttestationValueException(
                    "Attestation security level not StrongBox",
                    reason = AttestationValueException.Reason.SEC_LEVEL
                )
            if (keymasterSecurityLevel() != ParsedAttestationRecord.SecurityLevel.STRONG_BOX)
                throw AttestationValueException(
                    "Keymaster security level not StrongBox",
                    reason = AttestationValueException.Reason.SEC_LEVEL
                )
        } else {
            if (attestationSecurityLevel() == ParsedAttestationRecord.SecurityLevel.SOFTWARE)
                throw AttestationValueException(
                    "Attestation security level software",
                    reason = AttestationValueException.Reason.SEC_LEVEL
                )
            if (keymasterSecurityLevel() == ParsedAttestationRecord.SecurityLevel.SOFTWARE)
                throw AttestationValueException(
                    "Keymaster security level software",
                    reason = AttestationValueException.Reason.SEC_LEVEL
                )
        }
    }

    override val trustAnchors = attestationConfiguration.hardwareAttestationTrustAnchors

    @Throws(AttestationValueException::class)
    override fun ParsedAttestationRecord.verifyAndroidVersion(versionOverride: Int?, osPatchLevel: PatchLevel?) =
        teeEnforced().verifyAndroidVersion(versionOverride, osPatchLevel)

    @Throws(AttestationValueException::class)
    override fun ParsedAttestationRecord.verifyBootStateAndSystemImage() = teeEnforced().verifySystemLocked()

    @Throws(AttestationValueException::class)
    override fun ParsedAttestationRecord.verifyRollbackResistance() = teeEnforced().verifyRollbackResistance()
}