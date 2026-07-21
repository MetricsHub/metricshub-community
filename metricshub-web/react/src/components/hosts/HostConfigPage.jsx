import * as React from "react";
import { useBlocker } from "react-router-dom";
import {
	Alert,
	Box,
	Button,
	Dialog,
	DialogActions,
	DialogContent,
	DialogContentText,
	DialogTitle,
	Paper,
	Stack,
	Typography,
} from "@mui/material";
import SaveIcon from "@mui/icons-material/Save";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import SaveAsOutlinedIcon from "@mui/icons-material/SaveAsOutlined";
import NodeTypeIcons from "../explorer/tree/icons/NodeTypeIcons";
import { useSnackbar } from "../../hooks/use-snackbar";
import { deleteHostConfigDraft, saveHostConfigDraft } from "./host-config-drafts";
import { useHostConfig } from "./useHostConfig";
import {
	createEmptyHostFormState,
	getHostFormCommittedFingerprint,
	normalizeHostFormState,
} from "./host-config-state";
import { getHostNames } from "./host-config-utils";
import HostConfigFormBody from "./HostConfigFormBody";
import HostConfigSectionNav from "./HostConfigSectionNav";
import AboutConnectorsCard from "./AboutConnectorsCard";
import {
	guidedConfigDeleteButtonSx,
	guidedConfigSaveButtonSx,
	guidedConfigSectionFlashSx,
} from "./guided-config-form-primitives";
import {
	HOST_CONFIG_CREATE_FORM_CONTENT_PR,
	HOST_CONFIG_CREATE_FORM_STEPPER_GAP,
	HOST_CONFIG_STEP_RAIL_WIDTH,
} from "./host-config-form-layout";
import { getFormSectionPageSubtitle } from "./host-config-sections";
import { useHostConfigSectionScrollSpy } from "./useHostConfigSectionScrollSpy";
import { scrollbarThumbSx } from "../split-screen/SplitScreen";

/**
 * Single-page create/edit form with scroll-driven stepper.
 *
 * @param {object} props
 * @param {"create" | "edit"} [props.mode]
 * @param {string} [props.hostId] resource id shown in edit mode title
 * @param {(state: object) => Promise<void>} props.onSubmit
 * @param {() => void} props.onCancel
 * @param {() => void} [props.onDelete]
 * @param {string[]} [props.resourceGroups]
 * @param {string | null} [props.defaultResourceGroup]
 * @param {object} [props.initialState]
 * @param {{ groups: Record<string, string[]>; standalone: string[] }} [props.existingHostIdScopes]
 * @param {Record<string, 0 | 1 | null>} [props.protocolHealth] live host.up per protocol (single-host edit)
 * @param {boolean} [props.busy]
 * @param {string} [props.sessionPathname]
 * @param {(dirty: boolean) => void} [props.onUnsavedChangesChange]
 * @param {React.ReactNode} [props.headerEndAction] optional control above the step rail (edit mode)
 * @param {(draftId: string) => void} [props.onSaveAsDraft] create-mode: open the saved draft
 */
