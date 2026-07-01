import * as React from "react";
import { IconButton, InputAdornment, TextField } from "@mui/material";
import Visibility from "@mui/icons-material/Visibility";
import VisibilityOff from "@mui/icons-material/VisibilityOff";
import { useSnackbar } from "../../hooks/use-snackbar";
import {
	CREATE_WIZARD_PASSWORD_HELPER_TEXT,
	PASSWORD_ENCRYPT_HELPER_TEXT,
	delay,
	encryptPlainPassword,
	ENCRYPT_SPINNER_MIN_MS,
	looksEncrypted,
} from "../../utils/password-encrypt";

/**
 * Password field for protocol configuration in the host wizard / edit form.
 *
 * @param {object} props
 * @param {string} props.label
 * @param {string} props.value
 * @param {(value: string) => void} props.onChange
 * @param {boolean} [props.error]
 * @param {string} [props.helperText]
 * @param {boolean} [props.required]
 * @param {boolean} [props.disabled]
 * @param {boolean} [props.allowReveal] create wizard: show/hide toggle
 * @param {boolean} [props.deferEncryptUntilSave] skip blur encryption (encrypt on submit only)
 * @param {React.ReactNode} [props.startAdornment] icon node placed at the start of the input
 * @param {boolean} [props.hiddenLabel] use when the label is rendered outside the field
 * @param {"small" | "medium"} [props.size]
 */
const ProtocolPasswordField = ({
	label,
	value,
	onChange,
	error = false,
	helperText,
	required = false,
	disabled = false,
	allowReveal = false,
	deferEncryptUntilSave = false,
	startAdornment = null,
	hiddenLabel = false,
	size = "medium",
}) => {
	const { show: showSnackbar } = useSnackbar();
	const [encrypting, setEncrypting] = React.useState(false);
	const [showPassword, setShowPassword] = React.useState(false);
	const userEditedRef = React.useRef(false);
	const skipNextBlurRef = React.useRef(false);

	const encrypted = looksEncrypted(value);
	const canReveal = allowReveal && !encrypted;

	const resolvedHelperText =
		helperText ||
		(encrypted
			? "Password is encrypted and ready to be stored."
			: deferEncryptUntilSave
				? undefined
				: allowReveal
					? CREATE_WIZARD_PASSWORD_HELPER_TEXT
					: PASSWORD_ENCRYPT_HELPER_TEXT);

	React.useEffect(() => {
		if (encrypted) {
			setShowPassword(false);
		}
	}, [encrypted]);

	const handleBlur = async () => {
		if (allowReveal || deferEncryptUntilSave) {
			return;
		}
		if (skipNextBlurRef.current) {
			skipNextBlurRef.current = false;
			return;
		}
		if (!userEditedRef.current || encrypting || disabled) {
			return;
		}
		const plain = String(value || "").trim();
		if (!plain || looksEncrypted(plain)) {
			userEditedRef.current = false;
			return;
		}
		const startedAt = Date.now();
		setEncrypting(true);
		try {
			const ciphertext = await encryptPlainPassword(plain);
			skipNextBlurRef.current = true;
			onChange(ciphertext);
			userEditedRef.current = false;
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

	return (
		<TextField
			fullWidth
			label={hiddenLabel ? undefined : label}
			hiddenLabel={hiddenLabel}
			size={size}
			type={canReveal && showPassword ? "text" : "password"}
			value={value ?? ""}
			onChange={(e) => {
				userEditedRef.current = true;
				onChange(e.target.value);
			}}
			onBlur={() => void handleBlur()}
			error={error}
			helperText={encrypting ? "Encrypting password…" : resolvedHelperText}
			required={required}
			disabled={disabled || encrypting}
			autoComplete="new-password"
			slotProps={{
				input: {
					startAdornment: startAdornment ? (
						<InputAdornment
							position="start"
							sx={{
								marginTop: 0,
								marginBottom: 0,
								marginLeft: 0,
								marginRight: 1.25,
								height: "auto",
								maxHeight: "none",
								display: "inline-flex",
								alignItems: "center",
								alignSelf: "center",
							}}
						>
							{startAdornment}
						</InputAdornment>
					) : undefined,
					endAdornment: (
						<InputAdornment position="end">
							{canReveal && (
								<IconButton
									aria-label={showPassword ? "Hide password" : "Show password"}
									onClick={() => setShowPassword((v) => !v)}
									onMouseDown={(e) => e.preventDefault()}
									edge="end"
									size="small"
									disabled={disabled}
								>
									{showPassword ? (
										<VisibilityOff fontSize="small" />
									) : (
										<Visibility fontSize="small" />
									)}
								</IconButton>
							)}
						</InputAdornment>
					),
				},
			}}
		/>
	);
};

export default ProtocolPasswordField;
