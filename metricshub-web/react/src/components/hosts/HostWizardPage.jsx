import * as React from "react";
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
import NodeTypeIcons from "../explorer/tree/icons/NodeTypeIcons";
import { useHostWizard } from "./useHostWizard";
import {
	createEmptyHostWizardState,
	getHostWizardCommittedFingerprint,
	normalizeHostWizardState,
} from "./host-wizard-state";
import { getHostNames } from "./host-config-utils";
import HostConfigFormBody from "./HostConfigFormBody";
import HostWizardCardStepper from "./HostWizardCardStepper";
import AboutConnectorsCard from "./AboutConnectorsCard";
import {
	guidedConfigDeleteButtonSx,
	guidedConfigSaveButtonSx,
} from "./guided-config-form-primitives";
import {
	HOST_CONFIG_CREATE_FORM_CONTENT_PR,
	HOST_CONFIG_CREATE_FORM_STEPPER_GAP,
	HOST_CONFIG_STEP_RAIL_WIDTH,
} from "./host-config-form-layout";
import { getWizardStepPageSubtitle } from "./host-wizard-steps";
import { useHostConfigSectionScrollSpy } from "./useHostConfigSectionScrollSpy";

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
 * @param {string[]} [props.existingHostIds]
 * @param {Record<string, 0 | 1 | null>} [props.protocolHealth] live host.up per protocol (single-host edit)
 * @param {boolean} [props.busy]
 * @param {string} [props.sessionPathname]
 * @param {(dirty: boolean) => void} [props.onUnsavedChangesChange]
 * @param {React.ReactNode} [props.headerEndAction] optional control above the step rail (edit mode)
 */
