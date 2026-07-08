import * as React from "react";
import { useLocation } from "react-router-dom";
import { Alert, Box, Button, Paper, Stack, Typography } from "@mui/material";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import SaveIcon from "@mui/icons-material/Save";

import NodeTypeIcons from "../explorer/tree/icons/NodeTypeIcons";
import { useHostsProtocolHealth } from "../../hooks/use-hosts-protocol-health";
import {
	HOST_CONFIG_CREATE_FORM_CONTENT_PR,
	HOST_CONFIG_CREATE_FORM_STEPPER_GAP,
	HOST_CONFIG_STEP_RAIL_WIDTH,
	hostConfigSectionId,
} from "./host-config-form-layout";
import {
	guidedConfigBorderedPanelSx,
	guidedConfigDeleteButtonSx,
	guidedConfigSaveButtonSx,
	LabeledTextField,
} from "./guided-config-form-primitives";
import HostConfigSectionNav from "./HostConfigSectionNav";
import HostConfigSectionHeader from "./HostConfigSectionHeader";
import HostsResourceTable, { buildMultiHostDerivedIds } from "./HostsResourceTable";
import KeyValueRowsEditor, {
	kvRowsToNumericObject,
	kvRowsToObject,
	makeKvRow,
	objectToKvRows,
} from "./KeyValueRowsEditor";
import { getGroupResources, getHostNames, isMultiHostConfig } from "./host-config-utils";
import { sortHostEntries } from "./hosts-filter-utils";
import {
	DEFAULT_METRIC_SEEDS,
	getResourceGroupFormSteps,
	getResourceGroupStepSubtitle,
	METRIC_HELP_BY_KEY,
	buildResourceGroupBaselineSteps,
	getResourceGroupStepFingerprint,
	isResourceGroupStepValid,
	normalizeGroupAttributes,
	normalizeGroupMetrics,
	resourceGroupConfigFingerprint,
	RESOURCE_GROUP_FORM_STEPS,
	RESOURCE_GROUP_RESOURCES_STEP,
} from "./resource-group-form-shared";
import {
	isSessionFromCurrentAppBoot,
	loadHostFormScrollPosition,
	saveHostFormScrollPosition,
} from "./host-config-session";
import { useHostConfigSectionScrollSpy } from "./useHostConfigSectionScrollSpy";

const buildDefaultMetricRows = () => DEFAULT_METRIC_SEEDS.map((s) => makeKvRow(s.key, s.value));

/**
 * Create or edit a resource group (unified form layout).
 *
 * @param {object} props
 * @param {"create" | "edit"} [props.mode]
 * @param {string} [props.groupName] required in edit mode
 * @param {Record<string, unknown>} [props.groupNode] snapshot node in edit mode
 * @param {string[]} [props.existingGroupNames]
 * @param {boolean} [props.busy]
 * @param {(payload: { name: string; attributes: Record<string, string>; metrics: Record<string, number> }) => Promise<void>} [props.onSave] create
 * @param {(payload: { name: string; attributes: Record<string, string>; metrics: Record<string, number> }) => Promise<void>} [props.onUpdate] edit
 * @param {() => void} props.onCancel
 * @param {() => void} [props.onDeleteGroup]
 * @param {(hostId: string) => void} [props.onOpenResource]
 * @param {(hostIds: string[]) => void} [props.onDeleteResources]
 * @param {(dirty: boolean) => void} [props.onUnsavedChangesChange]
 */
