package com.gymtracker.app.data.scale

import java.util.UUID
import kotlin.math.roundToInt

enum class ScaleProtocol {
    BODY_COMPOSITION_181B,
    WEIGHT_SCALE_181D
}

data class ParsedScalePacket(
    val protocol: ScaleProtocol,
    val weightKg: Double,
    val isStabilized: Boolean,
    val isRemoved: Boolean,
    val hasImpedance: Boolean,
    val impedanceOhm: Int?,
    val unitLabel: String
)

object XiaomiScaleBleParser {

    /** Mi Body Composition Scale 1 / 2 (XMTZC02HM / XMTZC05HM) — Weight + BIA Impedance */
    val UUID_BODY_COMPOSITION: UUID = UUID.fromString("0000181b-0000-1000-8000-00805f9b34fb")

    /** Mi Smart Scale 1 / 2 (XMTZC01HM / XMTZC04HM) — Weight only */
    val UUID_WEIGHT_SCALE: UUID = UUID.fromString("0000181d-0000-1000-8000-00805f9b34fb")

    private const val LBS_TO_KG = 0.45359237
    private const val MIN_VALID_KG = 2.0
    private const val MAX_VALID_KG = 250.0
    private const val MIN_VALID_IMPEDANCE = 100
    private const val MAX_VALID_IMPEDANCE = 3000

    fun parseServiceData(serviceDataByUuid: Map<UUID, ByteArray>): ParsedScalePacket? {
        serviceDataByUuid[UUID_BODY_COMPOSITION]?.let { bytes ->
            parseBodyComposition181B(bytes)?.let { return it }
        }
        serviceDataByUuid[UUID_WEIGHT_SCALE]?.let { bytes ->
            parseWeightScale181D(bytes)?.let { return it }
        }
        return null
    }

    /**
     * Parses BLE Service Data for GATT Service 0x181B (Mi Body Composition Scale 2 / XMTZC05HM).
     * Frame layout (13 bytes, little-endian):
     *   [0]    ctrl0: bit 0 = lbs
     *   [1]    ctrl1: bit 1 = hasImpedance (0x02), bit 5 = stabilized (0x20),
     *                 bit 6 = catty/jin (0x40), bit 7 = weightRemoved (0x80)
     *   [2..8] timestamp (YYYY, M, D, H, m, s)
     *   [9..10] impedance uint16 LE (Ohms)
     *   [11..12] weight uint16 LE (raw/200 kg, or raw/100 lbs / jin)
     */
    fun parseBodyComposition181B(data: ByteArray): ParsedScalePacket? {
        if (data.size < 13) return null
        val ctrl0 = data[0].toInt() and 0xFF
        val ctrl1 = data[1].toInt() and 0xFF

        val isLbs = (ctrl0 and 0x01) != 0
        val isCatty = (ctrl1 and 0x40) != 0
        val hasImpedanceFlag = (ctrl1 and 0x02) != 0
        val isStabilized = (ctrl1 and 0x20) != 0
        val isRemoved = (ctrl1 and 0x80) != 0

        val rawImpedance = (data[9].toInt() and 0xFF) or ((data[10].toInt() and 0xFF) shl 8)
        val rawWeight = (data[11].toInt() and 0xFF) or ((data[12].toInt() and 0xFF) shl 8)

        val (weightKg, unitLabel) = convertRawWeight(rawWeight, isLbs = isLbs, isCatty = isCatty)
            ?: return null

        val impedanceOhm = if (hasImpedanceFlag && rawImpedance in MIN_VALID_IMPEDANCE..MAX_VALID_IMPEDANCE) {
            rawImpedance
        } else {
            null
        }

        return ParsedScalePacket(
            protocol = ScaleProtocol.BODY_COMPOSITION_181B,
            weightKg = weightKg,
            isStabilized = isStabilized,
            isRemoved = isRemoved,
            hasImpedance = impedanceOhm != null,
            impedanceOhm = impedanceOhm,
            unitLabel = unitLabel
        )
    }

    /**
     * Parses BLE Service Data for GATT Service 0x181D (Mi Smart Scale 1 / 2 / XMTZC01HM, XMTZC04HM).
     * Frame layout (10 bytes, little-endian):
     *   [0]    ctrl: bit 0 = lbs (0x01), bit 4 = catty/jin (0x10),
     *                bit 5 = stabilized (0x20), bit 7 = weightRemoved (0x80)
     *   [1..2] weight uint16 LE
     *   [3..9] timestamp
     */
    fun parseWeightScale181D(data: ByteArray): ParsedScalePacket? {
        if (data.size < 10) return null
        val ctrl = data[0].toInt() and 0xFF

        val isLbs = (ctrl and 0x01) != 0
        val isCatty = (ctrl and 0x10) != 0
        val isStabilized = (ctrl and 0x20) != 0
        val isRemoved = (ctrl and 0x80) != 0

        val rawWeight = (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
        val (weightKg, unitLabel) = convertRawWeight(rawWeight, isLbs = isLbs, isCatty = isCatty)
            ?: return null

        return ParsedScalePacket(
            protocol = ScaleProtocol.WEIGHT_SCALE_181D,
            weightKg = weightKg,
            isStabilized = isStabilized,
            isRemoved = isRemoved,
            hasImpedance = false,
            impedanceOhm = null,
            unitLabel = unitLabel
        )
    }

    fun isWithinUserRange(weightKg: Double, minKg: Double, maxKg: Double): Boolean {
        val lo = minOf(minKg, maxKg)
        val hi = maxOf(minKg, maxKg)
        return weightKg in lo..hi
    }

    private fun convertRawWeight(rawWeight: Int, isLbs: Boolean, isCatty: Boolean): Pair<Double, String>? {
        val kg = when {
            isLbs -> (rawWeight / 100.0) * LBS_TO_KG
            isCatty -> (rawWeight / 100.0) * 0.5
            else -> rawWeight / 200.0
        }
        val roundedKg = (kg * 100.0).roundToInt() / 100.0
        if (roundedKg < MIN_VALID_KG || roundedKg > MAX_VALID_KG) return null
        val unitLabel = when {
            isLbs -> "lbs"
            isCatty -> "jin"
            else -> "kg"
        }
        return roundedKg to unitLabel
    }
}
