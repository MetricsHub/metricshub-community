import React, { useMemo, useState, useEffect, useCallback, useRef } from "react";
import { Box, Stack, Typography, IconButton, Tooltip, Chip } from "@mui/material";
import SaveIcon from "@mui/icons-material/Save";
import CheckIcon from "@mui/icons-material/Check";
import AutoFixHighIcon from "@mui/icons-material/AutoFixHigh";
import ErrorOutlineIcon from "@mui/icons-material/ErrorOutline";
import { useTheme } from "@mui/material/styles";

import CodeMirror from "@uiw/react-codemirror";
import { yaml as cmYaml } from "@codemirror/lang-yaml";
import { linter, lintGutter } from "@codemirror/lint";
import { history, historyKeymap, defaultKeymap } from "@codemirror/commands";
import { keymap } from "@codemirror/view";
import YAML from "yaml";

const LOCAL_STORAGE_KEY = "yaml-editor-doc";

const DEFAULT_YAML = `# Example
service:
  name: metricshub
  port: 8080
  enabled: true
`;

function createYamlLinter() {
    return linter((view) => {
        const text = view.state.doc.toString();
        if (!text.trim()) return [];
        try {
            YAML.parse(text);
            return [];
        } catch (e) {
            const from = e.pos ?? 0;
            return [
                {
                    from,
                    to: from,
                    message: e.message || "YAML parse error",
                    severity: "error",
                },
            ];
        }
    });
}

export default function YamlEditor({
    value,
    onChange,
    onSave,
    height = "100%",
    readOnly = false,
}) {
    const theme = useTheme();

    // Load initial value from localStorage OR prop OR default
    const [doc, setDoc] = useState(() => {
        const stored = localStorage.getItem(LOCAL_STORAGE_KEY);
        return stored ?? value ?? DEFAULT_YAML;
    });
    const [hasError, setHasError] = useState(false);

    // Visual cue state for explicit "Validate" clicks
    const [validateResult, setValidateResult] = useState(null); // "ok" | "error" | null
    const [showValidateCue, setShowValidateCue] = useState(false);
    const validateTimerRef = useRef(null);

    // Sync external value if provided
    useEffect(() => {
        if (typeof value === "string") {
            setDoc(value);
            localStorage.setItem(LOCAL_STORAGE_KEY, value);
        }
    }, [value]);

    // Save to localStorage on every change
    useEffect(() => {
        localStorage.setItem(LOCAL_STORAGE_KEY, doc);
    }, [doc]);

    // Clear timeout on unmount
    useEffect(() => {
        return () => {
            if (validateTimerRef.current) clearTimeout(validateTimerRef.current);
        };
    }, []);

    const extensions = useMemo(() => {
        const km = [...defaultKeymap, ...historyKeymap];
        // Ctrl+S to save when onSave implemented
        if (onSave) {
            km.push({
                key: "Mod-s",
                preventDefault: true,
                run: () => { onSave(doc); return true; },
            });
        }
        return [
            cmYaml(),
            lintGutter(),
            createYamlLinter(),
            history(),                 // enables undo/redo stack
            keymap.of(km),             // binds Ctrl/Cmd+Z and Ctrl+Y / Cmd+Shift+Z
        ];
    }, [onSave, doc]);

    const handleChange = useCallback(
        (val) => {
            setDoc(val);
            // live validation for icon
            try {
                if (val.trim()) YAML.parse(val);
                setHasError(false);
            } catch {
                setHasError(true);
            }
            onChange && onChange(val);
        },
        [onChange]
    );

    const handleFormat = useCallback(() => {
        try {
            const obj = doc.trim() ? YAML.parse(doc) : {};
            const formatted = YAML.stringify(obj, { indent: 2, lineWidth: 100 });
            setDoc(formatted);
            onChange && onChange(formatted);
            setHasError(false);
        } catch {
            setHasError(true);
            setValidateResult("error");
            setShowValidateCue(true);
            if (validateTimerRef.current) clearTimeout(validateTimerRef.current);
            validateTimerRef.current = setTimeout(() => setShowValidateCue(false), 1500);
        }
    }, [doc, onChange]);

    const handleValidate = useCallback(() => {
        try {
            if (doc.trim()) YAML.parse(doc);
            setHasError(false);
            setValidateResult("ok");
        } catch {
            setHasError(true);
            setValidateResult("error");
        }
        setShowValidateCue(true);
        if (validateTimerRef.current) clearTimeout(validateTimerRef.current);
        validateTimerRef.current = setTimeout(() => setShowValidateCue(false), 1500);
    }, [doc]);

    const handleSave = useCallback(() => {
        onSave && onSave(doc);
    }, [onSave, doc]);

    return (
        <Box sx={{ height, display: "flex", flexDirection: "column", minHeight: 0 }}>
            {/* Top toolbar */}
            <Stack direction="row" alignItems="center" spacing={1} sx={{ px: 1, py: 0.5 }}>
                <Typography variant="subtitle2" sx={{ flex: 1 }}>
                    YAML Editor
                </Typography>

                {/* Feedback chip */}
                {showValidateCue && (
                    <Chip
                        size="small"
                        label={validateResult === "ok" ? "Valid YAML" : "Invalid YAML"}
                        color={validateResult === "ok" ? "success" : "error"}
                        variant="filled"
                        sx={{ mr: 0.5 }}
                    />
                )}

                {/* Validate */}
                <Tooltip title="Validate (parse YAML)">
                    <span>
                        <IconButton
                            size="small"
                            onClick={handleValidate}
                            aria-label="Validate YAML"
                            sx={{
                                ...(showValidateCue && validateResult === "ok" && { color: "success.main" }),
                                ...(showValidateCue && validateResult === "error" && { color: "error.main" }),
                            }}
                        >
                            {hasError ? <ErrorOutlineIcon /> : <CheckIcon />}
                        </IconButton>
                    </span>
                </Tooltip>

                {/* Format */}
                <Tooltip title="Format YAML">
                    <IconButton size="small" onClick={handleFormat} aria-label="Format YAML">
                        <AutoFixHighIcon />
                    </IconButton>
                </Tooltip>

                {/* Save */}
                {onSave && (
                    <Tooltip title="Save">
                        <IconButton size="small" onClick={handleSave} aria-label="Save YAML">
                            <SaveIcon />
                        </IconButton>
                    </Tooltip>
                )}
            </Stack>

            {/* Editor container */}
            <Box
                sx={{
                    flex: 1,
                    minHeight: 0,
                    borderTop: 1,
                    borderColor: theme.palette.divider,
                    ".cm-editor": { height: "100%" },
                }}
            >
                <CodeMirror
                    value={doc}
                    onChange={handleChange}
                    extensions={extensions}
                    editable={!readOnly}
                    basicSetup={{ lineNumbers: true, highlightActiveLine: true, foldGutter: true }}
                    theme={theme.palette.mode === "dark" ? "dark" : "light"}
                />
            </Box>
        </Box>
    );
}
