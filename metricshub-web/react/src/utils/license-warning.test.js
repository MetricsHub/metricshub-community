import { describe, it, expect } from "vitest";
import { getLicenseWarning } from "./license-warning";

describe("getLicenseWarning", () => {
	describe("Enterprise license with no valid license", () => {
		it("returns error severity when licenseDaysRemaining is null for Enterprise", () => {
			const result = getLicenseWarning({
				licenseDaysRemaining: null,
				licenseType: "Enterprise",
			});

			expect(result).not.toBeNull();
			expect(result.severity).toBe("error");
			expect(result.message).toContain("No valid enterprise license was detected");
			expect(result.includeSupportLink).toBe(true);
		});

		it("returns error severity when licenseDaysRemaining is undefined for Enterprise", () => {
			const result = getLicenseWarning({
				licenseDaysRemaining: undefined,
				licenseType: "Enterprise",
			});

			expect(result).not.toBeNull();
			expect(result.severity).toBe("error");
			expect(result.message).toContain("No valid enterprise license was detected");
		});
	});

	describe("Community license with no valid license", () => {
		it("returns null when licenseDaysRemaining is null for non-Enterprise", () => {
			const result = getLicenseWarning({
				licenseDaysRemaining: null,
				licenseType: "Community",
			});

			expect(result).toBeNull();
		});

		it("returns null when licenseDaysRemaining is undefined without licenseType", () => {
			const result = getLicenseWarning({
				licenseDaysRemaining: undefined,
			});

			expect(result).toBeNull();
		});
	});

	describe("Expired license", () => {
		it("returns error severity when license has expired (0 days)", () => {
			const result = getLicenseWarning({
				licenseDaysRemaining: 0,
				licenseType: "Enterprise",
			});

			expect(result).not.toBeNull();
			expect(result.severity).toBe("error");
			expect(result.message).toContain("License has expired");
			expect(result.includeSupportLink).toBe(true);
			expect(result.suffix).toBe(".");
		});

		it("returns error severity when license is past expiry (negative days)", () => {
			const result = getLicenseWarning({
				licenseDaysRemaining: -5,
				licenseType: "Enterprise",
			});

			expect(result).not.toBeNull();
			expect(result.severity).toBe("error");
			expect(result.message).toContain("License has expired");
		});
	});

	describe("License expiring soon", () => {
		it("returns error severity when license expires in 1 day", () => {
			const result = getLicenseWarning({
				licenseDaysRemaining: 1,
				licenseType: "Enterprise",
			});

			expect(result).not.toBeNull();
			expect(result.severity).toBe("error");
			expect(result.message).toContain("License expires in 1 days");
			expect(result.includeSupportLink).toBe(true);
			expect(result.suffix).toBe(" if you need assistance.");
		});

		it("returns error severity when license expires in 6 days", () => {
			const result = getLicenseWarning({
				licenseDaysRemaining: 6,
				licenseType: "Community",
			});

			expect(result).not.toBeNull();
			expect(result.severity).toBe("error");
			expect(result.message).toContain("License expires in 6 days");
		});
	});

	describe("License expiring warning threshold", () => {
		it("returns warning severity when license expires in 7 days (boundary)", () => {
			const result = getLicenseWarning({
				licenseDaysRemaining: 7,
				licenseType: "Community",
			});

			expect(result).not.toBeNull();
			expect(result.severity).toBe("warning");
			expect(result.message).toContain("License expires in 7 days");
			expect(result.includeSupportLink).toBe(true);
		});

		it("returns warning severity when license expires in 29 days", () => {
			const result = getLicenseWarning({
				licenseDaysRemaining: 29,
				licenseType: "Enterprise",
			});

			expect(result).not.toBeNull();
			expect(result.severity).toBe("warning");
			expect(result.message).toContain("License expires in 29 days");
		});
	});

	describe("License not expiring", () => {
		it("returns null when license expires in 30 days (boundary)", () => {
			const result = getLicenseWarning({
				licenseDaysRemaining: 30,
				licenseType: "Enterprise",
			});

			expect(result).toBeNull();
		});

		it("returns null when license expires in 90 days", () => {
			const result = getLicenseWarning({
				licenseDaysRemaining: 90,
				licenseType: "Community",
			});

			expect(result).toBeNull();
		});
	});

	describe("Return value structure", () => {
		it("includes all required fields when warning is returned", () => {
			const result = getLicenseWarning({
				licenseDaysRemaining: 5,
				licenseType: "Enterprise",
			});

			expect(result).toHaveProperty("severity");
			expect(result).toHaveProperty("message");
			expect(result).toHaveProperty("includeSupportLink");
			expect(result).toHaveProperty("suffix");
			expect(typeof result.message).toBe("string");
			expect(typeof result.includeSupportLink).toBe("boolean");
		});
	});
});