const HostConfigPage = ({
	mode = "create",
	hostId: editHostId = "",
	onSubmit,
	onCancel,
	onDelete,
	resourceGroups = [],
	defaultResourceGroup = null,
	initialState,
	existingHostIdScopes,
	protocolHealth,
	busy = false,
	sessionPathname = "",
	onUnsavedChangesChange,
	headerEndAction,
	draftId = null,
	onSaveAsDraft,
	onCreateResourceGroup,
}) => {
	const isEdit = mode === "edit";
	const { show: showSnackbar } = useSnackbar();
	const [cancelDialogOpen, setCancelDialogOpen] = React.useState(false);
	const [cancelEditDialogOpen, setCancelEditDialogOpen] = React.useState(false);
	const [deleteDraftDialogOpen, setDeleteDraftDialogOpen] = React.useState(false);
	// True once a save/draft/explicit discard authorized the next navigation, so the
	// route blocker lets programmatic redirects (after save, delete...) through.
	const allowNavigationRef = React.useRef(false);
	// Tracks the draft this session is attached to. Starts at the `draftId` prop
	// (resuming a saved draft) and is filled in the first time "Save as draft" is
	// clicked from a blank session, so repeated clicks — and the eventual real
	// save's cleanup — target the same draft instead of creating a new one each time.
	const activeDraftIdRef = React.useRef(draftId);
	const formRootRef = React.useRef(/** @type {HTMLDivElement | null} */ (null));
	const [scrollAnchorReady, setScrollAnchorReady] = React.useState(false);
	const bindFormRootRef = React.useCallback((node) => {
		formRootRef.current = node;
		setScrollAnchorReady(node instanceof HTMLElement);
	}, []);

	const form = useHostConfig({
		open: true,
		mode,
		defaultResourceGroup,
		initialState,
		sessionPathname: isEdit ? "" : sessionPathname,
	});

	const { goToStep } = form;
	const [restoreEpoch, setRestoreEpoch] = React.useState(0);
	const [scrollRestoreLocked, setScrollRestoreLocked] = React.useState(
		() => form.initialScrollTop != null,
	);
	const scrollRestoreDoneRef = React.useRef(false);

	// The boot-time scroll restore must never fire again once the user starts
	// interacting: its effect re-runs when content settles (catalog load, steps
	// added by selecting a protocol), and re-applying the saved offset then would
	// yank the page away from where the user actually is.
	React.useEffect(() => {
		const markRestoreDone = () => {
			scrollRestoreDoneRef.current = true;
		};
		const options = { capture: true, passive: true };
		window.addEventListener("pointerdown", markRestoreDone, options);
		window.addEventListener("wheel", markRestoreDone, options);
		window.addEventListener("keydown", markRestoreDone, options);
		return () => {
			window.removeEventListener("pointerdown", markRestoreDone, options);
			window.removeEventListener("wheel", markRestoreDone, options);
			window.removeEventListener("keydown", markRestoreDone, options);
		};
	}, []);

	React.useLayoutEffect(() => {
		const target = form.initialScrollTop;
		const el = formRootRef.current;
		if (target == null || scrollRestoreDoneRef.current) {
			setScrollRestoreLocked(false);
			return undefined;
		}
		if (!el || !scrollAnchorReady) {
			return undefined;
		}

		setScrollRestoreLocked(true);
		let cancelled = false;
		let frame = 0;

		const attempt = () => {
			if (cancelled) {
				return;
			}
			frame += 1;
			el.scrollTop = target;
			const maxScroll = Math.max(0, el.scrollHeight - el.clientHeight);
			const reachedTarget = Math.abs(el.scrollTop - target) <= 2;
			const reachedMaxForTarget = target >= maxScroll - 2 && el.scrollTop >= maxScroll - 2;
			if (reachedTarget || reachedMaxForTarget || frame >= 300) {
				// Reaching only maxScroll means content is still settling; leave the
				// restore re-runnable so the pending deps (catalog, steps) finish the job.
				if (reachedTarget || frame >= 300) {
					scrollRestoreDoneRef.current = true;
				}
				form.updateScrollPosition(el.scrollTop);
				setScrollRestoreLocked(false);
				setRestoreEpoch((value) => value + 1);
				return;
			}
			requestAnimationFrame(attempt);
		};

		requestAnimationFrame(attempt);
		return () => {
			cancelled = true;
		};
	}, [
		scrollAnchorReady,
		form.connectorCatalogLoading,
		form.initialScrollTop,
		form.state.connectorDetectionMode,
		form.steps.length,
		form.updateScrollPosition,
	]);

	const handleActiveStepChange = React.useCallback(
		(index) => {
			goToStep(index);
		},
		[goToStep],
	);

	const { activeStep: scrollActiveStep, scrollToStepIndex } = useHostConfigSectionScrollSpy({
		enabled: true,
		steps: form.steps,
		formRootRef,
		scrollAnchorReady,
		scrollRestoreLocked,
		onActiveStepChange: handleActiveStepChange,
		contentKey: `${form.state.connectorDetectionMode}:${form.steps.length}:${restoreEpoch}`,
		initialActiveStep: form.initialActiveStep,
		onScrollPositionChange: form.updateScrollPosition,
	});

	const currentStep = form.steps[scrollActiveStep];
	const hasConnectorsStep = form.connectorsStepIndex >= 0;

	const scrollToConnectorsStep = React.useCallback(() => {
		const idx = form.connectorsStepIndex;
		if (idx < 0) {
			return;
		}
		requestAnimationFrame(() => {
			requestAnimationFrame(() => scrollToStepIndex(idx));
		});
	}, [scrollToStepIndex, form.connectorsStepIndex]);

	const emptyStateFingerprint = React.useMemo(() => {
		const baseline = initialState
			? {
					...createEmptyHostFormState(defaultResourceGroup),
					...normalizeHostFormState(initialState),
				}
			: createEmptyHostFormState(defaultResourceGroup);
		return getHostFormCommittedFingerprint(baseline);
	}, [defaultResourceGroup, initialState]);

	const hasEnteredInformation = React.useMemo(
		() => getHostFormCommittedFingerprint(form.state) !== emptyStateFingerprint,
		[emptyStateFingerprint, form.state],
	);

	const hasUnsavedChanges = isEdit ? form.isDirty : hasEnteredInformation;
	const canSaveEdit = form.isDirty;

	React.useEffect(() => {
		onUnsavedChangesChange?.(hasUnsavedChanges);
	}, [hasUnsavedChanges, onUnsavedChangesChange]);

	React.useEffect(
		() => () => {
			onUnsavedChangesChange?.(false);
		},
		[onUnsavedChangesChange],
	);

	const handleCancel = () => {
		if (!hasUnsavedChanges) {
			if (!isEdit) {
				form.clearSession();
			}
			onCancel();
			return;
		}
		setCancelDialogOpen(true);
	};

	const handleConfirmCancel = () => {
		setCancelDialogOpen(false);
		if (!isEdit) {
			form.clearSession();
		}
		allowNavigationRef.current = true;
		onCancel();
	};

	/** @returns {Promise<boolean>} true when validation passed and the save succeeded */
	const handleSubmit = async () => {
		const failedAt = form.validateAllSteps();
		if (failedAt != null) {
			scrollToStepIndex(failedAt);
			return false;
		}
		const connectorsIdx = form.connectorsStepIndex;
		if (connectorsIdx >= 0 && !form.validateConnectorsStep()) {
			scrollToStepIndex(connectorsIdx);
			return false;
		}
		form.setSubmitError(null);
		allowNavigationRef.current = true;
		try {
			await onSubmit(form.state);
			if (isEdit) {
				form.commitSavedBaseline();
			}
			if (!isEdit && activeDraftIdRef.current) {
				deleteHostConfigDraft(activeDraftIdRef.current);
			}
			form.clearSession();
			return true;
		} catch (e) {
			allowNavigationRef.current = false;
			form.setSubmitError(e?.message || "Failed to save resource");
			return false;
		}
	};

	const hostNames = React.useMemo(() => getHostNames(form.state.hostName), [form.state.hostName]);

	const saveDraftNow = React.useCallback(() => {
		// Drafts are labeled by resource ID, matching saved resources in the tree. When
		// neither a resource id nor a host name is set, an empty name lets the drafts store
		// mint a unique "untitled-resource" label ("untitled-resource-2", …).
		const name = String(form.state.hostId || "").trim() || hostNames[0] || "";
		activeDraftIdRef.current = saveHostConfigDraft({
			id: activeDraftIdRef.current,
			name,
			state: form.state,
		});
		// Reset the dirty baseline so "Save as draft" disables until the next edit.
		form.commitSavedBaseline();
	}, [form.state, hostNames, form.commitSavedBaseline]);

	const handleSaveAsDraftClick = () => {
		saveDraftNow();
		showSnackbar("Resource saved as draft.", { severity: "success" });
		// Redirect onto the saved draft: the form re-mounts with the draft as its
		// baseline, so leaving again without further edits won't prompt to save.
		if (onSaveAsDraft && activeDraftIdRef.current) {
			allowNavigationRef.current = true;
			onSaveAsDraft(activeDraftIdRef.current);
		}
	};

	// Blocks in-app navigation while the form holds unsaved work. Tab close/reload is
	// intentionally not guarded: the form session auto-persists and restores.
	const navigationBlocker = useBlocker(
		React.useCallback(
			({ currentLocation, nextLocation }) =>
				!allowNavigationRef.current &&
				!busy &&
				hasUnsavedChanges &&
				(currentLocation.pathname !== nextLocation.pathname ||
					currentLocation.search !== nextLocation.search ||
					// Same create URL can host different forms (a draft vs. a blank one).
					(currentLocation.state?.draftId ?? null) !== (nextLocation.state?.draftId ?? null)),
			[busy, hasUnsavedChanges],
		),
	);
	const navigationBlocked = navigationBlocker.state === "blocked";

	const handleGuardKeepEditing = () => navigationBlocker.reset();

	const handleGuardQuit = () => {
		// Quitting means discarding: drop the auto-persisted session in both modes,
		// otherwise the abandoned edits get restored on the next visit.
		form.clearSession();
		navigationBlocker.proceed();
	};

	const handleGuardSaveDraft = () => {
		saveDraftNow();
		form.clearSession();
		showSnackbar("Resource saved as draft.", { severity: "success" });
		navigationBlocker.proceed();
	};

	const handleGuardSave = async () => {
		const saved = await handleSubmit();
		if (navigationBlocker.state !== "blocked") {
			return;
		}
		if (saved) {
			navigationBlocker.proceed();
		} else {
			// Validation failed or save errored: stay on the page (handleSubmit already
			// scrolled to the failing step / set the submit error).
			navigationBlocker.reset();
		}
	};

	const handleConfirmCancelSaveDraft = () => {
		setCancelDialogOpen(false);
		saveDraftNow();
		form.clearSession();
		allowNavigationRef.current = true;
		showSnackbar("Resource saved as draft.", { severity: "success" });
		onCancel();
	};

	const handleCancelEditChanges = () => {
		setCancelEditDialogOpen(false);
		form.resetToBaseline();
	};

	const handleDeleteDraft = () => {
		setDeleteDraftDialogOpen(false);
		deleteHostConfigDraft(draftId);
		form.clearSession();
		allowNavigationRef.current = true;
		showSnackbar("Draft deleted.", { severity: "success" });
		onCancel();
	};

	const isMultiHostResource = hostNames.length > 1;
	const resourceIconType = isMultiHostResource ? "multi-host-resource" : "resource";

	const pageTitle = React.useMemo(() => {
		if (!isEdit) {
			return "Add Resource";
		}
		if (isMultiHostResource) {
			return String(editHostId || form.state.hostId || "Resource").trim();
		}
		if (hostNames.length === 1) {
			return hostNames[0];
		}
		return editHostId || form.state.hostId || "Resource";
	}, [editHostId, hostNames, isEdit, isMultiHostResource, form.state.hostId]);

	// A draft-loaded form is always submittable: its content is real but unsaved,
	// even when nothing changed since the draft was stored.
	const canSave = isEdit ? canSaveEdit : hasEnteredInformation || Boolean(draftId);
	// "Save as draft" only acts on unsaved changes relative to the last draft save (or the
	// empty form), so it disables right after saving until the next edit.
	const canSaveDraft = form.isDirtyFromBaseline;
	const submitLabel = isEdit ? "Save" : "Add Resource";

	return (
		<>
			<Box
				sx={{
					flex: 1,
					minHeight: 0,
					height: "100%",
					width: "100%",
					display: "grid",
					gridTemplateColumns: {
						xs: "minmax(0, 1fr)",
						md: `minmax(0, 1fr) ${HOST_CONFIG_STEP_RAIL_WIDTH}px`,
					},
					gridTemplateRows: "auto minmax(0, 1fr)",
					columnGap: { xs: 2.5, md: HOST_CONFIG_CREATE_FORM_STEPPER_GAP },
					rowGap: 2.5,
					overflow: "hidden",
				}}
			>
				<Box
					sx={{
						gridColumn: 1,
						gridRow: 1,
						minWidth: 0,
						pr: { md: HOST_CONFIG_CREATE_FORM_CONTENT_PR },
					}}
				>
					<Stack
						direction="row"
						alignItems="flex-start"
						justifyContent="space-between"
						spacing={2}
						useFlexGap
						flexWrap="wrap"
					>
						<Box sx={{ minWidth: 0, flex: 1 }}>
							<Stack direction="row" alignItems="center" spacing={0.5}>
								<NodeTypeIcons type={resourceIconType} fontSize="large" />
								<Typography variant="h4" fontWeight={700} sx={{ lineHeight: 1.2 }}>
									{pageTitle}
								</Typography>
							</Stack>
							<Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
								Step {scrollActiveStep + 1} of {form.steps.length}
								{getFormSectionPageSubtitle(currentStep)
									? ` — ${getFormSectionPageSubtitle(currentStep)}`
									: ""}
							</Typography>
						</Box>
						<Stack
							direction="row"
							spacing={1}
							alignItems="center"
							useFlexGap
							flexWrap="wrap"
							sx={{ flexShrink: 0, pt: 0.25 }}
						>
							{isEdit && onDelete ? (
								<Button
									size="small"
									variant="contained"
									startIcon={<DeleteOutlineIcon />}
									onClick={() => {
										// A successful delete redirects away; the guard must not block it.
										allowNavigationRef.current = true;
										onDelete();
									}}
									disabled={busy}
									sx={guidedConfigDeleteButtonSx}
								>
									Delete
								</Button>
							) : null}
							{isEdit ? (
								<Button
									size="small"
									onClick={() => setCancelEditDialogOpen(true)}
									disabled={busy || !form.isDirty}
								>
									Cancel
								</Button>
							) : draftId ? (
								<Button
									size="small"
									color="error"
									onClick={() => setDeleteDraftDialogOpen(true)}
									disabled={busy}
								>
									Delete draft
								</Button>
							) : (
								<Button size="small" onClick={handleCancel} disabled={busy}>
									Cancel
								</Button>
							)}
							{!isEdit ? (
								<Button
									size="small"
									variant="outlined"
									startIcon={<SaveAsOutlinedIcon />}
									onClick={handleSaveAsDraftClick}
									disabled={busy || !canSaveDraft}
								>
									Save as draft
								</Button>
							) : null}
							<Button
								size="small"
								variant="contained"
								startIcon={<SaveIcon />}
								onClick={() => void handleSubmit()}
								disabled={busy || !canSave}
								sx={guidedConfigSaveButtonSx}
							>
								{busy ? "Saving..." : submitLabel}
							</Button>
						</Stack>
					</Stack>
					{form.submitError && (
						<Alert severity="error" sx={{ mt: 2 }}>
							{form.submitError}
						</Alert>
					)}
					{isEdit && headerEndAction ? (
						<Box sx={{ display: { xs: "flex", md: "none" }, justifyContent: "flex-end", mt: 2 }}>
							{headerEndAction}
						</Box>
					) : null}
				</Box>

				<Box
					ref={bindFormRootRef}
					sx={(t) => ({
						gridColumn: 1,
						gridRow: 2,
						minHeight: 0,
						minWidth: 0,
						overflowY: "auto",
						overflowX: "hidden",
						pr: { xs: 0.5, md: HOST_CONFIG_CREATE_FORM_CONTENT_PR },
						...scrollbarThumbSx(t),
						...guidedConfigSectionFlashSx(t),
					})}
				>
					<HostConfigFormBody
						form={form}
						resourceGroups={resourceGroups}
						existingHostIdScopes={existingHostIdScopes}
						protocolHealth={protocolHealth}
						onScrollToConnectorsStep={scrollToConnectorsStep}
						onCreateResourceGroup={onCreateResourceGroup}
					/>
				</Box>

				<Box
					sx={{
						gridColumn: { xs: 1, md: 2 },
						gridRow: 1,
						display: { xs: "none", md: isEdit && headerEndAction ? "block" : "none" },
						minWidth: 0,
						alignSelf: "start",
						"& > span": { display: "block", width: "100%" },
						"& .MuiButton-root": { width: "100%" },
					}}
				>
					{headerEndAction}
				</Box>

				<Box
					sx={(t) => ({
						gridColumn: { xs: 1, md: 2 },
						gridRow: { xs: "auto", md: 2 },
						display: { xs: "none", md: "flex" },
						flexDirection: "column",
						gap: 2,
						minHeight: 0,
						minWidth: 0,
						overflowY: "auto",
						alignSelf: "start",
						pb: 1,
						...scrollbarThumbSx(t),
					})}
				>
					<Paper
						elevation={0}
						sx={{
							borderRadius: 2,
							p: 2,
							flexShrink: 0,
							bgcolor: "transparent",
							border: 1,
							borderColor: "divider",
							boxShadow: "none",
						}}
					>
						<HostConfigSectionNav
							steps={form.steps}
							activeStep={scrollActiveStep}
							validatedStepIds={form.validatedStepIds}
							invalidStepIds={form.invalidStepIds}
							editedStepIds={form.editedStepIds}
							onStepClick={scrollToStepIndex}
						/>
					</Paper>
					{hasConnectorsStep ? <AboutConnectorsCard /> : null}
				</Box>
			</Box>

			<Dialog
				open={cancelDialogOpen}
				onClose={() => setCancelDialogOpen(false)}
				fullWidth
				maxWidth="xs"
			>
				<DialogTitle>
					{isEdit ? "Discard unsaved changes?" : "Discard this new resource?"}
				</DialogTitle>
				<DialogContent>
					<DialogContentText>
						{isEdit
							? "Your changes will be lost. You can keep editing or leave without saving."
							: "Your current entries will be lost. You can stay on this page to keep editing, or discard the draft and leave."}
					</DialogContentText>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setCancelDialogOpen(false)} autoFocus>
						Keep editing
					</Button>
					<Button color="error" onClick={handleConfirmCancel}>
						{isEdit ? "Leave without saving" : "Discard"}
					</Button>
					{!isEdit ? (
						<Button variant="contained" onClick={handleConfirmCancelSaveDraft}>
							Save as draft
						</Button>
					) : null}
				</DialogActions>
			</Dialog>

			<Dialog
				open={deleteDraftDialogOpen}
				onClose={() => setDeleteDraftDialogOpen(false)}
				fullWidth
				maxWidth="xs"
			>
				<DialogTitle>Delete this draft?</DialogTitle>
				<DialogContent>
					<DialogContentText>
						The draft and its entered information will be permanently removed.
					</DialogContentText>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDeleteDraftDialogOpen(false)} autoFocus>
						Keep editing
					</Button>
					<Button color="error" variant="contained" onClick={handleDeleteDraft}>
						Delete draft
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog
				open={cancelEditDialogOpen}
				onClose={() => setCancelEditDialogOpen(false)}
				fullWidth
				maxWidth="xs"
			>
				<DialogTitle>Cancel your changes?</DialogTitle>
				<DialogContent>
					<DialogContentText>
						Your modifications to this resource will be discarded.
					</DialogContentText>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setCancelEditDialogOpen(false)} autoFocus>
						Keep editing
					</Button>
					<Button color="error" variant="contained" onClick={handleCancelEditChanges}>
						Cancel changes
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={navigationBlocked} onClose={handleGuardKeepEditing} fullWidth maxWidth="xs">
				<DialogTitle>Leave without saving?</DialogTitle>
				<DialogContent>
					<DialogContentText>
						{isEdit
							? "The changes will be lost if you quit."
							: "This new resource hasn't been saved. Save it as a draft to finish later, or quit to discard the entered information."}
					</DialogContentText>
				</DialogContent>
				<DialogActions>
					<Button onClick={handleGuardKeepEditing} autoFocus disabled={busy}>
						Keep editing
					</Button>
					<Button color="error" onClick={handleGuardQuit} disabled={busy}>
						Quit
					</Button>
					{isEdit ? (
						<Button
							variant="contained"
							startIcon={<SaveIcon />}
							onClick={() => void handleGuardSave()}
							disabled={busy}
							sx={guidedConfigSaveButtonSx}
						>
							{busy ? "Saving..." : "Save"}
						</Button>
					) : (
						<Button
							variant="contained"
							onClick={handleGuardSaveDraft}
							sx={guidedConfigSaveButtonSx}
						>
							Save as draft
						</Button>
					)}
				</DialogActions>
			</Dialog>
		</>
	);
};

export default HostConfigPage;
