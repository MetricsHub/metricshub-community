import * as React from "react";
import {
	Button,
	CircularProgress,
	Dialog,
	DialogContent,
	DialogTitle,
	IconButton,
	InputAdornment,
	Stack,
	TextField,
} from "@mui/material";
import VpnKeyIcon from "@mui/icons-material/VpnKey";
import CloseIcon from "@mui/icons-material/Close";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import Visibility from "@mui/icons-material/Visibility";
import VisibilityOff from "@mui/icons-material/VisibilityOff";
import { configApi } from "../../api/config";
import { encodeUtf8ToBase64 } from "../../utils/base64-utf8";
import { useSnackbar } from "../../hooks/use-snackbar";

async function copyTextToClipboard(text) {
	if (!text) return false;
	try {
		if (navigator.clipboard?.writeText) {
			await navigator.clipboard.writeText(text);
			return true;
		}
	} catch {
		// fall through
	}
	try {
		const ta = document.createElement("textarea");
		ta.value = text;
		ta.setAttribute("readonly", "");
		ta.style.position = "fixed";
		ta.style.left = "-9999px";
		document.body.appendChild(ta);
		ta.select();
		const ok = document.execCommand("copy");
		document.body.removeChild(ta);
		return ok;
	} catch {
		return false;
	}
}

/** Minimum time to show the encrypt spinner so the UI does not flash on fast responses. */
const ENCRYPT_SPINNER_MIN_MS = 400;

function delay(ms) {
	return new Promise((resolve) => {
		setTimeout(resolve, ms);
	});
}

/** Mask ciphertext in the UI: only the last 4 characters are visible; the rest are asterisks. */
function maskCiphertextForDisplay(ciphertext) {
	if (!ciphertext) return "";
	const n = ciphertext.length;
	if (n <= 4) {
		return "*".repeat(n);
	}
	return `${"*".repeat(n - 4)}${ciphertext.slice(-4)}`;
}

/** Opens a dialog to encrypt a password via the agent; ciphertext is copied to the clipboard. */
export default function EncryptPasswordDialog({
	disabled = false,
	size = "small",
	variant = "outlined",
}) {
	const { show: showSnackbar } = useSnackbar();
	const [open, setOpen] = React.useState(false);
	const [password, setPassword] = React.useState("");
	const [showPassword, setShowPassword] = React.useState(false);
	const [lastCiphertext, setLastCiphertext] = React.useState("");
	const [busy, setBusy] = React.useState(false);

	const handleClose = React.useCallback(() => {
		setOpen(false);
		setPassword("");
		setLastCiphertext("");
		setShowPassword(false);
		setBusy(false);
	}, []);

	const handleEncode = React.useCallback(async () => {
		if (busy || disabled) return;
		const transport = encodeUtf8ToBase64(password);
		const startedAt = Date.now();
		setBusy(true);
		try {
			const ciphertext = await configApi.encryptPassword(transport);
			setLastCiphertext(ciphertext);
			const copied = await copyTextToClipboard(ciphertext);
			if (copied) {
				showSnackbar("Encrypted password copied to clipboard", { severity: "success" });
			} else {
				showSnackbar("Encrypted; could not copy automatically — use the copy control", {
					severity: "warning",
				});
			}
		} catch (e) {
			showSnackbar(e?.message || "Password encryption failed", { severity: "error" });
			setLastCiphertext("");
		} finally {
			const elapsed = Date.now() - startedAt;
			if (elapsed < ENCRYPT_SPINNER_MIN_MS) {
				await delay(ENCRYPT_SPINNER_MIN_MS - elapsed);
			}
			setBusy(false);
		}
	}, [busy, disabled, password, showSnackbar]);

	const handleCopyAgain = React.useCallback(async () => {
		if (!lastCiphertext) return;
		const copied = await copyTextToClipboard(lastCiphertext);
		showSnackbar(copied ? "Copied to clipboard" : "Could not copy to clipboard", {
			severity: copied ? "success" : "error",
		});
	}, [lastCiphertext, showSnackbar]);

	return (
		<>
			<Button
				size={size}
				variant={variant}
				color="inherit"
				startIcon={<VpnKeyIcon />}
				onClick={() => setOpen(true)}
				disabled={disabled}
			>
				Encrypt password
			</Button>
			<Dialog
				open={open}
				onClose={handleClose}
				maxWidth="sm"
				fullWidth
				aria-labelledby="encrypt-pwd-title"
			>
				<DialogTitle
					id="encrypt-pwd-title"
					sx={{
						display: "flex",
						alignItems: "center",
						justifyContent: "space-between",
						gap: 1,
						pr: 1,
					}}
				>
					Encrypt password
					<IconButton
						type="button"
						aria-label="Close"
						onClick={handleClose}
						edge="end"
						size="small"
					>
						<CloseIcon />
					</IconButton>
				</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ pt: 1 }}>
						<TextField
							label="Password"
							type={showPassword ? "text" : "password"}
							value={password}
							onChange={(e) => {
								setPassword(e.target.value);
								setLastCiphertext("");
							}}
							onKeyDown={(e) => {
								if (e.key === "Enter" && !disabled && !busy) {
									e.preventDefault();
									void handleEncode();
								}
							}}
							fullWidth
							autoFocus
							autoComplete="off"
							slotProps={{
								input: {
									endAdornment: (
										<InputAdornment position="end">
											<IconButton
												aria-label={showPassword ? "Hide password" : "Show password"}
												onClick={() => setShowPassword((v) => !v)}
												edge="end"
												size="small"
											>
												{showPassword ? <VisibilityOff /> : <Visibility />}
											</IconButton>
										</InputAdornment>
									),
								},
							}}
						/>
						<TextField
							label="Encrypted password"
							value={maskCiphertextForDisplay(lastCiphertext)}
							fullWidth
							sx={{
								"& .MuiInputBase-input": {
									fontFamily: "ui-monospace, monospace",
								},
							}}
							slotProps={{
								htmlInput: {
									"aria-label": lastCiphertext
										? "Encrypted password, mostly masked; use copy for the full value"
										: "Encrypted password",
								},
								input: {
									readOnly: true,
									endAdornment: (
										<InputAdornment position="end">
											<IconButton
												aria-label="Copy encrypted password"
												onClick={handleCopyAgain}
												edge="end"
												size="small"
												disabled={!lastCiphertext}
											>
												<ContentCopyIcon fontSize="small" />
											</IconButton>
										</InputAdornment>
									),
								},
							}}
						/>
						<Stack direction="row" justifyContent="flex-end" alignItems="center">
							<Button
								variant="contained"
								onClick={() => void handleEncode()}
								disabled={disabled || busy}
								startIcon={
									busy ? (
										<CircularProgress size={18} color="inherit" aria-label="Encrypting" />
									) : null
								}
							>
								{busy ? "Encrypting…" : "Encrypt"}
							</Button>
						</Stack>
					</Stack>
				</DialogContent>
			</Dialog>
		</>
	);
}
