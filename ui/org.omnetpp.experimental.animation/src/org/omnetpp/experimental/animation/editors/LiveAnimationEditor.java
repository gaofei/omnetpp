package org.omnetpp.experimental.animation.editors;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.CoolItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.PartInitException;
import org.omnetpp.experimental.animation.AnimationPlugin;
import org.omnetpp.experimental.animation.controller.ILiveAnimationListener;
import org.omnetpp.experimental.animation.live.LiveAnimationController;
import org.omnetpp.experimental.animation.widgets.AnimationCanvas;
import org.omnetpp.experimental.simkernel.swig.Simkernel;
import org.omnetpp.experimental.simkernel.swig.cEnvir;
import org.omnetpp.experimental.simkernel.swig.cStaticFlag;

public class LiveAnimationEditor extends ReplayAnimationEditor implements ILiveAnimationListener {

	cStaticFlag staticFlag; 
	
	@Override
	public void init(IEditorSite site, IEditorInput input) throws PartInitException {
		super.init(site, input);
	}
	
	@Override
	public void dispose() {
		super.dispose();

		// see matching code in createPartControl()
		AnimationPlugin.getDefault().setCurrentLiveAnimation(null);
		
		// release simkernel
		shutdownSimulation();
	}

	@Override
	public void createPartControl(Composite parent) {
		// make sure there's only one simulation running at a time
		if (AnimationPlugin.getDefault().getCurrentLiveAnimation()!=null) {
			MessageDialog.openError(null, "Error", "There can only be one simulation running at a time.");
		    getSite().getPage().closeEditor(this, false);
		    return;
		}
		AnimationPlugin.getDefault().setCurrentLiveAnimation(this);

		// initialize simulation
		initSimulation();

		// now create controls.
		// note: cannot simply call super.createPartControl(parent) because
		// we want toolbars in different order (could use coolBar.setItemLayout() though)
		parent.setLayout(new GridLayout());
		
		createCoolbar(parent);
		createSimulationToolbar();
		createNavigationToolbar();
		createTimeGauges();
		createSpeedSlider();

		createAnimationController(parent);
		
		coolBar.setWrapIndices(new int[] {2,3});

	}

	private void initSimulation() {
		// read the ".launch" file and extract inifile name from it
	    IFile launchFile = ((IFileEditorInput)getEditorInput()).getFile();
		String inifilePathName = null;
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(launchFile.getContents()));
			String line = reader.readLine();
			String[] tokens = line.split(" ");
			if (tokens.length>=2 && tokens[0].equals("inifile"))
				inifilePathName = tokens[1];
			else
				throw new RuntimeException(launchFile+": invalid syntax"); //XXX better error handling
		} catch (CoreException e) {
			e.printStackTrace(); //XXX better error handling
			return; //XXX close editor etc
		} catch (IOException e) {
			e.printStackTrace(); //XXX better error handling
			return; //XXX close editor etc
		}

		// change into the ini file's directory
		IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
		IPath inifilePath = new Path(inifilePathName);
		if (!inifilePath.isAbsolute())
			inifilePath = launchFile.getParent().getFullPath().append(inifilePath);
		IResource inifile = workspaceRoot.findMember(inifilePath);
		if (!(inifile instanceof IFile))
			throw new RuntimeException("File "+inifilePath+" does not exist"); //XXX better error handling
		String inifileDir = inifile.getRawLocation().removeLastSegments(1).toOSString();

		Simkernel.changeToDir(inifileDir);

		staticFlag = new cStaticFlag(); // needed by sim kernel
		Simkernel.evSetup(inifile.getName()); // ev.setup(argc, argv);

		//TEST CODE:
		//Javaenv jenv = Simkernel.getJavaenv();
		//jenv.setJCallback(null, new DummyEnvirCallback());

		//cEnvir ev = Simkernel.getEV();
		//cSimulation simulation = Simkernel.getSimulation();

		//jenv.newRun(1);
		//for (int i=0; i<10; i++)
		//	jenv.doOneStep();
	}

	private void shutdownSimulation() {
		cEnvir ev = Simkernel.getEV();
		ev.shutdown();
		staticFlag.delete();
		staticFlag = null;
	}

	private void createSimulationToolbar() {
		ToolBar toolBar = new ToolBar(coolBar, SWT.NONE);

		ToolItem toolItem;

		toolItem = new ToolItem(toolBar, SWT.PUSH);
		toolItem.setToolTipText("Restart");
	    toolItem.setImage(AnimationPlugin.getImageDescriptor("icons/restart.gif").createImage());
		toolItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				getLiveAnimationController().restartSimulation();
			}
		});

		toolItem = new ToolItem(toolBar, SWT.PUSH);
		toolItem.setToolTipText("Step");
	    toolItem.setImage(AnimationPlugin.getImageDescriptor("icons/dooneevent.gif").createImage());
		toolItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				getLiveAnimationController().simulateStep();
			}
		});
		
		toolItem = new ToolItem(toolBar, SWT.PUSH);
		toolItem.setToolTipText("Run");
	    toolItem.setImage(AnimationPlugin.getImageDescriptor("icons/run.gif").createImage());
		toolItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				getLiveAnimationController().simulateRun();
			}
		});
		
		toolItem = new ToolItem(toolBar, SWT.PUSH);
		toolItem.setToolTipText("Fast");
	    toolItem.setImage(AnimationPlugin.getImageDescriptor("icons/fast.gif").createImage());
		toolItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				getLiveAnimationController().simulateFast();
			}
		});

		toolItem = new ToolItem(toolBar, SWT.PUSH);
		toolItem.setToolTipText("Express");
	    toolItem.setImage(AnimationPlugin.getImageDescriptor("icons/express.gif").createImage());
		toolItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				getLiveAnimationController().simulateExpress();
			}
		});

		toolItem = new ToolItem(toolBar, SWT.PUSH);
		toolItem.setToolTipText("Stop");
	    toolItem.setImage(AnimationPlugin.getImageDescriptor("icons/stopsim.gif").createImage());
		toolItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				getLiveAnimationController().simulateStop();
			}
		});

		toolItem = new ToolItem(toolBar, SWT.PUSH);
		toolItem.setToolTipText("Finish");
	    toolItem.setImage(AnimationPlugin.getImageDescriptor("icons/finish.gif").createImage());
		toolItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				getLiveAnimationController().finishSimulation();
			}
		});
		
		//toolBar.setSize(toolBar.computeSize(SWT.DEFAULT, SWT.DEFAULT)); XXX does not work

		CoolItem coolItem = new CoolItem(coolBar, SWT.NONE);
		coolItem.setControl(toolBar);

		coolItem.setSize(new Point(250, COOLBAR_HEIGHT));
	}
	
	@Override
	protected void createAnimationController(Composite parent) {
		AnimationCanvas canvas = new AnimationCanvas(parent, SWT.NONE);
		canvas.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		
		animationController = new LiveAnimationController(canvas);
		animationController.addAnimationListener(this);
		animationController.init();
	}

	protected LiveAnimationController getLiveAnimationController() {
		return ((LiveAnimationController)animationController);
	}

	public void livePositionChanged(long eventNumber, double simulationTime, long animationNumber, double animationTime) {
		// TODO:
	}
}