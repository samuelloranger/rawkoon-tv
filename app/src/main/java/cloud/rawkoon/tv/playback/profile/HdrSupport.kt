package cloud.rawkoon.tv.playback.profile

import cloud.rawkoon.tv.data.HdrOverride
import org.jellyfin.sdk.model.api.VideoRangeType

/** Whether a format counts as supported: what the TV reports ([detected]) unless the user forces it either way. */
fun HdrOverride.resolve(detected: Boolean): Boolean = when (this) {
	HdrOverride.AUTO -> detected
	HdrOverride.ALWAYS -> true
	HdrOverride.NEVER -> false
}

// TODO Use VideoRangeType enum with Jellyfin 10.11 based SDK
/** The range types the server must not direct play in an AV1 file, given what is supported. */
fun unsupportedAv1RangeTypes(
	jellyfinTenEleven: Boolean,
	dolbyVision: Boolean,
	hdr10: Boolean,
	hdr10Plus: Boolean,
): Set<String> = buildSet {
	if (jellyfinTenEleven) add("DOVIInvalid")

	if (!dolbyVision) {
		add(VideoRangeType.DOVI.serialName)
		if (!hdr10) add(VideoRangeType.DOVI_WITH_HDR10.serialName)
		if (jellyfinTenEleven && !hdr10Plus) add("DOVIWithHDR10Plus")
	}

	if (!hdr10Plus) {
		add(VideoRangeType.HDR10_PLUS.serialName)

		if (!hdr10) add(VideoRangeType.HDR10.serialName)
	}
}

/** The range types the server must not direct play in a HEVC file, given what is supported. */
fun unsupportedHevcRangeTypes(
	jellyfinTenEleven: Boolean,
	dolbyVision: Boolean,
	dolbyVisionEL: Boolean,
	hdr10: Boolean,
	hdr10Plus: Boolean,
	doviHdr10PlusBug: Boolean,
): Set<String> = buildSet {
	if (jellyfinTenEleven) add("DOVIInvalid")

	if (!dolbyVisionEL) {
		if (jellyfinTenEleven) {
			add("DOVIWithEL")
			if (!hdr10Plus && !doviHdr10PlusBug) add("DOVIWithELHDR10Plus")
		}

		if (!dolbyVision) {
			add(VideoRangeType.DOVI.serialName)
			if (!hdr10) add(VideoRangeType.DOVI_WITH_HDR10.serialName)
			if (jellyfinTenEleven && !hdr10Plus && !doviHdr10PlusBug) add("DOVIWithHDR10Plus")
		}
	}

	if (!hdr10Plus) {
		add(VideoRangeType.HDR10_PLUS.serialName)
		if (!hdr10) add(VideoRangeType.HDR10.serialName)
	}

	if (jellyfinTenEleven && doviHdr10PlusBug) {
		add("DOVIWithHDR10Plus")
		add("DOVIWithELHDR10Plus")
	}
}
