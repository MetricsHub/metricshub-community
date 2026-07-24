import * as React from "react";
import { Button } from "@mui/material";
import DomainIcon from "@mui/icons-material/Domain";
import DnsOutlinedIcon from "@mui/icons-material/DnsOutlined";

/** Matches YAML editor tree toolbar buttons (Refresh / Import). */
export const treeToolbarButtonProps = {
	size: "small",
	variant: "outlined",
	color: "inherit",
};

/**
 * New group / New resource — same markup as ConfigurationPage tree toolbar buttons.
 *
 * @param {object} props
 * @param {boolean} [props.busy]
 * @param {boolean} [props.disableNewGroup]
 * @param {boolean} [props.disableNewResource]
 * @param {() => void} props.onCreateGroup
 * @param {() => void} props.onCreateHost
 */
const HostsPageHeader = ({
	busy = false,
	disableNewGroup = false,
	disableNewResource = false,
	onCreateGroup,
	onCreateHost,
}) => (
	<>
		<Button
			{...treeToolbarButtonProps}
			startIcon={<DomainIcon />}
			onClick={onCreateGroup}
			disabled={busy || disableNewGroup}
		>
			New group
		</Button>
		<Button
			{...treeToolbarButtonProps}
			startIcon={<DnsOutlinedIcon />}
			onClick={onCreateHost}
			disabled={busy || disableNewResource}
		>
			New resource
		</Button>
	</>
);

export default HostsPageHeader;
