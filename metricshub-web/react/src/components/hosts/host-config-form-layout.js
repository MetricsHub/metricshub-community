/**
 * Shared max width for all guided-config create/edit form content
 * (new resource, new/edit resource group, edit resource). Keeps every
 * form the same comfortable reading width, centered in its pane.
 */
export const HOST_CONFIG_FORM_CONTENT_MAX_WIDTH = 1280;

/** Width reserved for the step navigation column. */
export const HOST_CONFIG_STEP_RAIL_WIDTH = 280;

/** Top band height on the fixed steps column (aligns with breadcrumb row). */
export const HOST_CONFIG_BREADCRUMB_ROW_MIN_HEIGHT = 48;

/** Right inset for create-wizard main column (clears the scrollbar). */
export const HOST_CONFIG_CREATE_FORM_CONTENT_PR = 2;

/** Column gap between create form and stepper (tighter than default page gap). */
export const HOST_CONFIG_CREATE_FORM_STEPPER_GAP = 1;

/**
 * @param {{ id: string }} step
 * @returns {string}
 */
export const hostConfigSectionId = (step) => `host-config-section-${step.id}`;
