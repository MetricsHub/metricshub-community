import * as React from "react";

import { Box } from "@mui/material";

import AppBreadcrumbs from "../common/AppBreadcrumbs";

import HostConfigStepRail from "./HostConfigStepRail";

import {
	HOST_CONFIG_BREADCRUMB_ROW_MIN_HEIGHT,
	HOST_CONFIG_STEP_RAIL_WIDTH,
} from "./host-config-form-layout";

/**

 * Edit/create layout: breadcrumb row, scrollable form, optional step rail.

 *

 * @param {object} props

 * @param {React.ReactNode} props.children

 * @param {{ steps: Array<{ id: string; label: string }>; activeStep: number; onStepClick?: (index: number) => void } | null} [props.stepRail]

 * @param {string} [props.breadcrumbPathname]

 */

const HostConfigEditLayout = ({
	children,

	stepRail = null,

	breadcrumbPathname,
}) => (
	<Box
		sx={{
			display: "flex",

			flexDirection: "column",

			width: "100%",

			height: "100%",

			minHeight: 0,

			overflow: "hidden",
		}}
	>
		<Box
			sx={{
				boxSizing: "border-box",

				px: 2,

				py: 1,

				minHeight: HOST_CONFIG_BREADCRUMB_ROW_MIN_HEIGHT,

				display: "flex",

				alignItems: "flex-start",

				flexShrink: 0,
			}}
		>
			<AppBreadcrumbs embedded pathname={breadcrumbPathname} />
		</Box>

		<Box
			sx={{
				display: "flex",

				flex: 1,

				minHeight: 0,

				overflow: "hidden",
			}}
		>
			<Box
				sx={{
					flex: 1,

					minWidth: 0,

					minHeight: 0,

					overflow: "hidden",

					display: "flex",

					flexDirection: "column",
				}}
			>
				<Box
					sx={{
						flex: 1,

						minHeight: 0,

						height: "100%",

						overflow: "hidden",

						p: 2,

						display: "flex",

						flexDirection: "column",

						boxSizing: "border-box",
					}}
				>
					{children}
				</Box>
			</Box>

			{stepRail ? (
				<Box
					sx={{
						width: HOST_CONFIG_STEP_RAIL_WIDTH,

						flexShrink: 0,

						display: "flex",

						flexDirection: "column",

						minHeight: 0,

						overflow: "hidden",

						borderLeft: 1,

						borderColor: "divider",
					}}
				>
					<Box sx={{ flex: 1, minHeight: 0, overflowY: "auto" }}>
						<HostConfigStepRail
							steps={stepRail.steps}
							activeStep={stepRail.activeStep}
							onStepClick={stepRail.onStepClick}
						/>
					</Box>
				</Box>
			) : null}
		</Box>
	</Box>
);

export default HostConfigEditLayout;
