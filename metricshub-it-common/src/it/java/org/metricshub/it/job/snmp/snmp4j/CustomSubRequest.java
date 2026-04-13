package org.metricshub.it.job.snmp.snmp4j;

import org.snmp4j.agent.MOQuery;
import org.snmp4j.agent.MOScope;
import org.snmp4j.agent.ManagedObject;
import org.snmp4j.agent.request.Request;
import org.snmp4j.agent.request.RequestStatus;
import org.snmp4j.agent.request.SubRequest;
import org.snmp4j.agent.request.SubRequestIterator;
import org.snmp4j.smi.VariableBinding;

/**
 * Custom sub request used to register the SNMP Agent configuration
 *
 * @param <S>
 */
public class CustomSubRequest<S extends CustomSubRequest<S>> implements SubRequest<S> {

	private boolean completed;
	private MOQuery query;

	private RequestStatus status;
	private MOScope scope;
	private VariableBinding vb;

	public CustomSubRequest(RequestStatus status, MOScope scope, VariableBinding vb) {
		this.status = status;
		this.scope = scope;
		this.vb = vb;
	}

	public boolean hasError() {
		return false;
	}

	public void setErrorStatus(int errorStatus) {
		status.setErrorStatus(errorStatus);
	}

	public int getErrorStatus() {
		return status.getErrorStatus();
	}

	public RequestStatus getStatus() {
		return status;
	}

	public MOScope getScope() {
		return scope;
	}

	public VariableBinding getVariableBinding() {
		return vb;
	}

	public Request<?, ?, ?> getRequest() {
		return null;
	}

	public Object getUndoValue() {
		return null;
	}

	public void setUndoValue(Object undoInformation) {
		// Not implemented
	}

	public void completed() {
		completed = true;
	}

	public boolean isComplete() {
		return completed;
	}

	public void setTargetMO(ManagedObject<? super S> managedObject) {
		// Not implemented
	}

	public ManagedObject<S> getTargetMO() {
		return null;
	}

	public int getIndex() {
		return 0;
	}

	public void setQuery(MOQuery query) {
		this.query = query;
	}

	public MOQuery getQuery() {
		return query;
	}

	public SubRequestIterator<S> repetitions() {
		return null;
	}

	public void updateNextRepetition() {
		// Not implemented
	}

	public Object getUserObject() {
		return null;
	}

	public void setUserObject(Object userObject) {
		// Not implemented
	}
}
