// src/components/config/ConfigList.jsx
import { List, Typography } from "@mui/material";
import ConfigListItem from "./ConfigListItem";

export default function ConfigList({
  files,
  selectedName,
  loading,
  onSelect,
  onDeleteRequest,
  onRename,
}) {
  if (!loading && files.length === 0) {
    return <Typography variant="body2" sx={{ opacity: 0.7, p: 1 }}>No configuration files found.</Typography>;
  }
  return (
    <List dense disablePadding>
      {files.map((f) => (
        <ConfigListItem
          key={f.name}
          file={f}
          selected={selectedName === f.name}
          onSelect={onSelect}
          onDeleteRequest={onDeleteRequest}
          onRename={onRename}
        />
      ))}
    </List>
  );
}
