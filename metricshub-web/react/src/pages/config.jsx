import React, { useEffect, useMemo } from "react";
import { Box, Stack, Button, Typography, Alert, IconButton, Tooltip } from "@mui/material";
import SaveIcon from "@mui/icons-material/Save";
import FormatAlignLeftIcon from "@mui/icons-material/FormatAlignLeft";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import DownloadIcon from "@mui/icons-material/Download";
import { useTheme } from "@mui/material/styles";
import CodeMirror from "@uiw/react-codemirror";
import { yaml as yamlLang } from "@codemirror/lang-yaml";
import YAML from "yaml";

import { useAppDispatch, useAppSelector } from "../hooks/store";
import {
    setContent,
    setFilename,
    setValidation,
    markSaved,
} from "../store/slices/configEditorSlice";

const LS_KEY_CONTENT = "config_yaml_content";
const LS_KEY_FILENAME = "config_yaml_filename";

const DEFAULT_YAML = `config: here
`;

export default function Config() {
    const theme = useTheme();
    const dispatch = useAppDispatch();
    const { content, filename, isValid, error } = useAppSelector(
        (state) => state.configEditor
    );

    const extensions = useMemo(() => [yamlLang()], []);

    // --- Rehydrate from localStorage on first mount
    useEffect(() => {
        const savedContent = localStorage.getItem(LS_KEY_CONTENT);
        const savedFilename = localStorage.getItem(LS_KEY_FILENAME);
        const initialContent = savedContent ?? (content || DEFAULT_YAML);
        if (initialContent !== content) {
            dispatch(setContent(initialContent));
            try {
                YAML.parse(initialContent);
                dispatch(setValidation({ isValid: true, error: "" }));
            } catch (e) {
                dispatch(setValidation({ isValid: false, error: e.message }));
            }
        }

        if (savedFilename && savedFilename !== filename) {
            dispatch(setFilename(savedFilename));
        }
    }, []);

    // --- Persist to localStorage whenever content / filename change
    useEffect(() => {
        if (typeof content === "string") {
            localStorage.setItem(LS_KEY_CONTENT, content);
        }
    }, [content]);

    useEffect(() => {
        if (typeof filename === "string") {
            localStorage.setItem(LS_KEY_FILENAME, filename);
        }
    }, [filename]);

    const onChange = (v) => {
        dispatch(setContent(v));
        try {
            YAML.parse(v);
            dispatch(setValidation({ isValid: true, error: "" }));
        } catch (e) {
            dispatch(setValidation({ isValid: false, error: e.message }));
        }
    };

    const handleFormat = () => {
        try {
            const obj = YAML.parse(content || "");
            const pretty = YAML.stringify(obj, { indent: 2, lineWidth: 0 });
            dispatch(setContent(pretty));
            dispatch(setValidation({ isValid: true, error: "" }));
        } catch (e) {
            dispatch(setValidation({ isValid: false, error: e.message }));
        }
    };

    const handleDownload = () => {
        const blob = new Blob([content || ""], { type: "text/yaml;charset=utf-8" });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = filename || "config.yaml";
        a.click();
        URL.revokeObjectURL(url);
    };

    const handleUpload = async () => {
        const input = document.createElement("input");
        input.type = "file";
        input.accept = ".yml,.yaml,text/yaml,text/plain";
        input.onchange = async () => {
            const file = input.files?.[0];
            if (!file) return;
            const text = await file.text();
            dispatch(setFilename(file.name));
            dispatch(setContent(text));
            try {
                YAML.parse(text);
                dispatch(setValidation({ isValid: true, error: "" }));
            } catch (e) {
                dispatch(setValidation({ isValid: false, error: e.message }));
            }
        };
        input.click();
    };

    const handleSave = async () => {
        // TODO: POST to backend:
        dispatch(markSaved());
        // keep localStorage so content survives reloads
    };

    const iconColor = theme.palette.mode === "dark" ? "#fff" : "#000";

    return (
        <Stack spacing={2}>

            {isValid ? (
                <Alert severity="success" variant="outlined">
                    YAML looks valid{filename ? ` — ${filename}` : ""}.
                </Alert>
            ) : (
                <Alert severity="error" variant="outlined">
                    YAML error: {error}
                </Alert>
            )}
            {filename && (
                <Typography
                    variant="subtitle2"
                    sx={{
                        mt: 1,
                        mb: -1,
                        color: "text.secondary",
                        fontStyle: "italic",
                    }}
                >
                    {filename}
                </Typography>
            )}

            <Box
                sx={{
                    border: 1,
                    borderColor: "divider",
                    borderRadius: 1,
                    overflow: "auto",
                    width: "100%",
                    height: "75vh",
                }}
            >
                <CodeMirror
                    height="100%"
                    value={content ?? ""}
                    onChange={onChange}
                    extensions={extensions}
                    theme={theme.palette.mode === "dark" ? "dark" : "light"}
                    basicSetup={{
                        lineNumbers: true,
                        highlightActiveLine: true,
                        foldGutter: true,
                        bracketMatching: true,
                        autocompletion: true,
                    }}
                />
            </Box>

            <Stack
                direction="row"
                spacing={2}
                justifyContent={"flex-end"}
            >
                <Tooltip title="Save">
                    <IconButton onClick={handleSave} sx={{ color: iconColor }}>
                        <SaveIcon />
                    </IconButton>
                </Tooltip>

                <Tooltip title="Format">
                    <IconButton onClick={handleFormat} sx={{ color: iconColor }}>
                        <FormatAlignLeftIcon />
                    </IconButton>
                </Tooltip>

                <Tooltip title="Upload YAML">
                    <IconButton onClick={handleUpload} sx={{ color: iconColor }}>
                        <UploadFileIcon />
                    </IconButton>
                </Tooltip>

                <Tooltip title="Download YAML">
                    <IconButton onClick={handleDownload} sx={{ color: iconColor }}>
                        <DownloadIcon />
                    </IconButton>
                </Tooltip>
            </Stack>

        </Stack>
    );
}
