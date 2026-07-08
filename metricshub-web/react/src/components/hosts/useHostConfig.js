import * as React from "react";
import {
	filterSelectedProtocolsForHostType,
	PROTOCOL_DEFAULTS,
	collectProtocolConfigErrors,
	protocolConfigToForm,
} from "./protocol-definitions";
import {
	buildFormSections,
	findStepIndex,
	getOrderedSelectedProtocols,
	getStepAt,
} from "./host-config-sections";
import {
	clearHostFormSession,
	clearHostFormScrollPosition,
	getHostFormSessionKey,
	isSessionFromCurrentAppBoot,
	loadHostFormScrollPosition,
	loadHostFormSession,
	saveHostFormScrollPosition,
	saveHostFormSession,
} from "./host-config-session";
import { compareLocale } from "../../utils/alphabetic-sort";
import {
	annotateConnectorCatalog,
	applyAdditionalConnectorsChange,
	collectConnectorVariablesErrors,
	computeCompatibleConnectorIdsFromCatalog,
	fetchConnectorCatalog,
	pruneFormConnectorsForHostContext,
} from "./connector-utils";
import {
	applySelectedProtocols,
	createEmptyHostFormState,
	getHostFormCommittedFingerprint,
	isHostFormDirty,
	normalizeHostFormState,
	getFormSectionFingerprint,
} from "./host-config-state";

/**
 * @param {object} state
 * @param {import("./host-config-sections").FormSectionDescriptor[]} steps
 * @returns {boolean}
 */
const areAllFormSectionsValid = (state, steps) => {
	if (!steps.length) {
		return false;
	}
	for (const step of steps) {
		switch (step.type) {
			case "basics": {
				if (!String(state.hostId || "").trim()) {
					return false;
				}
				if (!String(state.hostName || "").trim()) {
					return false;
				}
				if (!state.hostType) {
					return false;
				}
				if (state.targetType !== "standalone" && state.targetType !== "group") {
					return false;
				}
				if (state.targetType === "group" && !String(state.resourceGroup || "").trim()) {
					return false;
				}
				if ((state.selectedProtocols || []).length === 0) {
					return false;
				}
				break;
			}
			case "protocol": {
				if (!step.protocolId) {
					return false;
				}
				const fieldErrors = collectProtocolConfigErrors(
					step.protocolId,
					state.protocols?.[step.protocolId] || {},
					{
						hostId: state.hostId,
						hostName: state.hostName,
					},
				);
				if (Object.keys(fieldErrors).length > 0) {
					return false;
				}
				break;
			}
			case "connectors": {
				if (state.connectorDetectionMode === "manual" || state.connectorDetectionMode === "raw") {
					const hasDirectives = (state.connectors ?? []).length > 0;
					const hasAdditionalConnectors = Object.keys(state.additionalConnectors || {}).length > 0;
					if (!hasDirectives && !hasAdditionalConnectors) {
						return false;
					}
				}
				if (
					!collectConnectorVariablesErrors({
						additionalConnectors: state.additionalConnectors,
						selectedVariableConnectorTemplates: [],
					}).valid
				) {
					return false;
				}
				break;
			}
			default:
				break;
		}
	}
	return true;
};

/**
 * @param {object} options
 * @param {object | null | undefined} [options.sessionState]
 * @param {object | null | undefined} [options.initialState]
 * @param {string | null} [options.defaultResourceGroup]
 * @param {"create" | "edit"} options.mode
 */
const buildFormStateFromSources = ({
	sessionState,
	initialState,
	defaultResourceGroup = null,
	mode,
}) => {
	const serverState = initialState
		? {
				...createEmptyHostFormState(defaultResourceGroup),
				...normalizeHostFormState(initialState),
				_editMode: mode === "edit",
			}
		: null;

	if (sessionState) {
		return {
			...(serverState || createEmptyHostFormState(defaultResourceGroup)),
			...normalizeHostFormState(sessionState),
			_editMode: mode === "edit",
		};
	}

	return (
		serverState || {
			...createEmptyHostFormState(defaultResourceGroup),
			_editMode: mode === "edit",
		}
	);
};

