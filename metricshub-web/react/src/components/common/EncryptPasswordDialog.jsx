import * as React from "react";
import {
	Button,
	Dialog,
	DialogActions,
	DialogContent,
	DialogTitle,
	IconButton,
	InputAdornment,
	Stack,
	TextField,
} from "@mui/material";
import VpnKeyIcon from "@mui/icons-material/VpnKey";
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
		const transport = encodeUtf8ToBase64(password);
		setBusy(true);
		try {
			const ciphertext = await configApi.encryptPassword(transport);
			setLastCiphertext(ciphertext);
			const copied = await copyTextToClipboard(ciphertext);
			if (copied) {
				showSnackbar("Encrypted value copied to clipboard", { severity: "success" });
			} else if (ciphertext) {
				showSnackbar("Encrypted, but clipboard failed — use Copy to clipboard", {
					severity: "warning",
				});
			}
		} catch (e) {
			showSnackbar(e?.message || "Password encryption failed", { severity: "error" });
			setLastCiphertext("");
		} finally {
			setBusy(false);
		}
	}, [password, showSnackbar]);

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
				<DialogTitle id="encrypt-pwd-title">Encrypt password</DialogTitle>
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
						<Stack direction="row" spacing={1} flexWrap="wrap" alignItems="center">
							<Button variant="contained" onClick={handleEncode} disabled={disabled || busy}>
								{busy ? "Encrypting…" : "Encrypt"}
							</Button>
							<Button
								variant="outlined"
								startIcon={<ContentCopyIcon />}
								onClick={handleCopyAgain}
								disabled={!lastCiphertext}
							>
								Copy to clipboard
							</Button>
						</Stack>
					</Stack>
				</DialogContent>
				<DialogActions sx={{ px: 3, pb: 2 }}>
					<Button onClick={handleClose}>Close</Button>
				</DialogActions>
			</Dialog>
		</>
	);
}
