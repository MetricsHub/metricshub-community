import React, { useMemo, useState, useEffect, useCallback, useRef } from "react";
import { Box } from "@mui/material";
import { useTheme } from "@mui/material/styles";

import CodeMirror from "@uiw/react-codemirror";
import { yaml as cmYaml } from "@codemirror/lang-yaml";
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

/**
 * YAML Editor component
 * Props:
 * - value?: string
 * - onChange?: (val: string) => void
 * - onSave?: (val: string) => void  // still supported via Ctrl/Cmd+S
 * - height?: CSS height (default "100%")
 * - readOnly?: boolean (default false)
 */
export default function YamlEditor({ value, onChange, onSave, height = "100%", readOnly = false }) {
  const theme = useTheme();

  const [doc, setDoc] = useState(() => {
    const stored = localStorage.getItem(LOCAL_STORAGE_KEY);
    return stored ?? value ?? DEFAULT_YAML;
  });

  const debounceRef = useRef(null);

  // Sync external value if provided
  useEffect(() => {
    if (value != null) {
      setDoc(value);
      localStorage.setItem(LOCAL_STORAGE_KEY, value);
    }
  }, [value]);

  // Save to localStorage on every change
  useEffect(() => {
    localStorage.setItem(LOCAL_STORAGE_KEY, doc);
  }, [doc]);

  // Cleanup timers on unmount
  useEffect(() => {
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, []);

  // CodeMirror extensions
  const extensions = useMemo(() => {
    const km = [...defaultKeymap, ...historyKeymap];
    if (onSave) {
      km.unshift({
        key: "Mod-s",
        preventDefault: true,
        run: (view) => {
          onSave(view.state.doc.toString());
          return true;
        },
      });
    }
    return [cmYaml(), history(), keymap.of(km)];
  }, [onSave]);

  // Handle changes
  const handleChange = useCallback(
    (val) => {
      setDoc(val);
      onChange?.(val);
    },
    [onChange],
  );

  const runValidation = useCallback(() => {
    try {
      if (doc.trim()) YAML.parse(doc);
      // no visual feedback by design
    } catch {
      // no visual feedback by design
    }
  }, [doc]);

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(runValidation, 1000);
  }, [doc, runValidation]);

  return (
    <Box sx={{ height, display: "flex", flexDirection: "column", minHeight: 0 }}>
      {/* Editor container only (no toolbar/header/buttons) */}
      <Box
        sx={{
          flex: 1,
          minHeight: 0,
          borderTop: 0,
          ".cm-editor": { height: "100%" },
          ".cm-scroller": { overflow: "auto" },
        }}
      >
        <CodeMirror
          value={doc}
          onChange={handleChange}
          extensions={extensions}
          editable={!readOnly}
          basicSetup={{ lineNumbers: true, highlightActiveLine: true, foldGutter: true }}
          theme={theme.palette.mode}
        />
      </Box>
    </Box>
  );
}
