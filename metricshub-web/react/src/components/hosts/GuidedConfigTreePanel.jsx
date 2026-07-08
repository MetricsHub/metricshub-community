import * as React from "react";
import { Box, Stack } from "@mui/material";
import GuidedConfigSearch from "./GuidedConfigSearch";
import GuidedConfigTreeActions from "./GuidedConfigTreeActions";
import HostsResourcesTree from "./HostsResourcesTree";

/**
 * Left sidebar for guided configuration — same outer spacing/padding as YAML editor tree panel.
 *
 * @param {object} props
 * @param {{ resources?: Record<string, unknown>; resourceGroups?: Record<string, unknown> }} props.snapshot
 * @param {object} props.view current HOSTS_VIEWS selection
 * @param {boolean} [props.busy]
 * @param {boolean} [props.disableNewGroup]
 * @param {boolean} [props.disableNewResource]
 * @param {(view: object) => void} props.onViewChange
 * @param {() => void} props.onCreateGroup
 * @param {() => void} props.onCreateHost
 */
const GuidedConfigTreePanel = ({
	snapshot,
	view,
	busy = false,
	disableNewGroup = false,
	disableNewResource = false,
	drafts = [],
	onOpenDraft,
	onViewChange,
	onCreateGroup,
	onCreateHost,
}) => (
	<Stack
		spacing={1.5}
		sx={{
			p: 1.5,
			height: "100%",
			minHeight: 0,
			boxSizing: "border-box",
		}}
	>
		<GuidedConfigTreeActions
			busy={busy}
			disableNewGroup={disableNewGroup}
			disableNewResource={disableNewResource}
			onCreateGroup={onCreateGroup}
			onCreateHost={onCreateHost}
		/>
		<GuidedConfigSearch snapshot={snapshot} onViewChange={onViewChange} />
		<Box sx={{ flex: 1, minHeight: 0, overflow: "hidden" }}>
			<HostsResourcesTree
				snapshot={snapshot}
				view={view}
				onViewChange={onViewChange}
				drafts={drafts}
				onOpenDraft={onOpenDraft}
				filterSearch=""
				filterProtocol=""
				filterSortBy="name-asc"
			/>
		</Box>
	</Stack>
);

export default React.memo(GuidedConfigTreePanel);
