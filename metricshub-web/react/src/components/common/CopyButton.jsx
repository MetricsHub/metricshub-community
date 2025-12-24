import * as React from "react";
import { IconButton, useTheme } from "@mui/material";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import CheckIcon from "@mui/icons-material/Check";

/**
 * Copy button component for assistant messages
 */
const CopyButton = React.memo(({ content }) => {
	const theme = useTheme();
	const [copied, setCopied] = React.useState(false);

	const handleCopy = React.useCallback(async () => {
		try {
			await navigator.clipboard.writeText(content);
			setCopied(true);
			setTimeout(() => setCopied(false), 2000);
		} catch (err) {
			console.error("Failed to copy:", err);
		}
	}, [content]);

	return (
		<IconButton
			size="small"
			onClick={handleCopy}
			sx={{
				p: 0.5,
				color: theme.palette.text.secondary,
				"&:hover": {
					color: theme.palette.primary.main,
					backgroundColor:
						theme.palette.mode === "dark" ? theme.palette.neutral[700] : theme.palette.neutral[100],
				},
			}}
			title={copied ? "Copied!" : "Copy message"}
		>
			{copied ? <CheckIcon fontSize="small" /> : <ContentCopyIcon fontSize="small" />}
		</IconButton>
	);
});

CopyButton.displayName = "CopyButton";

export default CopyButton;