const HostWizardPage = ({
	mode = "create",
	hostId: editHostId = "",
	onSubmit,
	onCancel,
	onDelete,
	resourceGroups = [],
	defaultResourceGroup = null,
	initialState,
	existingHostIds = [],
	protocolHealth,
	busy = false,
	sessionPathname = "",
	onUnsavedChangesChange,
	headerEndAction,
}) => {
	const isEdit = mode === "edit";
	const [cancelDialogOpen, setCancelDialogOpen] = React.useState(false);
	const formRootRef = React.useRef(/** @type {HTMLDivElement | null} */ (null));
	const [scrollAnchorReady, setScrollAnchorReady] = React.useState(false);
	const bindFormRootRef = React.useCallback((node) => {
		formRootRef.current = node;
		setScrollAnchorReady(node instanceof HTMLElement);
	}, []);

	const wizard = useHostWizard({
		open: true,
		mode,
		defaultResourceGroup,
		initialState,
		sessionPathname: isEdit ? "" : sessionPathname,
	});

	const { goToStep } = wizard;
	const [restoreEpoch, setRestoreEpoch] = React.useState(0);
	const [scrollRestoreLocked, setScrollRestoreLocked] = React.useState(
		() => wizard.initialScrollTop != null,
	);

	React.useLayoutEffect(() => {
		const target = wizard.initialScrollTop;
		const el = formRootRef.current;
		if (target == null) {
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
				wizard.updateScrollPosition(el.scrollTop);
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
		wizard.connectorCatalogLoading,
		wizard.initialScrollTop,
		wizard.state.connectorDetectionMode,
		wizard.steps.length,
		wizard.updateScrollPosition,
	]);

	const handleActiveStepChange = React.useCallback(
		(index) => {
			goToStep(index);
		},
		[goToStep],
	);

	const { activeStep: scrollActiveStep, scrollToStepIndex } = useHostConfigSectionScrollSpy({
		enabled: true,
		steps: wizard.steps,
		formRootRef,
		scrollAnchorReady,
		scrollRestoreLocked,
		onActiveStepChange: handleActiveStepChange,
		contentKey: `${wizard.state.connectorDetectionMode}:${wizard.steps.length}:${restoreEpoch}`,
		initialActiveStep: wizard.initialActiveStep,
		onScrollPositionChange: wizard.updateScrollPosition,
	});

	const currentStep = wizard.steps[scrollActiveStep];
	const hasConnectorsStep = wizard.connectorsStepIndex >= 0;

	const scrollToConnectorsStep = React.useCallback(() => {
		const idx = wizard.connectorsStepIndex;
		if (idx < 0) {
			return;
		}
		requestAnimationFrame(() => {
			requestAnimationFrame(() => scrollToStepIndex(idx));
		});
	}, [scrollToStepIndex, wizard.connectorsStepIndex]);

	const emptyStateFingerprint = React.useMemo(() => {
		const baseline = initialState
			? {
					...createEmptyHostWizardState(defaultResourceGroup),
					...normalizeHostWizardState(initialState),
				}
			: createEmptyHostWizardState(defaultResourceGroup);
		return getHostWizardCommittedFingerprint(baseline);
	}, [defaultResourceGroup, initialState]);

	const hasEnteredInformation = React.useMemo(
		() => getHostWizardCommittedFingerprint(wizard.state) !== emptyStateFingerprint,
		[emptyStateFingerprint, wizard.state],
	);

	const hasUnsavedChanges = isEdit ? wizard.isDirty : hasEnteredInformation;
	const canSaveEdit = wizard.isDirty;

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
				wizard.clearSession();
			}
			onCancel();
			return;
		}
		setCancelDialogOpen(true);
	};

	const handleConfirmCancel = () => {
		setCancelDialogOpen(false);
		if (!isEdit) {
			wizard.clearSession();
		}
		onCancel();
	};

	const handleSubmit = async () => {
		const failedAt = wizard.validateAllSteps();
		if (failedAt != null) {
			scrollToStepIndex(failedAt);
			return;
		}
		const connectorsIdx = wizard.connectorsStepIndex;
		if (connectorsIdx >= 0 && !wizard.validateConnectorsStep()) {
			scrollToStepIndex(connectorsIdx);
			return;
		}
		wizard.setSubmitError(null);
		try {
			await onSubmit(wizard.state);
			if (isEdit) {
				wizard.commitSavedBaseline();
			}
			wizard.clearSession();
		} catch (e) {
			wizard.setSubmitError(e?.message || "Failed to save resource");
		}
	};

	const hostNames = React.useMemo(
		() => getHostNames(wizard.state.hostName),
		[wizard.state.hostName],
	);
	const isMultiHostResource = hostNames.length > 1;
	const resourceIconType = isMultiHostResource ? "multi-host-resource" : "resource";

	const pageTitle = React.useMemo(() => {
		if (!isEdit) {
			return "Add Resource";
		}
		if (isMultiHostResource) {
			return String(editHostId || wizard.state.hostId || "Resource").trim();
		}
		if (hostNames.length === 1) {
			return hostNames[0];
		}
		return editHostId || wizard.state.hostId || "Resource";
	}, [editHostId, hostNames, isEdit, isMultiHostResource, wizard.state.hostId]);

	const canSave = isEdit ? canSaveEdit : hasEnteredInformation;
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
								Step {scrollActiveStep + 1} of {wizard.steps.length}
								{getWizardStepPageSubtitle(currentStep)
									? ` — ${getWizardStepPageSubtitle(currentStep)}`
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
									onClick={onDelete}
									disabled={busy}
									sx={guidedConfigDeleteButtonSx}
								>
									Delete
								</Button>
							) : null}
							{!isEdit ? (
								<Button size="small" onClick={handleCancel} disabled={busy}>
									Cancel
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
					{wizard.submitError && (
						<Alert severity="error" sx={{ mt: 2 }}>
							{wizard.submitError}
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
					sx={{
						gridColumn: 1,
						gridRow: 2,
						minHeight: 0,
						minWidth: 0,
						overflowY: "auto",
						overflowX: "hidden",
						pr: { xs: 0.5, md: HOST_CONFIG_CREATE_FORM_CONTENT_PR },
					}}
				>
					<HostConfigFormBody
						wizard={wizard}
						resourceGroups={resourceGroups}
						existingHostIds={existingHostIds}
						protocolHealth={protocolHealth}
						onScrollToConnectorsStep={scrollToConnectorsStep}
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
					sx={{
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
					}}
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
						<HostWizardCardStepper
							steps={wizard.steps}
							activeStep={scrollActiveStep}
							validatedStepIds={wizard.validatedStepIds}
							invalidStepIds={wizard.invalidStepIds}
							editedStepIds={wizard.editedStepIds}
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
					<Button color="error" variant="contained" onClick={handleConfirmCancel}>
						{isEdit ? "Leave without saving" : "Discard"}
					</Button>
				</DialogActions>
			</Dialog>
		</>
	);
};

export default HostWizardPage;
