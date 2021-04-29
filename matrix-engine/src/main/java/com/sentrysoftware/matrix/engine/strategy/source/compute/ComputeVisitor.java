package com.sentrysoftware.matrix.engine.strategy.source.compute;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.sentrysoftware.matrix.common.helpers.HardwareConstants;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Add;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.And;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.ArrayTranslate;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Awk;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Convert;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Divide;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.DuplicateColumn;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.ExcludeMatchingLines;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Extract;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.ExtractPropertyFromWbemPath;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Json2CSV;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.KeepColumns;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.KeepOnlyMatchingLines;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.LeftConcat;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Multiply;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.PerBitTranslation;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Replace;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.RightConcat;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Substract;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Substring;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.Translate;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.XML2CSV;
import com.sentrysoftware.matrix.engine.strategy.source.SourceTable;

import lombok.Getter;
import lombok.Setter;

@Component
public class ComputeVisitor implements IComputeVisitor {

	@Setter
	@Getter
	private SourceTable sourceTable;

	@Override
	public void visit(final Add add) {
		// Not implemented yet
	}

	@Override
	public void visit(final ArrayTranslate arrayTranslate) {
		// Not implemented yet
	}

	@Override
	public void visit(final And and) {
		// Not implemented yet
	}

	@Override
	public void visit(final Awk awk) {
		// Not implemented yet
	}

	@Override
	public void visit(final Convert convert) {
		// Not implemented yet
	}

	@Override
	public void visit(final Divide divide) {
		// Not implemented yet
	}

	@Override
	public void visit(final DuplicateColumn duplicateColumn) {
		// Not implemented yet
	}

	@Override
	public void visit(final ExcludeMatchingLines excludeMatchingLines) {
		// Not implemented yet
	}

	@Override
	public void visit(final Extract extract) {
		// Not implemented yet
	}

	@Override
	public void visit(final ExtractPropertyFromWbemPath extractPropertyFromWbemPath) {
		// Not implemented yet
	}

	@Override
	public void visit(final Json2CSV json2csv) {
		// Not implemented yet
	}

	@Override
	public void visit(final KeepColumns keepColumns) {
		// Not implemented yet
	}

	@Override
	public void visit(final KeepOnlyMatchingLines keepOnlyMatchingLines) {
		// Not implemented yet
	}

	@Override
	public void visit(final LeftConcat leftConcat) {
		if (leftConcat != null && leftConcat.getString() != null && leftConcat.getColumn() != null && leftConcat.getColumn() > 0
				&& sourceTable != null && sourceTable.getTable() != null && !sourceTable.getTable().isEmpty()
				&& leftConcat.getColumn() <= sourceTable.getTable().get(0).size()) {
			int columnIndex = leftConcat.getColumn() - 1;
			String concatString = leftConcat.getString();

			// If leftConcat.getString() is like "Concat(n)", we concat the column n instead of leftConcat.getString() 
			if (Pattern.compile(HardwareConstants.COLUMN_REGEXP, Pattern.CASE_INSENSITIVE).matcher(concatString).matches()) {
				int leftColumnIndex = Integer.parseInt(concatString.substring(concatString.indexOf(HardwareConstants.OPENING_PARENTHESIS) + 1, 
						concatString.indexOf(HardwareConstants.CLOSING_PARENTHESIS))) - 1;

				if (leftColumnIndex < sourceTable.getTable().get(0).size()) {
					sourceTable.getTable()
							.stream()
							.forEach(column -> column.set(columnIndex, column.get(leftColumnIndex).concat(column.get(columnIndex))));
				}
			} else {
				sourceTable.getTable()
						.stream()
						.forEach(column -> column.set(columnIndex, leftConcat.getString().concat(column.get(columnIndex))));

				// Serialize and deserialize in case the String to concat contains a ';' so that a new column is created.
				if (concatString.contains(HardwareConstants.SEMICOLON)) {
					sourceTable.setTable(SourceTable.csvToTable(SourceTable.tableToCsv(sourceTable.getTable(), HardwareConstants.SEMICOLON), HardwareConstants.SEMICOLON));
				}
			}
		}
	}

	@Override
	public void visit(final Multiply multiply) {
		// Not implemented yet
	}

	@Override
	public void visit(final PerBitTranslation perBitTranslation) {
		// Not implemented yet
	}

	@Override
	public void visit(final Replace replace) {
		// Not implemented yet
	}

	@Override
	public void visit(final RightConcat rightConcat) {
		// Not implemented yet
	}

	@Override
	public void visit(final Substract substract) {
		// Not implemented yet
	}

	@Override
	public void visit(final Substring substring) {
		// Not implemented yet
	}

	@Override
	public void visit(final Translate translate) {
		// Not implemented yet
	}

	@Override
	public void visit(final XML2CSV xml2csv) {
		// Not implemented yet
	}

}
