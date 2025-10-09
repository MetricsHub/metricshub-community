// src/components/common/QuestionDialog.jsx
import * as React from "react";
import {
	Dialog,
	DialogTitle,
	DialogContent,
	DialogContentText,
	DialogActions,
	Button,
} from "@mui/material";

/**
 * @param {Object} props
 * @param {boolean} props.open
 * @param {string|React.ReactNode} [props.title="Question"]
 * @param {string|React.ReactNode} [props.question="Do you confirm?"]
 * @param {Array<{
 *   btnTitle: string,
 *   btnColor?: "inherit"|"primary"|"secondary"|"success"|"error"|"info"|"warning",
 *   btnVariant?: "text"|"outlined"|"contained",
 *   btnIcon?: React.ReactNode,
 *   autoFocus?: boolean,
 *   callback: () => void
 * }>} props.actionButtons
 * @param {() => void} [props.onClose]
 */
export default function QuestionDialog({
	open,
	title = "Question",
	question = "Do you confirm?",
	actionButtons = [],
	onClose,
}) {
	return (
		<Dialog open={open} onClose={onClose} aria-labelledby="question-dialog-title">
			<DialogTitle id="question-dialog-title">{title}</DialogTitle>
			<DialogContent>
				<DialogContentText>{question}</DialogContentText>
			</DialogContent>
			<DialogActions>
				{actionButtons.map(
					({ btnTitle, btnColor, btnVariant, btnIcon, callback, autoFocus }, i) => (
						<Button
							key={i}
							color={btnColor}
							variant={btnVariant}
							onClick={callback}
							startIcon={btnIcon}
							autoFocus={autoFocus}
						>
							{btnTitle}
						</Button>
					),
				)}
			</DialogActions>
		</Dialog>
	);
}
