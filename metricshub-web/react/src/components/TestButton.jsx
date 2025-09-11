import React from "react";
import { Button } from "@mui/material";

export default function TestButton() {
    // Toggle to verify new builds
    const testColor = "red"; // change to "grey" or text to confirm deploy

    return (
        <Button
            variant="contained"
            sx={{
                backgroundColor: testColor,
                minWidth: 80,
                fontWeight: "bold",
                color: "#fff",
                "&:hover": { backgroundColor: testColor, opacity: 0.85 },
            }}
        >
            Test
        </Button>
    );
}
