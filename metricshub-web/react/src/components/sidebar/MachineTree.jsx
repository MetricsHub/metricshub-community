import * as React from "react";
import { useState, useMemo, useCallback } from "react";
import {
    Box,
    List,
    ListItemButton,
    ListItemIcon,
    ListItemText,
    Collapse,
    Typography,
    Divider,
} from "@mui/material";
import ExpandLessIcon from "@mui/icons-material/ExpandLess";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import ComputerIcon from "@mui/icons-material/Computer";
import FolderIcon from "@mui/icons-material/Folder";
import FolderOpenIcon from "@mui/icons-material/FolderOpen";
import { useAppDispatch, useAppSelector } from "../../hooks/store";
import { selectMachine } from "../../store/slices/machinesSlice";

function Node({ node, depth, expanded, toggle, selectedId, onSelect }) {
    const hasChildren = Array.isArray(node.children) && node.children.length > 0;
    const isExpanded = expanded.has(node.id);
    const isSelected = selectedId === node.id;

    return (
        <>
            <ListItemButton
                dense
                selected={isSelected}
                sx={{ pl: 1.5 + depth * 2 }} // slightly tighter left padding
                onClick={() => {
                    if (hasChildren) toggle(node.id);
                    else onSelect(node.id);
                }}
            >
                <ListItemIcon sx={{ minWidth: 28 }}>
                    {hasChildren ? <FolderIcon fontSize="small" /> : <ComputerIcon fontSize="small" />}
                </ListItemIcon>
                <ListItemText
                    primary={
                        <Typography variant="body2" sx={{ fontWeight: hasChildren ? 600 : 400 }}>
                            {node.name}
                        </Typography>
                    }
                />
                {hasChildren ? (isExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />) : null}
            </ListItemButton>

            {hasChildren && (
                <Collapse in={isExpanded} timeout="auto" unmountOnExit>
                    <List disablePadding dense>
                        {node.children.map((child) => (
                            <Node
                                key={child.id}
                                node={child}
                                depth={depth + 1}
                                expanded={expanded}
                                toggle={toggle}
                                selectedId={selectedId}
                                onSelect={onSelect}
                            />
                        ))}
                    </List>
                </Collapse>
            )}
        </>
    );
}

export default function MachineTree() {
    const dispatch = useAppDispatch();
    const nodes = useAppSelector((s) => s.machines.nodes);
    const selectedId = useAppSelector((s) => s.machines.selectedId);

    const defaultExpanded = useMemo(() => new Set(nodes.map((n) => n.id)), [nodes]);
    const [expanded, setExpanded] = useState(defaultExpanded);

    const toggle = useCallback((id) => {
        setExpanded((prev) => {
            const next = new Set(prev);
            next.has(id) ? next.delete(id) : next.add(id);
            return next;
        });
    }, []);

    const onSelect = useCallback((id) => dispatch(selectMachine(id)), [dispatch]);

    return (
        <Box sx={{
            display: "flex", flexDirection: "column", flex: 1, minHeight: 0, pt: 1.25

        }}>
            {/* Compact sticky header */}
            <Box
                sx={{
                    position: "sticky",
                    top: 0,
                    zIndex: 1,
                    bgcolor: "background.paper",
                    borderBottom: (t) => `1px solid ${t.palette.divider}`,
                    height: 36,              // keeps it visually “higher”
                    px: 1.5,
                    display: "flex",
                    alignItems: "center",
                    gap: 1,
                }}
            >
                <FolderOpenIcon fontSize="small" sx={{ color: "text.secondary" }} />
                <Typography
                    variant="overline"
                    sx={{ letterSpacing: 0.08, fontWeight: 700, color: "text.secondary", lineHeight: 1 }}
                >
                    Machines
                </Typography>
            </Box>

            {/* Tree content */}
            <Box sx={{ flex: 1, overflowY: "auto" }}>
                <List dense disablePadding>
                    {nodes.map((n) => (
                        <Node
                            key={n.id}
                            node={n}
                            depth={0}
                            expanded={expanded}
                            toggle={toggle}
                            selectedId={selectedId}
                            onSelect={onSelect}
                        />
                    ))}
                </List>
            </Box>

            <Divider />
        </Box>
    );
}
