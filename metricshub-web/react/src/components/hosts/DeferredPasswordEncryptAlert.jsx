import AppAlert from "../common/AppAlert";
import {
	CREATE_WIZARD_PASSWORD_HELPER_TEXT,
	EDIT_WIZARD_PASSWORD_HELPER_TEXT,
} from "../../utils/password-encrypt";

/**
 * Green notice shown below a protocol password field when encryption is deferred until save.
 *
 * @param {object} props
 * @param {boolean} [props.allowPasswordReveal] create flow vs edit flow copy
 */
const DeferredPasswordEncryptAlert = ({ allowPasswordReveal = false }) => (
	<AppAlert
		severity="success"
		sx={{
			mb: 0,
			mt: 1,
			"& .MuiAlert-message": {
				fontSize: "0.8125rem",
			},
		}}
	>
		{allowPasswordReveal ? CREATE_WIZARD_PASSWORD_HELPER_TEXT : EDIT_WIZARD_PASSWORD_HELPER_TEXT}
	</AppAlert>
);

export default DeferredPasswordEncryptAlert;
