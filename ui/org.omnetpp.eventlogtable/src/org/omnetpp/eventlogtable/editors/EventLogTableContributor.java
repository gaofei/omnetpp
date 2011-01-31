/*--------------------------------------------------------------*
  Copyright (C) 2006-2008 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  'License' for details on this and other legal matters.
*--------------------------------------------------------------*/

package org.omnetpp.eventlogtable.editors;

import java.lang.reflect.Field;
import java.util.ArrayList;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.fieldassist.ContentProposalAdapter;
import org.eclipse.jface.fieldassist.IContentProposal;
import org.eclipse.jface.fieldassist.IContentProposalListener;
import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.window.Window;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ContributionItemFactory;
import org.eclipse.ui.fieldassist.ContentAssistCommandAdapter;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.internal.Workbench;
import org.eclipse.ui.keys.IBindingService;
import org.eclipse.ui.menus.CommandContributionItem;
import org.eclipse.ui.menus.CommandContributionItemParameter;
import org.eclipse.ui.part.EditorActionBarContributor;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;
import org.eclipse.ui.texteditor.StatusLineContributionItem;
import org.omnetpp.common.contentassist.ContentProposal;
import org.omnetpp.common.eventlog.EventLogEntryProposalProvider;
import org.omnetpp.common.eventlog.EventLogEntryReference;
import org.omnetpp.common.eventlog.EventLogInput;
import org.omnetpp.common.eventlog.GotoSimulationTimeDialog;
import org.omnetpp.common.eventlog.IEventLogChangeListener;
import org.omnetpp.common.image.ImageFactory;
import org.omnetpp.eventlog.engine.BeginSendEntry;
import org.omnetpp.eventlog.engine.EventLogEntry;
import org.omnetpp.eventlog.engine.FilteredEventLog;
import org.omnetpp.eventlog.engine.IEvent;
import org.omnetpp.eventlog.engine.IEventLog;
import org.omnetpp.eventlog.engine.IMessageDependency;
import org.omnetpp.eventlog.engine.IMessageDependencyList;
import org.omnetpp.eventlog.engine.MatchKind;
import org.omnetpp.eventlogtable.EventLogTablePlugin;
import org.omnetpp.eventlogtable.widgets.EventLogTable;


@SuppressWarnings("restriction")
public class EventLogTableContributor extends EditorActionBarContributor implements ISelectionChangedListener, IEventLogChangeListener {
	private static EventLogTableContributor singleton;

    public final static String TOOL_IMAGE_DIR = "icons/full/etool16/";

    public final static String IMAGE_NAME_MODE = TOOL_IMAGE_DIR + "NameMode.gif";

    public final static String IMAGE_LINE_FILTER_MODE = TOOL_IMAGE_DIR + "LineFilterMode.png";

	protected EventLogTable eventLogTable;

	protected Separator separatorAction;

	protected EventLogTableAction gotoMessageOriginAction;

	protected EventLogTableAction gotoMessageReuseAction;

    protected EventLogTableAction toggleBookmarkAction;

    protected EventLogTableMenuAction typeModeAction;

    protected EventLogTableMenuAction nameModeAction;

    protected EventLogTableMenuAction lineFilterModeAction;

	protected EventLogTableMenuAction displayModeAction;

	protected EventLogTableAction filterAction;

    protected EventLogTableAction refreshAction;

    protected StatusLineContributionItem filterStatus;

	/*************************************************************************************
	 * CONSTRUCTION
	 */

	public EventLogTableContributor() {
		this.separatorAction = new Separator();
		this.gotoMessageOriginAction = createGotoMessageOriginAction();
		this.gotoMessageReuseAction = createGotoMessageReuseAction();
        this.toggleBookmarkAction = createToggleBookmarkAction();
        this.typeModeAction = createTypeModeAction();
        this.nameModeAction = createNameModeAction();
		this.lineFilterModeAction = createLineFilterModeAction();
		this.displayModeAction = createDisplayModeAction();
		this.filterAction = createFilterAction();
		this.refreshAction = createRefreshAction();

		this.filterStatus = createFilterStatus();

		if (singleton == null)
			singleton = this;
	}

	public EventLogTableContributor(EventLogTable eventLogTable) {
		this();
		this.eventLogTable = eventLogTable;
		eventLogTable.addSelectionChangedListener(this);
	}

	@Override
	public void dispose() {
        if (eventLogTable != null)
            eventLogTable.removeSelectionChangedListener(this);

		eventLogTable = null;
		singleton = null;

		super.dispose();
	}

	private IEventLog getEventLog() {
		return eventLogTable.getEventLog();
	}

	public static EventLogTableContributor getDefault() {
		Assert.isTrue(singleton != null);

		return singleton;
	}

	/*************************************************************************************
	 * CONTRIBUTIONS
	 */

