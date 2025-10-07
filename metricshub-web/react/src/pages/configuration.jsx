// src/pages/configuration.jsx
import * as React from "react";
import { useEffect, useCallback, useState } from "react";
import { Box, Button, Chip, CircularProgress, Stack } from "@mui/material";
import RefreshIcon from "@mui/icons-material/Refresh";

import { withAuthGuard } from "../hocs/with-auth-guard";
import { SplitScreen, Left, Right } from "../components/split-screen/split-screen";

import { useAppDispatch, useAppSelector } from "../hooks/store";
import {
	fetchConfigList,
	fetchConfigContent,
	saveConfig,
	validateConfig,
	deleteConfig,
	renameConfig,
} from "../store/thunks/configThunks";
import { select as selectFile, setContent } from "../store/slices/configSlice";

import EditorHeader from "../components/config/EditorHeader";
import ConfigEditor from "../components/config/Editor/ConfigEditor";
import ConfirmDeleteDialog from "../components/config/ConfirmDeleteDialog";
import ConfigTree from "../components/config/Tree/ConfigTree";

function ConfigurationPage() {
	const dispatch = useAppDispatch();
	const { list, selected, content, loadingList, loadingContent, saving, validation, error } =
		useAppSelector((s) => s.config);

	const [deleteOpen, setDeleteOpen] = useState(false);
	const [deleteTarget, setDeleteTarget] = useState(null);

	useEffect(() => {
		dispatch(fetchConfigList());
	}, [dispatch]);

	const onSelect = useCallback(
		(name) => {
			dispatch(selectFile(name));
			dispatch(fetchConfigContent(name));
		},
		[dispatch],
	);

	const handleInlineRename = useCallback(
		(oldName, newName) => {
			dispatch(renameConfig({ oldName, newName }));
		},
		[dispatch],
	);

	const onSave = useCallback(
		(doc) => {
			if (!selected) return;
			dispatch(saveConfig({ name: selected, content: doc, skipValidation: false }));
		},
		[dispatch, selected],
	);

	const onValidate = useCallback(() => {
		if (!selected) return;
		dispatch(validateConfig({ name: selected, content }));
	}, [dispatch, selected, content]);

	const openDelete = useCallback((fileName) => {
		setDeleteTarget(fileName);
		setDeleteOpen(true);
	}, []);
	const submitDelete = useCallback(() => {
		if (!deleteTarget) {
			setDeleteOpen(false);
			return;
		}
		dispatch(deleteConfig(deleteTarget));
		setDeleteOpen(false);
	}, [dispatch, deleteTarget]);

	return (
		<SplitScreen initialLeftPct={35}>
			<Left>
				<Stack spacing={1.5} sx={{ p: 1.5 }}>
					<Stack direction="row" spacing={1} alignItems="center">
						<Button
							size="small"
							startIcon={<RefreshIcon />}
							onClick={() => dispatch(fetchConfigList())}
						>
							Refresh
						</Button>
						{loadingList && <CircularProgress size={18} />}
						{error && <Chip size="small" color="error" label={error} sx={{ maxWidth: 280 }} />}
					</Stack>

					<ConfigTree
						files={list}
						selectedName={selected}
						onSelect={onSelect}
						onRename={handleInlineRename}
						onDeleteRequest={openDelete}
					/>

					<ConfirmDeleteDialog
						open={deleteOpen}
						fileName={deleteTarget}
						onCancel={() => setDeleteOpen(false)}
						onConfirm={submitDelete}
					/>
				</Stack>
			</Left>

			<Right>
				<Stack sx={{ p: 1.5, gap: 1 }}>
					<EditorHeader
						selected={selected}
						saving={saving}
						validation={validation}
						onValidate={onValidate}
						onSave={() => onSave(content)}
						canSave={!!selected && !saving}
					/>
					<Box sx={{ height: "calc(100vh - 160px)" }}>
						<ConfigEditor
							value={content}
							readOnly={!selected || loadingContent}
							onChange={(doc) => dispatch(setContent(doc))}
							onSave={(doc) => onSave(doc)}
							height="100%"
						/>
					</Box>
					{loadingContent && (
						<Stack direction="row" alignItems="center" spacing={1}>
							<CircularProgress size={18} />
							Loading content…
						</Stack>
					)}
				</Stack>
			</Right>
		</SplitScreen>
	);
}

export default withAuthGuard(ConfigurationPage);