const ResourceGroupFormPage = ({
	mode = "create",
	groupName: initialGroupName = "",
	groupNode,
	existingGroupNames = [],
	busy = false,
	onSave,
	onUpdate,
	onCancel,
	onDeleteGroup,
	onOpenResource,
	onDeleteResources,
	onUnsavedChangesChange,
}) => {
	const isEdit = mode === "edit";
	const rawAttributes = groupNode?.attributes || {};
	const rawMetrics = groupNode?.metrics || {};

	const [name, setName] = React.useState(isEdit ? initialGroupName : "");
	const [attributeRows, setAttributeRows] = React.useState(() =>
		isEdit ? objectToKvRows(rawAttributes) : [makeKvRow("site", "")],
	);
	const [metricRows, setMetricRows] = React.useState(() =>
		isEdit ? objectToKvRows(rawMetrics) : buildDefaultMetricRows(),
	);
	const [nameError, setNameError] = React.useState(null);
	const [saving, setSaving] = React.useState(false);
	const [selectedHostIds, setSelectedHostIds] = React.useState([]);
	const formRootRef = React.useRef(/** @type {HTMLDivElement | null} */ (null));

	const siteRowIdRef = React.useRef(attributeRows[0]?.id ?? null);
	const siteAutoSyncRef = React.useRef(!isEdit);

	const createBaselineRef = React.useRef(
		resourceGroupConfigFingerprint(
			"",
			{ site: "" },
			normalizeGroupMetrics(
				Object.fromEntries(DEFAULT_METRIC_SEEDS.map((s) => [s.key, Number(s.value)])),
			),
		),
	);

	const baselineRef = React.useRef(
		isEdit
			? resourceGroupConfigFingerprint(
					initialGroupName,
					normalizeGroupAttributes(rawAttributes),
					normalizeGroupMetrics(rawMetrics),
				)
			: null,
	);
	const baselineStepsRef = React.useRef(
		isEdit ? buildResourceGroupBaselineSteps(initialGroupName, rawAttributes, rawMetrics) : null,
	);
	const [baselineVersion, setBaselineVersion] = React.useState(0);
	const [furthestStep, setFurthestStep] = React.useState(0);

	const steps = React.useMemo(() => getResourceGroupFormSteps(mode), [mode]);

	const { pathname } = useLocation();
	const handleScrollPositionChange = React.useCallback(
		(scrollTop) => {
			saveHostFormScrollPosition(pathname, scrollTop, 0);
		},
		[pathname],
	);

	const { activeStep: scrollActiveStep, scrollToStepIndex } = useHostConfigSectionScrollSpy({
		enabled: true,
		steps,
		formRootRef,
		expandedSections: {},
		onActiveStepChange: (index) => {
			setFurthestStep((prev) => Math.max(prev, index));
		},
		onScrollPositionChange: handleScrollPositionChange,
	});

	// Restores the scroll position after a page refresh only — a session saved during
	// this app lifetime means the user navigated back in-app, where the page starts
	// at the top (same rule as the resource form pages).
	React.useLayoutEffect(() => {
		const el = formRootRef.current;
		const saved = loadHostFormScrollPosition(pathname);
		const target =
			typeof saved?.scrollTop === "number" && saved.scrollTop >= 0 ? saved.scrollTop : null;
		if (!el || target == null || isSessionFromCurrentAppBoot(saved)) {
			return undefined;
		}
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
			if (reachedTarget || reachedMaxForTarget || frame >= 60) {
				// Re-save with the current boot id so an in-app return does not restore again.
				saveHostFormScrollPosition(pathname, el.scrollTop, 0);
				return;
			}
			requestAnimationFrame(attempt);
		};
		requestAnimationFrame(attempt);
		return () => {
			cancelled = true;
		};
	}, [pathname]);

	const handleStepClick = React.useCallback(
		(index) => {
			setFurthestStep((prev) => Math.max(prev, index));
			scrollToStepIndex(index);
		},
		[scrollToStepIndex],
	);

	const resources = React.useMemo(
		() => (isEdit && groupNode ? getGroupResources(groupNode) : {}),
		[groupNode, isEdit],
	);

	const hostEntries = React.useMemo(
		() => sortHostEntries(Object.entries(resources), "name-asc"),
		[resources],
	);

	const hostsForHealth = React.useMemo(() => {
		if (!isEdit) {
			return [];
		}
		/** @type {Array<{ hostId: string; resourceGroup: string }>} */
		const list = [];
		for (const [hostId, hostConfig] of Object.entries(resources)) {
			if (isMultiHostConfig(hostConfig)) {
				const names = getHostNames(hostConfig?.attributes?.["host.name"]);
				for (const derivedId of buildMultiHostDerivedIds(hostId, names)) {
					list.push({ hostId: derivedId, resourceGroup: initialGroupName });
				}
			} else {
				list.push({ hostId, resourceGroup: initialGroupName });
			}
		}
		return list;
	}, [initialGroupName, isEdit, resources]);

	const { healthByHostId } = useHostsProtocolHealth(
		hostsForHealth,
		isEdit && hostEntries.length > 0,
	);

	React.useEffect(() => {
		if (!isEdit) {
			return;
		}
		const attrs = groupNode?.attributes || {};
		const mets = groupNode?.metrics || {};
		setName(initialGroupName);
		setAttributeRows(objectToKvRows(attrs));
		setMetricRows(objectToKvRows(mets));
		setNameError(null);
		setSelectedHostIds([]);
		siteAutoSyncRef.current = false;
		baselineRef.current = resourceGroupConfigFingerprint(
			initialGroupName,
			normalizeGroupAttributes(attrs),
			normalizeGroupMetrics(mets),
		);
		baselineStepsRef.current = buildResourceGroupBaselineSteps(initialGroupName, attrs, mets);
		setBaselineVersion((version) => version + 1);
	}, [groupNode, initialGroupName, isEdit]);

	const currentAttributes = kvRowsToObject(attributeRows);
	const currentMetrics = kvRowsToNumericObject(metricRows);

	const currentStep = steps[scrollActiveStep];
	const stepSubtitle = getResourceGroupStepSubtitle(currentStep);
	const invalidStepIds = React.useMemo(() => (nameError ? ["details"] : []), [nameError]);

	const validatedStepIds = React.useMemo(
		() =>
			steps
				.filter((step) =>
					isResourceGroupStepValid(name, currentAttributes, currentMetrics, step.id, {
						mode: isEdit ? "edit" : "create",
						furthestStepIndex: furthestStep,
					}),
				)
				.map((step) => step.id),
		[steps, name, currentAttributes, currentMetrics, isEdit, furthestStep],
	);

	const editedStepIds = React.useMemo(() => {
		if (!isEdit || !baselineStepsRef.current) {
			return [];
		}
		const baseline = baselineStepsRef.current;
		const edited = [];
		if (
			getResourceGroupStepFingerprint(name, currentAttributes, currentMetrics, "details") !==
			baseline.details
		) {
			edited.push("details");
		}
		if (
			getResourceGroupStepFingerprint(name, currentAttributes, currentMetrics, "metrics") !==
			baseline.metrics
		) {
			edited.push("metrics");
		}
		return edited;
		// baselineVersion bumps after save so edited steps clear without a full reload.
		// eslint-disable-next-line react-hooks/exhaustive-deps -- baselineVersion is intentional
	}, [isEdit, name, currentAttributes, currentMetrics, baselineVersion]);

	const validatedSet = React.useMemo(() => new Set(validatedStepIds), [validatedStepIds]);
	const editedSet = React.useMemo(() => new Set(editedStepIds), [editedStepIds]);
	const invalidSet = React.useMemo(() => new Set(invalidStepIds), [invalidStepIds]);
	const currentFingerprint = resourceGroupConfigFingerprint(
		name,
		currentAttributes,
		currentMetrics,
	);
	const isDirty = isEdit
		? currentFingerprint !== baselineRef.current
		: currentFingerprint !== createBaselineRef.current;

	React.useEffect(() => {
		onUnsavedChangesChange?.(isDirty);
	}, [isDirty, onUnsavedChangesChange]);

	React.useEffect(
		() => () => {
			onUnsavedChangesChange?.(false);
		},
		[onUnsavedChangesChange],
	);

	const handleAttributeRowsChange = (next) => {
		if (siteAutoSyncRef.current) {
			setAttributeRows((prev) => {
				const prevSite = prev.find((r) => r.id === siteRowIdRef.current);
				const nextSite = next.find((r) => r.id === siteRowIdRef.current);
				if (!nextSite) {
					siteAutoSyncRef.current = false;
				} else if (nextSite.key !== "site") {
					siteAutoSyncRef.current = false;
				} else if (prevSite && nextSite.value !== prevSite.value) {
					siteAutoSyncRef.current = false;
				}
				return next;
			});
			return;
		}
		setAttributeRows(next);
	};

	const handleNameChange = (value) => {
		setName(value);
		if (nameError) {
			setNameError(null);
		}
		if (siteAutoSyncRef.current) {
			setAttributeRows((prev) =>
				prev.map((r) => (r.id === siteRowIdRef.current ? { ...r, value } : r)),
			);
		}
	};

	const validateName = () => {
		const trimmedName = name.trim();
		if (!trimmedName) {
			setNameError("Group name is required.");
			scrollToStepIndex(0);
			return null;
		}
		if (existingGroupNames.includes(trimmedName) && (!isEdit || trimmedName !== initialGroupName)) {
			setNameError(`A resource group named "${trimmedName}" already exists.`);
			scrollToStepIndex(0);
			return null;
		}
		return trimmedName;
	};

	const buildPayload = () => ({
		name: name.trim(),
		attributes: currentAttributes,
		metrics: currentMetrics,
	});

	const resetCreateForm = React.useCallback(() => {
		const defaultMetricRows = buildDefaultMetricRows();
		const defaultMetrics = normalizeGroupMetrics(
			Object.fromEntries(DEFAULT_METRIC_SEEDS.map((s) => [s.key, Number(s.value)])),
		);
		setName("");
		setAttributeRows([makeKvRow("site", "")]);
		setMetricRows(defaultMetricRows);
		setNameError(null);
		setSelectedHostIds([]);
		siteAutoSyncRef.current = true;
		createBaselineRef.current = resourceGroupConfigFingerprint("", { site: "" }, defaultMetrics);
		setBaselineVersion((version) => version + 1);
		setFurthestStep(0);
		scrollToStepIndex(0);
	}, [scrollToStepIndex]);

	const handleSubmit = async () => {
		const trimmedName = validateName();
		if (!trimmedName) {
			return;
		}
		setSaving(true);
		try {
			if (isEdit) {
				await onUpdate?.(buildPayload());
				baselineRef.current = resourceGroupConfigFingerprint(
					trimmedName,
					currentAttributes,
					currentMetrics,
				);
				baselineStepsRef.current = buildResourceGroupBaselineSteps(
					trimmedName,
					currentAttributes,
					currentMetrics,
				);
				setBaselineVersion((version) => version + 1);
			} else {
				await onSave?.(buildPayload());
				resetCreateForm();
			}
		} finally {
			setSaving(false);
		}
	};

	const pageTitle = isEdit
		? initialGroupName || name.trim() || "Resource Group"
		: "New Resource Group";
	const submitLabel = isEdit ? "Save" : "Create Group";
	const canSave = !isEdit || isDirty;

	if (isEdit && !groupNode) {
		return (
			<Alert severity="warning">Resource group &quot;{initialGroupName}&quot; was not found.</Alert>
		);
	}

	return (
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
							<NodeTypeIcons type="resource-group" fontSize="large" />
							<Typography variant="h4" fontWeight={700} sx={{ lineHeight: 1.2 }}>
								{pageTitle}
							</Typography>
						</Stack>
						<Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
							Step {scrollActiveStep + 1} of {steps.length}
							{stepSubtitle ? ` — ${stepSubtitle}` : ""}
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
						{isEdit && onDeleteGroup ? (
							<Button
								size="small"
								variant="contained"
								startIcon={<DeleteOutlineIcon />}
								onClick={onDeleteGroup}
								disabled={busy || saving}
								sx={guidedConfigDeleteButtonSx}
							>
								Delete
							</Button>
						) : null}
						{!isEdit ? (
							<Button size="small" onClick={onCancel} disabled={busy || saving}>
								Cancel
							</Button>
						) : null}
						<Button
							size="small"
							variant="contained"
							startIcon={<SaveIcon />}
							onClick={() => void handleSubmit()}
							disabled={busy || saving || !canSave}
							sx={guidedConfigSaveButtonSx}
						>
							{busy || saving ? "Saving..." : submitLabel}
						</Button>
					</Stack>
				</Stack>
			</Box>

			<Box
				ref={formRootRef}
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
				<Stack spacing={4}>
					<Box id={hostConfigSectionId(RESOURCE_GROUP_FORM_STEPS[0])} sx={{ scrollMarginTop: 88 }}>
						<Box component="section" sx={guidedConfigBorderedPanelSx}>
							<HostConfigSectionHeader
								step={RESOURCE_GROUP_FORM_STEPS[0]}
								stepNumber={1}
								isCompleted={
									validatedSet.has(RESOURCE_GROUP_FORM_STEPS[0].id) &&
									!editedSet.has(RESOURCE_GROUP_FORM_STEPS[0].id)
								}
								isInvalid={invalidSet.has(RESOURCE_GROUP_FORM_STEPS[0].id)}
								isEdited={editedSet.has(RESOURCE_GROUP_FORM_STEPS[0].id)}
								description={RESOURCE_GROUP_FORM_STEPS[0].description}
							/>
							<Stack spacing={2.5}>
								<LabeledTextField
									id="resource-group-name"
									label="Group name"
									required
									autoFocus={!isEdit}
									placeholder="e.g. Production datacenter"
									value={name}
									onChange={(e) => handleNameChange(e.target.value)}
									onKeyDown={(e) => {
										if (e.key === "Enter" && (!isEdit || isDirty)) {
											void handleSubmit();
										}
									}}
									error={Boolean(nameError)}
									helperText={
										nameError ||
										"Short label for the tree and configuration file (site, region, business unit…)."
									}
								/>
								<KeyValueRowsEditor
									rows={attributeRows}
									onRowsChange={handleAttributeRowsChange}
									addLabel="Add attribute"
									keyLabel="Key"
									valueLabel="Value"
									sectionTitle="Attributes"
									labelsAbove
									bordered
									defaultRows={[{ key: "site", value: name }]}
								/>
							</Stack>
						</Box>
					</Box>

					<Box id={hostConfigSectionId(RESOURCE_GROUP_FORM_STEPS[1])} sx={{ scrollMarginTop: 88 }}>
						<Box component="section" sx={guidedConfigBorderedPanelSx}>
							<HostConfigSectionHeader
								step={RESOURCE_GROUP_FORM_STEPS[1]}
								stepNumber={2}
								isCompleted={
									validatedSet.has(RESOURCE_GROUP_FORM_STEPS[1].id) &&
									!editedSet.has(RESOURCE_GROUP_FORM_STEPS[1].id)
								}
								isInvalid={invalidSet.has(RESOURCE_GROUP_FORM_STEPS[1].id)}
								isEdited={editedSet.has(RESOURCE_GROUP_FORM_STEPS[1].id)}
								description={RESOURCE_GROUP_FORM_STEPS[1].description}
							/>
							<KeyValueRowsEditor
								rows={metricRows}
								onRowsChange={setMetricRows}
								addLabel="Add metric"
								keyLabel="Key"
								valueLabel="Value"
								labelsAbove
								primaryHelpIcons
								monospaceKeys
								valueInputMode="decimal"
								helpByKey={METRIC_HELP_BY_KEY}
								defaultRows={DEFAULT_METRIC_SEEDS}
							/>
						</Box>
					</Box>

					{isEdit ? (
						<Box
							id={hostConfigSectionId(RESOURCE_GROUP_RESOURCES_STEP)}
							sx={{ scrollMarginTop: 88 }}
						>
							<Box component="section" sx={guidedConfigBorderedPanelSx}>
								<HostConfigSectionHeader
									step={RESOURCE_GROUP_RESOURCES_STEP}
									stepNumber={3}
									isCompleted={
										validatedSet.has(RESOURCE_GROUP_RESOURCES_STEP.id) &&
										!editedSet.has(RESOURCE_GROUP_RESOURCES_STEP.id)
									}
									isInvalid={invalidSet.has(RESOURCE_GROUP_RESOURCES_STEP.id)}
									isEdited={editedSet.has(RESOURCE_GROUP_RESOURCES_STEP.id)}
									description={RESOURCE_GROUP_RESOURCES_STEP.description}
									endAction={
										onDeleteResources && selectedHostIds.length > 0 ? (
											<Button
												variant="outlined"
												color="error"
												size="small"
												startIcon={<DeleteOutlineIcon />}
												onClick={() => onDeleteResources(selectedHostIds)}
												disabled={busy || saving}
											>
												Delete ({selectedHostIds.length})
											</Button>
										) : null
									}
								/>
								<Stack spacing={2}>
									{hostEntries.length === 0 ? (
										<Alert severity="info">This resource group has no resources yet.</Alert>
									) : (
										<HostsResourceTable
											hostEntries={hostEntries}
											healthByHostId={healthByHostId}
											onHostClick={onOpenResource}
											checkboxSelection
											selectedHostIds={selectedHostIds}
											onSelectedHostIdsChange={setSelectedHostIds}
											paginated
										/>
									)}
								</Stack>
							</Box>
						</Box>
					) : null}
				</Stack>
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
					<HostConfigSectionNav
						steps={steps}
						activeStep={scrollActiveStep}
						validatedStepIds={validatedStepIds}
						invalidStepIds={invalidStepIds}
						editedStepIds={editedStepIds}
						onStepClick={handleStepClick}
					/>
				</Paper>
			</Box>
		</Box>
	);
};

export default ResourceGroupFormPage;
