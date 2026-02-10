/**
 * SX style object for the "flash blue" animation.
 * Used to highlight elements upon navigation.
 */
export const flashBlueAnimation = {
	animation: "flash-blue 2s ease-out",
	"@keyframes flash-blue": {
		"0%": { backgroundColor: "rgba(25, 118, 210, 0.5)" },
		"100%": { backgroundColor: "transparent" },
	},
};
