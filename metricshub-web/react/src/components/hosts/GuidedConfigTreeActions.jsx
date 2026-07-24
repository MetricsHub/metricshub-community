import * as React from "react";
import { Stack } from "@mui/material";
import HostsPageHeader from "./HostsPageHeader";

/**
 * Toolbar row above the guided-config search/tree — layout matches ConfigurationPage tree toolbar.
 *
 * @param {object} props
 * @param {boolean} [props.busy]
 * @param {boolean} [props.disableNewGroup]
 * @param {boolean} [props.disableNewResource]
 * @param {() => void} props.onCreateGroup
 * @param {() => void} props.onCreateHost
 */
const GuidedConfigTreeActions = ({
	busy = false,
	disableNewGroup = false,
	disableNewResource = false,
	onCreateGroup,
	onCreateHost,
}) => {
	return (
		<Stack direction="row" spacing={1} alignItems="center">
			<HostsPageHeader
				busy={busy}
				disableNewGroup={disableNewGroup}
				disableNewResource={disableNewResource}
				onCreateGroup={onCreateGroup}
				onCreateHost={onCreateHost}
			/>
		</Stack>
	);
};

export default React.memo(GuidedConfigTreeActions);
