/*******************************************************************************
 * Copyright (c) 2011 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.openshift.express.internal.ui.command;

import java.text.MessageFormat;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.wst.server.core.IServer;
import org.jboss.tools.openshift.express.internal.ui.OpenShiftUIActivator;
import org.jboss.tools.openshift.express.internal.ui.console.ConsoleUtils;
import org.jboss.tools.openshift.express.internal.ui.job.RetrieveApplicationJob;
import org.jboss.tools.openshift.express.internal.ui.job.VerifySSHSessionJob;
import org.jboss.tools.openshift.express.internal.ui.utils.OpenShiftSshSessionFactory;
import org.jboss.tools.openshift.express.internal.ui.utils.UIUtils;

import com.openshift.client.IApplication;

/**
 * @author Xavier Coulon
 */
public class ShowEnvironmentHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		ISelection selection = HandlerUtil.getCurrentSelection(event);
		IApplication application = UIUtils.getFirstElement(selection, IApplication.class);
		if (application != null) {
			showEnvironmentPropertiesFor(application);
		} else {
			IServer server = UIUtils.getFirstElement(selection, IServer.class);
			if (server == null) {
				return Status.CANCEL_STATUS;
			}
			showEnvironmentPropertiesFor(server);
		}
		return Status.OK_STATUS;
	}

	/**
	 * Retrieves the application from the given server, then opens the dialog.
	 * Since retrieving the application can be time consuming, the task is
	 * performed in a separate job (ie, in a background thread).
	 * 
	 * @param server
	 */
	private void showEnvironmentPropertiesFor(final IServer server) {
		final RetrieveApplicationJob job = new RetrieveApplicationJob(server);
		job.addJobChangeListener(new JobChangeAdapter() {
			@Override
			public void done(IJobChangeEvent event) {
				if (event.getResult().isOK()) {
					final IApplication application = job.getApplication();
					showEnvironmentPropertiesFor(application);
				}
			}
		});
		job.setUser(true);
		job.schedule();
	}

	private void showEnvironmentPropertiesFor(final IApplication application) {
		final VerifySSHSessionJob job = new VerifySSHSessionJob(application);
		job.addJobChangeListener(new JobChangeAdapter() {
			@Override
			public void done(IJobChangeEvent event) {
				if (event.getResult().isOK() && job.isValidSession()) {
					showEnvironmentProperties(application);
				}
			}
		});

		job.setUser(true);
		job.schedule();
	}

	/**
	 * @param application
	 */
	private void showEnvironmentProperties(final IApplication application) {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				try {
					if (!application.hasSSHSession()) {
						application.setSSHSession(OpenShiftSshSessionFactory.getInstance().createSession(application));
					}
					List<String> props = application.getEnvironmentProperties();
					final MessageConsole console = ConsoleUtils.findMessageConsole(getMessageConsoleName(application));
					console.clearConsole();
					MessageConsoleStream stream = console.newMessageStream();
					for (String prop : props) {
						stream.println(prop);
					}
					ConsoleUtils.displayConsoleView(console);
				} catch (Exception e) {
					OpenShiftUIActivator.createErrorStatus("Failed to display remote environment variables", e);
				}
			}
		});
	}

	/**
	 * @return
	 */
	private String getMessageConsoleName(final IApplication application) {
		return MessageFormat.format(
				"Environment Variables for application {0} ({1})",
				application.getName(), application.getDomain().getId());
	}

}