/**
 * @param {object} options
 * @param {boolean} [options.open]
 * @param {"create" | "edit"} options.mode
 * @param {string | null} [options.defaultResourceGroup] pre-selects group placement on create
 * @param {object | null | undefined} [options.initialState]
 * @param {string} [options.sessionPathname]
 * @param {() => void} [options.onSessionClear]
 * @returns {object}
 */
export const useHostConfig = ({
	open = true,
	mode,
	defaultResourceGroup = null,
	initialState,
	sessionPathname = "",
	onSessionClear,
}) => {
	const sessionKey = React.useMemo(
		() =>
			getHostFormSessionKey({
				mode,
				defaultResourceGroup,
				hostId: initialState?.hostId || initialState?.originalHostId,
				pathname: sessionPathname,
			}),
		[
			mode,
			defaultResourceGroup,
			initialState?.hostId,
			initialState?.originalHostId,
			sessionPathname,
		],
	);

	const bootSession = React.useMemo(
		() => (open ? loadHostFormSession(sessionKey) : null),
		[open, sessionKey],
	);

	// Scroll/step restore is refresh-only: a session written during this app
	// lifetime means the user navigated back in-app, where the form starts at the top.
	const restoredScrollTop = React.useMemo(() => {
		const top = bootSession?.scrollTop;
		if (typeof top === "number" && top >= 0 && !isSessionFromCurrentAppBoot(bootSession)) {
			return top;
		}
		const fallback = loadHostFormScrollPosition(sessionPathname);
		if (
			typeof fallback?.scrollTop === "number" &&
			fallback.scrollTop >= 0 &&
			!isSessionFromCurrentAppBoot(fallback)
		) {
			return fallback.scrollTop;
		}
		return null;
	}, [bootSession, sessionPathname]);

	const restoredActiveStep = React.useMemo(() => {
		const fromBoot = isSessionFromCurrentAppBoot(bootSession) ? null : bootSession?.activeStep;
		const fallback = loadHostFormScrollPosition(sessionPathname);
		const fromFallback = isSessionFromCurrentAppBoot(fallback) ? null : fallback?.activeStep;
		const stepValue = typeof fromBoot === "number" ? fromBoot : fromFallback;
		if (typeof stepValue !== "number") {
			return null;
		}
		const sessionState = bootSession.state
			? {
					...createEmptyHostFormState(defaultResourceGroup),
					...normalizeHostFormState(bootSession.state),
				}
			: createEmptyHostFormState(defaultResourceGroup);
		const max = Math.max(0, buildFormSections(sessionState).length - 1);
		return Math.min(Math.max(0, stepValue), max);
	}, [bootSession, defaultResourceGroup, sessionPathname]);

	const [activeStep, setActiveStep] = React.useState(() => restoredActiveStep ?? 0);
	const [state, setState] = React.useState(() =>
		buildFormStateFromSources({
			sessionState: bootSession?.state,
			initialState,
			defaultResourceGroup,
			mode,
		}),
	);
	const [errors, setErrors] = React.useState({});
	const [submitError, setSubmitError] = React.useState(null);
	const [validatedStepIds, setValidatedStepIds] = React.useState(() => new Set());
	const [invalidStepIds, setInvalidStepIds] = React.useState(() => new Set());
	const baselineFingerprintRef = React.useRef("");
	const baselineStateRef = React.useRef(/** @type {object | null} */ (null));
	const scrollTopRef = React.useRef(restoredScrollTop ?? 0);
	const activeStepRef = React.useRef(activeStep);
	activeStepRef.current = activeStep;

	React.useLayoutEffect(() => {
		if (restoredScrollTop !== null) {
			scrollTopRef.current = restoredScrollTop;
		}
		if (restoredActiveStep !== null) {
			setActiveStep(restoredActiveStep);
		}
	}, [restoredActiveStep, restoredScrollTop, sessionKey]);
	const [baselineVersion, setBaselineVersion] = React.useState(0);
	const skipNextPersistRef = React.useRef(false);
	const [fullConnectorCatalog, setFullConnectorCatalog] = React.useState([]);
	const [connectorCatalogLoading, setConnectorCatalogLoading] = React.useState(false);
	const [connectorCatalogError, setConnectorCatalogError] = React.useState(null);
	const connectorVariablesEditRef = React.useRef(
		/** @type {{ instanceKey: string | null; instanceId: string }} */ ({
			instanceKey: null,
			instanceId: "",
		}),
	);
	const [highlightConnectorInstanceId, setHighlightConnectorInstanceId] = React.useState(
		/** @type {string | null} */ (null),
	);
	const [connectorVariablesValidationAttempt, setConnectorVariablesValidationAttempt] =
		React.useState(0);
	const stateRef = React.useRef(state);
	stateRef.current = state;

	const steps = React.useMemo(() => buildFormSections(state), [state]);

	const selectedProtocolsKey = React.useMemo(
		() => getOrderedSelectedProtocols(state.selectedProtocols).join("\u0001"),
		[state.selectedProtocols],
	);

	const connectorSelectionKey = React.useMemo(() => {
		const connectors = [...(state.connectors ?? [])].map(String).sort(compareLocale);
		const additional = Object.keys(state.additionalConnectors ?? {}).sort(compareLocale);
		return `${connectors.join("\u0001")}\u0002${additional.join("\u0001")}`;
	}, [state.connectors, state.additionalConnectors]);

	React.useEffect(() => {
		if (activeStep >= steps.length && steps.length > 0) {
			setActiveStep(steps.length - 1);
		}
	}, [activeStep, steps.length]);

	React.useEffect(() => {
		if (!open) {
			return undefined;
		}
		let cancelled = false;
		setConnectorCatalogLoading(true);
		setConnectorCatalogError(null);
		void fetchConnectorCatalog()
			.then((catalog) => {
				if (!cancelled) {
					setFullConnectorCatalog(catalog);
					setConnectorCatalogLoading(false);
				}
			})
			.catch(() => {
				if (!cancelled) {
					setFullConnectorCatalog([]);
					setConnectorCatalogError("Failed to load connectors");
					setConnectorCatalogLoading(false);
				}
			});
		return () => {
			cancelled = true;
		};
	}, [open]);

	const annotatedConnectorCatalog = React.useMemo(() => {
		const hostType = String(state.hostType ?? "").trim();
		if (!hostType || !selectedProtocolsKey || fullConnectorCatalog.length === 0) {
			return [];
		}
		return annotateConnectorCatalog(fullConnectorCatalog, {
			hostType,
			protocols: state.selectedProtocols,
		});
	}, [fullConnectorCatalog, state.hostType, state.selectedProtocols, selectedProtocolsKey]);

	const connectorCatalog = React.useMemo(
		() => annotatedConnectorCatalog.filter((item) => item.compatible),
		[annotatedConnectorCatalog],
	);

	const variableConnectorTemplates = React.useMemo(() => {
		const map = new Map();
		for (const item of annotatedConnectorCatalog) {
			if (item?.compatible && item?.hasVariables && item.id) {
				map.set(String(item.id), item);
			}
		}
		return map;
	}, [annotatedConnectorCatalog]);

	React.useEffect(() => {
		const hostType = String(state.hostType ?? "").trim();
		if (!hostType || !selectedProtocolsKey) {
			setState((prev) => {
				if (
					(prev.compatibleConnectorIds ?? []).length === 0 &&
					(prev.compatibleVariableConnectorIds ?? []).length === 0 &&
					!prev.compatibleVariableConnectorIdsStale
				) {
					return prev;
				}
				return {
					...prev,
					compatibleConnectorIds: [],
					compatibleVariableConnectorIds: [],
					compatibleVariableConnectorIdsStale: false,
				};
			});
		}
	}, [state.hostType, selectedProtocolsKey]);

	React.useEffect(() => {
		const hostType = String(state.hostType ?? "").trim();
		if (!hostType || !selectedProtocolsKey || connectorCatalogLoading) {
			return;
		}
		const catalogById = connectorCatalogError
			? null
			: new Map(connectorCatalog.map((item) => [String(item.id || ""), item]));
		setState((prev) => {
			const pruned = pruneFormConnectorsForHostContext(prev, annotatedConnectorCatalog);
			const nextState = {
				...prev,
				...pruned,
			};
			const { compatibleConnectorIds, compatibleVariableConnectorIds } =
				computeCompatibleConnectorIdsFromCatalog(nextState, catalogById, {
					failed: Boolean(connectorCatalogError),
				});

			const compatibleConnectorIdsUnchanged =
				JSON.stringify(prev.compatibleConnectorIds ?? []) ===
				JSON.stringify(compatibleConnectorIds);
			const compatibleVariableIdsUnchanged =
				JSON.stringify(prev.compatibleVariableConnectorIds ?? []) ===
				JSON.stringify(compatibleVariableConnectorIds);
			const prunedUnchanged =
				JSON.stringify(prev.connectors ?? []) === JSON.stringify(pruned.connectors) &&
				JSON.stringify(prev.additionalConnectors ?? {}) ===
					JSON.stringify(pruned.additionalConnectors) &&
				JSON.stringify(prev.selectedVariableConnectorTemplates ?? []) ===
					JSON.stringify(pruned.selectedVariableConnectorTemplates) &&
				Boolean(prev.configureVariableConnectors) === Boolean(pruned.configureVariableConnectors);
			if (
				compatibleConnectorIdsUnchanged &&
				compatibleVariableIdsUnchanged &&
				prunedUnchanged &&
				!prev.compatibleVariableConnectorIdsStale
			) {
				return prev;
			}
			return {
				...nextState,
				compatibleConnectorIds,
				compatibleVariableConnectorIds,
				compatibleVariableConnectorIdsStale: false,
			};
		});
	}, [
		connectorSelectionKey,
		connectorCatalog,
		annotatedConnectorCatalog,
		connectorCatalogError,
		connectorCatalogLoading,
		state.hostType,
		selectedProtocolsKey,
	]);

	React.useEffect(() => {
		const step = getStepAt(steps, activeStep);
		if (step?.type === "protocol" && step.protocolId) {
			const selected = new Set(state.selectedProtocols || []);
			if (!selected.has(step.protocolId)) {
				setActiveStep(0);
			}
		}
	}, [state.selectedProtocols, steps, activeStep]);

	const initialStateFingerprint = React.useMemo(
		() =>
			initialState ? getHostFormCommittedFingerprint(normalizeHostFormState(initialState)) : "",
		[initialState],
	);

	React.useEffect(() => {
		if (!open) {
			return;
		}
		setErrors({});
		setSubmitError(null);
		setValidatedStepIds(new Set());
		setInvalidStepIds(new Set());

		const fromSession = loadHostFormSession(sessionKey);
		const sessionState = fromSession?.state;

		const nextState = buildFormStateFromSources({
			sessionState,
			initialState,
			defaultResourceGroup,
			mode,
		});

		const serverState = initialState
			? {
					...createEmptyHostFormState(defaultResourceGroup),
					...normalizeHostFormState(initialState),
					_editMode: mode === "edit",
				}
			: null;

		const baselineState = serverState || nextState;

		skipNextPersistRef.current = true;
		setState(nextState);
		const fallbackScroll = loadHostFormScrollPosition(sessionPathname);
		if (typeof fromSession?.scrollTop === "number" && fromSession.scrollTop >= 0) {
			scrollTopRef.current = fromSession.scrollTop;
		} else if (typeof fallbackScroll?.scrollTop === "number" && fallbackScroll.scrollTop >= 0) {
			scrollTopRef.current = fallbackScroll.scrollTop;
		} else {
			scrollTopRef.current = 0;
		}
		const maxStep = buildFormSections(nextState).length - 1;
		if (typeof fromSession?.activeStep === "number") {
			setActiveStep(Math.min(Math.max(0, fromSession.activeStep), maxStep));
		} else if (typeof fallbackScroll?.activeStep === "number") {
			setActiveStep(Math.min(Math.max(0, fallbackScroll.activeStep), maxStep));
		} else {
			setActiveStep(0);
		}
		baselineFingerprintRef.current = getHostFormCommittedFingerprint(baselineState);
		baselineStateRef.current = baselineState;
		if (mode === "edit" && initialState) {
			const loadedSteps = buildFormSections(nextState);
			if (areAllFormSectionsValid(nextState, loadedSteps)) {
				setValidatedStepIds(new Set(loadedSteps.map((step) => step.id)));
				setInvalidStepIds(new Set());
			}
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps -- reset only when loaded config identity changes
	}, [open, initialStateFingerprint, defaultResourceGroup, mode, sessionKey]);

	React.useEffect(() => {
		setState((prev) => {
			const max = Math.max(0, steps.length - 1);
			if ((prev.furthestStep ?? 0) <= max) {
				return prev;
			}
			return { ...prev, furthestStep: max };
		});
	}, [steps.length]);

	React.useEffect(() => {
		if (!open || skipNextPersistRef.current) {
			skipNextPersistRef.current = false;
			return;
		}
		saveHostFormSession(sessionKey, {
			state,
			activeStep,
			furthestStep: state.furthestStep,
			scrollTop: scrollTopRef.current,
		});
	}, [open, sessionKey, state, activeStep]);

	const persistFormSession = React.useCallback(() => {
		if (!open) {
			return;
		}
		saveHostFormSession(sessionKey, {
			state: stateRef.current,
			activeStep: activeStepRef.current,
			furthestStep: stateRef.current.furthestStep,
			scrollTop: scrollTopRef.current,
		});
		if (sessionPathname) {
			saveHostFormScrollPosition(sessionPathname, scrollTopRef.current, activeStepRef.current);
		}
	}, [open, sessionKey, sessionPathname]);

	React.useEffect(() => {
		if (!open) {
			return undefined;
		}
		const persist = () => {
			persistFormSession();
		};
		const onVisibilityChange = () => {
			if (document.visibilityState === "hidden") {
				persist();
			}
		};
		window.addEventListener("pagehide", persist);
		window.addEventListener("beforeunload", persist);
		document.addEventListener("visibilitychange", onVisibilityChange);
		return () => {
			window.removeEventListener("pagehide", persist);
			window.removeEventListener("beforeunload", persist);
			document.removeEventListener("visibilitychange", onVisibilityChange);
		};
	}, [open, persistFormSession]);

	const updateScrollPosition = React.useCallback(
		(top) => {
			scrollTopRef.current = top;
			persistFormSession();
		},
		[persistFormSession],
	);

	const isDirty = mode === "edit" && isHostFormDirty(state, baselineFingerprintRef.current);
	const showSaveHostChanges = isDirty;

	const patchState = React.useCallback((patch) => {
		setState((prev) => {
			let next = { ...prev, ...patch };
			if (patch.hostType !== undefined || patch.selectedProtocols !== undefined) {
				const filtered = filterSelectedProtocolsForHostType(next.selectedProtocols, next.hostType);
				next = applySelectedProtocols(next, filtered);
				const protocols = { ...(next.protocols || {}) };
				for (const protocolId of next.selectedProtocols || []) {
					if (!protocols[protocolId]) {
						protocols[protocolId] = protocolConfigToForm(
							protocolId,
							PROTOCOL_DEFAULTS[protocolId] || {},
						);
					}
				}
				next = { ...next, protocols };
			}
			if (patch.additionalConnectors !== undefined || patch.connectors !== undefined) {
				next.compatibleVariableConnectorIdsStale = true;
				if (
					next.connectorDetectionMode === "automatic" &&
					((patch.connectors ?? next.connectors ?? []).length > 0 ||
						Object.keys(patch.additionalConnectors ?? next.additionalConnectors ?? {}).length > 0)
				) {
					next.connectorDetectionMode = "manual";
				}
			}
			if (patch.selectedVariableConnectorTemplates !== undefined) {
				const templates = (next.selectedVariableConnectorTemplates || [])
					.map(String)
					.filter(Boolean);
				next.selectedVariableConnectorTemplates = templates;
				next.configureVariableConnectors = templates.length > 0;
				next.compatibleVariableConnectorIdsStale = true;
				if (next.connectorDetectionMode === "automatic" && templates.length > 0) {
					next.connectorDetectionMode = "manual";
				}
			}
			return next;
		});
		setErrors({});
	}, []);

	const advanceFurthest = React.useCallback((stepIndex) => {
		setState((prev) => ({
			...prev,
			furthestStep: Math.max(prev.furthestStep ?? 0, stepIndex),
		}));
	}, []);

	const validateBasics = React.useCallback(() => {
		/** @type {Record<string, string>} */
		const next = {};
		if (!String(state.hostId || "").trim()) {
			next.hostId = "Resource ID is required";
		}
		if (!String(state.hostName || "").trim()) {
			next.hostName = "host.name is required";
		}
		if (!state.hostType) {
			next.hostType = "host.type is required";
		}
		if (state.targetType !== "standalone" && state.targetType !== "group") {
			next.resourceGroup = "Select a resource group or no resource group";
		}
		if (state.targetType === "group" && !String(state.resourceGroup || "").trim()) {
			next.resourceGroup = "Select a resource group";
		}
		if ((state.selectedProtocols || []).length === 0) {
			next.selectedProtocols = "Select at least one protocol";
		}
		setErrors(next);
		return Object.keys(next).length === 0;
	}, [state]);

	const validateProtocolStep = React.useCallback(
		(stepIndex) => {
			const step = getStepAt(steps, stepIndex);
			if (!step || step.type !== "protocol" || !step.protocolId) {
				return true;
			}
			const config = state.protocols?.[step.protocolId] || {};
			const fieldErrors = collectProtocolConfigErrors(step.protocolId, config, {
				hostId: state.hostId,
				hostName: state.hostName,
			});
			if (Object.keys(fieldErrors).length > 0) {
				setErrors(fieldErrors);
				return false;
			}
			setErrors({});
			return true;
		},
		[steps, state],
	);

	const validateConnectorsStep = React.useCallback(() => {
		if (state.connectorDetectionMode !== "manual" && state.connectorDetectionMode !== "raw") {
			setErrors({});
			return true;
		}
		/** @type {Record<string, string>} */
		const next = {};
		const hasDirectives = (state.connectors ?? []).length > 0;
		const hasAdditionalConnectors = Object.keys(state.additionalConnectors || {}).length > 0;
		if (!hasDirectives && !hasAdditionalConnectors) {
			next._connectors =
				"Select at least one connector or tag directive, add a configuration, or switch to Automatic detection.";
		}
		const variablesResult = collectConnectorVariablesErrors({
			additionalConnectors: state.additionalConnectors,
			selectedVariableConnectorTemplates: [],
			editDraft: connectorVariablesEditRef.current,
		});
		Object.assign(next, variablesResult.errors);
		setErrors(next);
		if (!variablesResult.valid) {
			if (variablesResult.highlightInstanceId) {
				setHighlightConnectorInstanceId(variablesResult.highlightInstanceId);
			}
			setConnectorVariablesValidationAttempt((attempt) => attempt + 1);
			return false;
		}
		setHighlightConnectorInstanceId(null);
		return Object.keys(next).length === 0;
	}, [state]);

	const validateConnectorVariablesStep = React.useCallback(
		() => validateConnectorsStep(),
		[validateConnectorsStep],
	);

	const setConnectorVariablesEditState = React.useCallback((draft) => {
		connectorVariablesEditRef.current = {
			instanceKey: draft?.instanceKey ? String(draft.instanceKey) : null,
			instanceId: String(draft?.instanceId ?? ""),
		};
	}, []);

	const clearHighlightConnectorInstance = React.useCallback(() => {
		setHighlightConnectorInstanceId(null);
	}, []);

	const validateStepIndex = React.useCallback(
		(stepIndex) => {
			const step = getStepAt(steps, stepIndex);
			if (!step) {
				return true;
			}
			switch (step.type) {
				case "basics":
					return validateBasics();
				case "protocol":
					return validateProtocolStep(stepIndex);
				case "connectors":
					return validateConnectorsStep();
				default:
					return true;
			}
		},
		[steps, validateBasics, validateProtocolStep, validateConnectorsStep],
	);

	const setStepValidationState = React.useCallback(
		(stepIndex, valid) => {
			const step = getStepAt(steps, stepIndex);
			if (!step) {
				return;
			}
			setValidatedStepIds((prev) => {
				const next = new Set(prev);
				if (valid) {
					next.add(step.id);
				} else {
					next.delete(step.id);
				}
				return next;
			});
			setInvalidStepIds((prev) => {
				const next = new Set(prev);
				if (valid) {
					next.delete(step.id);
				} else {
					next.add(step.id);
				}
				return next;
			});
		},
		[steps],
	);

	const goToStep = React.useCallback(
		(targetStep) => {
			if (targetStep === activeStep) {
				return;
			}
			setSubmitError(null);
			setActiveStep(targetStep);
			advanceFurthest(targetStep);
		},
		[activeStep, advanceFurthest],
	);

	const handleNext = React.useCallback(() => {
		if (!validateStepIndex(activeStep)) {
			setStepValidationState(activeStep, false);
			return;
		}
		setStepValidationState(activeStep, true);
		const next = Math.min(activeStep + 1, steps.length - 1);
		setActiveStep(next);
		advanceFurthest(next);
		setSubmitError(null);
		setErrors({});
	}, [activeStep, steps.length, validateStepIndex, setStepValidationState, advanceFurthest]);

	const handleBack = React.useCallback(() => {
		setSubmitError(null);
		setErrors({});
		setActiveStep((step) => Math.max(0, step - 1));
	}, []);

	const patchProtocolField = React.useCallback((protocolId, name, value) => {
		setState((prev) => ({
			...prev,
			protocols: {
				...prev.protocols,
				[protocolId]: {
					...(prev.protocols[protocolId] ||
						protocolConfigToForm(protocolId, PROTOCOL_DEFAULTS[protocolId] || {})),
					[name]: value,
				},
			},
		}));
		setErrors((prev) => {
			const next = { ...prev };
			delete next._protocol;
			delete next[name];
			return next;
		});
	}, []);

	const ensureProtocolConfig = React.useCallback((protocolId) => {
		setState((prev) => {
			if (prev.protocols?.[protocolId]) {
				return prev;
			}
			return {
				...prev,
				protocols: {
					...prev.protocols,
					[protocolId]: protocolConfigToForm(protocolId, PROTOCOL_DEFAULTS[protocolId] || {}),
				},
			};
		});
	}, []);

	React.useEffect(() => {
		for (const protocolId of getOrderedSelectedProtocols(state.selectedProtocols)) {
			ensureProtocolConfig(protocolId);
		}
	}, [state.selectedProtocols, ensureProtocolConfig]);

	const validateAllSteps = React.useCallback(() => {
		const nextValidated = new Set(validatedStepIds);
		const nextInvalid = new Set(invalidStepIds);
		for (let i = 0; i < steps.length; i++) {
			const step = getStepAt(steps, i);
			if (!validateStepIndex(i)) {
				if (step) {
					nextValidated.delete(step.id);
					nextInvalid.add(step.id);
				}
				setValidatedStepIds(nextValidated);
				setInvalidStepIds(nextInvalid);
				return i;
			}
			if (step) {
				nextValidated.add(step.id);
				nextInvalid.delete(step.id);
			}
		}
		setValidatedStepIds(nextValidated);
		setInvalidStepIds(nextInvalid);
		setErrors({});
		return null;
	}, [invalidStepIds, steps, validateStepIndex, validatedStepIds]);

	const allStepsValid = React.useMemo(() => areAllFormSectionsValid(state, steps), [state, steps]);

	const commitSavedBaseline = React.useCallback(() => {
		baselineStateRef.current = normalizeHostFormState(state);
		baselineFingerprintRef.current = getHostFormCommittedFingerprint(state);
		setBaselineVersion((version) => version + 1);
	}, [state]);

	const editedStepIds = React.useMemo(() => {
		if (mode !== "edit" || !baselineStateRef.current) {
			return [];
		}
		const baseline = baselineStateRef.current;
		return steps
			.filter(
				(step) =>
					getFormSectionFingerprint(state, step) !== getFormSectionFingerprint(baseline, step),
			)
			.map((step) => step.id);
		// baselineVersion bumps after save so edited steps clear without a full reload.
		// eslint-disable-next-line react-hooks/exhaustive-deps -- baselineVersion is intentional
	}, [mode, state, steps, baselineVersion]);

	const clearSession = React.useCallback(() => {
		clearHostFormSession(sessionKey);
		clearHostFormScrollPosition(sessionPathname);
		onSessionClear?.();
	}, [sessionKey, sessionPathname, onSessionClear]);

	const isLastStep = activeStep >= steps.length - 1;
	const connectorsStepIndex = findStepIndex(steps, "connectors");
	const variablesStepIndex = findStepIndex(steps, "variables");
	const applyAdditionalConnectorsPatch = React.useCallback(
		(nextAdditionalConnectors) => applyAdditionalConnectorsChange(state, nextAdditionalConnectors),
		[state],
	);

	const toggleSelectedVariableConnectorTemplate = React.useCallback((templateId) => {
		const id = String(templateId ?? "").trim();
		if (!id) {
			return false;
		}
		let added = false;
		setState((prev) => {
			const current = [...(prev.selectedVariableConnectorTemplates || [])];
			const index = current.indexOf(id);
			let nextTemplates;
			let nextAdditional = { ...(prev.additionalConnectors || {}) };
			if (index >= 0) {
				nextTemplates = current.filter((entry) => entry !== id);
				nextAdditional = Object.fromEntries(
					Object.entries(nextAdditional).filter(
						([, entry]) => String(entry?.uses ?? "").trim() !== id,
					),
				);
			} else {
				nextTemplates = [...current, id].sort(compareLocale);
				added = true;
			}
			return {
				...prev,
				selectedVariableConnectorTemplates: nextTemplates,
				configureVariableConnectors: nextTemplates.length > 0,
				additionalConnectors: nextAdditional,
				compatibleVariableConnectorIdsStale: true,
			};
		});
		setErrors({});
		return added;
	}, []);

	const clearSelectedVariableConnectors = React.useCallback(() => {
		setState((prev) => ({
			...prev,
			selectedVariableConnectorTemplates: [],
			configureVariableConnectors: false,
			additionalConnectors: {},
			compatibleVariableConnectorIdsStale: true,
		}));
		setErrors({});
	}, []);

	/** Discards edits: restores the last loaded/saved baseline (edit-mode Cancel). */
	const resetToBaseline = React.useCallback(() => {
		const baseline = baselineStateRef.current;
		if (!baseline) {
			return;
		}
		setState(baseline);
		setErrors({});
		setSubmitError(null);
	}, []);

	return {
		activeStep,
		steps,
		furthestStep: state.furthestStep ?? 0,
		validatedStepIds: [...validatedStepIds],
		invalidStepIds: [...invalidStepIds],
		editedStepIds,
		allowPasswordReveal: mode === "create",
		deferEncryptUntilSave: true,
		state,
		errors,
		submitError,
		setSubmitError,
		isDirty,
		allStepsValid,
		showSaveHostChanges,
		isLastStep,
		connectorsStepIndex,
		variablesStepIndex,
		orderedProtocols: getOrderedSelectedProtocols(state.selectedProtocols),
		connectorCatalog,
		annotatedConnectorCatalog,
		variableConnectorTemplates,
		connectorCatalogLoading,
		connectorCatalogError,
		compatibleVariableConnectorIds: state.compatibleVariableConnectorIds ?? [],
		applyAdditionalConnectorsPatch,
		toggleSelectedVariableConnectorTemplate,
		clearSelectedVariableConnectors,
		patchState,
		patchProtocolField,
		validateBasics,
		validateConnectorsStep,
		validateConnectorVariablesStep,
		validateStepIndex,
		validateAllSteps,
		goToStep,
		handleNext,
		handleBack,
		clearSession,
		commitSavedBaseline,
		resetToBaseline,
		highlightConnectorInstanceId,
		clearHighlightConnectorInstance,
		connectorVariablesValidationAttempt,
		setConnectorVariablesEditState,
		initialScrollTop: restoredScrollTop,
		initialActiveStep: restoredActiveStep,
		updateScrollPosition,
		getStepAt: (index) => getStepAt(steps, index),
	};
};
