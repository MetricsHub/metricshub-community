package org.snmp4j.agent;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import javax.management.Query;
import org.metricshub.it.job.snmp.snmp4j.SnmpAgent;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.PDU;
import org.snmp4j.SNMP4JSettings;
import org.snmp4j.agent.mo.DefaultMOTable;
import org.snmp4j.agent.mo.MOScalar;
import org.snmp4j.agent.mo.snmp.CoexistenceInfo;
import org.snmp4j.agent.request.Request;
import org.snmp4j.agent.request.RequestStatus;
import org.snmp4j.agent.request.SnmpRequest;
import org.snmp4j.agent.request.SubRequest;
import org.snmp4j.mp.MPv1;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Null;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.SMIConstants;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariableBinding;

/**
 * Extends the default {@link CommandProcessor} since we know our Scalar objects
 */
public class SnmpRequestProcessor extends CommandProcessor {

	public SnmpRequestProcessor(OctetString contextEngineID) {
		super(contextEngineID);
	}

	final GetHandler getHandler = new GetHandler();

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void processRequest(CommandResponderEvent command, CoexistenceInfo cinfo, RequestHandler handler) {
		synchronized (command) {
			if (command.getPDU().getType() == PDU.GET) {
				// Only GET requests are processed with our GetHandler
				super.processRequest(command, cinfo, getHandler);
			} else {
				// GET Next or any other SNMP request? go to the default super implementation
				super.processRequest(command, cinfo, handler);
			}
		}
	}

	/**
	 * Handler to process get requests <br>
	 * Why we don't use the default getHandler? <br>
	 * Since we are in a simulation mode we are creating the managed objects as {@link MOScalar} instances. To get the table managed object
	 * (columns) served via GET requests we need to register very complex instances called {@link DefaultMOTable}.<br>
	 * The current SNMP walk doesn't provide the MIB structure of the real SNMP agent that's why default {@link GetHandler} is overridden.
	 */
	public class GetHandler extends CommandProcessor.GetHandler {

		@SuppressWarnings({ "unchecked" })
		@Override
		public void processPdu(final SnmpRequest request, final MOServer server) {
			// The command response is already set by SNMP4J, so here we should receive a source which already contains a result from the
			// SNMP4J registry but it can contain NULL if we are in a nested table column
			final CommandResponderEvent<?> commandResponder = request.getSource();

			// Get the variable bindings
			final List<VariableBinding> variableBindings = (List<VariableBinding>) commandResponder
				.getPDU()
				.getVariableBindings();

			if (variableBindings != null && !variableBindings.isEmpty()) {
				final OID oid = ((VariableBinding) variableBindings.get(0)).getOid();
				final String oidStr = oid.toString();
				if (SnmpAgent.MANAGED_OBJECTS.containsKey(oidStr)) {
					// Clear everything
					variableBindings.clear();

					// Get our known managed object
					final MOScalar<? extends Variable> moScalar = SnmpAgent.MANAGED_OBJECTS.get(oidStr);

					// Force our known managed object
					VariableBinding variableBinding = new VariableBinding(oid, moScalar.getValue());
					variableBindings.add(variableBinding);

					// Then proceed
					proceed(request, moScalar);
					return;
				}
			}

			super.processPdu(request, server);
		}

		/**
		 * Go to the next phase of the request if it is on INIT
		 *
		 * @param request  The {@link SnmpRequest} we wish to handle
		 */
		private void initRequestPhase(SnmpRequest request) {
			if (request.getPhase() == Request.PHASE_INIT) {
				request.nextPhase();
			}
		}

		/**
		 * The aim of this method is to update the {@link Query} in each sub request, set the variable value using our known {@link MOScalar} object
		 * then handle requested data for SNMP V1 especially for the Counter64 SNMP V2
		 *
		 * @param request The {@link SnmpRequest} we wish to process
		 * @param mo      The managed object
		 */
		public void proceed(final SnmpRequest request, final MOScalar<? extends Variable> mo) {
			initRequestPhase(request);

			final OctetString context = request.getContext();
			try {
				Iterator<SnmpRequest.SnmpSubRequest> it = request.iterator();
				while (it.hasNext()) {
					final SnmpRequest.SnmpSubRequest sreq = it.next();
					MOScope scope = sreq.getScope();
					MOQuery query = sreq.getQuery();

					// Create a new query in read only access
					if (query == null) {
						query =
							new VACMQuery(
								context,
								scope.getLowerBound(),
								scope.isLowerIncluded(),
								scope.getUpperBound(),
								scope.isUpperIncluded(),
								request.getViewName(),
								false,
								request
							);
						sreq.setQuery(query);
					}
					final MOServerLookupEvent lookupEvent = new MOServerLookupEvent(
						this,
						null,
						query,
						MOServerLookupEvent.IntendedUse.get,
						true
					);
					try {
						// Update the variable binding
						update(sreq, mo);

						// Handle the Counter64
						if (
							(request.getMessageProcessingModel() == MPv1.ID) &&
							(sreq.getVariableBinding().getSyntax() == SMIConstants.SYNTAX_COUNTER64)
						) {
							sreq.getVariableBinding().setVariable(Null.noSuchInstance);
						}

						// Complete
						lookupEvent.completedUse(sreq);
					} catch (Exception moex) {
						moex.printStackTrace();
						if (sreq.getStatus().getErrorStatus() == PDU.noError) {
							sreq.getStatus().setErrorStatus(PDU.genErr);
						}
						if (SNMP4JSettings.isForwardRuntimeExceptions()) {
							throw new RuntimeException(moex);
						}
					}
				}
			} catch (NoSuchElementException nsex) {
				nsex.printStackTrace();
				request.setErrorStatus(PDU.genErr);
			}
		}
	}

	/**
	 * Update the variable value for the given {@link SubRequest}. <br>If the {@link MOScalar} is not accessible then an error in set in the status
	 * of the subRequest
	 *
	 * @param subRequest The SNMP sub request located in the {@link SnmpRequest}
	 * @param mo         The managed object we wish to use in order to extract it {@link Variable}
	 */
	public void update(SubRequest<?> subRequest, MOScalar<? extends Variable> mo) {
		final RequestStatus status = subRequest.getStatus();
		if (mo.getAccess().isAccessibleForRead()) {
			VariableBinding vb = subRequest.getVariableBinding();
			vb.setOid(mo.getOid());
			Variable variable = mo.getValue();
			if (variable == null) {
				vb.setVariable(Null.noSuchObject);
			} else {
				vb.setVariable((Variable) variable.clone());
			}
			subRequest.completed();
		} else {
			status.setErrorStatus(SnmpConstants.SNMP_ERROR_NO_ACCESS);
		}
	}
}
