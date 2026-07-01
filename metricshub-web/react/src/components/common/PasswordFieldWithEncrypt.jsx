import * as React from "react";
import {
	Button,
	CircularProgress,
	IconButton,
	InputAdornment,
	Stack,
	TextField,
} from "@mui/material";
import Visibility from "@mui/icons-material/Visibility";
import VisibilityOff from "@mui/icons-material/VisibilityOff";
import { configApi } from "../../api/config";
import { useSnackbar } from "../../hooks/use-snackbar";
import { encodeUtf8ToBase64 } from "../../utils/base64-utf8";

/** Minimum time to show the encrypt spinner so the UI does not flash on fast responses. */
const ENCRYPT_SPINNER_MIN_MS = 400;

function delay(ms) {
	return new Promise((resolve) => {
		setTimeout(resolve, ms);
	});
}

/**
 * Heuristic: MetricsHub keystore ciphertext is Base64 (AES-GCM), typically long.
 *
 * @param {string} value
 * @returns {boolean}
 */
function looksEncrypted(value) {
	const s = String(value || "").trim();
	if (s.length < 32) {
		return false;
	}
	return /^[A-Za-z0-9+/=]+$/.test(s);
}

/**
 * Password field with show/hide toggle and an Encrypt action for keystore ciphertext.
 *
 * @param {object} props
 * @param {string} props.label
 * @param {string} props.value
 * @param {(value: string) => void} props.onChange
 * @param {boolean} [props.error]
 * @param {string} [props.helperText]
 * @param {boolean} [props.required]
 * @param {boolean} [props.disabled]
 */
const PasswordFieldWithEncrypt = ({
	label,
	value,
	onChange,
	error = false,
	helperText,
	required = false,
	disabled = false,
}) => {
	const { show: showSnackbar } = useSnackbar();
	const [encrypting, setEncrypting] = React.useState(false);
	const [showPassword, setShowPassword] = React.useState(false);

	const encrypted = looksEncrypted(value);
	const showEncrypt = !encrypted && String(value || "").trim().length > 0;

	const handleEncrypt = async () => {
		if (encrypting || disabled || !showEncrypt) {
			return;
		}
		const plain = String(value || "");
		const startedAt = Date.now();
		setEncrypting(true);
		try {
			const ciphertext = await configApi.encryptPassword(encodeUtf8ToBase64(plain));
			onChange(ciphertext);
			setShowPassword(false);
			showSnackbar("Password encrypted", { severity: "success" });
		} catch (e) {
			showSnackbar(e?.message || "Password encryption failed", { severity: "error" });
		} finally {
			const elapsed = Date.now() - startedAt;
			if (elapsed < ENCRYPT_SPINNER_MIN_MS) {
				await delay(ENCRYPT_SPINNER_MIN_MS - elapsed);
			}
			setEncrypting(false);
		}
	};

	const resolvedHelperText =
		helperText || (encrypted ? "Password is encrypted and ready to be stored." : undefined);

	return (
		<TextField
			fullWidth
			label={label}
			type={showPassword ? "text" : "password"}
			value={value ?? ""}
			onChange={(e) => onChange(e.target.value)}
			error={error}
			helperText={resolvedHelperText}
			required={required}
			disabled={disabled || encrypting}
			autoComplete="off"
			slotProps={{
				input: {
					endAdornment: (
						<InputAdornment position="end">
							<Stack direction="row" alignItems="center" spacing={0.25}>
								<IconButton
									aria-label={showPassword ? "Hide password" : "Show password"}
									onClick={() => setShowPassword((v) => !v)}
									edge="end"
									size="small"
									disabled={disabled || encrypting || !String(value || "").length}
								>
									{showPassword ? (
										<VisibilityOff fontSize="small" />
									) : (
										<Visibility fontSize="small" />
									)}
								</IconButton>
								{showEncrypt && (
									<Button
										size="small"
										variant="outlined"
										onClick={() => void handleEncrypt()}
										disabled={disabled || encrypting}
										startIcon={
											encrypting ? (
												<CircularProgress size={14} color="inherit" aria-label="Encrypting" />
											) : null
										}
										sx={{ ml: 0.5 }}
									>
										{encrypting ? "Encrypting…" : "Encrypt"}
									</Button>
								)}
							</Stack>
						</InputAdornment>
					),
				},
			}}
		/>
	);
};

export default PasswordFieldWithEncrypt;
