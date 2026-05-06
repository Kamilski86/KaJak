package de.ca.qcc.domain.usecase

import de.ca.qcc.data.repository.MismatchRepository
import de.ca.qcc.domain.model.ComparisonResult
import javax.inject.Inject
import de.ca.qcc.domain.model.ComparisonStatus
import de.ca.qcc.domain.model.MismatchRecord
import de.ca.qcc.domain.model.QrScanResult
import de.ca.qcc.domain.model.RfidScanResult

class CompareScansUseCase @Inject constructor(
    private val mismatchRepository: MismatchRepository
) {
    suspend operator fun invoke(
        qr: QrScanResult,
        rfid: RfidScanResult
    ): ComparisonResult {
        if (qr.serial == null) return ComparisonResult(
            status = ComparisonStatus.MISMATCH,
            message = "QR enthält keine Seriennummer"
        )

        val qrSgtin = qr.sgtin
            ?: return ComparisonResult(
                status = ComparisonStatus.MISMATCH,
                message = "QR-SGTIN konnte nicht aufgebaut werden"
            )

        val qrNormalized = qrSgtin.trim().lowercase()
        val rfidNormalized = rfid.sgtin.trim().lowercase()

        val isMatch = qrNormalized == rfidNormalized

        mismatchRepository.addScan(
            gtinQr = qr.gtin,
            gtinTag = rfid.gtin,
            isMatch = isMatch,
            sgtinQr = qrNormalized,
            sgtinTag = rfidNormalized,
            qrRaw = qr.rawValue
        )

        return if (isMatch) {
            ComparisonResult(
                status = ComparisonStatus.MATCH,
                message = "SGTIN stimmt überein"
            )
        } else {
            mismatchRepository.add(
                MismatchRecord(
                    timestamp = System.currentTimeMillis(),
                    epc = rfid.epc,
                    sgtin = rfid.sgtin,
                    gtinTag = rfid.gtin,
                    gtinQr = qr.gtin
                )
            )

            ComparisonResult(
                status = ComparisonStatus.MISMATCH,
                message = "SGTIN stimmt nicht überein"
            )
        }
    }
}
