/**
 * User-facing labels for the resources UI (OpenTelemetry: prefer "resource" over "host").
 */

export const HOSTS_PAGE_TITLE = "Resources";

export const NO_RESOURCE_GROUP = "No Resource Group";

/** @param {number} count */
export const formatResourceCount = (count) => (count === 1 ? "1 resource" : `${count} resources`);

/** @param {number} filtered @param {number} total */
export const formatResourceCountFiltered = (filtered, total) =>
	`${filtered} of ${total} ${total === 1 ? "resource" : "resources"}`;

/**
 * Chip label for resource lists: matches visible table rows vs filtered/total counts.
 *
 * @param {object} params
 * @param {number} params.displayed rows currently shown in the table (page slice when paginated)
 * @param {number} params.filtered rows matching active filters
 * @param {number} params.total resources in the group or standalone pool
 */
export const formatResourceCountChip = ({ displayed, filtered, total }) => {
	if (total === 0) {
		return formatResourceCount(0);
	}
	if (displayed === filtered && filtered === total) {
		return formatResourceCount(total);
	}
	const denominator = filtered < total ? total : filtered;
	return formatResourceCountFiltered(displayed, denominator);
};
