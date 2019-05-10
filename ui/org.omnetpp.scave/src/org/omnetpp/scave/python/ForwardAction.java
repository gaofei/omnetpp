package org.omnetpp.scave.python;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.omnetpp.scave.actions.AbstractScaveAction;
import org.omnetpp.scave.editors.ChartScriptEditor;
import org.omnetpp.scave.editors.ScaveEditor;

public class ForwardAction extends AbstractScaveAction {
    public ForwardAction() {
        setText("Forward");
        setDescription("TODO.");
        setImageDescriptor(
                PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_TOOL_FORWARD));
    }

    @Override
    protected void doRun(ScaveEditor scaveEditor, IStructuredSelection selection) {
        ChartScriptEditor editor = scaveEditor.getActiveChartScriptEditor();
        if (editor != null)
            editor.getMatplotlibChartViewer().forward();
    }

    @Override
    protected boolean isApplicable(ScaveEditor scaveEditor, IStructuredSelection selection) {
        ChartScriptEditor editor = scaveEditor.getActiveChartScriptEditor();
        return editor != null && editor.isPythonProcessAlive();
    }
}