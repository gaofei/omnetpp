/*--------------------------------------------------------------*
  Copyright (C) 2006-2015 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  'License' for details on this and other legal matters.
*--------------------------------------------------------------*/

package org.omnetpp.scave.actions;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Display;
import org.omnetpp.scave.ScaveImages;
import org.omnetpp.scave.ScavePlugin;
import org.omnetpp.scave.editors.ScaveEditor;
import org.omnetpp.scave.python.ChartViewerBase;

/**
 * Copy chart image to the clipboard.
 */
public class CopyChartImageToClipboardAction extends AbstractScaveAction {
    public CopyChartImageToClipboardAction() {
        setText("Copy Image to Clipboard");
        setToolTipText("Copy Chart Image to Clipboard");
        setImageDescriptor(ScavePlugin.getImageDescriptor(ScaveImages.IMG_ETOOL16_COPY));
    }

    @Override
    protected void doRun(ScaveEditor editor, ISelection selection) {
        final ChartViewerBase chartViewer = editor.getActiveChartViewer();

        if (chartViewer != null) {
            BusyIndicator.showWhile(Display.getDefault(), new Runnable() {
                public void run() {
                    chartViewer.copyImageToClipboard();
                }
            });
        }

    }

    @Override
    protected boolean isApplicable(ScaveEditor editor, ISelection selection) {
        return editor.getActiveChartViewer() != null;
    }
}
