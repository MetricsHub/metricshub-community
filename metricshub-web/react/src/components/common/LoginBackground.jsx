import { useMemo } from "react";
import { useTheme } from "@mui/material/styles";
import { Box } from "@mui/material";
import loginBackgroundSvg from "../../assets/login-background.svg?raw";

const DARK_VARS = {
	"--svg-bg-fill": "#212529",
	"--trace-main": "#ffffff",
	"--trace-secondary": "#ffffff",
	"--trace-highlight": "#ffffff",
	"--trace-main-opacity": "0.2",
	"--trace-secondary-opacity": "0.1",
	"--group-opacity": "0.2",
	"--bg-offset-y": "-20px",
};

const LIGHT_VARS = {
	"--svg-bg-fill": "#ffffff",
	"--trace-main": "#6c757d",
	"--trace-secondary": "#6c757d",
	"--trace-highlight": "#6c757d",
	"--trace-main-opacity": "0.2",
	"--trace-secondary-opacity": "0.2",
	"--group-opacity": "0.5",
	"--bg-offset-y": "-20px",
};

const backgroundStyles = `
#login-background .bg-svg {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, calc(-50% + var(--bg-offset-y)));
  min-width: 112%;
  min-height: 112%;
  width: auto;
  height: auto;
}

@media (max-width: 992px) {
  #login-background .bg-svg {
    min-width: 105%;
    min-height: 105%;
  }
}

@media (max-width: 768px) {
  #login-background .bg-svg {
    min-width: 130%;
    min-height: 130%;
  }
}

#login-background .cls-1 { fill: var(--svg-bg-fill); }
#login-background .cls-2,
#login-background .cls-3,
#login-background .cls-4 { fill: var(--trace-main); }
#login-background .cls-2 { opacity: var(--trace-main-opacity); }
#login-background .cls-3,
#login-background .cls-5 { fill: var(--trace-secondary); opacity: var(--trace-secondary-opacity); }
#login-background .cls-6 { isolation: isolate; }
#login-background .cls-7 { opacity: var(--group-opacity); }
#login-background .cls-4 { fill: var(--trace-highlight); mix-blend-mode: color-dodge; }
#login-background[data-theme="light"] .cls-4 { mix-blend-mode: multiply; }

#login-background #traces-signal { pointer-events: none; }

#login-background .signal-path {
  fill: none;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 3;
  stroke-dasharray: 250 9999;
  animation-name: loginBackdropRunSignal;
  animation-duration: 10s;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
  animation-fill-mode: both;
  opacity: 0.5;
}

@media (prefers-reduced-motion: reduce) {
  #login-background .signal-path {
    animation: none;
  }
}

#login-background .signal-red { stroke: #E50031; }
#login-background .signal-yellow { stroke: #E50031; }
#login-background .signal-green { stroke: #45CE52; }
#login-background .signal-blue { stroke: #2684FF; }
#login-background .signal-white { stroke: #2684FF; }

#login-background .runner.d1 { animation-delay: 0s; }
#login-background .runner.d2 { animation-delay: 4.6s; }
#login-background .runner.d3 { animation-delay: 6.2s; }
#login-background .runner.d4 { animation-delay: 8.8s; }
#login-background .runner.d5 { animation-delay: 12.4s; }

@keyframes loginBackdropRunSignal {
  from { stroke-dashoffset: 2600; }
  to { stroke-dashoffset: -2600; }
}
`;

/**
 * Animated circuit-board background for the login page.
 * Adapts colors automatically to dark / light MUI theme.
 */
const LoginBackground = () => {
	const theme = useTheme();
	const isDark = theme.palette.mode === "dark";
	const cssVariables = isDark ? DARK_VARS : LIGHT_VARS;

	const svgMarkup = useMemo(() => ({ __html: loginBackgroundSvg }), []);

	return (
		<Box
			id="login-background"
			data-theme={isDark ? "dark" : "light"}
			sx={{
				position: "fixed",
				inset: 0,
				zIndex: 0,
				overflow: "hidden",
				bgcolor: isDark ? "#212529" : "#ffffff",
			}}
			style={cssVariables}
		>
			<style>{backgroundStyles}</style>
			<div dangerouslySetInnerHTML={svgMarkup} />
		</Box>
	);
};

export default LoginBackground;