    public void contributeToPopupMenu(IMenuManager menuManager) {
		menuManager.add(createFindTextCommandContributionItem());
        menuManager.add(createFindNextCommandContributionItem());
		menuManager.add(separatorAction);

		// goto submenu
        IMenuManager subMenuManager = new MenuManager("Go To");
        menuManager.add(subMenuManager);
        subMenuManager.add(createGotoEventCommandContributionItem());
        subMenuManager.add(createGotoSimulationTimeCommandContributionItem());
        subMenuManager.add(separatorAction);
        subMenuManager.add(createGotoEventCauseCommandContributionItem());
        subMenuManager.add(createGotoMessageArrivalCommandContributionItem());
        subMenuManager.add(gotoMessageOriginAction);
        subMenuManager.add(gotoMessageReuseAction);
        subMenuManager.add(separatorAction);
        subMenuManager.add(createGotoPreviousEventCommandContributionItem());
        subMenuManager.add(createGotoNextEventCommandContributionItem());
        subMenuManager.add(createGotoPreviousModuleEventCommandContributionItem());
        subMenuManager.add(createGotoNextModuleEventCommandContributionItem());

        menuManager.add(separatorAction);
        menuManager.add(filterAction);
        menuManager.add(lineFilterModeAction);
        menuManager.add(separatorAction);
        menuManager.add(typeModeAction);
        menuManager.add(nameModeAction);
        menuManager.add(displayModeAction);
        menuManager.add(separatorAction);
        menuManager.add(toggleBookmarkAction);
        menuManager.add(createRefreshCommandContributionItem());
        menuManager.add(separatorAction);

        MenuManager showInSubmenu = new MenuManager(getShowInMenuLabel());
        IWorkbenchWindow workbenchWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        IContributionItem showInViewItem = ContributionItemFactory.VIEWS_SHOW_IN.create(workbenchWindow);
        showInSubmenu.add(showInViewItem);
        menuManager.add(showInSubmenu);

	}

    private String getShowInMenuLabel() {
        String keyBinding = null;

        IBindingService bindingService = (IBindingService)PlatformUI.getWorkbench().getAdapter(IBindingService.class);
        if (bindingService != null)
            keyBinding = bindingService.getBestActiveBindingFormattedFor("org.eclipse.ui.navigate.showInQuickMenu");

        if (keyBinding == null)
            keyBinding = "";

        return NLS.bind("Show In \t{0}", keyBinding);
    }

	@Override
	public void contributeToToolBar(IToolBarManager toolBarManager) {
        toolBarManager.add(separatorAction);
        toolBarManager.add(filterAction);
        toolBarManager.add(lineFilterModeAction);
        toolBarManager.add(separatorAction);
        toolBarManager.add(nameModeAction);
		toolBarManager.add(displayModeAction);
        toolBarManager.add(separatorAction);
		toolBarManager.add(refreshAction);
	}

	@Override
    public void contributeToStatusLine(IStatusLineManager statusLineManager) {
    	statusLineManager.add(filterStatus);
    }

	@Override
	public void setActiveEditor(IEditorPart targetEditor) {
		if (targetEditor instanceof EventLogTableEditor) {
			EventLogInput eventLogInput;
			if (eventLogTable != null) {
				eventLogInput = eventLogTable.getEventLogInput();
				if (eventLogInput != null)
					eventLogInput.removeEventLogChangedListener(this);

				eventLogTable.removeSelectionChangedListener(this);
			}

			eventLogTable = ((EventLogTableEditor)targetEditor).getEventLogTable();

			eventLogInput = eventLogTable.getEventLogInput();
			if (eventLogInput != null)
				eventLogInput.addEventLogChangedListener(this);

			eventLogTable.addSelectionChangedListener(this);

			update();
		}
	}

