import * as React from "react";
import { Tooltip, Box } from "@mui/material";

const truncatedSx = {
	whiteSpace: "nowrap",
	overflow: "hidden",
	textOverflow: "ellipsis",
	display: "block",
	width: "100%",
};

/**
 * Renders text that truncates with ellipsis if it overflows,
 * and shows a tooltip with the full text only when truncated.
 *
 * @param {object} props - Component props
 * @param {React.ReactNode} props.children - Child elements
 * @param {string} [props.text] - Full text to show in tooltip (falls back to children)
 * @param {import("@mui/material").SxProps} [props.sx={}] - Custom styles
 */
const TruncatedText = ({ children, text, sx = {} }) => {
	const [isOverflowing, setIsOverflowing] = React.useState(false);
	const textRef = React.useRef(null);

	const checkOverflow = React.useCallback(() => {
		const el = textRef.current;
		if (el) {
			setIsOverflowing(el.scrollWidth > el.clientWidth);
		}
	}, []);

	React.useLayoutEffect(() => {
		checkOverflow();
		window.addEventListener("resize", checkOverflow);
		return () => window.removeEventListener("resize", checkOverflow);
	}, [checkOverflow, children]);

	const handleMouseEnter = () => {
		checkOverflow();
	};

	const content = (
		<Box ref={textRef} sx={{ ...truncatedSx, ...sx }} onMouseEnter={handleMouseEnter}>
			{children}
		</Box>
	);

	if (isOverflowing) {
		return (
			<Tooltip title={text || children} arrow placement="top">
				{content}
			</Tooltip>
		);
	}

	return content;
};

export default React.memo(TruncatedText);
