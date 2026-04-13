import * as React from "react";
import { useDispatch, useSelector } from "react-redux";
import QuestionDialog from "./QuestionDialog";
import { closeWriteProtectionModal } from "../../store/slices/ui-slice";

/**
 * Global dialog shown when a read-only user attempts a write action.
 * @returns {React.ReactElement} The write-protection dialog
 */
export default function WriteProtectionDialog() {
	const dispatch = useDispatch();
	const open = useSelector((state) => state.ui.writeProtectionModalOpen);

	// Handle the close action
	const handleClose = React.useCallback(() => {
		dispatch(closeWriteProtectionModal());
	}, [dispatch]);

	return (
		<QuestionDialog
			open={open}
			title="Permission denied"
			question="You don't have write permissions to perform write operations."
			actionButtons={[
				{ btnTitle: "OK", btnColor: "primary", btnVariant: "contained", callback: handleClose },
			]}
			onClose={handleClose}
		/>
	);
}