	public void update() {
		try {
			for (Field field : getClass().getDeclaredFields()) {
				Class<?> fieldType = field.getType();

				if (fieldType == EventLogTableAction.class ||
					fieldType == EventLogTableMenuAction.class)
				{
					EventLogTableAction fieldValue = (EventLogTableAction)field.get(this);

					fieldValue.setEnabled(true);
					fieldValue.update();
					if (eventLogTable.getEventLogInput().isLongRunningOperationInProgress())
						fieldValue.setEnabled(false);
				}

				if (fieldType == StatusLineContributionItem.class)
				{
					StatusLineContributionItem fieldValue = (StatusLineContributionItem)field.get(this);
					fieldValue.update();
				}
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

    private static void gotoEventLogEntry(EventLogTable eventLogTable, EventLogEntry entry, Action action, boolean gotoClosest) {
        String text = action != null ? action.getText() : "Go to";

        if (entry != null) {
            EventLogEntryReference reference = new EventLogEntryReference(entry);

            if (reference.isPresent(eventLogTable.getEventLog()))
                if (gotoClosest)
                    eventLogTable.gotoClosestElement(reference);
                else
                    eventLogTable.gotoElement(reference);
            else
                MessageDialog.openError(null, text, "Event not present in current event log input.");
        }
        else
            MessageDialog.openError(null, text, "No such event");
    }

	/*************************************************************************************
	 * NOTIFICATIONS
	 */

	public void selectionChanged(SelectionChangedEvent event) {
		update();
	}

	public void eventLogAppended() {
		// void
	}

    public void eventLogOverwritten() {
        // void
    }

	public void eventLogFilterRemoved() {
		update();
	}

	public void eventLogFiltered() {
		update();
	}

	public void eventLogLongOperationEnded() {
		update();
	}

	public void eventLogLongOperationStarted() {
		update();
	}

	public void eventLogProgress() {
		// void
	}

	/*************************************************************************************
	 * ACTIONS
	 */

    private CommandContributionItem createFindTextCommandContributionItem() {
        CommandContributionItemParameter parameter = new CommandContributionItemParameter(Workbench.getInstance(), null, "org.omnetpp.eventlogtable.findText", SWT.PUSH);
        parameter.icon = ImageFactory.getDescriptor(ImageFactory.TOOLBAR_IMAGE_SEARCH);
	    return new CommandContributionItem(parameter);
	}

    public static class FindTextHandler extends AbstractHandler {
        public Object execute(ExecutionEvent event) throws ExecutionException {
            IWorkbenchPart part = HandlerUtil.getActivePartChecked(event);

            if (part instanceof IEventLogTableProvider)
                ((IEventLogTableProvider)part).getEventLogTable().findText(false);

            return null;
        }
    }

    private CommandContributionItem createFindNextCommandContributionItem() {
        CommandContributionItemParameter parameter = new CommandContributionItemParameter(Workbench.getInstance(), null, "org.omnetpp.eventlogtable.findNext", SWT.PUSH);
        parameter.icon = ImageFactory.getDescriptor(ImageFactory.TOOLBAR_IMAGE_SEARCH_NEXT);
        return new CommandContributionItem(parameter);
    }

    public static class FindNextHandler extends AbstractHandler {
        public Object execute(ExecutionEvent event) throws ExecutionException {
            IWorkbenchPart part = HandlerUtil.getActivePartChecked(event);

            if (part instanceof IEventLogTableProvider)
                ((IEventLogTableProvider)part).getEventLogTable().findText(true);

            return null;
        }
    }

	private CommandContributionItem createGotoEventCauseCommandContributionItem() {
        return new CommandContributionItem(new CommandContributionItemParameter(Workbench.getInstance(), null, "org.omnetpp.eventlogtable.gotoEventCause", SWT.PUSH));
	}

    public static class GotoEventCauseHandler extends AbstractHandler {
        // TODO: setEnabled(getCauseEventLogEntry() != null);
        public Object execute(ExecutionEvent executionEvent) throws ExecutionException {
            IWorkbenchPart part = HandlerUtil.getActivePartChecked(executionEvent);

            if (part instanceof IEventLogTableProvider) {
                EventLogTable eventLogTable = ((IEventLogTableProvider)part).getEventLogTable();
                EventLogEntryReference eventLogEntryReference = eventLogTable.getSelectionElement();

                if (eventLogEntryReference != null) {
                    IEvent event = eventLogTable.getEventLog().getEventForEventNumber(eventLogEntryReference.getEventNumber());

                    if (event != null) {
                        IMessageDependency cause = event.getCause();

                        if (cause != null)
                            gotoEventLogEntry(eventLogTable, cause.getBeginSendEntry(), null, true);
                    }
                }
            }

            return null;
        }
    }

	private CommandContributionItem createGotoMessageArrivalCommandContributionItem() {
        return new CommandContributionItem(new CommandContributionItemParameter(Workbench.getInstance(), null, "org.omnetpp.eventlogtable.gotoMessageArrival", SWT.PUSH));
	}

    public static class GotoMessageArrivalHandler extends AbstractHandler {
        // TODO: setEnabled(getConsequenceEventLogEntry() != null);
        public Object execute(ExecutionEvent executionEvent) throws ExecutionException {
            IWorkbenchPart part = HandlerUtil.getActivePartChecked(executionEvent);

            if (part instanceof IEventLogTableProvider) {
                EventLogTable eventLogTable = ((IEventLogTableProvider)part).getEventLogTable();
                EventLogEntryReference eventLogEntryReference = eventLogTable.getSelectionElement();

                if (eventLogEntryReference != null) {
                    EventLogEntry eventLogEntry = eventLogEntryReference.getEventLogEntry(eventLogTable.getEventLogInput());
                    IEvent event = eventLogTable.getEventLog().getEventForEventNumber(eventLogEntryReference.getEventNumber());
                    IMessageDependencyList consequences = event.getConsequences();

                    if (consequences.size() == 1) {
                        IMessageDependency consequence = consequences.get(0);

                        if (!eventLogTable.getEventLogTableFacade().IMessageDependency_isReuse(consequence.getCPtr())) {
                            IEvent consequenceEvent = consequence.getConsequenceEvent();

                            if (consequenceEvent != null)
                                return consequenceEvent.getEventEntry();
                        }
                    }
                    else {
                        for (int i = 0; i < consequences.size(); i++) {
                            IMessageDependency consequence = consequences.get(i);

                            if (!eventLogTable.getEventLogTableFacade().IMessageDependency_isReuse(consequence.getCPtr()) &&
                                consequence.getBeginSendEntry().equals(eventLogEntry))
                            {
                                IEvent consequenceEvent = consequence.getConsequenceEvent();

                                if (consequenceEvent != null)
                                    gotoEventLogEntry(eventLogTable, consequenceEvent.getEventEntry(), null, false);
                            }
                        }
                    }
                }
            }

            return null;
        }
    }

	private EventLogTableAction createGotoMessageOriginAction() {
		return new EventLogTableAction("Goto Message Origin") {
			@Override
			protected void doRun() {
			    gotoEventLogEntry(eventLogTable, getMessageOriginEventLogEntry(), this, false);
			}

			@Override
			public void update() {
				setEnabled(getMessageOriginEventLogEntry() != null);
			}

			private EventLogEntry getMessageOriginEventLogEntry() {
				EventLogEntryReference eventLogEntryReference = eventLogTable.getSelectionElement();

				if (eventLogEntryReference != null) {
					EventLogEntry eventLogEntry = eventLogEntryReference.getEventLogEntry(eventLogTable.getEventLogInput());
                    IEvent event = getEventLog().getEventForEventNumber(eventLogEntryReference.getEventNumber());
					IMessageDependencyList causes = event.getCauses();

					for (int i = 0; i < causes.size(); i++) {
						IMessageDependency cause = causes.get(i);

						if (eventLogTable.getEventLogTableFacade().IMessageDependency_isReuse(cause.getCPtr()) &&
							cause.getBeginSendEntry().equals(eventLogEntry)) {
							IEvent causeEvent = cause.getCauseEvent();

							if (causeEvent != null)
								return causeEvent.getEventEntry();
						}
					}
				}

				return null;
			}
		};
	}

	private EventLogTableAction createGotoMessageReuseAction() {
		return new EventLogTableAction("Goto Message Reuse") {
			@Override
			protected void doRun() {
			    gotoEventLogEntry(eventLogTable, getMessageReuseEventLogEntry(), this, true);
			}

			@Override
			public void update() {
				setEnabled(getMessageReuseEventLogEntry() != null);
			}

			private EventLogEntry getMessageReuseEventLogEntry() {
				EventLogEntryReference eventLogEntryReference = eventLogTable.getSelectionElement();

				if (eventLogEntryReference != null) {
                    IEvent event = getEventLog().getEventForEventNumber(eventLogEntryReference.getEventNumber());
					IMessageDependencyList consequences = event.getConsequences();

					for (int i = 0; i < consequences.size(); i++) {
						IMessageDependency consequence = consequences.get(i);

						if (eventLogTable.getEventLogTableFacade().IMessageDependency_isReuse(consequence.getCPtr())) {
							BeginSendEntry beginSendEntry = consequence.getConsequenceBeginSendEntry();

							if (beginSendEntry != null)
								return beginSendEntry;
						}
					}
				}

				return null;
			}
		};
	}

    private CommandContributionItem createGotoPreviousEventCommandContributionItem() {
        return new CommandContributionItem(new CommandContributionItemParameter(Workbench.getInstance(), null, "org.omnetpp.eventlogtable.gotoPreviousEvent", SWT.PUSH));
    }

    public static class GotoPreviousEventHandler extends AbstractHandler {
        public Object execute(ExecutionEvent executionEvent) throws ExecutionException {
            IWorkbenchPart part = HandlerUtil.getActivePartChecked(executionEvent);

            if (part instanceof IEventLogTableProvider) {
                EventLogTable eventLogTable = ((IEventLogTableProvider)part).getEventLogTable();
                EventLogEntryReference eventLogEntryReference = eventLogTable.getSelectionElement();

                if (eventLogEntryReference != null) {
                    IEvent event = eventLogTable.getEventLog().getEventForEventNumber(eventLogEntryReference.getEventNumber());

                    if (event != null) {
                        event = event.getPreviousEvent();

                        if (event != null)
                            gotoEventLogEntry(eventLogTable, event.getEventEntry(), null, false);
                    }
                }
            }

            return null;
        }
    }

    private CommandContributionItem createGotoNextEventCommandContributionItem() {
        return new CommandContributionItem(new CommandContributionItemParameter(Workbench.getInstance(), null, "org.omnetpp.eventlogtable.gotoNextEvent", SWT.PUSH));
    }

    public static class GotoNextEventHandler extends AbstractHandler {
        public Object execute(ExecutionEvent executionEvent) throws ExecutionException {
            IWorkbenchPart part = HandlerUtil.getActivePartChecked(executionEvent);

            if (part instanceof IEventLogTableProvider) {
                EventLogTable eventLogTable = ((IEventLogTableProvider)part).getEventLogTable();
                EventLogEntryReference eventLogEntryReference = eventLogTable.getSelectionElement();

                if (eventLogEntryReference != null) {
                    IEvent event = eventLogTable.getEventLog().getEventForEventNumber(eventLogEntryReference.getEventNumber());

                    if (event != null) {
                        event = event.getNextEvent();

                        if (event != null)
                            gotoEventLogEntry(eventLogTable, event.getEventEntry(), null, false);
                    }
                }
            }

            return null;
        }
    }

    private CommandContributionItem createGotoPreviousModuleEventCommandContributionItem() {
        return new CommandContributionItem(new CommandContributionItemParameter(Workbench.getInstance(), null, "org.omnetpp.eventlogtable.gotoPreviousModuleEvent", SWT.PUSH));
    }

    public static class GotoPreviousModuleEventHandler extends AbstractHandler {
        public Object execute(ExecutionEvent executionEvent) throws ExecutionException {
            IWorkbenchPart part = HandlerUtil.getActivePartChecked(executionEvent);

            if (part instanceof IEventLogTableProvider) {
                EventLogTable eventLogTable = ((IEventLogTableProvider)part).getEventLogTable();
                EventLogEntryReference eventLogEntryReference = eventLogTable.getSelectionElement();

                if (eventLogEntryReference != null) {
                    IEvent event = eventLogTable.getEventLog().getEventForEventNumber(eventLogEntryReference.getEventNumber());

                    if (event != null) {
                        int moduleId = event.getModuleId();

                        while (event != null) {
                            event = event.getPreviousEvent();

                            if (event != null && moduleId == event.getModuleId()) {
                                gotoEventLogEntry(eventLogTable, event.getEventEntry(), null, false);
                                break;
                            }
                        }
                    }
                }
            }

            return null;
        }
    }

    private CommandContributionItem createGotoNextModuleEventCommandContributionItem() {
        return new CommandContributionItem(new CommandContributionItemParameter(Workbench.getInstance(), null, "org.omnetpp.eventlogtable.gotoNextModuleEvent", SWT.PUSH));
    }

    public static class GotoNextModuleEventHandler extends AbstractHandler {
        public Object execute(ExecutionEvent executionEvent) throws ExecutionException {
            IWorkbenchPart part = HandlerUtil.getActivePartChecked(executionEvent);

            if (part instanceof IEventLogTableProvider) {
                EventLogTable eventLogTable = ((IEventLogTableProvider)part).getEventLogTable();
                EventLogEntryReference eventLogEntryReference = eventLogTable.getSelectionElement();

                if (eventLogEntryReference != null) {
                    IEvent event = eventLogTable.getEventLog().getEventForEventNumber(eventLogEntryReference.getEventNumber());

                    if (event != null) {
                        int moduleId = event.getModuleId();

                        while (event != null) {
                            event = event.getNextEvent();

                            if (event != null && moduleId == event.getModuleId()) {
                                gotoEventLogEntry(eventLogTable, event.getEventEntry(), null, false);
                                break;
                            }
                        }
                    }
                }
            }

            return null;
        }
    }

    private CommandContributionItem createGotoEventCommandContributionItem() {
        return new CommandContributionItem(new CommandContributionItemParameter(Workbench.getInstance(), null, "org.omnetpp.eventlogtable.gotoEvent", SWT.PUSH));
    }

    public static class GotoEventHandler extends AbstractHandler {
        // TODO: setEnabled(!getEventLog().isEmpty());
        public Object execute(ExecutionEvent executionEvent) throws ExecutionException {
            IWorkbenchPart part = HandlerUtil.getActivePartChecked(executionEvent);

            if (part instanceof IEventLogTableProvider) {
                EventLogTable eventLogTable = ((IEventLogTableProvider)part).getEventLogTable();
				InputDialog dialog = new InputDialog(null, "Goto event", "Please enter the event number to go to", null, new IInputValidator() {
					public String isValid(String newText) {
						try {
							int eventNumber = Integer.parseInt(newText);

							if (eventNumber >= 0)
								return null;
							else
								return "Negative event number";
						}
						catch (Exception e) {
							return "Not a number";
						}
					}
				});

				if (dialog.open() == Window.OK) {
					try {
						int eventNumber = Integer.parseInt(dialog.getValue());
						IEventLog eventLog = eventLogTable.getEventLog();
						IEvent event = eventLog.getEventForEventNumber(eventNumber);

						if (event != null)
							eventLogTable.gotoElement(new EventLogEntryReference(event.getEventEntry()));
						else
							MessageDialog.openError(null, "Goto event" , "No such event: " + eventNumber);
					}
					catch (Exception x) {
						// void
					}
				}
			}

            return null;
		}
	}

	private CommandContributionItem createGotoSimulationTimeCommandContributionItem() {
        return new CommandContributionItem(new CommandContributionItemParameter(Workbench.getInstance(), null, "org.omnetpp.eventlogtable.gotoSimulationTime", SWT.PUSH));
	}

	public static class GotoSimulationTimeHandler extends AbstractHandler {
        // TODO: setEnabled(!getEventLog().isEmpty());
        public Object execute(ExecutionEvent executionEvent) throws ExecutionException {
            IWorkbenchPart part = HandlerUtil.getActivePartChecked(executionEvent);
            if (part instanceof IEventLogTableProvider) {
                EventLogTable eventLogTable = ((IEventLogTableProvider)part).getEventLogTable();
                EventLogEntryReference eventLogEntryReference = eventLogTable.getSelectionElement();
                if (eventLogEntryReference == null)
                    eventLogEntryReference = eventLogTable.getTopVisibleElement();
                GotoSimulationTimeDialog dialog = new GotoSimulationTimeDialog(eventLogTable.getEventLog(), eventLogEntryReference.getEvent(eventLogTable.getEventLogInput()).getSimulationTime());
				if (dialog.open() == Window.OK) {
					IEvent event = eventLogTable.getEventLog().getEventForSimulationTime(dialog.getSimulationTime(), MatchKind.FIRST_OR_NEXT);
					if (event != null)
						eventLogTable.gotoElement(new EventLogEntryReference(event.getEventEntry()));
				}
			}
            return null;
        }
	}

	private EventLogTableAction createToggleBookmarkAction() {
		return new EventLogTableAction("Toggle Bookmark", Action.AS_PUSH_BUTTON, ImageFactory.getDescriptor(ImageFactory.TOOLBAR_IMAGE_TOGGLE_BOOKMARK)) {
			@Override
			protected void doRun() {
				try {
					EventLogEntryReference eventLogEntryReference = eventLogTable.getSelectionElement();

					if (eventLogEntryReference != null) {
						IEvent event = eventLogEntryReference.getEventLogEntry(eventLogTable.getEventLogInput()).getEvent();
						EventLogInput eventLogInput = (EventLogInput)eventLogTable.getInput();

						boolean found = false;
						IMarker[] markers = eventLogInput.getFile().findMarkers(IMarker.BOOKMARK, true, IResource.DEPTH_ZERO);

						for (IMarker marker : markers)
							if (marker.getAttribute("EventNumber", "-1").equals(String.valueOf(event.getEventNumber()))) {
								marker.delete();
								found = true;
							}

						if (!found) {
                            InputDialog dialog = new InputDialog(null, "Add Bookmark", "Enter Bookmark name:", "", null);

                            if (dialog.open() == Window.OK) {
    							IMarker marker = eventLogInput.getFile().createMarker(IMarker.BOOKMARK);
    							marker.setAttribute(IMarker.LOCATION, "# " + event.getEventNumber());
    							marker.setAttribute("EventNumber", String.valueOf(event.getEventNumber()));
    							marker.setAttribute(IMarker.MESSAGE, dialog.getValue());
							}
						}

						update();
						eventLogTable.redraw();
					}
				}
				catch (CoreException e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public void update() {
				setEnabled(eventLogTable.getSelectionElement() != null);
			}
		};
	}

    private EventLogTableMenuAction createTypeModeAction() {
        return new EventLogTableMenuAction("Type Mode", Action.AS_DROP_DOWN_MENU) {
            private AbstractMenuCreator menuCreator;

            @Override
            protected void doRun() {
                EventLogTable.TypeMode[] values = EventLogTable.TypeMode.values();
                eventLogTable.setTypeMode(values[(getMenuIndex() + 1) % values.length]);
                update();
            }

            @Override
            protected int getMenuIndex() {
                return eventLogTable.getTypeMode().ordinal();
            }

            @Override
            public IMenuCreator getMenuCreator() {
                if (menuCreator == null) {
                    menuCreator = new AbstractMenuCreator() {
                        @Override
                        protected void createMenu(Menu menu) {
                            addSubMenuItem(menu, "C++", EventLogTable.TypeMode.CPP);
                            addSubMenuItem(menu, "NED", EventLogTable.TypeMode.NED);
                        }

                        private void addSubMenuItem(final Menu menu, String text, final EventLogTable.TypeMode typeMode) {
                            addSubMenuItem(menu, text, new SelectionAdapter() {
                                @Override
                                public void widgetSelected(SelectionEvent e) {
                                    MenuItem menuItem = (MenuItem)e.widget;

                                    if (menuItem.getSelection()) {
                                        eventLogTable.setTypeMode(typeMode);
                                        update();
                                    }
                                }
                            });
                        }

                        private void addSubMenuItem(Menu menu, String text, SelectionListener adapter) {
                            MenuItem subMenuItem = new MenuItem(menu, SWT.RADIO);
                            subMenuItem.setText(text);
                            subMenuItem.addSelectionListener(adapter);
                        }
                    };
                }

                return menuCreator;
            }
        };
    }

	private EventLogTableMenuAction createNameModeAction() {
        return new EventLogTableMenuAction("Name Mode", Action.AS_DROP_DOWN_MENU, EventLogTablePlugin.getImageDescriptor(IMAGE_NAME_MODE)) {
            private AbstractMenuCreator menuCreator;

            @Override
            protected void doRun() {
                EventLogTable.NameMode[] values = EventLogTable.NameMode.values();
                eventLogTable.setNameMode(values[(getMenuIndex() + 1) % values.length]);
                eventLogTable.configureVerticalScrollBar();
                update();
            }

            @Override
            protected int getMenuIndex() {
                return eventLogTable.getNameMode().ordinal();
            }

            @Override
            public IMenuCreator getMenuCreator() {
                if (menuCreator == null) {
                    menuCreator = new AbstractMenuCreator() {
                        @Override
                        protected void createMenu(Menu menu) {
                            addSubMenuItem(menu, "Smart Name", EventLogTable.NameMode.SMART_NAME);
                            addSubMenuItem(menu, "Full Name", EventLogTable.NameMode.FULL_NAME);
                            addSubMenuItem(menu, "Full Path", EventLogTable.NameMode.FULL_PATH);
                        }

                        private void addSubMenuItem(final Menu menu, String text, final EventLogTable.NameMode nameMode) {
                            addSubMenuItem(menu, text, new SelectionAdapter() {
                                @Override
                                public void widgetSelected(SelectionEvent e) {
                                    MenuItem menuItem = (MenuItem)e.widget;

                                    if (menuItem.getSelection()) {
                                        eventLogTable.setNameMode(nameMode);
                                        update();
                                    }
                                }
                            });
                        }

                        private void addSubMenuItem(Menu menu, String text, SelectionListener adapter) {
                            MenuItem subMenuItem = new MenuItem(menu, SWT.RADIO);
                            subMenuItem.setText(text);
                            subMenuItem.addSelectionListener(adapter);
                        }
                    };
                }

                return menuCreator;
            }
        };
    }

	private EventLogTableMenuAction createDisplayModeAction() {
		return new EventLogTableMenuAction("Display Mode", Action.AS_DROP_DOWN_MENU, ImageFactory.getDescriptor(ImageFactory.TOOLBAR_IMAGE_DISPLAY_MODE)) {
			private AbstractMenuCreator menuCreator;

			@Override
			protected void doRun() {
			    EventLogTable.DisplayMode[] values = EventLogTable.DisplayMode.values();
				eventLogTable.setDisplayMode(values[(getMenuIndex() + 1) % values.length]);
				eventLogTable.redraw();
				update();
			}

			@Override
			protected int getMenuIndex() {
				return eventLogTable.getDisplayMode().ordinal();
			}

			@Override
			public IMenuCreator getMenuCreator() {
				if (menuCreator == null) {
					menuCreator = new AbstractMenuCreator() {
						@Override
						protected void createMenu(Menu menu) {
							addSubMenuItem(menu, "Descriptive", EventLogTable.DisplayMode.DESCRIPTIVE);
							addSubMenuItem(menu, "Raw", EventLogTable.DisplayMode.RAW);
						}

						private void addSubMenuItem(Menu menu, String text, final EventLogTable.DisplayMode displayMode) {
							MenuItem subMenuItem = new MenuItem(menu, SWT.RADIO);
							subMenuItem.setText(text);
							subMenuItem.addSelectionListener( new SelectionAdapter() {
								@Override
                                public void widgetSelected(SelectionEvent e) {
									MenuItem menuItem = (MenuItem)e.widget;

									if (menuItem.getSelection()) {
										eventLogTable.setDisplayMode(displayMode);
										eventLogTable.redraw();
										update();
									}
								}
							});
						}
					};
				}

				return menuCreator;
			}
		};
	}

	private EventLogTableMenuAction createLineFilterModeAction() {
		return new EventLogTableMenuAction("Line Filter", Action.AS_DROP_DOWN_MENU, EventLogTablePlugin.getImageDescriptor(IMAGE_LINE_FILTER_MODE)) {
			private AbstractMenuCreator menuCreator;

			@Override
			protected void doRun() {
                if (!eventLogTable.hasInput())
                    return;
				eventLogTable.setLineFilterMode((eventLogTable.getLineFilterMode() + 1) % 5);
				update();
			}

			@Override
			protected int getMenuIndex() {
                if (!eventLogTable.hasInput())
                    return 0;
				return eventLogTable.getLineFilterMode();
			}

			@Override
			public IMenuCreator getMenuCreator() {
				if (menuCreator == null) {
					menuCreator = new AbstractMenuCreator() {
						@Override
						protected void createMenu(Menu menu) {
							addSubMenuItem(menu, "All", 0);
							addSubMenuItem(menu, "Events, message sends and log messages", 1);
							addSubMenuItem(menu, "Events and log messages", 2);
							addSubMenuItem(menu, "Events", 3);
							addSubMenuItem(menu, "Custom filter...", new SelectionAdapter() {
								@Override
                                public void widgetSelected(SelectionEvent e) {
									MenuItem menuItem = (MenuItem)e.widget;
					                if (!eventLogTable.hasInput())
					                    return;

									if (menuItem.getSelection()) {
										InputDialog dialog = new InputDialog(null, "Search pattern", "Please enter the search pattern such as: (BS and c(MyMessage))\nSee Event Log Table Raw Mode for other fields and entry types.", null, null) {
										    @Override
										    protected Control createDialogArea(Composite parent) {
										        Control control = super.createDialogArea(parent);
										        final Text text = getText();
										        ContentAssistCommandAdapter commandAdapter = new ContentAssistCommandAdapter(text, new TextContentAdapter(), new EventLogEntryProposalProvider(EventLogEntry.class),
										            ITextEditorActionDefinitionIds.CONTENT_ASSIST_PROPOSALS, "( ".toCharArray(), true);
										        commandAdapter.setProposalAcceptanceStyle(ContentProposalAdapter.PROPOSAL_IGNORE);
										        commandAdapter.addContentProposalListener(new IContentProposalListener() {
										            public void proposalAccepted(IContentProposal proposal) {
										                ContentProposal contentProposal = (ContentProposal)proposal;
                                                        int start = contentProposal.getStartIndex();
                                                        int end =contentProposal.getEndIndex();
                                                        int cursorPosition = contentProposal.getCursorPosition();
                                                        String content = contentProposal.getContent();
										                text.setSelection(start, end);
										                text.insert(content);
										                text.setSelection(start + cursorPosition, start + cursorPosition);
										            }
										        });
										        return control;
										    }
										};

										if (dialog.open() == Window.OK) {
    										String pattern = dialog.getValue();
    										if (pattern == null || pattern.equals(""))
    											pattern = "*";

    										eventLogTable.setCustomFilter(pattern);
    										eventLogTable.setLineFilterMode(4);
    										update();
										}
									}
								}
							});
						}

						private void addSubMenuItem(final Menu menu, String text, final int lineFilterMode) {
							addSubMenuItem(menu, text, new SelectionAdapter() {
								@Override
                                public void widgetSelected(SelectionEvent e) {
									MenuItem menuItem = (MenuItem)e.widget;

									if (menuItem.getSelection()) {
									    if (!eventLogTable.hasInput())
									        return;
										eventLogTable.setLineFilterMode(lineFilterMode);
										update();
									}
								}
							});
						}

						private void addSubMenuItem(Menu menu, String text, SelectionListener adapter) {
							MenuItem subMenuItem = new MenuItem(menu, SWT.RADIO);
							subMenuItem.setText(text);
							subMenuItem.addSelectionListener(adapter);
						}
					};
				}

				return menuCreator;
			}
		};
	}

	private EventLogTableAction createFilterAction() {
        return new EventLogTableMenuAction("Filter", Action.AS_DROP_DOWN_MENU, ImageFactory.getDescriptor(ImageFactory.TOOLBAR_IMAGE_FILTER)) {
            @Override
            protected void doRun() {
                if (isFilteredEventLog())
                    removeFilter();
                else
                    filter();
            }

            @Override
            protected int getMenuIndex() {
                if (isFilteredEventLog())
                    return 1;
                else
                    return 0;
            }

            private boolean isFilteredEventLog() {
                return getEventLog() instanceof FilteredEventLog;
            }

            @Override
            public IMenuCreator getMenuCreator() {
                return new AbstractMenuCreator() {
                    @Override
                    protected void createMenu(Menu menu) {
                        addSubMenuItem(menu, "Show All", new Runnable() {
                            public void run() {
                                removeFilter();
                            }
                        });
                        addSubMenuItem(menu, "Filter...", new Runnable() {
                            public void run() {
                                filter();
                            }
                        });
                    }

                    private void addSubMenuItem(Menu menu, String text, final Runnable runnable) {
                        MenuItem subMenuItem = new MenuItem(menu, SWT.RADIO);
                        subMenuItem.setText(text);
                        subMenuItem.addSelectionListener( new SelectionAdapter() {
                            @Override
                            public void widgetSelected(SelectionEvent e) {
                                MenuItem menuItem = (MenuItem)e.widget;

                                if (menuItem.getSelection()) {
                                    runnable.run();
                                    update();
                                }
                            }
                        });
                    }
                };
            }

			private void removeFilter() {
                if (!eventLogTable.hasInput())
                    return;

                final EventLogInput eventLogInput = eventLogTable.getEventLogInput();
				eventLogInput.runWithProgressMonitor(new Runnable() {
					public void run() {
						eventLogInput.removeFilter();

						eventLogTable.setInput(eventLogInput);

						update();
					}
				});
			}

			private void filter() {
                if (!eventLogTable.hasInput())
                    return;

                final EventLogInput eventLogInput = eventLogTable.getEventLogInput();
				if (eventLogInput.openFilterDialog() == Window.OK) {
                    eventLogInput.runWithProgressMonitor(new Runnable() {
                        public void run() {
							eventLogInput.filter();

							eventLogTable.setInput(eventLogInput);

							update();
                        }
                    });
				}
			}
		};
	}

	private EventLogTableAction createRefreshAction() {
        return new EventLogTableAction("Refresh", Action.AS_PUSH_BUTTON, ImageFactory.getDescriptor(ImageFactory.TOOLBAR_IMAGE_REFRESH)) {
            @Override
            protected void doRun() {
                eventLogTable.refresh();
            }
        };
	}

    private CommandContributionItem createRefreshCommandContributionItem() {
        CommandContributionItemParameter parameter = new CommandContributionItemParameter(Workbench.getInstance(), null, "org.omnetpp.eventlogtable.refresh", SWT.PUSH);
        parameter.icon = ImageFactory.getDescriptor(ImageFactory.TOOLBAR_IMAGE_REFRESH);
        return new CommandContributionItem(parameter);
    }

    public static class RefreshHandler extends AbstractHandler {
        public Object execute(ExecutionEvent event) throws ExecutionException {
            IWorkbenchPart part = HandlerUtil.getActivePartChecked(event);

            if (part instanceof IEventLogTableProvider)
                ((IEventLogTableProvider)part).getEventLogTable().refresh();

            return null;
        }
    }

	private StatusLineContributionItem createFilterStatus() {
		return new StatusLineContributionItem("Filter") {
			@Override
		    public void update() {
				setText(isFilteredEventLog() ? "Filtered" : "Unfiltered");
		    }

			private boolean isFilteredEventLog() {
				return eventLogTable.getEventLog() instanceof FilteredEventLog;
			}
		};
	}

    private abstract class EventLogTableAction extends Action {
		public EventLogTableAction(String text) {
			super(text);
		}

        public EventLogTableAction(String text, int style) {
            super(text, style);
        }

		public EventLogTableAction(String text, int style, ImageDescriptor image) {
			super(text, style);
			setImageDescriptor(image);
		}

		public void update() {
		}

        @Override
        public void run() {
            try {
                doRun();
            }
            catch (Exception e) {
                MessageDialog.openError(Display.getCurrent().getActiveShell(), "Error", "Internal error: " + e.toString());
                EventLogTablePlugin.logError(e);
            }
        }

        protected abstract void doRun();
	}

	private abstract class EventLogTableMenuAction extends EventLogTableAction {
		protected ArrayList<Menu> menus = new ArrayList<Menu>();

        public EventLogTableMenuAction(String text, int style) {
            super(text, style);
        }

		public EventLogTableMenuAction(String text, int style, ImageDescriptor image) {
			super(text, style, image);
		}

		@Override
		public void update() {
			for (Menu menu : menus)
				if (!menu.isDisposed())
					updateMenu(menu);
		}

		protected void addMenu(Menu menu) {
			Assert.isTrue(menu != null);

			menus.add(menu);
			updateMenu(menu);
		}

		protected void removeMenu(Menu menu) {
			Assert.isTrue(menu != null);

			menus.remove(menu);
		}

		protected abstract int getMenuIndex();

		protected void updateMenu(Menu menu) {
			for (int i = 0; i < menu.getItemCount(); i++) {
				boolean selection = i == getMenuIndex();
				MenuItem menuItem = menu.getItem(i);

				if (menuItem.getSelection() != selection)
					menuItem.setSelection(selection);
			}
		}

		protected abstract class AbstractMenuCreator implements IMenuCreator {
			private Menu controlMenu;

			private Menu parentMenu;

			public void dispose() {
				if (controlMenu != null) {
					controlMenu.dispose();
					removeMenu(controlMenu);
				}

				if (parentMenu != null) {
					parentMenu.dispose();
					removeMenu(parentMenu);
				}
			}

			public Menu getMenu(Control parent) {
				if (controlMenu == null) {
					controlMenu = new Menu(parent);
					createMenu(controlMenu);
					addMenu(controlMenu);
				}

				return controlMenu;
			}

			public Menu getMenu(Menu parent) {
				if (parentMenu == null) {
					parentMenu = new Menu(parent);
					createMenu(parentMenu);
					addMenu(parentMenu);
				}

				return parentMenu;
			}

			protected abstract void createMenu(Menu menu);
		}
	}
}
