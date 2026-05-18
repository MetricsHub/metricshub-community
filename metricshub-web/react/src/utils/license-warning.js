/**
 * Build warning metadata for license status.
 *
 * @param {{licenseDaysRemaining?: number | null, licenseType?: string}} params
 * @returns {{severity: "error" | "warning", message: string, includeSupportLink: boolean, suffix: string} | null}
 */
export const getLicenseWarning = ({ licenseDaysRemaining, licenseType }) => {
	if (licenseDaysRemaining === null || licenseDaysRemaining === undefined) {
		if (licenseType === "Enterprise") {
			return {
				severity: "error",
				message:
					"No valid enterprise license was detected. If your trial period has expired, please",
				includeSupportLink: true,
				suffix: ".",
			};
		}

		return null;
	}

	if (licenseDaysRemaining <= 0) {
		return {
			severity: "error",
			message: "License has expired. Please",
			includeSupportLink: true,
			suffix: ".",
		};
	}

	if (licenseDaysRemaining < 7) {
		return {
			severity: "error",
			message: `License expires in ${licenseDaysRemaining} days. Contact`,
			includeSupportLink: true,
			suffix: " if you need assistance.",
		};
	}

	if (licenseDaysRemaining < 30) {
		return {
			severity: "warning",
			message: `License expires in ${licenseDaysRemaining} days. Contact`,
			includeSupportLink: true,
			suffix: " if you need assistance.",
		};
	}

	return null;
};
